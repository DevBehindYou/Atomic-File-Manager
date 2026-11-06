package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.data.preview.MediaMetadata
import com.devbehindyou.atomicfilemanager.data.preview.MediaPreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.PreparedMedia
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

private const val SKIP_MS = 10_000L

@Composable
fun AudioPreviewContent(
    node: FileNode,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val helper = container.mediaPreviewHelper
    // A known source revision owns preparation, player state and explicit retry attempts.
    key(node.id, node.modifiedAt, node.size, helper) {
        var attempt by remember { mutableIntStateOf(0) }
        key(attempt) {
            AudioPreparationAttempt(node, helper, modifier, onRetry = { attempt++ })
        }
    }
}

@Composable
private fun AudioPreparationAttempt(
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

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            failure != null -> AudioPreparationFailure(failure!!, onRetry)
            preparedMedia != null -> AudioPlayerView(node = node, media = preparedMedia!!)
            else -> AtomicLoading(stringResource(R.string.audio_preview_preparing))
        }
    }
}

@Composable
private fun AudioPreparationFailure(
    error: FileError,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AtomicText(stringResource(R.string.audio_prepare_failed), AtomicTextRole.BodySecondary)
        if (error.recoverable) AtomicButton(stringResource(R.string.action_retry), onClick = onRetry)
    }
}

@Composable
private fun AudioPlayerView(
    node: FileNode,
    media: PreparedMedia,
    modifier: Modifier = Modifier,
) {
    val player = rememberPreviewPlayer(media.playbackUri, playWhenReady = false, onRelease = {})
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(media.metadata.durationMs) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(1f) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && player.duration != C.TIME_UNSET) durationMs = player.duration
                    if (state == Player.STATE_ENDED) {
                        // Back to the start, paused, ready to play again.
                        player.pause()
                        player.seekTo(0)
                        currentPositionMs = 0L
                    }
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isUserSeeking) currentPositionMs = player.currentPosition
            delay(250)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PlaybackFailure(player)
        AudioArtworkCard(artwork = media.metadata.artwork)
        Spacer(modifier = Modifier.height(24.dp))
        AudioTrackInfo(node = node, media = media)
        Spacer(modifier = Modifier.height(20.dp))
        AudioProgressBar(
            currentMs = if (isUserSeeking) seekPositionMs.toLong() else currentPositionMs,
            totalMs = durationMs,
            sliderValue = if (isUserSeeking) seekPositionMs else currentPositionMs.toFloat(),
            onSeekChange = {
                isUserSeeking = true
                seekPositionMs = it
            },
            onSeekFinished = {
                isUserSeeking = false
                player.seekTo(seekPositionMs.toLong())
                currentPositionMs = seekPositionMs.toLong()
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        AudioPlaybackControls(
            isPlaying = isPlaying,
            onTogglePlayPause = { if (player.isPlaying) player.pause() else player.play() },
            onRewind = {
                player.seekTo((player.currentPosition - SKIP_MS).coerceAtLeast(0))
                currentPositionMs = player.currentPosition
            },
            onForward = {
                val end = if (durationMs > 0) durationMs else Long.MAX_VALUE
                player.seekTo((player.currentPosition + SKIP_MS).coerceAtMost(end))
                currentPositionMs = player.currentPosition
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        AtomicChip(
            label = "Speed ${PlaybackSpeeds.label(speed)}",
            selected = speed != 1f,
            onSelectedChange = {
                speed = PlaybackSpeeds.next(speed)
                player.setPlaybackSpeed(speed)
            },
            modifier = Modifier.testTag("audio_speed"),
        )
        val details = AudioTagText.facts(media.metadata)
        if (details.isNotEmpty()) {
            var showDetails by remember(node.id) { mutableStateOf(false) }
            Spacer(modifier = Modifier.height(8.dp))
            AtomicButton(
                if (showDetails) "Hide details" else "Details",
                onClick = { showDetails = !showDetails },
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.testTag("audio_details"),
            )
            if (showDetails) AtomicFactSheet(details)
        }
    }
}

@Composable
private fun AudioArtworkCard(
    artwork: Bitmap?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.size(200.dp),
        tonalElevation = 6.dp,
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork.asImageBitmap(),
                contentDescription = "Album Artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = AtomicIcons.Audio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioTrackInfo(
    node: FileNode,
    media: PreparedMedia,
    modifier: Modifier = Modifier,
) {
    val title = media.metadata.title ?: node.displayName
    val artist = media.metadata.artist ?: "Unknown Artist"
    val album = media.metadata.album?.takeIf { it.isNotBlank() }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (album != null) "$artist • $album" else artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AudioProgressBar(
    currentMs: Long,
    totalMs: Long,
    sliderValue: Float,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue.coerceIn(0f, totalMs.toFloat().coerceAtLeast(1f)),
            onValueChange = onSeekChange,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..totalMs.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = FileUtils.formatDuration(currentMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = FileUtils.formatDuration(totalMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AudioPlaybackControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtomicIconButton(AtomicIcons.Rewind10, "Back 10 seconds", onClick = onRewind)
        Spacer(modifier = Modifier.width(AtomicSpacing.s16))
        AtomicIconButton(
            if (isPlaying) AtomicIcons.Pause else AtomicIcons.Play,
            if (isPlaying) "Pause" else "Play",
            onClick = onTogglePlayPause,
            variant = AtomicIconButtonVariant.Accent,
        )
        Spacer(modifier = Modifier.width(AtomicSpacing.s16))
        AtomicIconButton(AtomicIcons.Forward10, "Forward 10 seconds", onClick = onForward)
    }
}

/** Tag details under the player (ALL_IN_ONE_PLAN.md 4.2); pure so it is unit-tested. */
internal object AudioTagText {
    fun facts(meta: MediaMetadata): List<AtomicFact> {
        val tags = meta.tags
        return listOfNotNull(
            tags.albumArtist?.takeIf { it != meta.artist }?.let { AtomicFact("Album artist", it) },
            tags.year?.let { AtomicFact("Year", it, monoValue = true) },
            tags.genre?.let { AtomicFact("Genre", it) },
            tags.track?.let { AtomicFact("Track", it, monoValue = true) },
            tags.disc?.let { AtomicFact("Disc", it, monoValue = true) },
            tags.composer?.let { AtomicFact("Composer", it) },
            quality(tags.bitrateBps, tags.sampleRateHz, tags.bitsPerSample)?.let {
                AtomicFact("Quality", it, monoValue = true)
            },
            meta.mimeType?.let { AtomicFact("Format", it, monoValue = true) },
        ).takeIf { facts -> facts.any { it.label != "Format" } }.orEmpty()
    }

    /** "320 kbps · 44.1 kHz · 16-bit", leaving out what the file doesn't say. */
    fun quality(
        bitrateBps: Int?,
        sampleRateHz: Int?,
        bitsPerSample: Int?,
    ): String? =
        listOfNotNull(
            bitrateBps?.let { "${(it + KILO / 2) / KILO} kbps" },
            sampleRateHz?.let { if (it % KILO == 0) "${it / KILO} kHz" else "${it / KILO}.${it % KILO / HUNDRED} kHz" },
            bitsPerSample?.let { "$it-bit" },
        ).joinToString(" · ").ifEmpty { null }

    private const val KILO = 1000
    private const val HUNDRED = 100
}
