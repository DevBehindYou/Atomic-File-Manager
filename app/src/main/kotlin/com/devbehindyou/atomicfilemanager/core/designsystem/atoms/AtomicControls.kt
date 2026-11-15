package com.devbehindyou.atomicfilemanager.core.designsystem.atoms

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicMotion
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons

/** 40 dp icon tile with a 1.5 dp border (spec §7.2). [inverted] is the ink tile used for Private. */
@Composable
fun AtomicIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    tint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
) {
    val colors = Atomic.colors
    val shape = AtomicShape.sm
    Box(
        modifier =
            modifier
                .size(AtomicSize.iconTile)
                .background(
                    if (containerColor != Color.Unspecified) {
                        containerColor
                    } else if (inverted) {
                        colors.content
                    } else {
                        colors.surfaceInset
                    },
                    shape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint =
                if (tint != Color.Unspecified) {
                    tint
                } else if (inverted) {
                    colors.background
                } else {
                    colors.content
                },
            modifier = Modifier.size(AtomicSize.iconSmall),
        )
    }
}

/** Filter chip (spec §9.2): off = paper with a hairline; on = ink fill. 48 dp touch target. */
@Composable
fun AtomicChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Atomic.colors
    val spec: AnimationSpec<Color> = if (Atomic.reducedMotion) snap() else AtomicMotion.toggle()
    val fill by animateColorAsState(if (selected) colors.content else colors.background, spec, label = "chipFill")
    val text by animateColorAsState(
        if (selected) colors.background else colors.contentSecondary,
        spec,
        label = "chipText",
    )
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onSelectedChange,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .height(AtomicSize.chipHeight)
                    .background(fill, AtomicShape.pill)
                    .border(
                        AtomicBorder.structure,
                        if (selected) colors.content else colors.borderHair,
                        AtomicShape.pill,
                    )
                    .padding(horizontal = AtomicSpacing.s12),
            contentAlignment = Alignment.Center,
        ) {
            AtomicText(label, AtomicTextRole.MonoLabel, color = text, maxLines = 1)
        }
    }
}

/** Status pill: accent fill with a short state word ("On", "Locked", "Granted"). */
@Composable
fun AtomicStatusPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    Box(
        modifier =
            modifier
                .background(colors.accent, AtomicShape.pill)
                .padding(horizontal = AtomicSpacing.s10, vertical = AtomicSpacing.s4),
        contentAlignment = Alignment.Center,
    ) {
        AtomicText(text, AtomicTextRole.MonoLabel, color = colors.onAccent, maxLines = 1)
    }
}

/** Numeric badge (bubble counts, notifications). */
@Composable
fun AtomicBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    Box(
        modifier =
            modifier
                .heightIn(min = AtomicSpacing.s16)
                .background(colors.accent, AtomicShape.pill)
                .padding(horizontal = AtomicSpacing.s6),
        contentAlignment = Alignment.Center,
    ) {
        AtomicText(count.toString(), AtomicTextRole.MonoMeta, color = colors.onAccent, maxLines = 1)
    }
}

/** Square checkbox (spec §9.5). Pass `onCheckedChange = null` when a parent row handles the toggle. */
@Composable
fun AtomicCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Atomic.colors
    val box = RoundedCornerShape(2.dp)
    val toggle =
        if (onCheckedChange != null) {
            Modifier
                .minimumInteractiveComponentSize()
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        } else {
            Modifier
        }
    Box(modifier = modifier.then(toggle), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(AtomicSize.iconSmall)
                    .background(if (checked) colors.accent else colors.surfaceCard, box)
                    .border(AtomicBorder.structure, if (checked) colors.accent else colors.contentSecondary, box),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    AtomicIcons.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Switch (spec §9.5): on = accent track; off = paper track with an ink outline and ink thumb. */
@Composable
fun AtomicSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Atomic.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.content,
                uncheckedTrackColor = colors.background,
                uncheckedBorderColor = colors.borderStrong,
            ),
    )
}

/** Divider: [strong] is the 1 dp ink rule under headers and section labels; otherwise a hairline. */
@Composable
fun AtomicDivider(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
) {
    val colors = Atomic.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(if (strong) AtomicBorder.rule else AtomicBorder.hair)
            .background(if (strong) colors.borderStrong else colors.borderHair),
    )
}

/** Mono counter on a title's baseline: "128 items", "3 / 50". */
@Composable
fun AtomicCounter(
    text: String,
    modifier: Modifier = Modifier,
) {
    AtomicText(text, AtomicTextRole.MonoLabel, modifier = modifier, maxLines = 1)
}

/** Row of [AtomicChip]s that wraps onto several lines at large font sizes. */
@Composable
fun AtomicChipRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) { content() }
}
