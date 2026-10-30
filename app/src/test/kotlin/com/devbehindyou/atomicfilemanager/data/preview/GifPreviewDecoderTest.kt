package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class GifPreviewDecoderTest {
    @Test
    fun invalidHeaderIsRejectedAndItsOwnedInputCloses() =
        runTest {
            var closed = false
            val input =
                object : ByteArrayInputStream("not a GIF".toByteArray()) {
                    override fun close() {
                        closed = true
                    }
                }
            val result = GifPreviewDecoder({ backend(input) }, StandardTestDispatcher(testScheduler)).decode(ID)
            assertTrue(result is FileResult.Failure && result.error is FileError.UnsupportedFormat)
            assertTrue(closed)
        }

    @Test
    fun typedProviderFailureIsPreserved() =
        runTest {
            val error = FileError.AccessDenied(null)
            val backend =
                object : StorageBackend by InMemoryBackend() {
                    override suspend fun openInput(id: FileNodeId) = FileResult.Failure(error)
                }
            assertEquals(
                FileResult.Failure(error),
                GifPreviewDecoder({ backend }, StandardTestDispatcher(testScheduler)).decode(ID),
            )
        }

    @Test
    fun encodedBudgetReadsOnlyOneOverflowByteAndCloses() =
        runTest {
            var count = 0
            var closed = false
            val input =
                object : InputStream() {
                    override fun read() = error("Bulk reads required")

                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        buffer.fill(0, offset, offset + length)
                        count += length
                        return length
                    }

                    override fun close() {
                        closed = true
                    }
                }
            val result = GifPreviewDecoder({ backend(input) }, StandardTestDispatcher(testScheduler)).decode(ID)
            assertTrue(result is FileResult.Failure && result.error is FileError.FileTooLarge)
            assertEquals(16 * 1024 * 1024 + 1, count)
            assertTrue(closed)
        }

    @Test
    fun encodedBudgetRemainsPrimaryWhenClosingAlsoFails() =
        runTest {
            var consumed = 0
            val input =
                object : InputStream() {
                    override fun read() = error("Bulk reads required")

                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        buffer.fill(0, offset, offset + length)
                        consumed += length
                        return length
                    }

                    override fun close(): Unit = throw IOException("close failure")
                }
            val result = GifPreviewDecoder({ backend(input) }, StandardTestDispatcher(testScheduler)).decode(ID)
            assertTrue(result is FileResult.Failure && result.error is FileError.FileTooLarge)
            assertEquals(16 * 1024 * 1024 + 1, consumed)
        }

    @Test
    fun readFailureClosesAndRemainsRecoverable() =
        runTest {
            var closed = false
            val input =
                object : InputStream() {
                    override fun read(): Int = throw IOException("owned fixture")

                    override fun close() {
                        closed = true
                    }
                }
            val result = GifPreviewDecoder({ backend(input) }, StandardTestDispatcher(testScheduler)).decode(ID)
            assertTrue(result is FileResult.Failure && result.error is FileError.IoFailure)
            assertTrue(closed)
        }

    @Test
    fun cancellationPropagatesAndClosesBeforeDecode() =
        runTest {
            val request = Job()
            var closed = false
            val input =
                object : ByteArrayInputStream(ByteArray(32)) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        request.cancel()
                        return super.read(buffer, offset, length)
                    }

                    override fun close() {
                        closed = true
                    }
                }
            try {
                withContext(request) {
                    GifPreviewDecoder({ backend(input) }, StandardTestDispatcher(testScheduler)).decode(ID)
                }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertTrue(closed)
            } finally {
                request.cancel()
            }
        }

    @Test
    fun zeroProgressStreamFailsInsteadOfSpinning() =
        runTest {
            var closed = false
            var calls = 0
            val input =
                object : InputStream() {
                    override fun read() = 0

                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        calls++
                        return 0
                    }

                    override fun close() {
                        closed = true
                    }
                }
            val result = GifPreviewDecoder({ backend(input) }, StandardTestDispatcher(testScheduler)).decode(ID)
            assertTrue(result is FileResult.Failure && result.error is FileError.IoFailure)
            assertEquals(1, calls)
            assertTrue(closed)
        }

    private fun backend(input: InputStream): StorageBackend =
        object : StorageBackend by InMemoryBackend() {
            override suspend fun openInput(id: FileNodeId) = FileResult.Success(InputStreamProvider { input })
        }

    private companion object {
        val ID = FileNodeId.file("/owned-gif-fixture.gif")
    }
}
