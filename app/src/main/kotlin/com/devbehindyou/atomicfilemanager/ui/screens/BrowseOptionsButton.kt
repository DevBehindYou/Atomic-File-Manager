package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.ui.components.AtomicTopDialog
import com.devbehindyou.atomicfilemanager.ui.components.BalancedPopupHeader

private enum class BrowseOptionsPage(val title: String) {
    MAIN("File options"),
    VIEW("View"),
    SORT("Sort by"),
    FILTER("Filter"),
}

/** Compact folder options; selecting an option never discards the pane's clipboard. */
@Composable
internal fun BrowseOptionsButton(
    viewMode: FileViewMode,
    onViewModeChange: (FileViewMode) -> Unit,
    sortOption: SortOption,
    onSortChange: (SortOption) -> Unit,
    filter: BrowseFileFilter,
    onFilterChange: (BrowseFileFilter) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    onNewFolder: () -> Unit,
    onShowHiddenFilesScreen: () -> Unit,
    dualPaneEnabled: Boolean? = null,
    onDualPaneChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(BrowseOptionsPage.MAIN) }
    Box {
        AtomicIconButton(
            AtomicIcons.More,
            "File options",
            onClick = {
                page = BrowseOptionsPage.MAIN
                expanded = !expanded
            },
            variant = if (expanded) AtomicIconButtonVariant.Accent else AtomicIconButtonVariant.Ghost,
            modifier = Modifier.testTag("browse_options_button"),
        )
        if (expanded) {
            AtomicTopDialog(onDismiss = { expanded = false }, testTag = "browse_options_popup") {
                BalancedPopupHeader(
                    title = page.title,
                    closeDescription = "Close file options",
                    onClose = { expanded = false },
                    testTagPrefix = "browse_options",
                    onBack = if (page != BrowseOptionsPage.MAIN) ({ page = BrowseOptionsPage.MAIN }) else null,
                    backDescription = "Back to file options",
                    buttonVariant = AtomicIconButtonVariant.Inset,
                    controlsGap = AtomicSpacing.s8,
                )
                AtomicDivider(
                    modifier = Modifier.padding(top = AtomicSpacing.s12, bottom = AtomicSpacing.screenMargin),
                )
                val scroll = remember(page) { ScrollState(0) }
                Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
                    when (page) {
                        BrowseOptionsPage.MAIN -> {
                            BrowseOptionRow(
                                "View",
                                viewMode.label(),
                                AtomicIcons.Grid,
                            ) { page = BrowseOptionsPage.VIEW }
                            BrowseOptionRow(
                                "Sort by",
                                sortOption.label,
                                AtomicIcons.Sort,
                            ) { page = BrowseOptionsPage.SORT }
                            BrowseOptionRow(
                                "Filter",
                                filter.label,
                                AtomicIcons.SelectSameType,
                            ) { page = BrowseOptionsPage.FILTER }
                            BrowseOptionToggle("Show hidden files", AtomicIcons.Visible, showHidden, onShowHiddenChange)
                            BrowseOptionRow("New folder", icon = AtomicIcons.NewFolder) {
                                expanded = false
                                onNewFolder()
                            }
                            BrowseOptionRow("Manage hidden files", icon = AtomicIcons.Hidden) {
                                expanded = false
                                onShowHiddenFilesScreen()
                            }
                            if (dualPaneEnabled != null) {
                                BrowseOptionToggle("Dual pane", AtomicIcons.DualPane, dualPaneEnabled) { enabled ->
                                    expanded = false
                                    onDualPaneChange(enabled)
                                }
                            }
                        }
                        BrowseOptionsPage.VIEW -> {
                            FileViewMode.entries.forEach { option ->
                                BrowseOptionChoice(option.label(), viewMode == option) {
                                    expanded = false
                                    onViewModeChange(option)
                                }
                            }
                        }
                        BrowseOptionsPage.SORT -> {
                            SortOption.entries.forEach { option ->
                                BrowseOptionChoice(option.label, sortOption == option) {
                                    expanded = false
                                    onSortChange(option)
                                }
                            }
                        }
                        BrowseOptionsPage.FILTER -> {
                            AtomicText(
                                "Folders stay visible in category filters so you can keep exploring.",
                                AtomicTextRole.BodySecondary,
                                Modifier.widthIn(max = AtomicSize.popupHelpMaxWidth)
                                    .padding(AtomicSpacing.s12),
                            )
                            BrowseFileFilter.entries.forEach { option ->
                                BrowseOptionChoice(option.label, filter == option) {
                                    expanded = false
                                    onFilterChange(option)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FileViewMode.label(): String =
    when (this) {
        FileViewMode.LIST -> "List"
        FileViewMode.GRID -> "Grid"
    }

@Composable
private fun BrowseOptionRow(
    title: String,
    value: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AtomicSize.touchTarget)
            .clickable(role = Role.Button, onClick = onClick).padding(AtomicSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        Icon(icon, null, tint = Atomic.colors.accentText, modifier = Modifier.size(AtomicSize.iconSmall))
        AtomicText(title, AtomicTextRole.Body, modifier = Modifier.weight(1f))
        if (value != null) {
            AtomicText(
                value,
                AtomicTextRole.BodySecondary,
                maxLines = 2,
                modifier = Modifier.widthIn(max = AtomicSize.popupHelpMaxWidth / 2),
            )
        } else {
            Icon(
                AtomicIcons.ChevronRight,
                null,
                tint = Atomic.colors.accentText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun BrowseOptionToggle(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AtomicSize.touchTarget)
            .toggleable(enabled, role = Role.Switch, onValueChange = onChange)
            .padding(AtomicSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        Icon(icon, null, tint = Atomic.colors.accentText, modifier = Modifier.size(AtomicSize.iconSmall))
        AtomicText(title, AtomicTextRole.Body, Modifier.weight(1f))
        AtomicText(if (enabled) "On" else "Off", AtomicTextRole.BodySecondary)
    }
}

@Composable
private fun BrowseOptionChoice(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = Atomic.colors
    val shape = RoundedCornerShape(AtomicSpacing.s8)
    Row(
        Modifier.fillMaxWidth().heightIn(min = AtomicSize.touchTarget)
            .clip(shape).background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .padding(AtomicSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtomicText(
            title,
            AtomicTextRole.Body,
            Modifier.weight(1f),
            color = if (selected) colors.accentText else colors.content,
        )
        if (selected) {
            Icon(
                AtomicIcons.Check,
                null,
                tint = colors.accentText,
                modifier = Modifier.size(AtomicSize.iconSmall),
            )
        }
    }
}
