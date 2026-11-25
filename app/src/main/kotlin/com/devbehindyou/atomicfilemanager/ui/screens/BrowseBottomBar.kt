package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.R
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
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo

// Browse's bottom action bar.
// Split from BrowseScreen.kt (ALL_IN_ONE_PLAN.md §16.2 H9).

internal data class DestinationPickerActions(
    val volumes: List<StorageVolumeInfo>,
    val pasteAt: (ClipboardState, FileNodeId) -> Boolean,
)

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
    destinationPicker: DestinationPickerActions? = null,
) {
    val clip = uiState.clipboard
    var chooseDestination by remember(clip, isSelectionMode) { mutableStateOf(false) }
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
        val comparison =
            if (clipFolder != null && onCompareFolders != null) {
                { onCompareFolders(clipFolder.id, uiState.currentFolderId) }
            } else {
                null
            }
        ClipboardActionBar(
            clip = clip,
            destination = clipboardDestinationName(uiState),
            onCancel = onClearClipboard,
            onCompare = comparison,
            onPaste = onPaste,
            onChoose = destinationPicker?.let { { chooseDestination = true } },
        )
        if (chooseDestination && destinationPicker != null) {
            BrowseDestinationPicker(
                clip = clip,
                initial = uiState,
                volumes = destinationPicker.volumes,
                onConfirm = { destinationPicker.pasteAt(clip, it) },
                onDismiss = { chooseDestination = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipboardActionBar(
    clip: ClipboardState,
    destination: String,
    onCancel: () -> Unit,
    onCompare: (() -> Unit)?,
    onChoose: (() -> Unit)?,
    onPaste: () -> Unit,
) {
    val verb = if (clip.operation == ClipboardOp.COPY) "Copy" else "Move"
    val source = clip.items.singleOrNull()?.let { it.displayName.ifBlank { "1 item" } } ?: "${clip.items.size} items"
    Column(Modifier.fillMaxWidth().background(Atomic.colors.background)) {
        AtomicDivider(strong = true)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val content = Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s10)
            val inlineWidth = 400.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            if (maxWidth < inlineWidth) {
                Column(content, verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
                    // The action buttons must never squeeze the status text to a few pixels and grow the bar.
                    ClipboardTransferSummary(verb, source, destination)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
                    ) {
                        ClipboardButtons(verb, clip.operation, onCancel, onCompare, onChoose, onPaste)
                    }
                }
            } else {
                Row(
                    content,
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClipboardTransferSummary(verb, source, destination, Modifier.weight(1f))
                    ClipboardButtons(verb, clip.operation, onCancel, onCompare, onChoose, onPaste)
                }
            }
        }
    }
}

// Folder loading may briefly leave the previous display name in state. Only show breadcrumbs for
// the actual current destination, and never derive a display path from an opaque provider ID.
private fun clipboardDestinationName(uiState: BrowseUiState): String =
    uiState.breadcrumbs.takeIf { it.lastOrNull()?.path == uiState.currentFolderId.raw }
        ?.joinToString(" / ") { it.name }
        ?: "Current folder"

@Composable
private fun ClipboardTransferSummary(
    verb: String,
    source: String,
    destination: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
        AtomicText("$verb $source", AtomicTextRole.MonoLabel, maxLines = 2)
        AtomicText("To $destination", AtomicTextRole.BodySecondary, maxLines = 2)
    }
}

@Composable
private fun ClipboardButtons(
    verb: String,
    operation: ClipboardOp,
    onCancel: () -> Unit,
    onCompare: (() -> Unit)?,
    onChoose: (() -> Unit)?,
    onPaste: () -> Unit,
) {
    AtomicButton("Cancel", onClick = onCancel, variant = AtomicButtonVariant.Text)
    if (onChoose != null) {
        AtomicButton(
            stringResource(R.string.destination_title),
            onClick = onChoose,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.testTag("choose_destination_button"),
        )
    }
    if (onCompare != null) {
        AtomicButton(
            "Compare",
            onClick = onCompare,
            variant = AtomicButtonVariant.Text,
            modifier = Modifier.testTag("compare_with_clip_button"),
        )
    }
    AtomicButton(
        "$verb here",
        onClick = onPaste,
        variant = AtomicButtonVariant.Solid,
        leadingIcon = if (operation == ClipboardOp.COPY) AtomicIcons.Copy else AtomicIcons.Move,
        modifier = Modifier.testTag("paste_button"),
    )
}
