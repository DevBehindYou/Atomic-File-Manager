package com.devbehindyou.atomicfilemanager

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devbehindyou.atomicfilemanager.ui.components.preview.createPreviewPlayer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

/**
 * The real preview player against the platform focus stack: another media request interrupts it
 * the way a second player app or a call would. Silent owned PCM keeps the device quiet.
 */
@RunWith(AndroidJUnit4::class)
class PreviewAudioFocusDeviceTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private lateinit var directory: File
    private lateinit var source: File
    private lateinit var original: ByteArray
    private lateinit var player: ExoPlayer
    private val competitors = mutableListOf<AudioFocusRequest>()

    @Before
    fun prepareOwnedSilentWav() {
        directory = Files.createTempDirectory(app.cacheDir.toPath(), "audio-focus-native-").toFile()
        source = File(directory, "owned.wav")
        original = silentWav(seconds = 20)
        source.writeBytes(original)
        player =
            onMain {
                createPreviewPlayer(app).apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(source)))
                    prepare()
                }
            }
    }

    @After
    fun releaseEverythingAndKeepTheSourceIntact() {
        try {
            competitors.forEach { audio.abandonAudioFocusRequest(it) }
            onMain { player.release() }
            assertArrayEquals("Playback must never change the source", original, source.readBytes())
        } finally {
            if (source.exists()) assertTrue(source.delete())
            assertTrue("Unexpected files must be retained", directory.delete())
        }
    }

    @Test
    fun permanentFocusLossPausesAndDoesNotResumeWhenTheOtherAppLeaves() {
        startPlaying()

        val competitor = takeFocus(AudioManager.AUDIOFOCUS_GAIN)
        awaitMain("Permanent loss must pause") { !player.playWhenReady && !player.isPlaying }

        audio.abandonAudioFocusRequest(competitor)
        Thread.sleep(SETTLE_MS)
        assertFalse("A permanent loss needs a fresh Play", onMain { player.playWhenReady })
        assertFalse(onMain { player.isPlaying })

        startPlaying()
    }

    @Test
    fun transientFocusLossSuppressesPlaybackAndResumesWhenFocusReturns() {
        startPlaying()

        val competitor = takeFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        awaitMain("Transient loss must suppress playback") {
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS &&
                !player.isPlaying
        }
        assertTrue("The user's Play request is kept", onMain { player.playWhenReady })

        audio.abandonAudioFocusRequest(competitor)
        awaitMain("Playback must resume when focus returns") {
            player.isPlaying && player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
        }
    }

    @Test
    fun duckingKeepsPlayingWithoutChangingTheUserVolume() {
        startPlaying()
        val volume = onMain { player.volume }

        val competitor = takeFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        Thread.sleep(SETTLE_MS)

        assertTrue("A duck request must not stop the preview", onMain { player.isPlaying })
        assertEquals(volume, onMain { player.volume }, 0f)
        audio.abandonAudioFocusRequest(competitor)
        Thread.sleep(SETTLE_MS)
        assertTrue(onMain { player.isPlaying })
        assertEquals(volume, onMain { player.volume }, 0f)
    }

    private fun startPlaying() {
        onMain { player.play() }
        awaitMain("The owned WAV must start playing") { player.isPlaying }
    }

    private fun takeFocus(gain: Int): AudioFocusRequest {
        val request =
            AudioFocusRequest.Builder(gain)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener { }
                .build()
        competitors += request
        assertEquals(
            "The competing request must be granted",
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
            audio.requestAudioFocus(request),
        )
        return request
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun awaitMain(
        message: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
        while (!onMain(condition)) {
            assertTrue(message, System.nanoTime() < deadline)
            Thread.sleep(POLL_MS)
        }
    }

    private fun silentWav(seconds: Int): ByteArray {
        val sampleRateHz = 16_000
        val bytesPerSample = 2
        val dataBytes = sampleRateHz * seconds * bytesPerSample
        // One channel of signed little-endian PCM silence.
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

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
        const val SETTLE_MS = 750L
    }
}
