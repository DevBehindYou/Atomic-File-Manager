package com.devbehindyou.atomicfilemanager.core.designsystem.atoms

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicMotion
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing

/** Fraction at which a storage meter uses the high-usage state. */
const val METER_HIGH_THRESHOLD = 0.8f

/**
 * Storage meter (the spec's energy bar). Below [highThreshold] the fill is the accent; at or above
 * it the fill uses the high-usage colour. Callers also show a "Nearly full" label.
 */
@Composable
fun AtomicMeter(
    fraction: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highThreshold: Float = METER_HIGH_THRESHOLD,
    onAccentSurface: Boolean = false,
) {
    val colors = Atomic.colors
    val target = fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = if (Atomic.reducedMotion) snap() else AtomicMotion.enter(),
        label = "meter",
    )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AtomicSize.meterHeight)
                .clip(AtomicShape.xs)
                .background(if (onAccentSurface) colors.onAccent.copy(alpha = 0.25f) else colors.meterTrack)
                .semantics {
                    this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown)
                .background(
                    when {
                        onAccentSurface -> colors.onAccent
                        target >= highThreshold -> colors.meterHigh
                        else -> colors.meterNormal
                    },
                ),
        )
    }
}

/** Determinate operation progress: 8 dp ink bar inside a 2 dp border (Operations screen). */
@Composable
fun AtomicProgressBar(
    fraction: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    val target = fraction.coerceIn(0f, 1f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(AtomicSize.meterHeight + AtomicBorder.control * 2)
                .clip(AtomicShape.xs)
                .background(colors.meterTrack)
                .border(AtomicBorder.control, colors.borderStrong, AtomicShape.xs)
                .semantics {
                    this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(target).background(colors.content))
    }
}

/**
 * Loading state (spec §9.9): a 2 dp ink indeterminate bar and a mono label such as
 * "Loading folder…". No spinners on content. With reduced motion the bar is static.
 */
@Composable
fun AtomicLoading(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    val reduced = Atomic.reducedMotion
    val position =
        if (reduced) {
            STATIC_SEGMENT_START
        } else {
            val transition = rememberInfiniteTransition(label = "loading")
            val value by transition.animateFloat(
                initialValue = -SEGMENT,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
                label = "loadingSegment",
            )
            value
        }
    Column(
        modifier =
            modifier.semantics(
                mergeDescendants = true,
            ) { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            drawRect(colors.meterTrack)
            val start = (position * size.width).coerceAtLeast(0f)
            val end = ((position + SEGMENT) * size.width).coerceAtMost(size.width)
            if (end > start) {
                drawRect(
                    colors.content,
                    topLeft = Offset(start, 0f),
                    size = Size(end - start, size.height),
                )
            }
        }
        AtomicText(label, AtomicTextRole.MonoLabel)
    }
}

private const val SEGMENT = 0.4f
private const val STATIC_SEGMENT_START = 0.3f
private const val LOOP_MS = 1400
