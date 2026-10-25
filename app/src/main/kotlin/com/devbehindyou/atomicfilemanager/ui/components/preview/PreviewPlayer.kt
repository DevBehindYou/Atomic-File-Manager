package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing

/**
 * One Media3 player for a preview (ALL_IN_ONE_PLAN.md 1.5): prepared for [uri], paused when the
 * app goes to the background, and released when the preview closes, so audio never keeps playing
 * behind the app. [onRelease] runs after the player is released (for example to delete a temp copy).
 */
@Composable
fun rememberPreviewPlayer(
    uri: Uri,
    playWhenReady: Boolean,
    onRelease: () -> Unit,
): ExoPlayer {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player =
        remember(uri) {
            createPreviewPlayer(context).apply {
                setMediaItem(MediaItem.fromUri(uri))
                this.playWhenReady = previewAutoplayAllowed(playWhenReady, lifecycleOwner.lifecycle)
                prepare()
            }
        }
    DisposableEffect(player, lifecycleOwner) {
        val binding = PreviewPlayerLifecycleBinding(lifecycleOwner.lifecycle, player::pause)
        onDispose { binding.close() }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
            onRelease()
        }
    }
    return player
}

/** The player created for both audio and video; lifecycle ownership stays with the preview. */
internal fun createPreviewPlayer(context: Context): ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

internal fun previewAutoplayAllowed(
    requested: Boolean,
    lifecycle: Lifecycle,
): Boolean = requested && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

/** Synchronizes late preparation and owner changes without restarting playback on foregrounding. */
internal class PreviewPlayerLifecycleBinding(
    private val lifecycle: Lifecycle,
    private val pause: () -> Unit,
) : AutoCloseable {
    private val observer =
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) pause()
        }

    init {
        lifecycle.addObserver(observer)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) pause()
    }

    override fun close() {
        lifecycle.removeObserver(observer)
    }
}

@Composable
internal fun rememberPlaybackFailed(player: Player): Boolean {
    var failed by remember(player) { mutableStateOf(player.playerError != null) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY) failed = false
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return failed
}

@Composable
internal fun PlaybackFailure(
    player: Player,
    modifier: Modifier = Modifier,
) {
    if (rememberPlaybackFailed(player)) {
        Column(
            modifier.background(Atomic.colors.surfaceCard, AtomicShape.sm).padding(AtomicSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicText(stringResource(R.string.viewer_playback_error), AtomicTextRole.Body)
            AtomicButton(stringResource(R.string.viewer_playback_retry), onClick = {
                player.prepare()
                player.play()
            })
        }
    }
}

/** Playback speeds the preview offers, in tap order; pure so it is unit-tested. */
object PlaybackSpeeds {
    private val steps = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

    fun next(current: Float): Float = steps[(steps.indexOfFirst { it == current } + 1).mod(steps.size)]

    fun label(speed: Float): String {
        val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().trimEnd('0')
        return "$text×"
    }
}
