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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** Actual controls/decoded pixels; the source before implementation lacks these controls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageViewModesComposeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var imageView: View
    private val image =
        FileNode(
            FileNodeId.file("/image-view-mode-fixture/red.png"), "red.png", "red.png", "image/png",
            1, 0, false, false, null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )
    private val shown = mutableStateOf(image)

    @Test
    fun fitWidthUsesTheViewportAndFitScreenRestoresLetterboxing() {
        show(100, 400)
        assertPixel(0.25f, 0.4f, Color.TRANSPARENT)
        compose.onNodeWithTag("image_mode_width").performScrollTo().performClick().assertIsOn()
        assertPixel(0.25f, 0.4f, Color.RED)
        compose.onNodeWithTag("image_mode_fit").performScrollTo().performClick().assertIsOn()
        assertPixel(0.25f, 0.4f, Color.TRANSPARENT)
    }

    @Test
    fun actualPreviewPixelsCanShrinkAnUpscaledSmallBitmapWithoutChangingTheSource() {
        show(100, 100)
        assertPixel(0.2f, 0.4f, Color.RED)
        compose.onNodeWithTag("image_mode_pixels").performScrollTo().performClick().assertIsOn()
        assertPixel(0.2f, 0.4f, Color.TRANSPARENT)
        assertPixel(0.5f, 0.5f, Color.RED)
        compose.onNodeWithTag("image_mode_fit").performScrollTo().performClick()
        assertPixel(0.2f, 0.4f, Color.RED)
    }

    @Test
    fun selectedWidthModeSurvivesComposeSavedStateRestoration() {
        val restoration = show(100, 400)
        compose.onNodeWithTag("image_mode_width").performScrollTo().performClick()
        assertPixel(0.25f, 0.4f, Color.RED)
        restoration.emulateSavedInstanceStateRestore()
        awaitDecoded()
        compose.onNodeWithTag("image_mode_width").assertIsOn()
        assertPixel(0.25f, 0.4f, Color.RED)
    }

    @Test
    fun sameIdNewRevisionStartsInFitModeRatherThanReusingThePreviousSourceTransform() {
        show(100, 400)
        compose.onNodeWithTag("image_mode_width").performScrollTo().performClick()
        assertPixel(0.25f, 0.4f, Color.RED)
        compose.runOnIdle { shown.value = image.copy(modifiedAt = 1) }
        awaitDecoded()
        compose.onNodeWithTag("image_mode_fit").assertIsOn()
        assertPixel(0.25f, 0.4f, Color.TRANSPARENT)
    }

    @Test
    fun aModeOutsideTheExistingZoomCeilingIsDisabledInsteadOfBeingMislabelled() {
        show(1, 2048)
        compose.onNodeWithTag("image_mode_width").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("image_mode_limit").assertExists()
        compose.onNodeWithTag("image_mode_fit").assertIsOn()
    }

    private fun show(
        width: Int,
        height: Int,
    ): StateRestorationTester {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bytes =
            try {
                bitmap.eraseColor(Color.RED)
                ByteArrayOutputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    it.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                    FileResult.Success(InputStreamProvider { ByteArrayInputStream(bytes) })
            }
        val container =
            object : AppContainer by DefaultAppContainer(app) {
                override val imagePreviewHelper = ImagePreviewHelper({ backend }, Dispatchers.IO)
            }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val view = LocalView.current
            SideEffect { imageView = view }
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Box(Modifier.size(200.dp).testTag("mode_viewport")) { ZoomableImage(shown.value, container) }
            }
        }
        awaitDecoded()
        return restoration
    }

    private fun awaitDecoded() {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithContentDescription(image.name).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertPixel(
        x: Float,
        y: Float,
        expected: Int,
    ) {
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("mode_viewport").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val pixels = Bitmap.createBitmap(imageView.width, imageView.height, Bitmap.Config.ARGB_8888)
            try {
                imageView.draw(Canvas(pixels))
                assertEquals(
                    expected,
                    pixels.getPixel((bounds.left + bounds.width * x).toInt(), (bounds.top + bounds.height * y).toInt()),
                )
            } finally {
                pixels.recycle()
            }
        }
    }
}
