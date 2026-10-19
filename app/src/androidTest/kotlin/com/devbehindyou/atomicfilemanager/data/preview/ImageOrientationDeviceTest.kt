package com.devbehindyou.atomicfilemanager.data.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Owns only uniquely named app-cache fixtures; never reads or changes a user's images. */
@RunWith(AndroidJUnit4::class)
class ImageOrientationDeviceTest {
    @Test
    fun sampledJpegAppliesAllEightExifRotationsAndFlipsWithoutChangingTheOriginal() =
        runBlocking {
            withContext(Dispatchers.IO) {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val helper = ImagePreviewHelper({ FileSystemBackend(context) }, Dispatchers.IO)
                val colorsByOrientation =
                    listOf(
                        intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
                        intArrayOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
                        intArrayOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
                        intArrayOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
                        intArrayOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
                        intArrayOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
                        intArrayOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
                        intArrayOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE),
                    )
                for (orientation in 1..8) {
                    val file = File.createTempFile("atomic-exif-$orientation-", ".jpg", context.cacheDir)
                    try {
                        val source = quadrantBitmap()
                        try {
                            file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
                        } finally {
                            source.recycle()
                        }
                        ExifInterface(file).apply {
                            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                            saveAttributes()
                        }
                        val originalDigest = digest(file)
                        val result = helper.decodeImage(FileNodeId.file(file.absolutePath), maxDimension = 32)
                        assertTrue("orientation $orientation: $result", result is FileResult.Success)
                        val image = (result as FileResult.Success).value
                        try {
                            assertEquals(if (orientation >= 5) 20 else 32, image.width)
                            assertEquals(if (orientation >= 5) 32 else 20, image.height)
                            assertCorners(image, colorsByOrientation[orientation - 1])
                        } finally {
                            image.recycle()
                        }
                        assertArrayEquals(originalDigest, digest(file))
                        assertEquals(orientation, ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
                    } finally {
                        assertTrue("fixture cleanup", file.delete() || !file.exists())
                    }
                }
            }
        }

    @Test
    fun bitmapFactorySupportedBmpStillDecodesWhenExifDoesNotSupportItsMetadata() =
        runBlocking {
            withContext(Dispatchers.IO) {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val file = File.createTempFile("atomic-bmp-preview-", ".bmp", context.cacheDir)
                try {
                    writeBmp(file)
                    val originalDigest = digest(file)
                    val helper = ImagePreviewHelper({ FileSystemBackend(context) }, Dispatchers.IO)
                    val result = helper.decodeImage(FileNodeId.file(file.absolutePath), maxDimension = 32)
                    assertTrue("BMP fallback: $result", result is FileResult.Success)
                    val image = (result as FileResult.Success).value
                    try {
                        assertEquals(32, image.width)
                        assertEquals(20, image.height)
                        assertCorners(image, intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW))
                    } finally {
                        image.recycle()
                    }
                    assertArrayEquals(originalDigest, digest(file))
                } finally {
                    assertTrue("fixture cleanup", file.delete() || !file.exists())
                }
            }
        }

    private fun quadrantBitmap(): Bitmap =
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            val paint = Paint()
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { index, color ->
                paint.color = color
                val left = if (index % 2 == 0) 0f else WIDTH / 2f
                val top = if (index < 2) 0f else HEIGHT / 2f
                canvas.drawRect(left, top, left + WIDTH / 2f, top + HEIGHT / 2f, paint)
            }
        }

    private fun assertCorners(
        image: Bitmap,
        expected: IntArray,
    ) {
        val coordinates =
            arrayOf(
                image.width / 8 to image.height / 8,
                image.width * 7 / 8 to image.height / 8,
                image.width / 8 to image.height * 7 / 8,
                image.width * 7 / 8 to image.height * 7 / 8,
            )
        coordinates.forEachIndexed { index, (x, y) ->
            val actual = image.getPixel(x, y)
            assertTrue("red corner $index", kotlin.math.abs(Color.red(actual) - Color.red(expected[index])) < 80)
            assertTrue("green corner $index", kotlin.math.abs(Color.green(actual) - Color.green(expected[index])) < 80)
            assertTrue("blue corner $index", kotlin.math.abs(Color.blue(actual) - Color.blue(expected[index])) < 80)
        }
    }

    private fun writeBmp(file: File) {
        val pixelBytes = WIDTH * HEIGHT * 3
        val header =
            ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('B'.code.toByte())
                put('M'.code.toByte())
                putInt(54 + pixelBytes)
                putInt(0)
                putInt(54)
                putInt(40)
                putInt(WIDTH)
                putInt(HEIGHT)
                putShort(1)
                putShort(24)
                putInt(0)
                putInt(pixelBytes)
                putInt(0)
                putInt(0)
                putInt(0)
                putInt(0)
            }.array()
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        file.outputStream().use { output ->
            output.write(header)
            for (row in HEIGHT - 1 downTo 0) {
                val bytes = ByteArray(WIDTH * 3)
                for (column in 0 until WIDTH) {
                    val color = colors[(if (row < HEIGHT / 2) 0 else 2) + if (column < WIDTH / 2) 0 else 1]
                    bytes[column * 3] = Color.blue(color).toByte()
                    bytes[column * 3 + 1] = Color.green(color).toByte()
                    bytes[column * 3 + 2] = Color.red(color).toByte()
                }
                output.write(bytes)
            }
        }
    }

    private fun digest(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }

    private companion object {
        const val WIDTH = 64
        const val HEIGHT = 40
    }
}
