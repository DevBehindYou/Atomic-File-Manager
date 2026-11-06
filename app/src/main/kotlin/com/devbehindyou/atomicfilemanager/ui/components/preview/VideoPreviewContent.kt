package com.devbehindyou.atomicfilemanager.ui.components.preview

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.awaitCancellation

@Composable
fun VideoPreviewContent(
    node: FileNode,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val helper = container.mediaPreviewHelper
    // A known source revision owns its prepared URI, player, failure and explicit retry attempts.
    key(node.id, node.modifiedAt, node.size, helper) {
        var attempt by remember { mutableIntStateOf(0) }
        key(attempt) {
            VideoPreparationAttempt(node, helper, modifier, onRetry = { attempt++ })
        }
    }
}

@Composable
private fun VideoPreparationAttempt(
    node: FileNode,
    helper: MediaPreviewHelper,
    modifier: Modifier,
    onRetry: () -> Unit,
) {
    var preparedMedia by remember { mutableStateOf<PreparedMedia?>(null) }
    var failure by remember { mutableStateOf<FileError?>(null) }
    LaunchedEffect(Unit) {
        when (
            val result =
                helper.withMedia<Unit>(node.id, node.name) { media ->
                    preparedMedia = media
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
            preparedMedia != null -> VideoPlayerView(media = preparedMedia!!)
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
    media: PreparedMedia,
    modifier: Modifier = Modifier,
) {
    val player = rememberPreviewPlayer(media.playbackUri, playWhenReady = true, onRelease = {})

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
