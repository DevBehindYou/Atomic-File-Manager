package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.webkit.MimeTypeMap
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SelectedContentMimeResolverTest {
    private val node =
        FileNode(
            FileNodeId.ftp("owned-prefix", "/opaque.atomic_unknown"), "opaque.atomic_unknown", "opaque.atomic_unknown",
            "application/octet-stream", -1, 1000, false, false, null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )

    @Test
    fun aLargeNonSeekableSourceReturnsOnlyEightBytesAndClosesBeforeRecognitionReturns() =
        runBlocking {
            val input = PrefixInput(PNG, trailingSize = 1_000_000)
            val calls = AtomicInteger()
            val helper =
                resolver {
                    calls.incrementAndGet()
                    input
                }
            assertEquals(SelectedContentType.Ready(node.copy(mimeType = "image/png")), helper.resolve(node))
            assertEquals(1, calls.get())
            assertEquals(8, input.returned)
            assertEquals(1, input.closed)
        }

    @Test
    fun shortAndZeroBulkReadsMakeBoundedProgressWithoutTreatingNulAsNoProgress() =
        runBlocking {
            val input = PrefixInput(PNG, chunk = 2, zeroBulk = true)
            assertTrue(resolver { input }.resolve(node) is SelectedContentType.Ready)
            assertEquals(8, input.returned)
            assertTrue(input.bulkCalls <= 8)
            assertEquals(1, input.closed)
            val nul = PrefixInput(ByteArray(8), zeroBulk = true)
            assertEquals(SelectedContentType.Unresolved(node), resolver { nul }.resolve(node))
            assertEquals(8, nul.returned)
            assertEquals(1, nul.closed)
        }

    @Test
    fun emptyTruncatedAndZipHeadersStayUnresolvedAndCloseTheirOwnedStreams() =
        runBlocking {
            val cases = (0..7).map { PNG.copyOf(it) } + listOf(byteArrayOf(80, 75, 3, 4, 0, 0, 0, 0))
            cases.forEach { bytes ->
                val input = PrefixInput(bytes)
                assertEquals(SelectedContentType.Unresolved(node), resolver { input }.resolve(node))
                assertEquals(bytes.size, input.returned)
                assertEquals(1, input.closed)
            }
        }

    @Test
    fun validMimeKnownExtensionAndDirectoryNeverAcquireABackend() =
        runBlocking {
            shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("png", "image/png")
            val helper = SelectedContentMimeResolver { error("Metadata must not select a backend") }
            val valid = node.copy(mimeType = "application/pdf")
            assertEquals(SelectedContentType.Ready(valid), helper.resolve(valid))
            val known = node.copy(name = "known.png")
            assertEquals(SelectedContentType.Ready(known), helper.resolve(known))
            val directory = node.copy(isDirectory = true)
            assertEquals(SelectedContentType.Unresolved(directory), helper.resolve(directory))
        }

    @Test
    fun unsupportedBackendTypesRemainMetadataOnlyWithoutOpeningAStream() =
        runBlocking {
            val supported = setOf(BackendType.FILE, BackendType.SAF, BackendType.MEDIASTORE)
            BackendType.entries.filterNot { it in supported }.forEach { type ->
                val acquisitions = AtomicInteger()
                val delegate = backend(type) { error("Unsupported $type must not open content") }
                val backend =
                    object : StorageBackend by delegate {
                        override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                            acquisitions.incrementAndGet()
                            return FileResult.Success(InputStreamProvider { error("Unsupported stream factory") })
                        }
                    }
                assertEquals(
                    SelectedContentType.Unresolved(node),
                    SelectedContentMimeResolver { backend }.resolve(node),
                )
                assertEquals("Unsupported $type must not acquire eager backend resources", 0, acquisitions.get())
            }
        }

    @Test
    fun metadataOnlyEligibilityOffersIdentificationWithoutAcquiringAnySupportedContent() =
        runBlocking {
            listOf(BackendType.FILE, BackendType.SAF, BackendType.MEDIASTORE).forEach { type ->
                val acquisitions = AtomicInteger()
                val delegate = backend(type) { error("Eligibility must not create a stream") }
                val candidate =
                    object : StorageBackend by delegate {
                        override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                            acquisitions.incrementAndGet()
                            return delegate.openInput(id)
                        }
                    }
                assertEquals(
                    SelectedContentType.Unresolved(node, canIdentify = true),
                    SelectedContentMimeResolver { candidate }.resolve(node, allowProbe = false),
                )
                assertEquals(0, acquisitions.get())
            }
            val unsupported = backend(BackendType.FTP) { error("FTP cannot identify") }
            assertEquals(
                SelectedContentType.Unresolved(node),
                SelectedContentMimeResolver { unsupported }.resolve(node, allowProbe = false),
            )
        }

    @Test
    fun everyEligibleBackendTypeReadsOnlyItsOwnedPrefixAndClosesIt() =
        runBlocking {
            listOf(BackendType.FILE, BackendType.SAF, BackendType.MEDIASTORE).forEach { type ->
                val owned = PrefixInput(PNG, trailingSize = 1_000_000)
                val converted = AtomicInteger()
                val backend =
                    backend(type) {
                        converted.incrementAndGet()
                        owned
                    }
                assertEquals(
                    SelectedContentType.Ready(node.copy(mimeType = "image/png")),
                    SelectedContentMimeResolver { backend }.resolve(node),
                )
                assertEquals(1, converted.get())
                assertEquals(8, owned.returned)
                assertEquals(1, owned.closed)
            }
        }

    @Test
    fun cancellationWhileSelectingABackendDoesNotBeginAcquisition() =
        runBlocking {
            val selecting = CountDownLatch(1)
            val release = CountDownLatch(1)
            val acquisitions = AtomicInteger()
            val backend =
                object : StorageBackend by InMemoryBackend() {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                        acquisitions.incrementAndGet()
                        return FileResult.Success(InputStreamProvider { error("Cancelled acquisition") })
                    }
                }
            val helper =
                SelectedContentMimeResolver {
                    selecting.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    backend
                }
            var published = false
            val work =
                launch {
                    helper.resolve(node)
                    published = true
                }
            try {
                withContext(Dispatchers.IO) { assertTrue(selecting.await(10, TimeUnit.SECONDS)) }
                work.cancel()
            } finally {
                release.countDown()
                work.join()
            }
            assertTrue(work.isCancelled)
            assertFalse(published)
            assertEquals(0, acquisitions.get())
        }

    @Test
    fun backendFailurePreservesItsTypedError() =
        runBlocking {
            val denied = FileError.AccessDenied(node.name)
            val backend =
                object : StorageBackend by InMemoryBackend() {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                        FileResult.Failure(denied)
                }
            assertEquals(
                SelectedContentType.Failure(node, denied),
                SelectedContentMimeResolver { backend }.resolve(node),
            )
        }

    @Test
    fun factoryReadAndCloseFailuresNeverBecomeRecognizedContent() =
        runBlocking {
            assertTrue(resolver { throw IOException("factory") }.resolve(node) is SelectedContentType.Failure)
            val readFailure =
                object : PrefixInput(PNG) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = throw IOException("read")
                }
            assertTrue(resolver { readFailure }.resolve(node) is SelectedContentType.Failure)
            assertEquals(1, readFailure.closed)
            val owned = PrefixInput(PNG)
            val closeFailure =
                object : FilterInputStream(owned) {
                    override fun close() {
                        super.close()
                        throw IOException("close")
                    }
                }
            assertTrue(resolver { closeFailure }.resolve(node) is SelectedContentType.Failure)
            assertEquals(8, owned.returned)
            assertEquals(1, owned.closed)
        }

    @Test
    fun cancellationAfterEagerAcquisitionStillConvertsAndClosesTheProviderBeforeReturning() =
        runTest {
            val acquired = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val eager = PrefixInput(PNG)
            val converted = AtomicInteger()
            val backend =
                object : StorageBackend by InMemoryBackend() {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                        acquired.complete(Unit)
                        release.await()
                        return FileResult.Success(
                            InputStreamProvider {
                                converted.incrementAndGet()
                                eager
                            },
                        )
                    }
                }
            var published = false
            val helper = SelectedContentMimeResolver(StandardTestDispatcher(testScheduler)) { backend }
            val work =
                launch {
                    helper.resolve(node)
                    published = true
                }
            runCurrent()
            assertTrue(acquired.isCompleted)
            work.cancel()
            runCurrent()
            assertFalse(work.isCompleted)
            assertEquals(0, eager.closed)
            release.complete(Unit)
            runCurrent()
            work.join()
            assertTrue(work.isCancelled)
            assertFalse(published)
            assertEquals(1, converted.get())
            assertEquals(0, eager.returned)
            assertEquals(1, eager.closed)
        }

    @Test
    fun blockedReadReleasedAfterCancelClosesWithoutPublishingItsRecognizableBytes() =
        runBlocking {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val closed = AtomicInteger()
            val input =
                object : InputStream() {
                    override fun read(): Int = error("Only a bounded bulk read is needed")

                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        assertEquals(8, length)
                        started.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        PNG.copyInto(buffer, offset)
                        return PNG.size
                    }

                    override fun close() {
                        closed.incrementAndGet()
                    }
                }
            var published = false
            val helper = SelectedContentMimeResolver { backend { input } }
            val work =
                launch {
                    helper.resolve(node)
                    published = true
                }
            try {
                withContext(Dispatchers.IO) { assertTrue(started.await(10, TimeUnit.SECONDS)) }
                work.cancel()
                assertFalse(work.isCompleted)
                assertEquals(0, closed.get())
            } finally {
                release.countDown()
                work.join()
            }
            assertTrue(work.isCancelled)
            assertFalse(published)
            assertEquals(1, closed.get())
        }

    @Test
    fun cancellationInsideCloseIsNotReturnedAsReadyOnAnUnchangedDispatcher() =
        runTest {
            lateinit var work: kotlinx.coroutines.Job
            val owned = PrefixInput(PNG)
            val input =
                object : FilterInputStream(owned) {
                    override fun close() {
                        super.close()
                        work.cancel()
                    }
                }
            var published = false
            val helper = SelectedContentMimeResolver(StandardTestDispatcher(testScheduler)) { backend { input } }
            work =
                launch {
                    helper.resolve(node)
                    published = true
                }
            runCurrent()
            work.join()
            assertTrue(work.isCancelled)
            assertFalse(published)
            assertEquals(1, owned.closed)
        }

    private fun resolver(input: () -> InputStream) =
        SelectedContentMimeResolver(Dispatchers.Unconfined) { backend(input = input) }

    private fun backend(
        type: BackendType = BackendType.FILE,
        input: () -> InputStream,
    ): StorageBackend =
        object : StorageBackend by InMemoryBackend() {
            override val type = type

            override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                FileResult.Success(InputStreamProvider(input))
        }

    /** Virtual tail avoids allocating/reading a large fixture; any ninth requested byte fails. */
    private open class PrefixInput(
        private val bytes: ByteArray,
        private val trailingSize: Int = 0,
        private val chunk: Int = Int.MAX_VALUE,
        private val zeroBulk: Boolean = false,
    ) : InputStream() {
        var returned = 0
            private set
        var closed = 0
            private set
        var bulkCalls = 0
            private set

        override fun read(): Int {
            check(returned < 8) { "A ninth byte must never be requested" }
            if (returned >= bytes.size + trailingSize) return -1
            return if (returned < bytes.size) {
                bytes[returned++].toInt() and 255
            } else {
                returned++
                77
            }
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            bulkCalls++
            check(length <= 8 - returned) { "Bulk request exceeds remaining prefix budget" }
            if (zeroBulk && bulkCalls % 2 == 1) return 0
            if (returned >= bytes.size + trailingSize) return -1
            val count = minOf(length, chunk, bytes.size + trailingSize - returned)
            repeat(count) { buffer[offset + it] = read().toByte() }
            return count
        }

        override fun available(): Int = error("Recognition cannot use available()")

        override fun close() {
            closed++
        }
    }

    private companion object {
        val PNG = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
