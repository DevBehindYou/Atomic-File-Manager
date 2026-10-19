package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicActionStrip
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicStripAction
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

// Browse's bottom action bar.
// Split from BrowseScreen.kt (ALL_IN_ONE_PLAN.md §16.2 H9).

@Composable
internal fun BrowseBottomBar(
    uiState: BrowseUiState,
    isSelectionMode: Boolean,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onCompressSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShowDetails: (FileNode) -> Unit,
    onClearClipboard: () -> Unit,
    onRenameSelected: (List<FileNode>) -> Unit = {},
    onOpenInNewTab: ((FileNode) -> Unit)? = null,
    onPaste: () -> Unit,
    onCompare: (FileNode, FileNode) -> Unit = { _, _ -> },
    onCompareFolders: ((FileNodeId, FileNodeId) -> Unit)? = null,
    singleFileActions: List<AtomicStripAction> = emptyList(),
) {
    val clip = uiState.clipboard
    val selectedFolders =
        uiState.rawItems.filter { it.id in uiState.selectedIds && it.isDirectory }.takeIf {
            it.size == 2 && uiState.selectedIds.size == 2
        }
    // A copied folder can be compared with the folder shown, on any storage.
    val clipFolder =
        clip?.items?.singleOrNull()?.takeIf { it.isDirectory && it.id != uiState.currentFolderId }
    val selectedFiles =
        if (uiState.selectedIds.size == 2) {
            uiState.rawItems.filter {
                it.id in uiState.selectedIds && !it.isDirectory
            }
        } else {
            emptyList()
        }
    if (isSelectionMode) {
        AtomicActionStrip(
            actions =
                listOfNotNull(
                    AtomicStripAction("Copy", AtomicIcons.Copy, onCopySelected),
                    AtomicStripAction("Move", AtomicIcons.Move, onCutSelected),
                    AtomicStripAction("Zip", AtomicIcons.Archive, onCompressSelected),
                    AtomicStripAction("Delete", AtomicIcons.Trash, onDeleteSelected, destructive = true),
                    if (uiState.selectedIds.size > 1) {
                        // In listing order, so numbering follows what the user sees.
                        AtomicStripAction("Rename", AtomicIcons.Document, {
                            onRenameSelected(uiState.filteredItems.filter { it.id in uiState.selectedIds })
                        })
                    } else {
                        null
                    },
                    uiState.filteredItems
                        .singleOrNull { it.id in uiState.selectedIds }
                        ?.takeIf { it.isDirectory && uiState.selectedIds.size == 1 && onOpenInNewTab != null }
                        ?.let {
                                folder ->
                            AtomicStripAction("New tab", AtomicIcons.DualPane, { onOpenInNewTab?.invoke(folder) })
                        },
                    if (uiState.selectedIds.size == 1) {
                        AtomicStripAction(
                            label = "Info",
                            icon = AtomicIcons.Info,
                            onClick = {
                                val single = uiState.rawItems.firstOrNull { it.id in uiState.selectedIds }
                                if (single != null) onShowDetails(single)
                            },
                        )
                    } else {
                        null
                    },
                    if (selectedFiles.size == 2) {
                        AtomicStripAction(
                            "Compare",
                            AtomicIcons.CheckCircle,
                            { onCompare(selectedFiles[0], selectedFiles[1]) },
                        )
                    } else {
                        null
                    },
                    if (selectedFolders != null && onCompareFolders != null) {
                        AtomicStripAction(
                            "Compare",
                            AtomicIcons.DualPane,
                            { onCompareFolders(selectedFolders[0].id, selectedFolders[1].id) },
                        )
                    } else {
                        null
                    },
                ) + if (uiState.selectedIds.size == 1) singleFileActions else emptyList(),
        )
    } else if (clip != null) {
        Column(Modifier.fillMaxWidth().background(Atomic.colors.background)) {
            AtomicDivider(strong = true)
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(
                        horizontal = AtomicSpacing.s16,
                        vertical = AtomicSpacing.s10,
                    ),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtomicText(
                    "${clip.items.size} ready to " + if (clip.operation == ClipboardOp.COPY) "copy" else "move",
                    AtomicTextRole.MonoLabel,
                    modifier = Modifier.weight(1f),
                )
                AtomicButton("Cancel", onClick = onClearClipboard, variant = AtomicButtonVariant.Text)
                if (clipFolder != null && onCompareFolders != null) {
                    AtomicButton(
                        "Compare",
                        onClick = { onCompareFolders(clipFolder.id, uiState.currentFolderId) },
                        variant = AtomicButtonVariant.Text,
                        modifier = Modifier.testTag("compare_with_clip_button"),
                    )
                }
                AtomicButton(
                    "Paste here",
                    onClick = onPaste,
                    variant = AtomicButtonVariant.Solid,
                    leadingIcon = AtomicIcons.Paste,
                    modifier = Modifier.testTag("paste_button"),
                )
            }
        }
    }
}
