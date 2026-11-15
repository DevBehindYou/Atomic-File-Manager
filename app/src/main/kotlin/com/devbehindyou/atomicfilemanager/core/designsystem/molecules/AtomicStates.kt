package com.devbehindyou.atomicfilemanager.core.designsystem.molecules

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing

/** Empty state (spec §9.9): a surface module with one plain sentence and one next step. */
@Composable
fun AtomicEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Atomic.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surfaceInset, AtomicShape.sm)
                .border(AtomicBorder.hair, colors.borderHair, AtomicShape.sm)
                .padding(AtomicSpacing.s22),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        AtomicText(message, AtomicTextRole.DisplayCard)
        if (detail != null) AtomicText(detail, AtomicTextRole.BodySecondary)
        if (actionLabel != null && onAction != null) {
            AtomicButton(actionLabel, onClick = onAction, variant = AtomicButtonVariant.Solid)
        }
    }
}

/** Error state: what happened, how to recover, and an optional details action. Announced politely. */
@Composable
fun AtomicErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    detailsLabel: String? = null,
    onDetails: (() -> Unit)? = null,
) {
    val colors = Atomic.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.errorContainer, AtomicShape.sm)
                .border(AtomicBorder.structure, colors.borderHair, AtomicShape.sm)
                .padding(AtomicSpacing.s16)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicText(title, AtomicTextRole.MonoLabel, color = colors.onErrorContainer)
        AtomicText(message, AtomicTextRole.BodySecondary, color = colors.onErrorContainer)
        Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            if (actionLabel != null && onAction != null) {
                AtomicButton(actionLabel, onClick = onAction, variant = AtomicButtonVariant.LightOnAccent)
            }
            if (detailsLabel != null && onDetails != null) {
                AtomicButton(detailsLabel, onClick = onDetails, variant = AtomicButtonVariant.Text)
            }
        }
    }
}

/** Warning or requirement box (spec §9.9): error container, mono caps title, body. */
@Composable
fun AtomicWarningBox(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.errorContainer, AtomicShape.sm)
                .border(AtomicBorder.structure, colors.borderHair, AtomicShape.sm)
                .padding(AtomicSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
    ) {
        AtomicText(title, AtomicTextRole.MonoLabel, color = colors.onErrorContainer)
        AtomicText(body, AtomicTextRole.BodySecondary, color = colors.onErrorContainer)
    }
}

/** Stat tile (spec §9.8): Display number with a mono caption. [featured] gets the accent. */
@Composable
fun AtomicStatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = Atomic.colors
    Column(
        modifier =
            modifier
                .background(colors.surfaceCard, AtomicShape.sm)
                .border(
                    if (featured) AtomicBorder.selected else AtomicBorder.structure,
                    if (featured) colors.accent else colors.borderStrong,
                    AtomicShape.sm,
                ).then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(AtomicSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
    ) {
        AtomicText(value, AtomicTextRole.DisplayPushed, color = if (featured) colors.accentText else colors.content)
        AtomicText(caption, AtomicTextRole.MonoLabel)
    }
}

/**
 * Category tile for the Home grid (canvas "Home"): icon tile, display label, mono meta line.
 * [inverted] draws the ink icon tile used for locked destinations such as Private.
 */
@Composable
fun AtomicCategoryTile(
    label: String,
    meta: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    tint: Color = Color.Unspecified,
) {
    val colors = Atomic.colors
    Column(
        modifier =
            modifier
                .clip(AtomicShape.md).background(colors.surfaceCard, AtomicShape.md)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = AtomicSize.touchTarget)
                .padding(horizontal = AtomicSpacing.s8, vertical = AtomicSpacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicIconTile(
            icon,
            modifier = Modifier.size(AtomicSpacing.s32 + AtomicSpacing.s8),
            inverted = inverted,
            tint = if (inverted) colors.background else tint,
            containerColor = if (inverted) colors.content else colors.accent.copy(alpha = 0.12f),
        )
        AtomicText(label, AtomicTextRole.MonoLabel, color = colors.content, maxLines = 2, textAlign = TextAlign.Center)
        AtomicText(meta, AtomicTextRole.MonoMeta, maxLines = 1, textAlign = TextAlign.Center)
    }
}
