package com.devbehindyou.atomicfilemanager

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.components.BuiltInFilePreviewPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

/** Plays generated PCM through the real preview and checks its visible playback clock. */
@RunWith(AndroidJUnit4::class)
class AudioWavDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ownedWavPlaysPausesRewindsAndCompletesWithoutChangingFiles() =
        runBlocking<Unit> {
            val app = compose.activity.application as AtomicApp
            val directory = Files.createTempDirectory(app.cacheDir.toPath(), "audio-wav-native-").toFile()
            val source = File(directory, "owned.wav")
            val sentinel = File(directory, "sentinel.bin")
            val id = FileNodeId.file(source.absolutePath)
            val original = pcmWav()
            val sentinelBytes = byteArrayOf(10, 33, 7)
            val shown = mutableStateOf(true)
            var composed = false
            try {
                source.writeBytes(original)
                sentinel.writeBytes(sentinelBytes)
                val node = (FileSystemBackend(app).getNode(id) as FileResult.Success).value
                compose.setContent {
                    AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                        if (shown.value) BuiltInFilePreviewPane(node, onClose = { shown.value = false })
                    }
                }
                composed = true
                compose.waitUntil(15_000) {
                    hasControl("Play") && compose.onAllNodesWithText("0:08").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals("WAV preview should start paused at zero", 0f, positionMs(), 0f)

                clickControl("Play")
                awaitPlayingPosition(1_000f)
                clickControl("Pause")
                compose.waitUntil(10_000) { hasControl("Play") }
                val paused = positionMs()
                assertTrue("Pause must keep an in-progress position", paused in 1_000f..<8_000f)
                delay(600)
                assertEquals("The paused clock must stop", paused, positionMs(), 0f)

                clickControl("Back 10 seconds")
                compose.waitUntil(10_000) { positionMs() == 0f }
                clickControl("Play")
                awaitPlayingPosition(6_000f)
                compose.waitUntil(10_000) { hasControl("Play") && positionMs() == 0f }
                compose.onNodeWithContentDescription("Play").assertIsDisplayed()

                compose.onNodeWithTag("preview_close").performClick()
                compose.onNodeWithTag("file_preview_pane").assertDoesNotExist()
                assertArrayEquals(original, source.readBytes())
                assertArrayEquals(sentinelBytes, sentinel.readBytes())
                assertEquals(setOf(source.name, sentinel.name), directory.list()?.toSet())
            } finally {
                try {
                    if (composed) compose.runOnIdle { shown.value = false }
                    compose.waitForIdle()
                    // The preview records a Recent entry. Remove only this test's own ID.
                    withContext(Dispatchers.IO) { app.container.recentsRepository.remove(id) }
                } finally {
                    if (source.exists()) assertTrue(source.delete())
                    if (sentinel.exists()) assertTrue(sentinel.delete())
                    assertTrue("Unexpected files must be retained", directory.delete())
                }
            }
        }

    private fun hasControl(label: String): Boolean =
        compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size == 1

    private fun clickControl(label: String) {
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
    }

    private fun positionMs(): Float =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun awaitPlayingPosition(minimumMs: Float) {
        compose.waitUntil(12_000) { hasControl("Pause") && positionMs() >= minimumMs }
    }

    private fun pcmWav(): ByteArray {
        val sampleRateHz = 16_000
        val durationSeconds = 8
        val bytesPerSample = 2
        val dataBytes = sampleRateHz * durationSeconds * bytesPerSample
        // One channel of signed little-endian PCM. Silence keeps a phone retest quiet.
        return ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM format
            putShort(1) // Channel count
            putInt(sampleRateHz)
            putInt(sampleRateHz * bytesPerSample)
            putShort(bytesPerSample.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()
    }
}
