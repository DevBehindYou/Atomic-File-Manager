package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.DefaultAppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.After
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Real BitmapFactory decoding and rendered pixels through the existing AppContainer boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageLoadRecoveryComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val render = mutableStateOf(true)
    private val shown = mutableStateOf(node("first.png"))

    @After
    fun disposeTheImageAndCancelAnyPendingDecode() {
        compose.runOnIdle { render.value = false }
    }

    @Test
    fun replacementNeverPublishesThePreviousPixelsUnderTheNewImagesName() {
        val first = shown.value
        val second = node("second.png")
        val red = png(Color.RED)
        val blue = png(Color.BLUE)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val container =
            container { id ->
                if (id == second.id) {
                    entered.complete(Unit)
                    release.await()
                }
                bytes(if (id == first.id) red else blue)
            }
        try {
            show(container)
            awaitImage(first)
            assertPixel(first, Color.RED)
            compose.runOnIdle { shown.value = second }
            awaitCondition { entered.isCompleted }
            compose.onNodeWithContentDescription(second.name).assertDoesNotExist()
            compose.runOnIdle { release.complete(Unit) }
            awaitImage(second)
            assertPixel(second, Color.BLUE)
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun updatedRevisionAtTheSameIdentityStartsANewDecodeWithoutShowingTheOldPixels() {
        val original = shown.value
        val updated = original.copy(modifiedAt = original.modifiedAt + 1)
        val red = png(Color.RED)
        val blue = png(Color.BLUE)
        val revised = AtomicBoolean()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val container =
            container {
                if (revised.get()) {
                    entered.complete(Unit)
                    release.await()
                    bytes(blue)
                } else {
                    bytes(red)
                }
            }
        try {
            show(container)
            awaitImage(original)
            assertPixel(original, Color.RED)
            compose.runOnIdle {
                revised.set(true)
                shown.value = updated
            }
            awaitCondition { entered.isCompleted }
            compose.onNodeWithContentDescription(updated.name).assertDoesNotExist()
            compose.runOnIdle { release.complete(Unit) }
            awaitImage(updated)
            assertPixel(updated, Color.BLUE)
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun anUnavailableProviderWaitsForExplicitRetryThenShowsTheRecoveredImage() {
        val available = AtomicBoolean()
        val opened = AtomicInteger()
        val red = png(Color.RED)
        val container =
            container {
                opened.incrementAndGet()
                if (available.get()) bytes(red) else FileResult.Failure(FileError.ProviderUnavailable("fixture"))
            }
        show(container)
        awaitCondition { opened.get() == 1 }
        val label = app.getString(R.string.action_retry)
        awaitCondition { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
        val retry = compose.onNodeWithContentDescription(label)
        retry.assertIsDisplayed()
        compose.runOnIdle { assertEquals("Failure must not start an automatic retry loop", 1, opened.get()) }
        compose.runOnIdle { available.set(true) }
        retry.performClick()
        awaitImage(shown.value)
        assertPixel(shown.value, Color.RED)
        retry.assertDoesNotExist()
    }

    private fun show(container: AppContainer) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Box(Modifier.fillMaxSize()) {
                    if (render.value) ZoomableImage(shown.value, container)
                }
            }
        }
    }

    private fun awaitImage(node: FileNode) {
        awaitCondition {
            compose.onAllNodesWithContentDescription(node.name).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertPixel(
        node: FileNode,
        expected: Int,
    ) {
        val pixels = compose.onNodeWithContentDescription(node.name).captureToImage().toPixelMap()
        assertEquals(expected, pixels[pixels.width / 2, pixels.height / 2].toArgb())
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun container(open: suspend (FileNodeId) -> FileResult<InputStreamProvider>): AppContainer {
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> = open(id)
            }
        return object : AppContainer by DefaultAppContainer(app) {
            override val imagePreviewHelper = ImagePreviewHelper({ backend }, Dispatchers.IO)
        }
    }

    private fun bytes(value: ByteArray): FileResult<InputStreamProvider> =
        FileResult.Success(InputStreamProvider { ByteArrayInputStream(value) })

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            return ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun node(name: String): FileNode =
        FileNode(
            FileNodeId.file("/image-fixture/$name"), name, name, "image/png", 1, 0, false, false,
            null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )
}
