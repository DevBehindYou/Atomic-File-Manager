package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicSwitch
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicElevation
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.hardShadow
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode

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
) {
    var expanded by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(BrowseOptionsPage.MAIN) }
    // Capture the host window, rather than observing the popup's own measured window.
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val width =
        with(density) {
            minOf(
                AtomicSize.popupMaxWidth,
                (windowSize.width.toDp() - AtomicSpacing.s32).coerceAtLeast(AtomicSpacing.s2),
            )
        }
    val height =
        with(density) {
            minOf(
                AtomicSize.popupMaxHeight,
                (windowSize.height.toDp() - AtomicSpacing.s32).coerceAtLeast(AtomicSpacing.s2),
            )
        }
    val position =
        remember(density) {
            with(density) {
                BrowseOptionsPositionProvider(AtomicSpacing.s8.roundToPx(), AtomicSpacing.s16.roundToPx())
            }
        }
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
            Popup(
                popupPositionProvider = position,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                val colors = Atomic.colors
                Column(
                    Modifier.widthIn(min = minOf(AtomicSize.popupMinWidth, width), max = width)
                        .width(IntrinsicSize.Max).heightIn(max = height)
                        .hardShadow(AtomicElevation.level3, colors.shadow, AtomicShape.md)
                        .background(colors.surfaceCard, AtomicShape.md)
                        .border(AtomicBorder.structure, colors.borderStrong, AtomicShape.md)
                        .padding(AtomicSpacing.s12).testTag("browse_options_popup"),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (page != BrowseOptionsPage.MAIN) {
                            AtomicIconButton(
                                AtomicIcons.Back,
                                "Back to file options",
                                { page = BrowseOptionsPage.MAIN },
                            )
                        }
                        AtomicText(page.title, AtomicTextRole.MonoLabel, Modifier.weight(1f), color = colors.accentText)
                        AtomicIconButton(AtomicIcons.Close, "Close file options", { expanded = false })
                    }
                    AtomicDivider()
                    val scroll = remember(page) { ScrollState(0) }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
                        when (page) {
                            BrowseOptionsPage.MAIN -> {
                                BrowseOptionRow("View", viewMode.label()) { page = BrowseOptionsPage.VIEW }
                                BrowseOptionRow("Sort by", sortOption.label) { page = BrowseOptionsPage.SORT }
                                BrowseOptionRow("Filter", filter.label) { page = BrowseOptionsPage.FILTER }
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = AtomicSize.touchTarget)
                                        .toggleable(showHidden, role = Role.Switch, onValueChange = onShowHiddenChange)
                                        .padding(vertical = AtomicSpacing.s8),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                                ) {
                                    AtomicText("Show hidden files", AtomicTextRole.Body, Modifier.weight(1f))
                                    AtomicSwitch(showHidden, onCheckedChange = null)
                                }
                                BrowseOptionRow("New folder") {
                                    expanded = false
                                    onNewFolder()
                                }
                                BrowseOptionRow("Manage hidden files") {
                                    expanded = false
                                    onShowHiddenFilesScreen()
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
                                        .padding(vertical = AtomicSpacing.s8),
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
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AtomicSize.touchTarget)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = AtomicSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        Column(Modifier.weight(1f)) {
            AtomicText(title, AtomicTextRole.Body)
            if (value != null) AtomicText(value, AtomicTextRole.MonoMeta, maxLines = 1)
        }
        AtomicText("→", AtomicTextRole.MonoMeta, color = Atomic.colors.accentText)
    }
}

@Composable
private fun BrowseOptionChoice(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AtomicSize.touchTarget)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = AtomicSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtomicText(title, AtomicTextRole.Body, Modifier.weight(1f))
        if (selected) AtomicText("✓", AtomicTextRole.Body, color = Atomic.colors.accentText)
    }
}

internal class BrowseOptionsPositionProvider(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredX =
            if (layoutDirection == LayoutDirection.Ltr) {
                anchorBounds.right - popupContentSize.width
            } else {
                anchorBounds.left
            }
        val maxX = (windowSize.width - margin - popupContentSize.width).coerceAtLeast(0)
        val x = preferredX.coerceIn(margin.coerceAtMost(maxX), maxX)
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val preferredY =
            when {
                below + popupContentSize.height <= windowSize.height - margin -> below
                above >= margin -> above
                else -> below
            }
        val maxY = (windowSize.height - margin - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, preferredY.coerceIn(margin.coerceAtMost(maxY), maxY))
    }
}
