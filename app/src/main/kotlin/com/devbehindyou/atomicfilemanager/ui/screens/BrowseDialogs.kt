package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.RemovableNames
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.ui.components.DeleteSheet
import com.devbehindyou.atomicfilemanager.ui.components.FileDetailsCommands
import com.devbehindyou.atomicfilemanager.ui.components.FileDetailsDialog
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewDialog
import com.devbehindyou.atomicfilemanager.ui.components.HideFileDialog
import com.devbehindyou.atomicfilemanager.ui.components.NewFolderDialog
import com.devbehindyou.atomicfilemanager.ui.components.RenameDialog
import com.devbehindyou.atomicfilemanager.ui.interaction.bubble.BubbleDetailsSheet
import com.devbehindyou.atomicfilemanager.ui.interaction.bubble.BubbleTransferDialog
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DropDecisionDialog
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController

// Dialogs and sheets opened from Browse.
// Split from BrowseScreen.kt (ALL_IN_ONE_PLAN.md §16.2 H9).

internal data class BrowseDialogState(
    val nodeToRename: FileNode?,
    val nodeForDetails: FileNode?,
    val nodeToDelete: FileNode?,
    val confirmMultiDelete: Boolean,
    val showNewFolderDialog: Boolean,
    val nodeToHide: FileNode?,
    val bubbleForTransfer: TransferBubble?,
    val bubbleForDetails: TransferBubble?,
    val nodeForPreview: FileNode?,
)

internal data class BrowseDialogCallbacks(
    val onDismissRename: () -> Unit,
    val onDismissDetails: () -> Unit,
    val onOpenDetails: (FileNode) -> Unit,
    val onRenameDetails: (FileNode) -> Unit,
    val onDismissDelete: () -> Unit,
    val onDismissMultiDelete: () -> Unit,
    val onDismissNewFolder: () -> Unit,
    val onDismissHide: () -> Unit,
    val onDismissTransfer: () -> Unit,
    val onDismissBubbleDetails: () -> Unit,
    val onDismissPreview: () -> Unit,
    val onReviewFiles: (TransferBubble) -> Unit,
    val onHideNode: (FileNode, HideMode) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseDialogs(
    viewModel: BrowseViewModel,
    secondaryViewModel: BrowseViewModel,
    isDualPane: Boolean,
    uiState: BrowseUiState,
    bubbles: List<TransferBubble>,
    dragController: FileDragController,
    state: BrowseDialogState,
    callbacks: BrowseDialogCallbacks,
) {
    dragController.pendingDecision?.let { (payload, target) ->
        DropDecisionDialog(
            payload = payload,
            target = target,
            onDecision = { decision ->
                dragController.clearPendingDecision()
                viewModel.executeDrop(decision)
                if (isDualPane) {
                    secondaryViewModel.refresh()
                }
            },
        )
    }

    state.bubbleForTransfer?.let { bubble ->
        BubbleTransferDialog(
            bubble = bubble,
            targetDirectoryName = uiState.currentFolderName.ifEmpty { "current folder" },
            onMove = { clearAfter ->
                viewModel.transferBubble(bubble, isMove = true, clearAfter = clearAfter)
                callbacks.onDismissTransfer()
            },
            onCopy = { clearAfter ->
                viewModel.transferBubble(bubble, isMove = false, clearAfter = clearAfter)
                callbacks.onDismissTransfer()
            },
            onReviewFiles = {
                callbacks.onReviewFiles(bubble)
            },
            onDismiss = callbacks.onDismissTransfer,
        )
    }

    state.bubbleForDetails?.let { bubble ->
        val currentBubble = bubbles.find { it.id == bubble.id } ?: bubble
        BubbleDetailsSheet(
            bubble = currentBubble,
            onRemoveItem = { itemId -> viewModel.removeBubbleItem(currentBubble.id, itemId) },
            onClearBubble = { viewModel.clearBubble(currentBubble.id) },
            onDeleteBubble = {
                viewModel.deleteBubble(currentBubble.id)
                callbacks.onDismissBubbleDetails()
            },
            onDismiss = callbacks.onDismissBubbleDetails,
        )
    }

    if (state.showNewFolderDialog) {
        NewFolderDialog(
            parentName = uiState.currentFolderName.ifEmpty { null },
            removable = RemovableNames.appliesTo(uiState.currentFolderId),
            onDismiss = callbacks.onDismissNewFolder,
            onConfirm = { name ->
                viewModel.createFolder(name)
                callbacks.onDismissNewFolder()
            },
        )
    }

    state.nodeToRename?.let { node ->
        RenameDialog(
            initialName = node.name,
            removable = RemovableNames.appliesTo(node.id),
            onDismiss = callbacks.onDismissRename,
            onConfirm = { newName ->
                viewModel.rename(node.id, newName)
                callbacks.onDismissRename()
            },
        )
    }

    state.nodeForDetails?.let { node ->
        FileDetailsDialog(
            node = node,
            commands = FileDetailsCommands(
                onOpen = { callbacks.onOpenDetails(node) },
                onRename = { callbacks.onRenameDetails(node) },
            ),
            onDismiss = callbacks.onDismissDetails,
        )
    }

    state.nodeToDelete?.let { node ->
        DeleteSheet(
            nodes = listOf(node),
            onConfirm = { toTrash ->
                viewModel.deleteNodes(listOf(node.id), toTrash)
                callbacks.onDismissDelete()
            },
            onDismiss = callbacks.onDismissDelete,
        )
    }

    state.nodeToHide?.let { node ->
        HideFileDialog(
            node = node,
            onConfirm = { mode -> callbacks.onHideNode(node, mode) },
            onDismiss = callbacks.onDismissHide,
        )
    }

    if (state.confirmMultiDelete) {
        val selected =
            remember(uiState.selectedIds, uiState.rawItems) {
                uiState.rawItems.filter { it.id in uiState.selectedIds }
            }
        DeleteSheet(
            nodes = selected,
            onConfirm = { toTrash ->
                viewModel.deleteNodes(selected.map { it.id }, toTrash)
                viewModel.clearSelection()
                callbacks.onDismissMultiDelete()
            },
            onDismiss = callbacks.onDismissMultiDelete,
        )
    }

    if (state.nodeForPreview != null) {
        FilePreviewDialog(
            node = state.nodeForPreview,
            onDismiss = callbacks.onDismissPreview,
            gallery = uiState.filteredItems,
        )
    }
}
