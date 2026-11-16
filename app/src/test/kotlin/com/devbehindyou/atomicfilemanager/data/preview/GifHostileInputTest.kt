package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.graphics.drawable.Drawable
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hostile GIFs through the real platform decoder: headers that promise huge canvases, very wide
 * canvases and files cut short. Each one fails with a bounded, typed result or decodes within the
 * preview caps, and its owned input is closed exactly once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GifHostileInputTest {
    @Test
    fun aSmallAnimatedGifDecodesWithinTheCaps() =
        runTest {
            val result = decode(gif(width = 2, height = 2, frames = 3))

            val drawable = (result as FileResult.Success).value
            assertEquals(2, drawable.intrinsicWidth)
            assertEquals(2, drawable.intrinsicHeight)
        }

    @Test
    fun aHeaderPromisingAHugeCanvasIsRefusedBeforeAllocation() =
        runTest {
            // 65,535 x 65,535 is about 4.3 billion pixels; the cap is 16 Mi pixels.
            val result = decode(gif(width = 65_535, height = 65_535, frames = 2))

            assertEquals(FileError.FileTooLarge(ID.raw, 16L * 1024 * 1024), (result as FileResult.Failure).error)
        }

    @Test
    fun aVeryWideCanvasInsideThePixelBudgetIsScaledToTheDimensionCap() =
        runTest {
            val result = decode(gif(width = 8_192, height = 2, frames = 2))

            val drawable = (result as FileResult.Success).value
            assertTrue("width ${drawable.intrinsicWidth}", drawable.intrinsicWidth <= 2_048)
            assertTrue(drawable.intrinsicHeight >= 1)
        }

    @Test
    fun filesCutShortFailAsUnreadableGifs() =
        runTest {
            val whole = gif(width = 4, height = 4, frames = 2)
            val cuts =
                listOf(
                    "header only" to whole.copyOf(6),
                    "screen descriptor without colours" to whole.copyOf(13),
                    "palette without frames" to whole.copyOf(19),
                    "first frame descriptor without pixels" to whole.copyOf(indexOfImageData(whole)),
                )
            for ((label, bytes) in cuts) {
                val error = (decode(bytes) as? FileResult.Failure)?.error
                assertTrue("$label gave $error", error == FileError.UnsupportedFormat("GIF"))
            }
        }

    @Test
    fun aDescriptorLargerThanItsCanvasStaysWithinTheCaps() =
        runTest {
            // The frame claims 60,000 x 60,000 inside a 4 x 4 screen; only the screen may be allocated.
            val bytes = gif(width = 4, height = 4, frames = 1, frameWidth = 60_000, frameHeight = 60_000)

            when (val result = decode(bytes)) {
                is FileResult.Success -> assertTrue(result.value.intrinsicWidth <= 2_048)
                is FileResult.Failure ->
                    assertTrue(
                        "${result.error}",
                        result.error == FileError.UnsupportedFormat("GIF") || result.error is FileError.FileTooLarge,
                    )
            }
        }

    private suspend fun TestScope.decode(bytes: ByteArray): FileResult<Drawable> {
        val closes = AtomicInteger()
        val input =
            object : ByteArrayInputStream(bytes) {
                override fun close() {
                    closes.incrementAndGet()
                    super.close()
                }
            }
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId) = FileResult.Success(InputStreamProvider { input })
            }
        val result = GifPreviewDecoder({ backend }, StandardTestDispatcher(testScheduler)).decode(ID)
        assertEquals("The owned input must close exactly once", 1, closes.get())
        return result
    }

    /** Index of the LZW minimum code size byte after the first image descriptor. */
    private fun indexOfImageData(bytes: ByteArray): Int =
        bytes.indices.first { bytes[it] == 0x2C.toByte() && it > 19 } + 10

    /**
     * A looping GIF89a: 2-colour global table, NETSCAPE2.0 loop, and [frames] 1-pixel frames each with
     * a 100 ms delay. [frameWidth]/[frameHeight] let a descriptor disagree with its pixel data.
     */
    private fun gif(
        width: Int,
        height: Int,
        frames: Int,
        frameWidth: Int = 1,
        frameHeight: Int = 1,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        out.short(width)
        out.short(height)
        out.write(byteArrayOf(0x80.toByte(), 0x00, 0x00)) // Global table of 2 colours.
        out.write(byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0xFF.toByte())) // Red, blue.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00)) // Loop forever.
        repeat(frames) { index ->
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00)) // 100 ms.
            out.write(0x2C)
            out.short(0)
            out.short(0)
            out.short(frameWidth)
            out.short(frameHeight)
            out.write(0x00)
            // LZW minimum code size 2; one pixel of colour [index % 2]; end code.
            out.write(byteArrayOf(0x02, 0x02, if (index % 2 == 0) 0x44 else 0x4C, 0x01, 0x00))
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.short(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
    }

    private companion object {
        val ID = FileNodeId.file("/owned-hostile-fixture.gif")
    }
}
