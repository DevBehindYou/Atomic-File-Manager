package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSettingsRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicTitleRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

// Browse headers: the top bar and its selection, search and folder variants.
// Split from BrowseScreen.kt (ALL_IN_ONE_PLAN.md §16.2 H9).

internal data class BrowseTopBarActions(
    val onClearSelection: () -> Unit,
    val onSelectAll: () -> Unit,
    val onAddToBubble: (String) -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val onToggleSearch: (Boolean) -> Unit,
    val onNavigateBack: () -> Unit,
    val onSetSortOption: (SortOption) -> Unit,
    val onShowHiddenScreen: () -> Unit,
    val onToggleDualPaneMode: () -> Unit,
    val onShowNewFolderDialog: () -> Unit,
    val onInvertSelection: () -> Unit = {},
    val onSelectSameType: () -> Unit = {},
)

@Composable
internal fun BrowseTopBar(
    uiState: BrowseUiState,
    isSelectionMode: Boolean,
    bubbles: List<TransferBubble>,
    isDualPane: Boolean,
    dualPaneMode: DualPaneMode,
    showAddToBubbleMenu: Boolean,
    onShowAddToBubbleMenu: (Boolean) -> Unit,
    actions: BrowseTopBarActions,
    tasksButton: @Composable () -> Unit = {},
    optionsButton: @Composable () -> Unit = {},
    storageSelector: @Composable () -> Unit = {},
) {
    when {
        isSelectionMode -> SelectionHeader(uiState, bubbles, onShowAddToBubbleMenu, actions)
        uiState.isSearching -> SearchHeader(uiState, actions)
        else -> FolderHeader(uiState, isDualPane, dualPaneMode, actions, tasksButton, optionsButton, storageSelector)
    }

    if (showAddToBubbleMenu) {
        AtomicSheet(label = "Add to bubble", onDismiss = { onShowAddToBubbleMenu(false) }) {
            Column {
                bubbles.forEach { bubble ->
                    AtomicSettingsRow(
                        title = bubble.displayName,
                        onClick = {
                            actions.onAddToBubble(bubble.id)
                            onShowAddToBubbleMenu(false)
                        },
                    )
                }
            }
        }
    }
}

/** "3 selected · All · Cancel" header (canvas "Files · selection"). */
@Composable
internal fun SelectionHeader(
    uiState: BrowseUiState,
    bubbles: List<TransferBubble>,
    onShowAddToBubbleMenu: (Boolean) -> Unit,
    actions: BrowseTopBarActions,
) {
    var showSelectMenu by remember { mutableStateOf(false) }
    if (showSelectMenu) {
        AtomicSheet(label = "Select", onDismiss = { showSelectMenu = false }) {
            AtomicText("Tip: long-press a second file to select everything between.", AtomicTextRole.BodySecondary)
            listOf(
                "Select all" to actions.onSelectAll,
                "Invert selection" to actions.onInvertSelection,
                "Select same type" to actions.onSelectSameType,
            ).forEach { (label, action) ->
                AtomicSettingsRow(
                    title = label,
                    onClick = {
                        showSelectMenu = false
                        action()
                    },
                )
            }
        }
    }
    val selectedBytes =
        remember(uiState.rawItems, uiState.selectedIds) {
            uiState.rawItems.filter { it.id in uiState.selectedIds && !it.isDirectory }.sumOf { it.size }
        }
    Column(Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AtomicText(
                "${uiState.selectedIds.size} selected",
                AtomicTextRole.DisplayPushed,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (bubbles.isNotEmpty()) {
                AtomicIconButton(
                    AtomicIcons.Bubble,
                    "Add to Transfer Bubble",
                    onClick = { onShowAddToBubbleMenu(true) },
                )
            }
            AtomicButton(
                "All",
                onClick = actions.onSelectAll,
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.testTag("select_all_button"),
            )
            AtomicIconButton(
                AtomicIcons.More,
                "More ways to select",
                onClick = { showSelectMenu = true },
                modifier = Modifier.testTag("select_more_button"),
            )
            AtomicButton("Cancel", onClick = actions.onClearSelection, variant = AtomicButtonVariant.Text)
        }
        AtomicText(
            "${uiState.currentFolderName.ifEmpty { "Files" }} · ${FileUtils.formatBytes(selectedBytes)} selected",
            AtomicTextRole.MonoMeta,
            maxLines = 1,
        )
        AtomicDivider(strong = true, modifier = Modifier.padding(top = AtomicSpacing.s8))
    }
}

@Composable
internal fun SearchHeader(
    uiState: BrowseUiState,
    actions: BrowseTopBarActions,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = AtomicSpacing.s10, end = AtomicSpacing.s16, top = AtomicSpacing.s8),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicIconButton(
                AtomicIcons.Back,
                "Close search",
                onClick = { actions.onToggleSearch(false) },
                variant = AtomicIconButtonVariant.Back,
            )
            AtomicTextField(
                value = uiState.searchQuery,
                onValueChange = actions.onSearchQueryChanged,
                label = "Search this folder",
                placeholder = "Name contains…",
                modifier = Modifier.weight(1f).testTag("search_field"),
            )
        }
        AtomicDivider(strong = true, modifier = Modifier.padding(top = AtomicSpacing.s8))
    }
}

/**
 * Pushed header with the volume as eyebrow, then the folder name as stored (`NameLarge`, never
 * uppercased) with an item counter, then the mono breadcrumb (ATOMIC_UI_PLAN.md §7.2).
 */
@Composable
internal fun FolderHeader(
    uiState: BrowseUiState,
    isDualPane: Boolean,
    dualPaneMode: DualPaneMode,
    actions: BrowseTopBarActions,
    tasksButton: @Composable () -> Unit = {},
    optionsButton: @Composable () -> Unit = {},
    storageSelector: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        AtomicPushedHeader(
            onBack = actions.onNavigateBack,
            eyebrow = uiState.breadcrumbs.firstOrNull()?.name ?: "Files",
            actions = {
                AtomicIconButton(
                    AtomicIcons.Search,
                    "Search this folder",
                    onClick = { actions.onToggleSearch(true) },
                    modifier = Modifier.testTag("search_icon_button"),
                )
                tasksButton()
                if (isDualPane) {
                    AtomicIconButton(
                        if (dualPaneMode == DualPaneMode.DUAL_BROWSE) AtomicIcons.Preview else AtomicIcons.DualPane,
                        if (dualPaneMode == DualPaneMode.DUAL_BROWSE) {
                            "Switch to preview pane"
                        } else {
                            "Switch to dual browse"
                        },
                        onClick = actions.onToggleDualPaneMode,
                    )
                }
                optionsButton()
            },
        )
        storageSelector()
        AtomicTitleRow(
            title = uiState.currentFolderName.ifEmpty { "Files" },
            counter = "${uiState.filteredItems.size} items · ${uiState.sortOption.label}",
            titleRole = AtomicTextRole.NameLarge,
            modifier = Modifier.padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8),
        )
    }
}
