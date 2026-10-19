package com.devbehindyou.atomicfilemanager.core.designsystem.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicMeter
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicProgressBar
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.METER_HIGH_THRESHOLD
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicElevation
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.hardShadow

/**
 * Storage volume card: Display name (app-authored, e.g. "Phone · internal"), mono usage, meter,
 * and a "nearly full" line at or above the meter threshold so colour is never the only signal.
 */
@Composable
fun AtomicStorageCard(
    name: String,
    usage: String,
    fraction: Float,
    meterDescription: String,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    browseLabel: String = "Browse →",
    nearlyFullLabel: String? = null,
) {
    val colors = Atomic.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .hardShadow(AtomicElevation.level4, colors.shadow, AtomicShape.md)
                .background(colors.surfaceCard, AtomicShape.md)
                .border(AtomicBorder.structure, colors.borderStrong, AtomicShape.md)
                .clickable(role = Role.Button, onClick = onBrowse)
                .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s10),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicText(name, AtomicTextRole.DisplayCard, modifier = Modifier.weight(1f), maxLines = 1)
            AtomicText(
                usage,
                AtomicTextRole.MonoMeta,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
        AtomicMeter(fraction = fraction, contentDescription = meterDescription)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fraction >= METER_HIGH_THRESHOLD && nearlyFullLabel != null) {
                AtomicText(
                    nearlyFullLabel,
                    AtomicTextRole.MonoLabel,
                    color = colors.error,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(Modifier.weight(1f)) {}
            }
            AtomicText(browseLabel, AtomicTextRole.MonoLabel, color = colors.accentText)
        }
    }
}

/** Running operation (spec Operations screen): title, route, ink progress bar, mono progress line. */
@Composable
fun AtomicOperationCard(
    title: String,
    subtitle: String,
    fraction: Float,
    progressText: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Cancel",
    detail: String? = null,
) {
    val colors = Atomic.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .hardShadow(AtomicElevation.level4, colors.shadow, AtomicShape.md)
                .background(colors.surfaceCard, AtomicShape.md)
                .border(AtomicBorder.structure, colors.borderStrong, AtomicShape.md)
                .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s10),
    ) {
        AtomicText(title, AtomicTextRole.DisplayCard)
        AtomicText(subtitle, AtomicTextRole.BodySecondary)
        AtomicProgressBar(fraction = fraction, contentDescription = "$title, $progressText")
        AtomicText(progressText, AtomicTextRole.MonoMeta)
        if (detail != null) AtomicText(detail, AtomicTextRole.MonoMeta)
        AtomicButton(
            cancelLabel,
            onClick = onCancel,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One destructive action in an [AtomicDangerZone]. */
data class AtomicDangerAction(
    val label: String,
    val onExecute: () -> Unit,
    val buttonLabel: String = "Execute",
)

/** Danger zone (spec §9.4): label, warning sentence, error-bordered panel, each action with its own button. */
@Composable
fun AtomicDangerZone(
    warning: String,
    actions: List<AtomicDangerAction>,
    modifier: Modifier = Modifier,
    label: String = "Danger zone",
) {
    val colors = Atomic.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
        AtomicText(label, AtomicTextRole.MonoLabel, color = colors.error)
        AtomicText(warning, AtomicTextRole.BodySecondary, color = colors.error)
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceInset, AtomicShape.sm)
                .border(AtomicBorder.danger, colors.error, AtomicShape.sm)
                .padding(AtomicSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
        ) {
            actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceCard, AtomicShape.xs)
                        .padding(start = AtomicSpacing.s12, end = AtomicSpacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AtomicText(
                        action.label,
                        AtomicTextRole.MonoLabel,
                        color = colors.content,
                        modifier = Modifier.weight(1f),
                    )
                    AtomicButton(
                        action.buttonLabel,
                        onClick = action.onExecute,
                        variant = AtomicButtonVariant.Destructive,
                    )
                }
            }
        }
    }
}

/** One action in an [AtomicActionStrip]. */
data class AtomicStripAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/**
 * Bottom action strip shown in selection mode: up to four actions plus "More" (spec Files
 * selection). Labels are sentence case and rendered as mono caps.
 */
@Composable
fun AtomicActionStrip(
    actions: List<AtomicStripAction>,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        AtomicDivider(strong = true)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s8, vertical = AtomicSpacing.s6),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            actions.forEach { action ->
                val tint = if (action.destructive) colors.error else colors.content
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = AtomicSize.headerContent)
                        .clickable(role = Role.Button, onClick = action.onClick)
                        .padding(vertical = AtomicSpacing.s6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
                ) {
                    Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(AtomicSize.icon))
                    AtomicText(action.label, AtomicTextRole.MonoLabel, color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** Floating actions (spec §9.6): primary ink button with an optional secondary above it. */
@Composable
fun AtomicFabStack(
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryIcon: ImageVector? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s10),
    ) {
        if (secondaryLabel != null && onSecondary != null) {
            AtomicButton(secondaryLabel, onClick = onSecondary, variant = AtomicButtonVariant.Ghost)
        }
        AtomicButton(primaryLabel, onClick = onPrimary, variant = AtomicButtonVariant.Solid, leadingIcon = primaryIcon)
    }
}
