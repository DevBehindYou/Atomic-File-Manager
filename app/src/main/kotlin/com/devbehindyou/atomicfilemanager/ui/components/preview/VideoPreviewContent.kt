package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.data.preview.MediaPreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.PreparedMedia
import com.devbehindyou.atomicfilemanager.data.repository.VideoResumePreferences
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeMemory
import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeState
import com.devbehindyou.atomicfilemanager.domain.repository.VideoRevision
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

@Composable
fun VideoPreviewContent(
    node: FileNode,
    container: AppContainer,
    modifier: Modifier = Modifier,
    resumeMemory: VideoResumeMemory = rememberVideoResumeMemory(),
) {
    val helper = container.mediaPreviewHelper
    // A known source revision owns its prepared URI, player, failure, retry attempts and resume point.
    key(node.id, node.modifiedAt, node.size, helper, resumeMemory) {
        val revision = remember { VideoRevision.of(node) }
        var attempt by remember { mutableIntStateOf(0) }
        key(attempt) {
            VideoPreparationAttempt(node, revision, helper, resumeMemory, modifier, onRetry = { attempt++ })
        }
    }
}

@Composable
private fun rememberVideoResumeMemory(): VideoResumeMemory {
    val application = LocalContext.current.applicationContext
    return remember(application) { VideoResumePreferences.shared(application) }
}

/** A prepared source and the resume point read for its exact revision before the player exists. */
private class PreparedVideo(
    val media: PreparedMedia,
    val saved: VideoResumeState?,
)

@Composable
private fun VideoPreparationAttempt(
    node: FileNode,
    revision: VideoRevision?,
    helper: MediaPreviewHelper,
    resumeMemory: VideoResumeMemory,
    modifier: Modifier,
    onRetry: () -> Unit,
) {
    var prepared by remember { mutableStateOf<PreparedVideo?>(null) }
    var failure by remember { mutableStateOf<FileError?>(null) }
    LaunchedEffect(Unit) {
        val saved = revision?.let { resumeMemory.read(it) }
        when (
            val result =
                helper.withMedia<Unit>(node.id, node.name) { media ->
                    prepared = PreparedVideo(media, saved)
                    awaitCancellation()
                }
        ) {
            is FileResult.Success -> Unit
            is FileResult.Failure -> failure = result.error
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            failure != null -> VideoPreparationFailure(failure!!, onRetry)
            prepared != null -> VideoPlayerView(prepared!!, revision?.let { it to resumeMemory })
            else ->
                AtomicLoading(
                    stringResource(R.string.video_preview_preparing),
                    Modifier
                        .padding(AtomicSpacing.s16)
                        .background(Atomic.colors.surfaceCard, AtomicShape.sm)
                        .padding(AtomicSpacing.s16),
                )
        }
    }
}

@Composable
private fun VideoPreparationFailure(
    error: FileError,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .padding(AtomicSpacing.s16)
            .background(Atomic.colors.surfaceCard, AtomicShape.sm)
            .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AtomicText(stringResource(R.string.video_prepare_failed), AtomicTextRole.BodySecondary)
        if (error.recoverable) AtomicButton(stringResource(R.string.action_retry), onClick = onRetry)
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoPlayerView(
    prepared: PreparedVideo,
    resume: Pair<VideoRevision, VideoResumeMemory>?,
    modifier: Modifier = Modifier,
) {
    val media = prepared.media
    val session =
        remember(media.playbackUri) {
            resume?.let { (revision, memory) ->
                VideoResumeSession(
                    revision,
                    memory,
                    prepared.saved,
                    media.metadata.durationMs,
                    SystemClock::elapsedRealtime,
                )
            }
        }
    val start = session?.start
    val player = rememberPreviewPlayer(media.playbackUri, playWhenReady = start?.playWhenReady ?: true, onRelease = {})
    // Only a newly created player is positioned; recomposing the same preview never seeks again.
    remember(player) {
        start?.let {
            player.seekTo(it.positionMs)
            player.setPlaybackSpeed(it.speed)
        }
    }
    if (session != null) RecordVideoResume(player, session)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Media3's own controls: play, pause, seek, and playback speed in its settings menu.
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        PlaybackFailure(player)

        if (media.metadata.width > 0 && media.metadata.height > 0) {
            VideoMetadataBadge(
                width = media.metadata.width,
                height = media.metadata.height,
                durationMs = media.metadata.durationMs,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
            )
        }
    }
}

/** Feeds pause/speed/end changes, STOP, coarse progress and disposal into [session]. */
@Composable
private fun RecordVideoResume(
    player: Player,
    session: VideoResumeSession,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, session, lifecycleOwner) {
        val listener =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) = session.checkpoint(player.resumeSnapshot())

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
                    session.checkpoint(player.resumeSnapshot())

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) session.checkpoint(player.resumeSnapshot())
                }
            }
        // STOP precedes any process death, so a background kill still restores the last position.
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) session.checkpoint(player.resumeSnapshot())
            }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
        }
    }
    // Registered after the player, so it is disposed before the player is released.
    DisposableEffect(player, session) {
        onDispose { session.close(player.resumeSnapshot()) }
    }
    LaunchedEffect(player, session) {
        while (true) {
            delay(VideoResumeSession.PROGRESS_INTERVAL_MS)
            if (player.isPlaying) session.progress(player.resumeSnapshot())
        }
    }
}

private fun Player.resumeSnapshot() =
    VideoPlaybackSnapshot(currentPosition, playWhenReady, playbackParameters.speed, playbackState == Player.STATE_ENDED)

@Composable
private fun VideoMetadataBadge(
    width: Int,
    height: Int,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.65f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AtomicIcons.Camera,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$width×$height • ${FileUtils.formatDuration(durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
