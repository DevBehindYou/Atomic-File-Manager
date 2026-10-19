package com.devbehindyou.atomicfilemanager.core.designsystem.molecules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicCheckbox
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicSwitch
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicElevation
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.hardShadow

private const val HIDDEN_ALPHA = 0.62f
private const val STACK_FONT_SCALE = 1.5f

/**
 * File or folder row (spec §9.3 note card adapted): icon tile, the name exactly as stored, and a
 * mono meta line ("4.2 MB · 2026-10-04 08:11"). Selected rows get a 2 dp accent border and a
 * checkbox; hidden files are faded. Long press starts selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AtomicFileRow(
    name: String,
    meta: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    hidden: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = Atomic.colors
    val frame =
        if (selected) {
            Modifier
                .background(colors.surfaceCard, AtomicShape.sm)
                .border(AtomicBorder.selected, colors.accent, AtomicShape.sm)
                .padding(horizontal = AtomicSpacing.s12)
        } else {
            Modifier
        }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = AtomicSize.touchTarget)
                    .then(frame)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .semantics { if (selectionMode) this.selected = selected }
                    .padding(vertical = AtomicSpacing.s10)
                    .alpha(if (hidden) HIDDEN_ALPHA else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            if (leading != null) leading() else AtomicIconTile(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s2)) {
                AtomicText(name, AtomicTextRole.Name, maxLines = 2)
                AtomicText(meta, AtomicTextRole.MonoMeta, maxLines = 1)
            }
            trailing?.invoke()
            if (selectionMode) AtomicCheckbox(checked = selected, onCheckedChange = null)
        }
        if (!selected) AtomicDivider()
    }
}

/** Settings row (spec §9.4): Display title, optional mono value under it, accent "→". */
@Composable
fun AtomicSettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Atomic.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = AtomicSize.headerContent)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AtomicSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s2)) {
                AtomicText(title, AtomicTextRole.DisplayCard)
                if (value != null) AtomicText(value, AtomicTextRole.MonoMeta)
            }
            if (trailing != null) trailing() else AtomicText("→", AtomicTextRole.MonoMeta, color = colors.accentText)
        }
        AtomicDivider()
    }
}

/** Settings toggle card (spec §9.3): surface panel, 2 dp accent border when on; the whole card toggles. */
@Composable
fun AtomicToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Atomic.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surfaceInset, AtomicShape.sm)
                .border(
                    if (checked) AtomicBorder.selected else AtomicBorder.structure,
                    if (checked) colors.accent else colors.borderHair,
                    AtomicShape.sm,
                ).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(AtomicSpacing.s16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
            AtomicText(title, AtomicTextRole.DisplayCard)
            AtomicText(description, AtomicTextRole.BodySecondary)
        }
        AtomicSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * One option in a single-choice list (hiding method, sort order): same card language as
 * [AtomicToggleCard], 2 dp accent border and a filled marker when selected, radio semantics.
 */
@Composable
fun AtomicChoiceCard(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colors = Atomic.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surfaceInset, AtomicShape.sm)
                .border(
                    if (selected) AtomicBorder.selected else AtomicBorder.structure,
                    if (selected) colors.accent else colors.borderHair,
                    AtomicShape.sm,
                ).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(AtomicSpacing.s16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        Box(
            Modifier
                .size(AtomicSize.iconSmall)
                .border(AtomicBorder.selected, if (selected) colors.accent else colors.borderStrong, CircleShape)
                .padding(AtomicSpacing.s4)
                .background(if (selected) colors.accent else Color.Transparent, CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
            AtomicText(title, AtomicTextRole.Name)
            if (description != null) AtomicText(description, AtomicTextRole.BodySecondary)
        }
    }
}

/**
 * Activity row for the Operations history: title, mono timestamp, optional mono delta, optional
 * text action (Undo, Retry). [failed] adds the 4 dp error priority border.
 */
@Composable
fun AtomicActivityRow(
    title: String,
    timestamp: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    failed: Boolean = false,
) {
    val colors = Atomic.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(colors.surfaceCard, AtomicShape.sm)
                .border(AtomicBorder.structure, colors.borderHair, AtomicShape.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (failed) Box(Modifier.width(AtomicBorder.priority).fillMaxHeight().background(colors.error))
        Row(
            Modifier.weight(1f).padding(AtomicSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s2)) {
                AtomicText(title, AtomicTextRole.Name)
                AtomicText(
                    timestamp,
                    AtomicTextRole.MonoMeta,
                    color = if (failed) colors.error else colors.contentSecondary,
                )
            }
            if (delta != null) AtomicText(delta, AtomicTextRole.MonoMeta, color = colors.accentText)
            if (actionLabel != null && onAction != null) {
                AtomicButton(actionLabel, onClick = onAction, variant = AtomicButtonVariant.Text)
            }
        }
    }
}

/** One row of an [AtomicFactSheet]. [monoValue] for paths, dates and hashes. */
data class AtomicFact(
    val label: String,
    val value: String,
    val monoValue: Boolean = false,
)

/**
 * Fact sheet (spec §9.3): mono labels with values, hairlines between rows, white card with a
 * 6 dp hard shadow. Stacks label above value at large font sizes.
 */
@Composable
fun AtomicFactSheet(
    facts: List<AtomicFact>,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .hardShadow(AtomicElevation.level5, colors.shadow, AtomicShape.md)
                .background(colors.surfaceCard, AtomicShape.md)
                .border(AtomicBorder.structure, colors.borderStrong, AtomicShape.md),
    ) {
        facts.forEachIndexed { index, fact ->
            val valueRole = if (fact.monoValue) AtomicTextRole.MonoMeta else AtomicTextRole.Name
            val content: @Composable () -> Unit = {
                AtomicText(
                    fact.label,
                    AtomicTextRole.MonoLabel,
                    modifier = if (stacked) Modifier else Modifier.width(FACT_LABEL_WIDTH),
                )
                AtomicText(fact.value, valueRole, color = colors.content)
            }
            if (stacked) {
                Column(
                    Modifier.fillMaxWidth().padding(AtomicSpacing.s12),
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
                ) {
                    content()
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(AtomicSpacing.s12),
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s10),
                ) {
                    content()
                }
            }
            if (index < facts.lastIndex) AtomicDivider()
        }
    }
}

private val FACT_LABEL_WIDTH = AtomicSpacing.s74 + AtomicSpacing.s24
