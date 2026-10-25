package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.DefaultAppContainer
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Pointer input and actual rendered pixels, with no production-only test state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageFocusedGestureComposeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var imageView: View
    private val image = FileNode(
        FileNodeId.file("/image-gesture-fixture/marker.png"), "marker.png", "marker.png", "image/png",
        1, 0, false, false, null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
    )

    @Test
    fun doubleTapKeepsTheTappedImagePointUnderTheFinger() {
        show(width = 100, height = 100, marker = true)
        assertPixel(0.7f, 0.5f, Color.GREEN)
        compose.onNodeWithTag("gesture_viewport").performTouchInput { doubleClick(Offset(width * 0.7f, height * 0.5f)) }
        assertPixel(0.7f, 0.5f, Color.GREEN)
        compose.onNodeWithTag("gesture_viewport").performTouchInput { doubleClick(Offset(width * 0.7f, height * 0.5f)) }
        assertPixel(0.7f, 0.5f, Color.GREEN)
    }

    @Test
    fun pinchKeepsItsOffCenterImagePointStationary() {
        show(width = 100, height = 100, marker = true)
        compose.onNodeWithTag("gesture_viewport").performTouchInput {
            pinch(
                start0 = Offset(width * 0.65f, height * 0.4f),
                start1 = Offset(width * 0.75f, height * 0.6f),
                end0 = Offset(width * 0.6f, height * 0.3f),
                end1 = Offset(width * 0.8f, height * 0.7f),
            )
        }
        assertPixel(0.7f, 0.5f, Color.GREEN)
    }

    @Test
    fun wideLetterboxedImageCannotBeDraggedOffItsShortAxis() {
        show(width = 400, height = 100, marker = false)
        compose.onNodeWithTag("gesture_viewport").performTouchInput { doubleClick(center) }
        compose.onNodeWithTag("gesture_viewport").performTouchInput {
            swipe(start = center, end = Offset(center.x, height * 0.9f))
        }
        assertPixel(0.5f, 0.5f, Color.RED)
    }

    @Test
    fun tallLetterboxedImageCannotBeDraggedOffItsShortAxis() {
        show(width = 100, height = 400, marker = false)
        compose.onNodeWithTag("gesture_viewport").performTouchInput { doubleClick(center) }
        compose.onNodeWithTag("gesture_viewport").performTouchInput {
            swipe(start = center, end = Offset(width * 0.9f, center.y))
        }
        assertPixel(0.5f, 0.5f, Color.RED)
    }

    private fun imageNode() = compose.onNodeWithContentDescription(image.name)

    private fun show(width: Int, height: Int, marker: Boolean) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bytes = try {
            bitmap.eraseColor(Color.RED)
            if (marker) {
                for (x in 65..75) for (y in 0 until height) bitmap.setPixel(x, y, Color.GREEN)
            }
            ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val backend = object : StorageBackend by InMemoryBackend() {
            override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                FileResult.Success(InputStreamProvider { ByteArrayInputStream(bytes) })
        }
        val container = object : AppContainer by DefaultAppContainer(app) {
            override val imagePreviewHelper = ImagePreviewHelper({ backend }, Dispatchers.IO)
        }
        compose.setContent {
            val view = LocalView.current
            SideEffect { imageView = view }
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Box(Modifier.size(200.dp).testTag("gesture_viewport")) { ZoomableImage(image, container) }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithContentDescription(image.name).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertPixel(x: Float, y: Float, expected: Int) {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("gesture_viewport").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val pixels = Bitmap.createBitmap(imageView.width, imageView.height, Bitmap.Config.ARGB_8888)
            try {
                imageView.draw(Canvas(pixels))
                val viewportWidth = bounds.width
                val viewportHeight = bounds.height
                val left = bounds.center.x - viewportWidth / 2f
                val top = bounds.center.y - viewportHeight / 2f
                assertEquals(expected, pixels.getPixel((left + viewportWidth * x).toInt(), (top + viewportHeight * y).toInt()))
            } finally {
                pixels.recycle()
            }
        }
    }
}
