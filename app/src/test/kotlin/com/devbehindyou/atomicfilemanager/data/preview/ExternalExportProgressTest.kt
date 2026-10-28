package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.AccessTarget
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class, shadows = [OwnedExportFileProviderShadow::class])
@OptIn(ExperimentalCoroutinesApi::class)
class ExternalExportProgressTest {
    private lateinit var ownedCache: File
    private lateinit var context: Context
    private val payload = ByteArray(16) { (it + 1).toByte() }

    @Before
    fun createOwnedCache() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        ownedCache = File(app.cacheDir, "export-contract-${UUID.randomUUID()}").apply { check(mkdir()) }
        context =
            object : ContextWrapper(app) {
                override fun getCacheDir() = ownedCache
            }
    }

    @After
    fun removeOnlyOwnedFixtures() {
        exports().forEach { check(it.delete()) }
        val directory = File(ownedCache, "external-preview")
        if (directory.exists()) check(directory.delete())
        ownedCache.listFiles().orEmpty().forEach { check(it.delete()) }
        check(ownedCache.delete())
    }

    @Test
    fun reportsZeroFirstWriteThrottledBoundaryAndForcedEofFromActualWrites() =
        runBlocking {
            val (node, backend) = fixture(payload, metadataSize = 1)
            val clock = listOf(0L, 10L, 109L, 110L, 115L).iterator()
            val reports = mutableListOf<Long>()
            val lengths = mutableListOf<Long>()
            val helper = ExternalPreviewHelper(context, clockMillis = { clock.next() }) { backend }
            assertTrue(
                helper.open(node, onProgress = {
                    reports += it.bytesCopied
                    lengths += exports().single().length()
                }) {
                    assertArrayEquals(payload, exports().single().readBytes())
                } is FileResult.Success,
            )
            assertEquals(listOf(0L, 4L, 12L, 16L), reports)
            assertEquals(reports, lengths)
        }

    @Test
    fun unknownZeroAndStaleSizesNeverLimitRealCopyOrInventProgress() =
        runBlocking {
            for (metadataSize in listOf(-1L, 0L, 1L)) {
                val (node, backend) = fixture(payload, metadataSize)
                val reports = mutableListOf<Long>()
                val helper = ExternalPreviewHelper(context, maxExportBytes = 16, clockMillis = { 0L }) { backend }
                val before = exports()
                assertTrue(
                    helper.open(node, onProgress = { reports += it.bytesCopied }) {
                        assertArrayEquals(payload, (exports() - before).single().readBytes())
                    } is FileResult.Success,
                )
                assertEquals(listOf(0L, 4L, 16L), reports)
                assertEquals(before.size + 1, exports().size)
            }
        }

    @Test
    fun emptySourceForcesZeroEofAndDirectContentUrisDoNotReportCopies() =
        runBlocking {
            val (node, backend) = fixture(byteArrayOf(), metadataSize = 9)
            val reports = mutableListOf<Long>()
            val helper = ExternalPreviewHelper(context) { backend }
            assertTrue(helper.open(node, onProgress = { reports += it.bytesCopied }) { } is FileResult.Success)
            assertEquals(listOf(0L, 0L), reports)
            val before = exports()
            val direct = ExternalPreviewHelper(context) { error("Direct URI must not acquire backend") }
            val noCopy: (ExternalExportProgress) -> Unit = { error("Direct URI must not report copied bytes") }
            for (id in listOf(
                FileNodeId.saf("content://provider/tree/root/document/report"),
                FileNodeId.media("external", "images", 42),
            )) {
                assertTrue(direct.open(node.copy(id = id), onProgress = noCopy) { } is FileResult.Success)
            }
            assertEquals(before, exports())
        }

    @Test
    fun actualCapRejectsOverflowWithoutPublishingOrLeavingPartialFiles() =
        runBlocking {
            val before = exports()
            val (node, backend) = fixture(payload, metadataSize = 0)
            val reports = mutableListOf<Long>()
            val helper = ExternalPreviewHelper(context, maxExportBytes = 8, clockMillis = { 0L }) { backend }
            val result =
                helper.open(
                    node,
                    onProgress = { reports += it.bytesCopied },
                ) { error("Overflow must not launch") }
            assertTrue(result is FileResult.Failure)
            assertEquals(FileError.FileTooLarge(node.name, 8), (result as FileResult.Failure).error)
            assertEquals(listOf(0L, 4L), reports)
            assertEquals(before, exports())
        }

    @Test
    fun callbackCancellationBeforeAcquisitionOrAfterWriteRemovesOnlyItsUnpublishedCopy() =
        runBlocking {
            for (cancelAt in listOf(0L, 4L, 16L)) {
                val (node, backend) = fixture(payload, metadataSize = -1)
                var acquired = 0
                var closed = false
                val observed =
                    object : StorageBackend by backend {
                        override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                            acquired++
                            val stream = chunked(payload) { closed = true }
                            return FileResult.Success(InputStreamProvider { stream })
                        }
                    }
                val before = exports()
                val helper = ExternalPreviewHelper(context, clockMillis = { 0L }) { observed }
                var cancelled = false
                try {
                    helper.open(node, onProgress = {
                        if (it.bytesCopied == cancelAt) throw CancellationException("Controlled callback cancellation")
                    }) { error("Cancelled preparation must not launch") }
                } catch (_: CancellationException) {
                    cancelled = true
                }
                assertTrue(cancelled)
                assertEquals(if (cancelAt == 0L) 0 else 1, acquired)
                assertEquals(cancelAt != 0L, closed)
                assertEquals(before, exports())
            }
        }

    @Test
    fun successfulLaunchAcknowledgementKeepsTheCopyWhenItsOwnerThenCancels() =
        runTest {
            val (node, backend) = fixture(payload, metadataSize = -1)
            val owner = Job()
            var launched = false
            val helper = ExternalPreviewHelper(context, StandardTestDispatcher(testScheduler), 32) { backend }
            launch(owner) {
                helper.open(node) {
                    launched = true
                    assertArrayEquals(payload, exports().single().readBytes())
                    owner.cancel()
                }
            }
            advanceUntilIdle()
            assertTrue(launched)
            assertTrue(owner.isCancelled)
            assertArrayEquals(payload, exports().single().readBytes())
        }

    @Test
    fun nextExportPrunesAtMost128ExpiredCopiesAndPreservesFreshFiles() =
        runBlocking {
            val directory = File(ownedCache, "external-preview").apply { check(mkdir()) }
            val oldTime = System.currentTimeMillis() - 25L * 60 * 60 * 1000
            val expired =
                (1..140).map { index ->
                    File(directory, "old-$index.bin").apply {
                        writeBytes(byteArrayOf(index.toByte()))
                        check(setLastModified(oldTime))
                    }
                }
            val fresh = File(directory, "fresh-sentinel.bin").apply { writeBytes(byteArrayOf(7, 8, 9)) }
            val (node, backend) = fixture(payload, metadataSize = -1)
            assertTrue(ExternalPreviewHelper(context) { backend }.open(node) { } is FileResult.Success)
            val removed = expired.count { !it.exists() }
            assertTrue("Bounded pruning must do useful work", removed > 0)
            assertTrue("Never inspect/delete beyond the 128-entry pass", removed <= 128)
            assertTrue(expired.count { it.exists() } >= 12)
            assertArrayEquals(byteArrayOf(7, 8, 9), fresh.readBytes())
            assertFalse(exports().isEmpty())
        }

    @Test
    fun backendFailureKeepsItsTypedErrorAndRemovesTheUnpublishedCopy() =
        runBlocking {
            val (node, backend) = fixture(payload, metadataSize = -1)
            val denied = FileError.PermissionDenied(AccessTarget.AllFiles)
            val unavailable =
                object : StorageBackend by backend {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                        FileResult.Failure(denied)
                }
            val reports = mutableListOf<Long>()
            val helper = ExternalPreviewHelper(context) { unavailable }
            val before = exports()
            val result =
                helper.open(
                    node,
                    onProgress = { reports += it.bytesCopied },
                ) { error("Denied must not launch") }
            assertEquals(FileResult.Failure(denied), result)
            assertEquals(listOf(0L), reports)
            assertEquals(before, exports())
        }

    private suspend fun fixture(
        bytes: ByteArray,
        metadataSize: Long,
    ): Pair<FileNode, StorageBackend> {
        val memory = InMemoryBackend()
        val id = memory.putFile(memory.rootId, "controlled.bin", bytes)
        val original = (memory.getNode(id) as FileResult.Success).value
        val node = original.copy(id = FileNodeId.ftp("contract", "/controlled.bin"), size = metadataSize)
        val backend =
            object : StorageBackend by memory {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                    FileResult.Success(InputStreamProvider { chunked(bytes) {} })
            }
        return node to backend
    }

    private fun chunked(
        bytes: ByteArray,
        onClose: () -> Unit,
    ) = object : ByteArrayInputStream(bytes) {
        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = super.read(buffer, offset, minOf(length, 4))

        override fun close() {
            onClose()
            super.close()
        }
    }

    private fun exports() = File(ownedCache, "external-preview").listFiles().orEmpty().toSet()
}
