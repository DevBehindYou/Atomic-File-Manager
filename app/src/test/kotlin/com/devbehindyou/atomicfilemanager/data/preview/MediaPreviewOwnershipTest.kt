package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MediaPreviewOwnershipTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val metadata = MediaMetadata(null, null, null, 0, 0, 0, null, null)

    @Test
    fun cacheCreationFailureDoesNotAcquireEagerBackendInput() =
        runBlocking {
            val unavailableDirectory = File.createTempFile("unavailable-cache-", ".tmp", context.cacheDir)
            try {
                val badCache =
                    object : ContextWrapper(context) {
                        override fun getCacheDir() = unavailableDirectory
                    }
                val backend = InMemoryBackend()
                var acquired = false
                val eagerBackend =
                    object : StorageBackend by backend {
                        override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                            acquired = true
                            return FileResult.Success(InputStreamProvider { ByteArrayInputStream(byteArrayOf(1)) })
                        }
                    }
                val helper = MediaPreviewHelper(badCache, metadataReader = { metadata }) { eagerBackend }
                assertTrue(helper.prepareMedia(FileNodeId.ftp("server", "/clip.mp4"), "clip.mp4") is FileResult.Failure)
                assertFalse(acquired)
            } finally {
                unavailableDirectory.delete()
            }
        }

    @Test
    fun eagerBackendStreamClosesOnCopyAndMetadataFailures() =
        runBlocking {
            for (failRead in listOf(false, true)) {
                var closed = false
                val backend = InMemoryBackend()
                val eagerBackend =
                    object : StorageBackend by backend {
                        override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                            val stream =
                                object : ByteArrayInputStream(byteArrayOf(1)) {
                                    override fun read(
                                        bytes: ByteArray,
                                        offset: Int,
                                        length: Int,
                                    ): Int {
                                        if (failRead) throw IOException("read failed")
                                        return super.read(bytes, offset, length)
                                    }

                                    override fun close() {
                                        closed = true
                                        super.close()
                                    }
                                }
                            return FileResult.Success(InputStreamProvider { stream })
                        }
                    }
                val before = stagedFiles()
                val helper =
                    MediaPreviewHelper(context, metadataReader = {
                        throw IOException("metadata failed")
                    }) { eagerBackend }
                assertTrue(helper.prepareMedia(FileNodeId.ftp("server", "/clip.mp4"), "clip.mp4") is FileResult.Failure)
                assertTrue(closed)
                assertEquals(before, stagedFiles())
            }
        }

    @Test
    fun localMediaUsesOriginalWithoutCopyAndCleanupNeverDeletesIt() =
        runBlocking {
            val source = File.createTempFile("local-media-", ".mp4", context.cacheDir)
            try {
                val helper =
                    MediaPreviewHelper(
                        context,
                        metadataReader = { metadata },
                    ) { error("Local media must not be copied") }
                val result =
                    helper.withMedia(FileNodeId.file(source.absolutePath), source.name) { media ->
                        assertEquals(Uri.fromFile(source), media.playbackUri)
                        assertTrue(source.exists())
                    }
                assertTrue(result is FileResult.Success)
                assertTrue(source.exists())
            } finally {
                source.delete()
            }
        }

    @Test
    fun stagingHasActualByteCapAndExceptionCleanup() =
        runBlocking {
            val backend = InMemoryBackend()
            val id = backend.putFile(backend.rootId, "clip.mp4", ByteArray(9))
            val before = stagedFiles()
            val helper = MediaPreviewHelper(context, maxCacheBytes = 8, metadataReader = { metadata }) { backend }
            assertTrue(helper.prepareMedia(id, "clip.mp4") is FileResult.Failure)
            assertEquals(before, stagedFiles())
            val broken =
                MediaPreviewHelper(context, metadataReader = { throw IOException("metadata failed") }) { backend }
            assertTrue(broken.prepareMedia(id, "clip.mp4") is FileResult.Failure)
            assertEquals(before, stagedFiles())
        }

    @Test
    fun exactLimitIsAcceptedAndStructuredLifetimeRemovesTheCopyOnIO() =
        runBlocking {
            val backend = InMemoryBackend()
            val id = backend.putFile(backend.rootId, "clip.mp4", ByteArray(8))
            val caller = Thread.currentThread()
            var worker: Thread? = null
            val before = stagedFiles()
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
                val helper =
                    MediaPreviewHelper(context, io, maxCacheBytes = 8, metadataReader = {
                        worker = Thread.currentThread()
                        metadata
                    }) { backend }
                val result =
                    helper.withMedia(id, "clip.mp4") { media ->
                        assertEquals(8L, File(media.filePath).length())
                        assertEquals(caller, Thread.currentThread())
                    }
                assertTrue(result is FileResult.Success)
                assertNotSame(caller, worker)
            }
            assertEquals(before, stagedFiles())
        }

    @Test
    fun cancellationDuringAcquisitionAndWhileDisplayedDoesNotLeak() =
        runTest {
            val backend = InMemoryBackend()
            val id = backend.putFile(backend.rootId, "clip.mp4", ByteArray(8))
            val before = stagedFiles()
            val io = StandardTestDispatcher(testScheduler)
            val owner = Job()
            var published = false
            val cancelledHelper =
                MediaPreviewHelper(context, io, metadataReader = {
                    owner.cancel()
                    metadata
                }) { backend }
            launch(owner) { cancelledHelper.withMedia(id, "clip.mp4") { published = true } }
            advanceUntilIdle()
            assertFalse(published)
            assertEquals(before, stagedFiles())

            var opened = false
            val helper = MediaPreviewHelper(context, io, metadataReader = { metadata }) { backend }
            val job =
                launch {
                    helper.withMedia<Unit>(id, "clip.mp4") {
                        opened = true
                        awaitCancellation()
                    }
                }
            advanceUntilIdle()
            assertTrue(opened)
            assertEquals(before.size + 1, stagedFiles().size)
            job.cancel()
            advanceUntilIdle()
            assertEquals(before, stagedFiles())
        }

    private fun stagedFiles() =
        context.cacheDir.listFiles().orEmpty().filter {
            it.name.startsWith("media-preview-")
        }.toSet()
}
