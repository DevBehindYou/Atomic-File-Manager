package com.devbehindyou.atomicfilemanager.core.designsystem.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.focusRing

/** A command offered by an action menu; forms and destructive confirmations keep their own sheets. */
data class AtomicMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val description: String? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val testTag: String? = null,
)

/** Natural content height, with the shared sheet providing the screen-height cap and scrolling. */
@Composable
fun AtomicMenuSheet(
    label: String,
    actions: List<AtomicMenuAction>,
    onDismiss: () -> Unit,
    intro: @Composable ColumnScope.() -> Unit = {},
) {
    AtomicSheet(label, onDismiss) {
        intro()
        AtomicCommandGrid(actions)
    }
}

/** A bounded number of columns; rows grow with font scale and wrap labels instead of truncating commands. */
@Composable
fun AtomicCommandGrid(
    actions: List<AtomicMenuAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns =
            ((maxWidth + AtomicSpacing.s8) / (AtomicSize.menuCardMinWidth + AtomicSpacing.s8)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            actions.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                ) {
                    row.forEach { action ->
                        AtomicCommandCard(action, Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AtomicCommandCard(
    action: AtomicMenuAction,
    modifier: Modifier,
) {
    val colors = Atomic.colors
    val tint = if (action.destructive) colors.error else colors.content
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tag = action.testTag?.let { Modifier.testTag(it) } ?: Modifier
    Column(
        modifier.then(tag)
            .focusRing(focused, colors.accentText, AtomicShape.radiusSm)
            .alpha(if (action.enabled) 1f else DISABLED_MENU_ALPHA)
            .background(colors.surfaceInset, AtomicShape.sm)
            .border(
                AtomicBorder.structure,
                if (action.destructive) colors.error else colors.borderStrong,
                AtomicShape.sm,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = action.enabled,
                role = Role.Button,
                onClick = action.onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = action.label
                if (action.destructive) {
                    stateDescription = "Destructive action"
                } else if (action.description != null) {
                    stateDescription = action.description
                }
            }
            .padding(AtomicSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(AtomicSize.menuIconTile)
                    .border(
                        AtomicBorder.structure,
                        if (action.destructive) colors.error else colors.borderStrong,
                        AtomicShape.xs,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, null, tint = tint, modifier = Modifier.size(AtomicSize.iconSmall))
            }
            Spacer(Modifier.weight(1f))
            Icon(AtomicIcons.Forward, null, tint = tint, modifier = Modifier.size(AtomicSize.iconSmall))
        }
        AtomicText(action.label, AtomicTextRole.Body, color = tint)
        action.description?.let { AtomicText(it, AtomicTextRole.BodySecondary, maxLines = 2) }
    }
}

private const val DISABLED_MENU_ALPHA = 0.45f
