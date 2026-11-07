package com.devbehindyou.atomicfilemanager

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.data.repository.VideoResumePreferences
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.VideoRevision
import com.devbehindyou.atomicfilemanager.ui.components.preview.VideoPreviewContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.abs

/**
 * Real Media3 decoding through the Video preview: generated app-private PCM (not a video codec)
 * is played, sought, paused, recreated and replaced. Only this test's own files and resume entry
 * are touched.
 */
@RunWith(AndroidJUnit4::class)
class VideoResumeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recreationResumesTheDecodedPausedPositionAndAReplacementStartsOver() {
        val app = compose.activity.application as AtomicApp
        val directory = Files.createTempDirectory(app.cacheDir.toPath(), "video-resume-native-").toFile()
        val source = File(directory, "owned.wav")
        val id = FileNodeId.file(source.absolutePath)
        val original = pcmWav(seconds = 20)
        val store = VideoResumePreferences(app)
        val shown = mutableStateOf<FileNode?>(null)
        val revisions = mutableListOf<VideoRevision>()
        try {
            source.writeBytes(original)
            assertTrue(source.setLastModified(1_700_000_000_000))
            shown.value = node(app, id).also { revisions += VideoRevision.of(it)!! }
            val restoration = StateRestorationTester(compose)
            restoration.setContent {
                AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                    shown.value?.let { VideoPreviewContent(it, app.container, resumeMemory = store) }
                }
            }

            val first = awaitPlayer { it.isPlaying && it.currentPosition >= 1_000 }
            onMain { first.seekTo(9_000) }
            awaitPlayer { it === first && it.playbackState == Player.STATE_READY && it.currentPosition >= 9_000 }
            onMain { first.pause() }
            val paused = onMain { first.currentPosition }
            assertTrue("Decoded seek reached $paused", paused in 9_000L..<11_000L)

            restoration.emulateSavedInstanceStateRestore()
            val second = awaitPlayer { it !== first && it.playbackState == Player.STATE_READY }
            val resumed = onMain { second.currentPosition }
            assertTrue("Resumed at $resumed after pausing at $paused", abs(resumed - paused) <= TOLERANCE_MS)
            assertFalse("A paused preview stays paused after recreation", onMain { second.playWhenReady })
            Thread.sleep(600)
            assertEquals("The restored pause holds the decoded clock", resumed, onMain { second.currentPosition })
            assertArrayEquals("The original file is never modified", original, source.readBytes())

            // Same path, different bytes and time: a replacement must not inherit the old place.
            onMain { shown.value = null }
            compose.waitForIdle()
            source.writeBytes(pcmWav(seconds = 12))
            assertTrue(source.setLastModified(1_700_000_600_000))
            val replacement = node(app, id).also { revisions += VideoRevision.of(it)!! }
            assertTrue(revisions[0] != revisions[1])
            onMain { shown.value = replacement }
            val third = awaitPlayer { it !== second && it.isPlaying }
            assertTrue("A replacement starts over", onMain { third.currentPosition } < 9_000)
            assertEquals(setOf(source.name), directory.list()?.toSet())
        } finally {
            onMain { shown.value = null }
            compose.waitForIdle()
            revisions.forEach(store::forget)
            runBlocking { revisions.forEach { assertEquals(null, store.read(it)) } }
            if (source.exists()) assertTrue(source.delete())
            assertTrue("Unexpected files must be retained", directory.delete())
        }
    }

    private fun node(
        app: AtomicApp,
        id: FileNodeId,
    ): FileNode = runBlocking { (FileSystemBackend(app).getNode(id) as FileResult.Success).value }

    private fun <T> onMain(block: () -> T): T = compose.runOnUiThread(block)

    private fun awaitPlayer(accept: (Player) -> Boolean): Player {
        var found: Player? = null
        compose.waitUntil(20_000) {
            found = onMain { playerView(compose.activity.window.decorView)?.player?.takeIf(accept) }
            found != null
        }
        return found!!
    }

    private fun playerView(view: View): PlayerView? =
        view as? PlayerView ?: (view as? ViewGroup)?.let { group ->
            (0 until group.childCount).firstNotNullOfOrNull { playerView(group.getChildAt(it)) }
        }

    private fun pcmWav(seconds: Int): ByteArray {
        val sampleRateHz = 16_000
        val bytesPerSample = 2
        val dataBytes = sampleRateHz * seconds * bytesPerSample
        // One channel of signed little-endian PCM silence.
        return ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRateHz)
            putInt(sampleRateHz * bytesPerSample)
            putShort(bytesPerSample.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()
    }

    private companion object {
        const val TOLERANCE_MS = 500L
    }
}
