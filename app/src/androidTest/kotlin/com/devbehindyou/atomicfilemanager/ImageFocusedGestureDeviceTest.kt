package com.devbehindyou.atomicfilemanager

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.components.ZoomableImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** Actual FILE decode, pointer input and hardware-captured viewer pixels; app-cache fixtures only. */
@RunWith(AndroidJUnit4::class)
class ImageFocusedGestureDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun doubleTapKeepsTheSelectedPointAndActuallyMagnifiesItsMarker() =
        withImage(width = 100, height = 100, marker = true) {
            assertPixel(0.7f, 0.5f, green = true)
            assertPixel(0.8f, 0.5f, green = false)
            compose.onNodeWithTag(VIEWPORT).performTouchInput {
                doubleClick(Offset(width * 0.7f, height * 0.5f))
            }
            // Retaining the focal point alone would also pass if no zoom happened.
            assertPixel(0.7f, 0.5f, green = true)
            assertPixel(0.8f, 0.5f, green = true)
        }

    @Test
    fun pinchKeepsItsOffCenterPointAndActuallyMagnifiesItsMarker() =
        withImage(width = 100, height = 100, marker = true) {
            assertPixel(0.7f, 0.5f, green = true)
            assertPixel(0.77f, 0.5f, green = false)
            compose.onNodeWithTag(VIEWPORT).performTouchInput {
                pinch(
                    start0 = Offset(width * 0.65f, height * 0.4f),
                    start1 = Offset(width * 0.75f, height * 0.6f),
                    end0 = Offset(width * 0.6f, height * 0.3f),
                    end1 = Offset(width * 0.8f, height * 0.7f),
                )
            }
            assertPixel(0.7f, 0.5f, green = true)
            assertPixel(0.77f, 0.5f, green = true)
        }

    @Test
    fun zoomedWideImageCannotLeaveItsShortAxisAfterVerticalDrag() =
        withImage(width = 400, height = 100, marker = false) {
            compose.onNodeWithTag(VIEWPORT).performTouchInput { doubleClick(center) }
            assertPixel(0.5f, 0.25f, green = false)
            compose.onNodeWithTag(VIEWPORT).performTouchInput {
                swipe(start = center, end = Offset(center.x, height * 0.9f))
            }
            assertPixel(0.5f, 0.5f, green = false)
        }

    @Test
    fun zoomedTallImageCannotLeaveItsShortAxisAfterHorizontalDrag() =
        withImage(width = 100, height = 400, marker = false) {
            compose.onNodeWithTag(VIEWPORT).performTouchInput { doubleClick(center) }
            assertPixel(0.25f, 0.5f, green = false)
            compose.onNodeWithTag(VIEWPORT).performTouchInput {
                swipe(start = center, end = Offset(width * 0.9f, center.y))
            }
            assertPixel(0.5f, 0.5f, green = false)
        }

    private fun withImage(width: Int, height: Int, marker: Boolean, check: () -> Unit) =
        runBlocking<Unit> {
            val context = compose.activity.applicationContext
            val exports = File(context.cacheDir, "external-preview")
            val exportsExisted = exports.exists()
            val beforeExports = snapshot(exports)
            val directory = Files.createTempDirectory(context.cacheDir.toPath(), "image-focused-").toFile().canonicalFile
            val source = File(directory, "marker.png")
            val sentinel = File(directory, "preserve-sentinel.bin")
            val bytes = png(width, height, marker)
            val sentinelBytes = byteArrayOf(31, 41, 59)
            val shown = mutableStateOf(true)
            var composed = false
            var sourceWritten = false
            var sentinelWritten = false
            try {
                source.writeBytes(bytes)
                sourceWritten = true
                sentinel.writeBytes(sentinelBytes)
                sentinelWritten = true
                val local = FileSystemBackend(context)
                val node = (local.getNode(FileNodeId.file(source.absolutePath)) as FileResult.Success).value
                val base = (context as AtomicApp).container
                val container = object : AppContainer by base {
                    override val imagePreviewHelper = ImagePreviewHelper({ local }, Dispatchers.IO)
                }
                compose.setContent {
                    AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                        Box(Modifier.size(200.dp).testTag(VIEWPORT)) {
                            if (shown.value) ZoomableImage(node, container)
                        }
                    }
                }
                composed = true
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithContentDescription(node.name).fetchSemanticsNodes().isNotEmpty()
                }
                // The pixel wait is needed in addition to semantic publication.
                compose.waitUntil(15_000) { pixel(0.5f, 0.5f, green = false) }
                check()
            } finally {
                try {
                    if (composed) {
                        compose.runOnIdle { shown.value = false }
                        compose.waitForIdle()
                    }
                    if (sourceWritten) assertArrayEquals("Original image bytes", bytes, source.readBytes())
                    if (sentinelWritten) assertArrayEquals("Unrelated sentinel bytes", sentinelBytes, sentinel.readBytes())
                    assertEquals(exportsExisted, exports.exists())
                    assertEquals("Unrelated exports unchanged", beforeExports, snapshot(exports))
                } finally {
                    val failures = mutableListOf<String>()
                    for (owned in listOf(source, sentinel)) {
                        if (!owned.delete() && owned.exists()) failures += "Owned fixture cleanup failed"
                    }
                    // Delete only this empty captured directory; never recursively remove unexpected files.
                    if (!directory.delete() && directory.exists()) failures += "Owned directory was not empty"
                    assertTrue(failures.joinToString(), failures.isEmpty())
                }
            }
        }

    private fun assertPixel(x: Float, y: Float, green: Boolean) {
        compose.waitForIdle()
        assertTrue("Expected " + (if (green) "green" else "red") + " viewer pixel at ($x,$y)", pixel(x, y, green))
    }

    private fun pixel(x: Float, y: Float, green: Boolean): Boolean {
        val pixels = compose.onNodeWithTag(VIEWPORT).captureToImage().toPixelMap()
        val color = pixels[
            (pixels.width * x).toInt().coerceIn(0, pixels.width - 1),
            (pixels.height * y).toInt().coerceIn(0, pixels.height - 1),
        ]
        return if (green) {
            color.green > 0.8f && color.red < 0.2f && color.blue < 0.2f
        } else {
            color.red > 0.8f && color.green < 0.2f && color.blue < 0.2f
        }
    }

    private fun png(width: Int, height: Int, marker: Boolean): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            if (marker) {
                for (x in 65..75) for (y in 0 until height) bitmap.setPixel(x, y, Color.GREEN)
            }
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun snapshot(directory: File): Map<File, Pair<Long, Long>> =
        directory.listFiles().orEmpty().associate { it.canonicalFile to (it.length() to it.lastModified()) }

    private companion object {
        const val VIEWPORT = "image_gesture_device_viewport"
    }
}
