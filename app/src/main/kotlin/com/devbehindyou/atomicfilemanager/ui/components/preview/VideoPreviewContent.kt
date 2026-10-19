package com.devbehindyou.atomicfilemanager.ui.components.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.data.preview.PreparedMedia
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

@Composable
fun VideoPreviewContent(
    node: FileNode,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    var preparedMedia by remember(node.id) { mutableStateOf<PreparedMedia?>(null) }
    var isLoading by remember(node.id) { mutableStateOf(true) }
    var errorMessage by remember(node.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(node.id) {
        isLoading = true
        when (
            val res =
                container.mediaPreviewHelper.withMedia<Unit>(node.id, node.name) { media ->
                    preparedMedia = media
                    isLoading = false
                    kotlinx.coroutines.awaitCancellation()
                }
        ) {
            is FileResult.Success -> {
                Unit
            }
            is FileResult.Failure -> {
                errorMessage = "Failed to load video file"
                isLoading = false
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> AtomicLoading("Loading video…")
            errorMessage != null -> Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            preparedMedia != null -> VideoPlayerView(media = preparedMedia!!)
        }
    }
}

@Composable
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
