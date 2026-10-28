package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class ImagePreviewHelperTest {
    @Test
    fun allEightExifOrientationsMapEveryCornerIncludingMirroredDiagonalTransforms() {
        val expected =
            listOf(
                floatArrayOf(0f, 0f, 3f, 0f, 0f, 2f, 3f, 2f),
                floatArrayOf(3f, 0f, 0f, 0f, 3f, 2f, 0f, 2f),
                floatArrayOf(3f, 2f, 0f, 2f, 3f, 0f, 0f, 0f),
                floatArrayOf(0f, 2f, 3f, 2f, 0f, 0f, 3f, 0f),
                floatArrayOf(0f, 0f, 0f, 3f, 2f, 0f, 2f, 3f),
                floatArrayOf(2f, 0f, 2f, 3f, 0f, 0f, 0f, 3f),
                floatArrayOf(2f, 3f, 2f, 0f, 0f, 3f, 0f, 0f),
                floatArrayOf(0f, 3f, 0f, 0f, 2f, 3f, 2f, 0f),
            )
        expected.forEachIndexed { index, corners ->
            val mapped = floatArrayOf(0f, 0f, 3f, 0f, 0f, 2f, 3f, 2f)
            exifOrientationMatrix(index + 1).mapPoints(mapped)
            val minX = mapped.indices.filter { it % 2 == 0 }.minOf { mapped[it] }
            val minY = mapped.indices.filter { it % 2 == 1 }.minOf { mapped[it] }
            mapped.indices.forEach { mapped[it] -= if (it % 2 == 0) minX else minY }
            assertArrayEquals("orientation ${index + 1}", corners, mapped, .001f)
        }
        listOf(-1, 0, 9).forEach { assertTrue(exifOrientationMatrix(it).isIdentity) }
    }

    @Test
    fun samplingUsesPositivePowerOfTwoAndNeverOverflowsForMaliciousDimensions() {
        assertEquals(1, imagePreviewSampleSize(2048, 2048))
        assertEquals(2, imagePreviewSampleSize(2049, 2048))
        assertEquals(4, imagePreviewSampleSize(4097, 2048))
        assertEquals(1 shl 30, imagePreviewSampleSize(Int.MAX_VALUE, 1))
        assertThrows(IllegalArgumentException::class.java) { imagePreviewSampleSize(10, 0) }
    }

    @Test
    fun zeroOrNegativeRequestedSizeFailsBeforeConstructingAStorageBackend() =
        runTest {
            var selected = false
            val helper =
                ImagePreviewHelper({
                    selected = true
                    InMemoryBackend()
                }, StandardTestDispatcher(testScheduler))
            assertTrue(helper.decodeImage(ID, 0) is FileResult.Failure)
            assertTrue(helper.decodeImage(ID, -1) is FileResult.Failure)
            assertFalse(selected)
        }

    @Test
    fun backendConstructionOpenAndProviderExceptionsMapToFailuresWithoutLosingTypedBackendErrors() =
        runTest {
            val io = StandardTestDispatcher(testScheduler)
            assertTrue(ImagePreviewHelper({ throw IOException("factory") }, io).decodeImage(ID) is FileResult.Failure)
            val original = FileResult.Failure(FileError.ProviderUnavailable("provider"))
            assertEquals(original, ImagePreviewHelper({ backend { original } }, io).decodeImage(ID))
            assertTrue(
                ImagePreviewHelper({ backend { throw IOException("open") } }, io).decodeImage(ID) is FileResult.Failure,
            )
            val provider = backend { FileResult.Success(InputStreamProvider { throw IOException("stream") }) }
            assertTrue(ImagePreviewHelper({ provider }, io).decodeImage(ID) is FileResult.Failure)
        }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun failedReadClosesItsStreamOnTheInjectedPhysicalWorker() {
        val caller = Thread.currentThread()
        var closed = false
        val broken =
            object : ByteArrayInputStream(byteArrayOf()) {
                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    assertFalse(Thread.currentThread() === caller)
                    throw IOException("read")
                }

                override fun close() {
                    assertFalse(Thread.currentThread() === caller)
                    closed = true
                    super.close()
                }
            }
        Executors.newSingleThreadExecutor().use { executor ->
            executor.asCoroutineDispatcher().use { io ->
                val helper =
                    ImagePreviewHelper({
                        assertFalse(Thread.currentThread() === caller)
                        backend {
                            assertFalse(Thread.currentThread() === caller)
                            FileResult.Success(InputStreamProvider { broken })
                        }
                    }, io)
                assertTrue(runBlocking { helper.decodeImage(ID) } is FileResult.Failure)
                assertTrue(closed)
            }
        }
    }

    @Test
    fun cancellationDuringReadClosesAndPropagatesEvenWhenTheDecoderSwallowsTheStreamException() =
        runTest {
            val io = StandardTestDispatcher(testScheduler)
            val owner = CoroutineScope(SupervisorJob() + io)
            var closed = false
            val stream =
                object : ByteArrayInputStream(ByteArray(256)) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        owner.cancel()
                        return super.read(buffer, offset, length)
                    }

                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            val helper =
                ImagePreviewHelper({
                    backend {
                        FileResult.Success(
                            InputStreamProvider {
                                owner.cancel()
                                stream
                            },
                        )
                    }
                }, io)
            val result = owner.async { helper.decodeImage(ID) }
            testScheduler.advanceUntilIdle()
            assertTrue(runCatching { result.await() }.exceptionOrNull() is CancellationException)
            assertTrue(closed)
            val cancelled = ImagePreviewHelper({ backend { throw CancellationException("provider") } }, io)
            assertTrue(runCatching { cancelled.decodeImage(ID) }.exceptionOrNull() is CancellationException)
        }

    @Test
    fun metadataBudgetBoundsReadSkipAndResetRereadsAndClosesOwnedInput() {
        var closed = false
        val raw =
            object : ByteArrayInputStream(ByteArray(100)) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        ImagePreviewInput(raw, EmptyCoroutineContext, 6).use { input ->
            input.mark(100)
            assertEquals(4, input.read(ByteArray(4)))
            input.reset()
            assertEquals(2L, input.skip(100))
            assertThrows(IOException::class.java) { input.read() }
            assertTrue(input.limitExceeded)
        }
        assertTrue(closed)
    }

    @Test
    fun aCancellationCaughtByAMetadataLibraryCanStillBeRethrownByItsCaller() {
        val job = Job()
        val cancelled =
            object : ByteArrayInputStream(ByteArray(1)) {
                override fun read(): Int = throw CancellationException("stream cancelled independently")
            }
        ImagePreviewInput(cancelled, job).use { input ->
            assertThrows(CancellationException::class.java) { input.read() }
            assertThrows(CancellationException::class.java) { input.rethrowCancellation() }
        }
    }

    private fun backend(open: () -> FileResult<InputStreamProvider>): StorageBackend =
        object : StorageBackend by InMemoryBackend() {
            override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> = open()
        }

    private companion object {
        val ID = FileNodeId("file:/image.jpg")
    }
}
