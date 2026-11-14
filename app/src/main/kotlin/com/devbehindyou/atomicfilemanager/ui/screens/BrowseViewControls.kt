package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicMotion
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode

/** Same persisted view preference as File options, made directly reachable from the listing. */
@Composable
internal fun BrowseViewControls(
    mode: FileViewMode,
    onChange: (FileViewMode) -> Unit,
) {
    val colors = Atomic.colors
    Row(Modifier.background(colors.surfaceCard, AtomicShape.sm).padding(AtomicSpacing.s2).selectableGroup()) {
        FileViewMode.entries.forEach { value ->
            val selected = mode == value
            val fill by animateColorAsState(
                if (selected) colors.surfaceInset else Color.Transparent,
                if (Atomic.reducedMotion) snap() else AtomicMotion.toggle(),
                label = "fileViewFill",
            )
            Box(
                Modifier.size(AtomicSize.touchTarget).clip(AtomicShape.sm).background(fill)
                    .selectable(selected, role = Role.RadioButton, onClick = { onChange(value) })
                    .testTag("browse_view_${value.name.lowercase()}")
                    .semantics { contentDescription = if (value == FileViewMode.LIST) "List view" else "Grid view" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (value == FileViewMode.LIST) AtomicIcons.List else AtomicIcons.Grid,
                    null,
                    tint = if (selected) colors.accentText else colors.content,
                    modifier = Modifier.size(AtomicSize.iconSmall),
                )
            }
        }
    }
}
