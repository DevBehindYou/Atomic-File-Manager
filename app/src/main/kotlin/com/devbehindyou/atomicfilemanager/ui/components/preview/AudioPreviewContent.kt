package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
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
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16, Alignment.CenterVertically),
    ) {
        PlaybackFailure(player)
        AudioArtworkCard(artwork = media.metadata.artwork)
        AudioTrackInfo(node = node, media = media)
        PreviewTransportProgress(
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
        PreviewTransportButtons(
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
private fun AudioArtworkCard(artwork: Bitmap?) {
    val colors = Atomic.colors
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp)
                .background(colors.surfaceInset, AtomicShape.lg)
                .padding(AtomicSpacing.s24)
                .testTag("audio_artwork_stage"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .widthIn(max = 140.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(AtomicShape.lg)
                .background(colors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = "Album Artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = AtomicIcons.Audio,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioTrackInfo(
    node: FileNode,
    media: PreparedMedia,
) {
    val title = media.metadata.title ?: node.displayName
    val artist = media.metadata.artist ?: "Unknown Artist"
    val album = media.metadata.album?.takeIf { it.isNotBlank() }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
    ) {
        AtomicText(title, AtomicTextRole.NameLarge, textAlign = TextAlign.Center, maxLines = 2)
        AtomicText(
            if (album != null) "$artist • $album" else artist,
            AtomicTextRole.BodySecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
internal fun PreviewTransportProgress(
    currentMs: Long,
    totalMs: Long,
    sliderValue: Float,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    seekTag: String = "audio_seek",
    seekEnabled: Boolean = true,
) {
    val elapsed = FileUtils.formatDuration(currentMs)
    val total = totalMs.takeIf { it > 0 }?.let(FileUtils::formatDuration)
    Column(Modifier.fillMaxWidth()) {
        AtomicText("Playback position", AtomicTextRole.BodySecondary)
        Slider(
            value = sliderValue.coerceIn(0f, totalMs.toFloat().coerceAtLeast(1f)),
            onValueChange = onSeekChange,
            onValueChangeFinished = onSeekFinished,
            enabled = seekEnabled,
            valueRange = 0f..totalMs.toFloat().coerceAtLeast(1f),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(seekTag)
                    .semantics {
                        contentDescription = "Playback position"
                        stateDescription = if (total == null) elapsed else "$elapsed of $total"
                    },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AtomicText(elapsed, AtomicTextRole.MonoMeta)
            AtomicText(total ?: "Unknown duration", AtomicTextRole.MonoMeta)
        }
    }
}

@Composable
internal fun PreviewTransportButtons(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    seekEnabled: Boolean = true,
) {
    val colors = Atomic.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s16, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtomicIconButton(AtomicIcons.Rewind10, "Back 10 seconds", onClick = onRewind, enabled = seekEnabled)
        IconButton(
            onClick = onTogglePlayPause,
            modifier =
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        ) {
            Icon(
                imageVector = if (isPlaying) AtomicIcons.Pause else AtomicIcons.Play,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(28.dp),
            )
        }
        AtomicIconButton(AtomicIcons.Forward10, "Forward 10 seconds", onClick = onForward, enabled = seekEnabled)
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
