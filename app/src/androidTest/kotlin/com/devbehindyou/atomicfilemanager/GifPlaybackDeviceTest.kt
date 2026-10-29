package com.devbehindyou.atomicfilemanager

import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.components.ZoomableImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

/** Actual decoded pixels, not a MIME label or Drawable type assertion. Owned app-cache file only. */
@RunWith(AndroidJUnit4::class)
class GifPlaybackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun framesAdvanceStopAndReplayWithoutChangingTheSource() = verifyPlayback(finite = false)

    @Test
    fun finiteGifCompletesAndReplaysWithoutChangingTheSource() = verifyPlayback(finite = true)

    private fun verifyPlayback(finite: Boolean) =
        runBlocking<Unit> {
            val application = compose.activity.application
            val directory = Files.createTempDirectory(application.cacheDir.toPath(), "gif-playback-").toFile()
            val source = File(directory, "animation.gif")
            val shown = mutableStateOf(true)
            val active = mutableStateOf(true)
            var composed = false
            try {
                val bytes =
                    InstrumentationRegistry.getInstrumentation().context.assets.open(
                        if (finite) "gif/red-green-finite.gif" else "gif/red-green.gif",
                    ).use {
                        it.readBytes()
                    }
                source.writeBytes(bytes)
                val backend = FileSystemBackend(application)
                val node = (backend.getNode(FileNodeId.file(source.absolutePath)) as FileResult.Success).value
                val container =
                    object : AppContainer by DefaultAppContainer(application) {
                        override val imagePreviewHelper = ImagePreviewHelper { backend }
                    }
                compose.setContent {
                    AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                        if (shown.value) {
                            Box(Modifier.size(240.dp).testTag("gif_viewport")) {
                                ZoomableImage(node, container, isActive = active.value)
                            }
                        }
                    }
                }
                composed = true
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithContentDescription(source.name).fetchSemanticsNodes().isNotEmpty()
                }
                awaitBothFrames()
                if (finite) {
                    compose.waitUntil(6_000) {
                        compose.onNodeWithContentDescription("Stop animation").fetchSemanticsNode()
                            .config.contains(SemanticsProperties.Disabled)
                    }
                    val completed = centerColor()
                    Thread.sleep(750)
                    assertEquals("Finite GIF must stay on its final frame", completed, centerColor())
                    compose.onNodeWithContentDescription("Replay animation").performClick()
                    awaitBothFrames()
                    assertArrayEquals(bytes, source.readBytes())
                    return@runBlocking
                }
                repeat(3) { compose.onNodeWithContentDescription("Replay animation").performClick() }
                awaitBothFrames()
                compose.onNodeWithContentDescription("Stop animation").performClick()
                val stopped = centerColor()
                Thread.sleep(750)
                assertEquals("Stopped image must keep its frame", stopped, centerColor())
                compose.onNodeWithContentDescription("Replay animation").performClick()
                awaitBothFrames()
                var owned: AnimatedImageDrawable? = null
                compose.runOnIdle {
                    owned =
                        compose.activity.window.decorView.findViewWithTag<ImageView>("atomic_gif_drawable")
                            .drawable as AnimatedImageDrawable
                }
                val animation = checkNotNull(owned)
                compose.runOnIdle { active.value = false }
                compose.waitForIdle()
                InstrumentationRegistry.getInstrumentation().runOnMainSync { assertFalse(animation.isRunning) }
                compose.runOnIdle { active.value = true }
                awaitBothFrames()
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                InstrumentationRegistry.getInstrumentation().runOnMainSync { assertFalse(animation.isRunning) }
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                awaitBothFrames()
                compose.runOnIdle { shown.value = false }
                compose.waitForIdle()
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    assertFalse("Disposed GIF must stop", animation.isRunning)
                    assertNull("Disposed GIF must detach its view", animation.callback)
                }
                assertArrayEquals(bytes, source.readBytes())
            } finally {
                if (composed) compose.runOnIdle { shown.value = false }
                compose.waitForIdle()
                if (source.exists()) assertTrue(source.delete())
                assertTrue("Unexpected files must be retained", directory.delete())
            }
        }

    private fun awaitBothFrames() {
        val colors = mutableSetOf<Int>()
        try {
            compose.waitUntil(6_000) {
                val color = centerColor()
                if (color != 0) colors.add(color)
                colors.size == 2
            }
        } catch (failure: Throwable) {
            throw AssertionError("GIF did not show both frames; observed fixture colors=$colors", failure)
        }
        assertEquals(setOf(1, 2), colors)
    }

    private fun centerColor(): Int {
        val pixels = compose.onNodeWithTag("gif_viewport").captureToImage().toPixelMap()
        val color = pixels[pixels.width / 2, pixels.height / 3]
        return when {
            color.red > 0.8f && color.green < 0.2f -> 1
            color.green > 0.8f && color.red < 0.2f -> 2
            else -> 0
        }
    }
}
