package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.BaseMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real pinned Media3 control policy, with platform focus/broadcast delivery through Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PreviewPlayerAudioPolicyTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val audio get() = shadowOf(app.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    private val noisy = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private lateinit var player: ExoPlayer
    private var source: PendingMediaSource? = null
    private var released = false

    @Before
    fun createTheSamePlayerAsAudioAndVideoPreviews() {
        audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        player = createPreviewPlayer(app)
    }

    @After
    fun releasePlayerAndItsPendingSource() {
        if (!released) player.release()
        shadowOf(Looper.getMainLooper()).idle()
        source?.let { assertTrue("Owned pending source must release", it.closed.await(3, TimeUnit.SECONDS)) }
    }

    @Test
    fun pausedPlayerDoesNotRequestFocusOrStartByItself() {
        prepare(play = false)
        assertFalse(player.playWhenReady)
        assertNull(audio.lastAudioFocusRequest)
        assertNull(player.playerError)
    }

    @Test
    fun deniedAudioFocusPreventsRequestedPlayback() {
        audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        prepare(play = true)
        assertNotNull("Requested playback must ask Android for focus", audio.lastAudioFocusRequest)
        assertFalse("A denied focus request must not leave playback requested", player.playWhenReady)
        assertNull(player.playerError)
    }

    @Test
    fun permanentFocusLossPausesWithoutResumingOnLaterFocusGain() {
        prepare(play = true)
        val request = checkNotNull(audio.lastAudioFocusRequest) { "Preview did not request focus" }
        assertEquals(C.USAGE_MEDIA, player.audioAttributes.usage)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, request.audioFocusRequest.focusGain)
        assertTrue(player.playWhenReady)
        dispatchFocusChange(request.listener, AudioManager.AUDIOFOCUS_LOSS)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(player.playWhenReady)
        dispatchFocusChange(request.listener, AudioManager.AUDIOFOCUS_GAIN)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("Permanent loss needs a fresh explicit play action", player.playWhenReady)
    }

    @Test
    fun transientFocusLossSuppressesPlaybackUntilFocusReturns() {
        prepare(play = true)
        val request = checkNotNull(audio.lastAudioFocusRequest) { "Preview did not request focus" }
        dispatchFocusChange(request.listener, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Transient loss retains the user's play request", player.playWhenReady)
        assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS, player.playbackSuppressionReason)
        dispatchFocusChange(request.listener, AudioManager.AUDIOFOCUS_GAIN)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(player.playWhenReady)
        assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, player.playbackSuppressionReason)
    }

    @Test
    fun duckingFocusLossKeepsThePlayRequestAndDoesNotOverwriteTheUserVolume() {
        prepare(play = true)
        val request = checkNotNull(audio.lastAudioFocusRequest) { "Preview did not request focus" }
        player.volume = 0.6f
        dispatchFocusChange(request.listener, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(player.playWhenReady)
        assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, player.playbackSuppressionReason)
        assertEquals(0.6f, player.volume, 0.001f)
        dispatchFocusChange(request.listener, AudioManager.AUDIOFOCUS_GAIN)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(player.playWhenReady)
        assertEquals(0.6f, player.volume, 0.001f)
    }

    @Test
    fun headsetDisconnectBroadcastPausesAndDoesNotReplayOnReconnect() {
        prepare(play = true)
        awaitReceiver()
        assertTrue(player.playWhenReady)
        app.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("Rerouting to speakers must pause the preview", player.playWhenReady)
        app.sendBroadcast(Intent(AudioManager.ACTION_HEADSET_PLUG).putExtra("state", 1))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("Reconnect must not override the paused state", player.playWhenReady)
    }

    @Test
    fun releasingPreviewAbandonsFocusAndUnregistersItsNoisyReceiver() {
        prepare(play = true)
        val request = checkNotNull(audio.lastAudioFocusRequest) { "Preview did not request focus" }
        awaitReceiver()
        player.release()
        released = true
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(request.audioFocusRequest, audio.lastAbandonedAudioFocusRequest)
        awaitCondition { !shadowOf(app).hasReceiverForIntent(noisy) }
        val afterRelease = player.playWhenReady
        request.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("An old focus callback cannot revive a released preview", afterRelease, player.playWhenReady)
        assertSame(request, audio.lastAudioFocusRequest)
    }

    private fun prepare(play: Boolean) {
        source = PendingMediaSource()
        player.setMediaSource(checkNotNull(source))
        player.playWhenReady = play
        player.prepare()
        assertTrue("Owned pending source must be admitted", checkNotNull(source).prepared.await(3, TimeUnit.SECONDS))
        awaitPlaybackCallbacks()
        assertEquals(Player.STATE_BUFFERING, player.playbackState)
    }

    /** Platform focus listeners run on Media3's requested playback handler, not the test/main thread. */
    private fun dispatchFocusChange(
        listener: AudioManager.OnAudioFocusChangeListener,
        change: Int,
    ) {
        val delivered = CountDownLatch(1)
        assertTrue(
            Handler(player.playbackLooper).post {
                listener.onAudioFocusChange(change)
                delivered.countDown()
            },
        )
        assertTrue("Focus callback must reach the playback thread", delivered.await(3, TimeUnit.SECONDS))
        awaitPlaybackCallbacks()
    }

    /** Drain commands queued by the callback before reading acknowledged application-thread state. */
    private fun awaitPlaybackCallbacks() {
        val commandsApplied = CountDownLatch(1)
        assertTrue(Handler(player.playbackLooper).post { commandsApplied.countDown() })
        assertTrue("Media3 playback commands must be acknowledged", commandsApplied.await(3, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitReceiver() = awaitCondition { shadowOf(app).hasReceiverForIntent(noisy) }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertTrue("Timed out waiting for Media3's bounded platform callback", condition())
    }

    /** Keeps real ExoPlayer buffering without media I/O, decoder timing, network or unbounded read threads. */
    private class PendingMediaSource : BaseMediaSource() {
        val prepared = CountDownLatch(1)
        val closed = CountDownLatch(1)

        override fun getMediaItem(): MediaItem = MediaItem.EMPTY

        override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
            prepared.countDown()
        }

        override fun maybeThrowSourceInfoRefreshError() = Unit

        override fun createPeriod(
            id: MediaSource.MediaPeriodId,
            allocator: Allocator,
            startPositionUs: Long,
        ): MediaPeriod = error("The pending source never publishes a playable timeline")

        override fun releasePeriod(mediaPeriod: MediaPeriod) = Unit

        override fun releaseSourceInternal() {
            closed.countDown()
        }
    }
}
