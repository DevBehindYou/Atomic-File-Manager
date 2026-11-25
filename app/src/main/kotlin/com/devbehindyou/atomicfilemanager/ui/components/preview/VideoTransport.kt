package com.devbehindyou.atomicfilemanager.ui.components.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import kotlinx.coroutines.delay

private const val VIDEO_POSITION_REFRESH_MS = 250L
private const val VIDEO_SKIP_MS = 10_000L
private val videoPlaybackSpeeds = listOf(0.5f, 1f, 1.5f, 2f)

private data class VideoControlState(
    val duration: Long,
    val seekable: Boolean,
    val playRequested: Boolean,
    val speed: Float,
    val adjustableSpeed: Boolean,
)

private fun Player.controlState() =
    VideoControlState(
        duration.coerceAtLeast(0),
        isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) && isCurrentMediaItemSeekable && duration > 0,
        playWhenReady && playbackState != Player.STATE_ENDED,
        playbackParameters.speed,
        isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH),
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoTransport(
    player: Player,
    onExpand: () -> Unit,
) {
    var state by remember(player) { mutableStateOf(player.controlState()) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var drag by remember(player) { mutableStateOf<Float?>(null) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    state = player.controlState()
                    position = player.currentPosition.coerceAtLeast(0L)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, state.playRequested) {
        while (state.playRequested) {
            position = player.currentPosition.coerceAtLeast(0L)
            delay(VIDEO_POSITION_REFRESH_MS)
        }
    }

    fun seek(requested: Long) {
        val current = player.controlState()
        if (!current.seekable) return
        val target = requested.coerceIn(0L, current.duration)
        player.seekTo(target)
        position = target
    }

    val maxHeight = (LocalConfiguration.current.screenHeightDp / 2).dp
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .testTag("video_transport"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
    ) {
        PreviewTransportProgress(
            currentMs = drag?.toLong() ?: position,
            totalMs = state.duration,
            sliderValue = drag ?: position.toFloat(),
            onSeekChange = { drag = it },
            onSeekFinished = {
                drag?.let { seek(it.toLong()) }
                drag = null
            },
            seekTag = "video_seek",
            seekEnabled = state.seekable,
        )
        PreviewTransportButtons(
            isPlaying = state.playRequested,
            onTogglePlayPause = {
                when {
                    player.controlState().playRequested -> player.pause()
                    player.playbackState == Player.STATE_ENDED && state.seekable -> {
                        player.seekTo(0L)
                        player.play()
                    }
                    else -> player.play()
                }
            },
            onRewind = { seek(player.currentPosition - VIDEO_SKIP_MS) },
            onForward = { seek(player.currentPosition + VIDEO_SKIP_MS) },
            seekEnabled = state.seekable,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8, Alignment.CenterHorizontally)) {
            VideoSpeedMenu(state, onSpeed = { player.setPlaybackSpeed(it) })
            AtomicButton(
                stringResource(R.string.video_expand),
                onClick = onExpand,
                variant = AtomicButtonVariant.Ghost,
                modifier = Modifier.testTag("video_expand"),
            )
        }
    }
}

@Composable
private fun VideoSpeedMenu(
    state: VideoControlState,
    onSpeed: (Float) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AtomicButton(
            stringResource(R.string.video_speed, state.speed),
            onClick = { open = true },
            enabled = state.adjustableSpeed,
            variant = AtomicButtonVariant.Ghost,
        )
        DropdownMenu(expanded = open && state.adjustableSpeed, onDismissRequest = { open = false }) {
            for (speed in videoPlaybackSpeeds) {
                DropdownMenuItem(
                    text = { AtomicText(stringResource(R.string.video_speed_value, speed), AtomicTextRole.Body) },
                    onClick = {
                        open = false
                        onSpeed(speed)
                    },
                )
            }
        }
    }
}
