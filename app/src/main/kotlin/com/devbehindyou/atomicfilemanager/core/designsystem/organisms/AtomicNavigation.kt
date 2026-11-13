package com.devbehindyou.atomicfilemanager.core.designsystem.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing

/** A top-level destination for the bottom bar or rail. [label] is sentence case. */
data class AtomicDestination(
    val key: String,
    val label: String,
    val icon: ImageVector,
    /** Stable tag for UI tests (for example "tab_home"). */
    val testTag: String? = null,
)

/** Bottom navigation with visible labels and a pill around the selected icon. */
@Composable
fun AtomicBottomBar(
    destinations: List<AtomicDestination>,
    selectedKey: String,
    onSelect: (AtomicDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Box(
            Modifier.fillMaxWidth().heightIn(
                min = AtomicBorder.rule,
                max = AtomicBorder.rule,
            ).background(colors.borderHair),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = BAR_HEIGHT)
                    .padding(horizontal = AtomicSpacing.s12)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                DestinationItem(destination, destination.key == selectedKey, Modifier.weight(1f)) { onSelect(destination) }
            }
        }
    }
}

/** Navigation rail for medium and expanded widths, same language as [AtomicBottomBar]. */
@Composable
fun AtomicNavRail(
    destinations: List<AtomicDestination>,
    selectedKey: String,
    onSelect: (AtomicDestination) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = Atomic.colors
    Row(modifier.fillMaxHeight().background(colors.background)) {
        Column(
            modifier =
                Modifier
                    .width(RAIL_WIDTH)
                    .fillMaxHeight()
                    .padding(vertical = AtomicSpacing.s16)
                    .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            header?.invoke()
            destinations.forEach { destination ->
                DestinationItem(destination, destination.key == selectedKey, Modifier.fillMaxWidth()) { onSelect(destination) }
            }
        }
        Box(Modifier.fillMaxHeight().width(AtomicBorder.rule).background(colors.borderHair))
    }
}

@Composable
private fun DestinationItem(
    destination: AtomicDestination,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = Atomic.colors
    Column(
        modifier =
            modifier
                .heightIn(min = AtomicSize.touchTarget)
                .widthIn(min = AtomicSize.touchTarget)
                .then(if (destination.testTag != null) Modifier.testTag(destination.testTag) else Modifier)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .semantics { contentDescription = destination.label }
                .padding(vertical = AtomicSpacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4, Alignment.CenterVertically),
    ) {
        Box(
            modifier =
                Modifier
                    .then(if (selected) Modifier.background(colors.accent, AtomicShape.pill) else Modifier)
                    .padding(horizontal = AtomicSpacing.s12, vertical = AtomicSpacing.s4),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = if (selected) colors.onAccent else colors.content,
                modifier = Modifier.size(AtomicSize.icon),
            )
        }
        Text(
            text = destination.label,
            modifier = Modifier.clearAndSetSemantics {},
            color = if (selected) colors.accentText else colors.contentSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

private val BAR_HEIGHT = AtomicSpacing.s74 - AtomicSpacing.s2
private val RAIL_WIDTH = AtomicSpacing.s74 + AtomicSpacing.s22
