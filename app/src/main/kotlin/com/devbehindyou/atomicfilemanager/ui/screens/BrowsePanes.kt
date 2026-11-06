package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicProgressBar
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicErrorState
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.ui.components.BreadcrumbBar
import com.devbehindyou.atomicfilemanager.ui.components.FavouriteToggle
import com.devbehindyou.atomicfilemanager.ui.components.FileItemPresentation
import com.devbehindyou.atomicfilemanager.ui.components.FileListItem
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewPane
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.ActiveDropTarget
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DropTargetType
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.edgeAutoScroll
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.fileDragSource
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.fileDropTarget
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.QuickPeekController
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.mediaGestureArbiter
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.items as gridItems

// Browse's list, dual-pane and preview content, and the running-operation banner.
// Split from BrowseScreen.kt (ALL_IN_ONE_PLAN.md §16.2 H9).

internal data class DualPaneItemActions(
    val onOpenFile: ((FileNode) -> Unit)?,
    val onPreviewNode: (FileNode) -> Unit,
    val onSecondaryPreviewNode: (FileNode) -> Unit,
    val onClosePreview: () -> Unit,
    val onShowDetails: (FileNode) -> Unit,
    val onRename: (FileNode) -> Unit,
    val onDelete: (FileNode) -> Unit,
    val onHide: (FileNode) -> Unit,
    val onNewFolder: () -> Unit,
    val onPrimaryActivated: () -> Unit = {},
    val onSecondaryActivated: () -> Unit = {},
)

@Composable
internal fun DualPaneBrowseContent(
    uiState: BrowseUiState,
    secondaryUiState: BrowseUiState,
    dualPaneMode: DualPaneMode,
    viewModel: BrowseViewModel,
    secondaryViewModel: BrowseViewModel,
    dragController: FileDragController,
    quickPeekController: QuickPeekController,
    nodeForPreview: FileNode?,
    actions: DualPaneItemActions,
    previewGallery: List<FileNode> = uiState.filteredItems,
    viewMode: FileViewMode = FileViewMode.LIST,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .fileDropTarget(
                        controller = dragController,
                        target =
                            ActiveDropTarget(
                                id = uiState.currentFolderId.raw,
                                destinationId = uiState.currentFolderId,
                                type = DropTargetType.PANE,
                                displayName = uiState.breadcrumbs.lastOrNull()?.name ?: "Left Pane",
                                isWritable = true,
                            ),
                    ),
        ) {
            FileListContent(
                uiState = uiState,
                viewModel = viewModel,
                dragController = dragController,
                quickPeekController = quickPeekController,
                onOpenFile = actions.onOpenFile,
                onPreviewNode = actions.onPreviewNode,
                onShowDetails = actions.onShowDetails,
                onRename = actions.onRename,
                onDelete = actions.onDelete,
                onHide = actions.onHide,
                onNewFolder = actions.onNewFolder,
                onPaneActivated = actions.onPrimaryActivated,
                viewMode = viewMode,
            )
        }
        Box(Modifier.fillMaxHeight().width(AtomicBorder.rule).background(Atomic.colors.borderStrong))
        Box(
            modifier =
                Modifier
                    .weight(if (dualPaneMode == DualPaneMode.DUAL_BROWSE) 1f else 1.2f)
                    .fillMaxHeight(),
        ) {
            if (dualPaneMode == DualPaneMode.DUAL_BROWSE) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .fileDropTarget(
                                controller = dragController,
                                target =
                                    ActiveDropTarget(
                                        id = secondaryUiState.currentFolderId.raw,
                                        destinationId = secondaryUiState.currentFolderId,
                                        type = DropTargetType.PANE,
                                        displayName = secondaryUiState.breadcrumbs.lastOrNull()?.name ?: "Right Pane",
                                        isWritable = true,
                                    ),
                            ),
                ) {
                    FileListContent(
                        uiState = secondaryUiState,
                        viewModel = secondaryViewModel,
                        dragController = dragController,
                        quickPeekController = quickPeekController,
                        onOpenFile = actions.onOpenFile,
                        onPreviewNode = actions.onSecondaryPreviewNode,
                        onShowDetails = actions.onShowDetails,
                        onRename = actions.onRename,
                        onDelete = actions.onDelete,
                        onHide = actions.onHide,
                        onNewFolder = actions.onNewFolder,
                        onPaneActivated = actions.onSecondaryActivated,
                        viewMode = viewMode,
                    )
                }
            } else {
                if (nodeForPreview != null) {
                    FilePreviewPane(
                        node = nodeForPreview,
                        onClose = actions.onClosePreview,
                        gallery = previewGallery,
                    )
                } else {
                    EmptyPreviewPane()
                }
            }
        }
    }
}

/**
 * The running operation as one tappable line that opens Operations (ALL_IN_ONE_PLAN.md 0.1):
 * progress while it runs, "waiting for your decision" while a name clash is open.
 */
@Composable
internal fun OperationBanner(
    snapshot: OperationSnapshot?,
    onOpen: () -> Unit,
) {
    if (snapshot == null) return
    val status = snapshot.status
    val label = OperationText.verb(snapshot.operation.type)
    val progress = (status as? OperationStatus.Running)?.progress
    val waiting = status is OperationStatus.AwaitingInput
    val fraction =
        if (progress != null && progress.itemsTotal > 0) progress.itemsDone.toFloat() / progress.itemsTotal else 0f
    val line =
        when {
            waiting -> "$label · waiting for your decision · tap to open"
            progress != null ->
                "$label · ${progress.itemsDone} / ${progress.itemsTotal} · ${progress.currentName.orEmpty()}"
            else -> "$label · preparing"
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open operations", role = Role.Button, onClick = onOpen)
            .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8)
            .testTag("operation_banner"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
    ) {
        AtomicProgressBar(
            fraction = fraction,
            contentDescription =
                if (progress != null) "$label, ${progress.itemsDone} of ${progress.itemsTotal}" else line,
        )
        AtomicText(
            line,
            AtomicTextRole.MonoMeta,
            color = if (waiting) Atomic.colors.accentText else Atomic.colors.contentSecondary,
            maxLines = 1,
        )
    }
}

@Composable
internal fun FileListContent(
    uiState: BrowseUiState,
    viewModel: BrowseViewModel,
    dragController: FileDragController,
    quickPeekController: QuickPeekController,
    onOpenFile: ((FileNode) -> Unit)?,
    onPreviewNode: (FileNode) -> Unit,
    onShowDetails: (FileNode) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit,
    onHide: (FileNode) -> Unit,
    onNewFolder: () -> Unit,
    modifier: Modifier = Modifier,
    onPaneActivated: () -> Unit = {},
    viewMode: FileViewMode = FileViewMode.LIST,
) {
    val isSelectionMode = uiState.selectedIds.isNotEmpty()
    val favourites = (LocalContext.current.applicationContext as AtomicApp).container.favouritesRepository
    val favouriteFlow = remember(favourites) { favourites.observe() }
    val favouriteList by favouriteFlow.collectAsState(initial = emptyList())
    val favouriteIds = remember(favouriteList) { favouriteList.mapTo(HashSet()) { it.id } }
    val favouriteScope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.breadcrumbs.isNotEmpty()) {
            BreadcrumbBar(
                breadcrumbs = uiState.breadcrumbs,
                onBreadcrumbClick = {
                    onPaneActivated()
                    viewModel.navigateToBreadcrumb(it)
                },
                dragController = dragController,
            )
        }

        when {
            uiState.isLoading ->
                AtomicLoading(
                    "Loading folder…",
                    modifier = Modifier.padding(AtomicSpacing.s16).testTag("loading_indicator"),
                )
            uiState.errorMessage != null ->
                AtomicErrorState(
                    title = "Can't open this folder",
                    message = uiState.errorMessage.orEmpty(),
                    actionLabel = "Go up",
                    onAction = { viewModel.navigateUp() },
                    modifier = Modifier.padding(AtomicSpacing.s16),
                )
            uiState.filteredItems.isEmpty() ->
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AtomicSpacing.s16)) {
                    if (uiState.fileFilter != BrowseFileFilter.ALL && uiState.rawItems.isNotEmpty()) {
                        AtomicEmptyState(
                            message = "No items match this filter.",
                            detail = "Show all files to explore the rest of this folder.",
                            actionLabel = "Clear filter",
                            onAction = { viewModel.setFileFilter(BrowseFileFilter.ALL) },
                        )
                    } else if (uiState.searchQuery.isNotEmpty()) {
                        AtomicEmptyState(
                            message = "Nothing here matches \"${uiState.searchQuery}\".",
                            detail = "Search looks at names in this folder only.",
                        )
                    } else {
                        AtomicEmptyState(
                            message = "This folder is empty.",
                            actionLabel = "New folder",
                            onAction = {
                                onPaneActivated()
                                onNewFolder()
                            },
                        )
                    }
                }
            else -> {
                // Resolved once per selection change, not once per visible row (which was O(rows x items)).
                val allSelectedNodes =
                    remember(uiState.rawItems, uiState.selectedIds) {
                        if (uiState.selectedIds.isEmpty()) {
                            emptyList()
                        } else {
                            uiState.rawItems.filter { it.id in uiState.selectedIds }
                        }
                    }
                FileBrowserLayout(
                    nodes = uiState.filteredItems,
                    viewMode = viewMode,
                    dragController = dragController,
                ) { node ->
                    val isSelected = node.id in uiState.selectedIds
                    val selectedNodes = if (isSelected) allSelectedNodes else listOf(node)

                    val isMedia =
                        node.mimeType?.startsWith("image/") == true ||
                            node.mimeType?.startsWith("video/") == true
                    val dragOrPeekMod =
                        if (isMedia && !isSelectionMode) {
                            Modifier.mediaGestureArbiter(
                                node = node,
                                onTap = {
                                    onPaneActivated()
                                    if (onOpenFile != null) {
                                        onOpenFile(node)
                                    } else {
                                        onPreviewNode(node)
                                    }
                                },
                                onQuickPeek = { quickPeekController.startPeek(it) },
                                onDismissPeek = { quickPeekController.dismiss() },
                            )
                        } else {
                            Modifier.fileDragSource(
                                controller = dragController,
                                node = node,
                                selectedNodes = selectedNodes,
                                originLocation = uiState.currentFolderId,
                            )
                        }

                    val dropMod =
                        if (node.isDirectory) {
                            Modifier.fileDropTarget(
                                controller = dragController,
                                target =
                                    ActiveDropTarget(
                                        id = node.id.raw,
                                        destinationId = node.id,
                                        type = DropTargetType.FOLDER,
                                        displayName = node.name,
                                        isWritable = true,
                                    ),
                                onHoverSpringOpen = { viewModel.navigateTo(node) },
                            )
                        } else {
                            Modifier
                        }

                    FileListItem(
                        node = node,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onToggleSelect = {
                            onPaneActivated()
                            viewModel.longPressSelect(node.id)
                        },
                        onClick = {
                            onPaneActivated()
                            if (isSelectionMode) {
                                viewModel.toggleSelection(node.id)
                            } else if (node.isDirectory) {
                                viewModel.navigateTo(node)
                            } else {
                                if (onOpenFile != null) {
                                    onOpenFile(node)
                                } else {
                                    onPreviewNode(node)
                                }
                            }
                        },
                        onShowDetails = {
                            onPaneActivated()
                            onShowDetails(node)
                        },
                        onRename = {
                            onPaneActivated()
                            onRename(node)
                        },
                        onDelete = {
                            onPaneActivated()
                            onDelete(node)
                        },
                        onExtract = { viewModel.extractArchive(node) },
                        onCompress = { viewModel.compressSingle(node) },
                        onQuickPeek = { quickPeekController.startPeek(node) },
                        onHide = {
                            onPaneActivated()
                            onHide(node)
                        },
                        modifier =
                            Modifier
                                .then(dragOrPeekMod)
                                .then(dropMod),
                        presentation =
                            FileItemPresentation(
                                viewMode = viewMode,
                                favourite =
                                    FavouriteToggle(node.id in favouriteIds) {
                                        favouriteScope.launch {
                                            if (node.id in favouriteIds) {
                                                favourites.remove(
                                                    node.id,
                                                )
                                            } else {
                                                favourites.add(node)
                                            }
                                        }
                                    },
                            ),
                    )
                }
            }
        }
    }
}

/** Both layouts use stable node IDs and the same item gestures, actions and thumbnail lifecycle. */
@Composable
internal fun FileBrowserLayout(
    nodes: List<FileNode>,
    viewMode: FileViewMode,
    dragController: FileDragController,
    modifier: Modifier = Modifier,
    itemContent: @Composable (FileNode) -> Unit,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    if (viewMode == FileViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(AtomicSize.fileGridCellMin),
            state = gridState,
            contentPadding = PaddingValues(AtomicSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            modifier = modifier.fillMaxSize().edgeAutoScroll(dragController, gridState).testTag("file_grid"),
        ) {
            gridItems(nodes, key = { it.id.raw }) { node -> itemContent(node) }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().edgeAutoScroll(dragController, listState).testTag("file_list"),
        ) {
            items(nodes, key = { it.id.raw }) { node -> itemContent(node) }
        }
    }
}

@Composable
internal fun EmptyPreviewPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(AtomicSpacing.s24)) {
        AtomicEmptyState(
            message = "Select a file to preview.",
            detail = "Images, documents, code, archives and audio appear here.",
        )
    }
}
