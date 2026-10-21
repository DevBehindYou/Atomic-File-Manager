package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicStripAction
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailKind
import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.usecase.ArchiveFormats
import com.devbehindyou.atomicfilemanager.ui.components.BatchRenameSheet
import com.devbehindyou.atomicfilemanager.ui.components.CompareSheet
import com.devbehindyou.atomicfilemanager.ui.components.TasksButton
import com.devbehindyou.atomicfilemanager.ui.interaction.bubble.TransferBubbleRail
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DragFloatingPreview
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.QuickPeekController
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.QuickPeekOverlay
import com.devbehindyou.atomicfilemanager.ui.security.AuthGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DualPaneMode {
    DUAL_BROWSE,
    PREVIEW,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    initialFolderId: FileNodeId,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Changes each time the caller explicitly opens [initialFolderId]; see [BrowseViewModel.onOpenRequest]. */
    openRequest: Int = 0,
    onOpenFile: ((FileNode) -> Unit)? = null,
    /** Shows feedback in the app snackbar. */
    onNotify: (String) -> Unit = {},
    /** Opens the Operations screen from the progress banner. */
    onOpenOperations: () -> Unit = {},
    /** Keeps each Browse tab's view model apart (ALL_IN_ONE_PLAN.md 2.4); empty for the first tab. */
    tabKey: String = "",
    /** Shown above the top bar; the tab strip when several tabs are open. */
    tabStrip: @Composable () -> Unit = {},
    /** Reports the folder shown, for the tab label. */
    onLocationChange: (String) -> Unit = {},
    /** Opens a selected folder in a new tab; null hides the action. */
    onOpenInNewTab: ((FileNode) -> Unit)? = null,
    /** Compares two folders (ALL_IN_ONE_PLAN.md 4.1); null hides the actions. */
    onCompareFolders: ((FileNodeId, FileNodeId) -> Unit)? = null,
    storage: BrowseStorageAccess = BrowseStorageAccess(),
    viewModel: BrowseViewModel =
        run {
            val app = LocalContext.current.applicationContext as AtomicApp
            val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner())
            remember(owner, tabKey, initialFolderId.raw) {
                ViewModelProvider(
                    owner,
                    BrowseViewModel.provideFactory(
                        initialFolderId = initialFolderId,
                        getDirectoryListingUseCase = app.container.getDirectoryListingUseCase,
                        getNodeUseCase = app.container.getNodeUseCase,
                        createDirectoryUseCase = app.container.createDirectoryUseCase,
                        renameFileUseCase = app.container.renameFileUseCase,
                        deleteFileUseCase = app.container.deleteFileUseCase,
                        operationQueue = app.container.operationQueue,
                        transferBubbleRepository = app.container.transferBubbleRepository,
                        folderSorts = app.container.folderSortMemory,
                        startVisible = false,
                    ),
                )["browse:$tabKey${initialFolderId.raw}", BrowseViewModel::class.java]
            }
        },
) {
    val app = LocalContext.current.applicationContext as AtomicApp
    val volumes = storage.volumes
    val uiState by viewModel.uiState.collectAsState()
    val settings by app.container.settingsRepository.settings.collectAsState()

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var nodeToRename by remember { mutableStateOf<FileNode?>(null) }
    var nodeForDetails by remember { mutableStateOf<FileNode?>(null) }
    var nodeForPreview by remember { mutableStateOf<FileNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<FileNode?>(null) }
    var confirmMultiDelete by remember { mutableStateOf(false) }
    var batchRename by remember { mutableStateOf<List<FileNode>?>(null) }
    var comparePair by remember { mutableStateOf<Pair<FileNode, FileNode>?>(null) }

    var useSecondary by rememberSaveable(tabKey, initialFolderId.raw) { mutableStateOf(false) }
    var previewFromSecondary by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isDualPane = configuration.screenWidthDp >= 720
    var dualPaneMode by remember { mutableStateOf(DualPaneMode.DUAL_BROWSE) }
    val dragController = remember { FileDragController() }
    val quickPeekController = remember { QuickPeekController() }

    val secondaryViewModel =
        rememberSecondaryBrowseViewModel(isDualPane, tabKey, initialFolderId) {
            BrowseViewModel.provideFactory(
                initialFolderId = initialFolderId,
                getDirectoryListingUseCase = app.container.getDirectoryListingUseCase,
                getNodeUseCase = app.container.getNodeUseCase,
                createDirectoryUseCase = app.container.createDirectoryUseCase,
                renameFileUseCase = app.container.renameFileUseCase,
                deleteFileUseCase = app.container.deleteFileUseCase,
                operationQueue = app.container.operationQueue,
                transferBubbleRepository = app.container.transferBubbleRepository,
                folderSorts = app.container.folderSortMemory,
            )
        } ?: viewModel
    val secondaryUiState = if (isDualPane) secondaryViewModel.uiState.collectAsState().value else uiState
    val actionViewModel = if (isDualPane && useSecondary) secondaryViewModel else viewModel
    val actionState = if (isDualPane && useSecondary) secondaryUiState else uiState
    val currentStorage = currentStorageVolume(volumes, actionState.currentFolderId, actionState.breadcrumbs)
    val displayFolderName =
        if (currentStorage?.rootNodeId == actionState.currentFolderId) {
            currentStorage.label
        } else {
            actionState.currentFolderName
        }
    val isSelectionMode = actionState.selectedIds.isNotEmpty()
    LaunchedEffect(isDualPane) {
        if (!isDualPane) {
            useSecondary = false
            previewFromSecondary = false
        }
    }

    val bubbles by viewModel.bubbles.collectAsState()
    var bubbleForDetails by remember { mutableStateOf<TransferBubble?>(null) }
    var bubbleForTransfer by remember { mutableStateOf<TransferBubble?>(null) }
    var showAddToBubbleMenu by remember { mutableStateOf(false) }

    var nodeToHide by remember { mutableStateOf<FileNode?>(null) }
    var showHiddenScreen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val favouriteIds by produceState<Set<FileNodeId>?>(null, app.container) {
        withContext(Dispatchers.IO) { app.container.favouritesRepository }.observe().collect { favourites ->
            value = favourites.mapTo(HashSet()) { it.id }
        }
    }
    val focusRequester = remember { FocusRequester() }

    fun hideNode(
        node: FileNode,
        mode: HideMode,
    ) {
        val owner = actionViewModel
        coroutineScope.launch {
            val repository = app.container.hiddenFilesRepository
            val result =
                when (mode) {
                    HideMode.GALLERY -> repository.hideFromGallery(node)
                    HideMode.FAST_OBSCURE -> repository.fastObscure(node)
                    HideMode.PRIVATE_STORAGE -> repository.moveToPrivateStorage(node)
                }
            val error = result.exceptionOrNull()
            val message =
                if (error == null) {
                    "Hidden ${node.name}."
                } else {
                    "Couldn't hide ${node.name}: ${error.message ?: "unknown error"}"
                }
            onNotify(message)
            owner.refresh()
            nodeToHide = null
        }
    }

    // With a default hiding method set in Settings the choice dialog is skipped.
    LaunchedEffect(nodeToHide, settings.defaultHideMode) {
        val node = nodeToHide
        val mode = settings.defaultHideMode
        if (node != null && mode != null) hideNode(node, mode)
    }

    LaunchedEffect(viewModel, secondaryViewModel, settings.showHiddenFiles) {
        viewModel.setShowHiddenFiles(settings.showHiddenFiles)
        if (isDualPane) secondaryViewModel.setShowHiddenFiles(settings.showHiddenFiles)
    }

    PrimaryBrowseVisibility(viewModel, openRequest)

    val primaryStorage = currentStorageVolume(volumes, uiState.currentFolderId, uiState.breadcrumbs)
    val primaryFolderName =
        if (primaryStorage?.rootNodeId == uiState.currentFolderId) primaryStorage.label else uiState.currentFolderName
    LaunchedEffect(primaryFolderName) {
        if (primaryFolderName.isNotBlank()) onLocationChange(primaryFolderName)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler {
        when {
            showHiddenScreen -> {
                showHiddenScreen = false
                viewModel.refresh()
            }
            quickPeekController.isPeeking -> quickPeekController.dismiss()
            isSelectionMode -> actionViewModel.clearSelection()
            actionState.isSearching -> actionViewModel.toggleSearch(false)
            !actionViewModel.navigateBack() -> onNavigateBack()
        }
    }

    if (showHiddenScreen) {
        AuthGate(
            required = settings.requireAuthForHidden,
            title = "Unlock hidden files",
            onDenied = {
                showHiddenScreen = false
                viewModel.refresh()
            },
        ) {
            HiddenFilesScreen(
                repository = app.container.hiddenFilesRepository,
                onNavigateBack = {
                    showHiddenScreen = false
                    viewModel.refresh()
                },
            )
        }
        return
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    handleBrowseKeyEvent(
                        event = event,
                        viewModel = actionViewModel,
                        isSelectionMode = isSelectionMode,
                        uiState = actionState,
                        onOpenFile = onOpenFile,
                        onRequestRename = { nodeToRename = it },
                        onRequestPreview = {
                            nodeForPreview = it
                            previewFromSecondary = useSecondary
                            if (isDualPane) dualPaneMode = DualPaneMode.PREVIEW
                        },
                        onRequestMultiDelete = { confirmMultiDelete = true },
                    )
                },
    ) {
        Column(Modifier.fillMaxSize().background(Atomic.colors.background)) {
            tabStrip()
            BrowseTopBar(
                uiState = actionState.copy(currentFolderName = displayFolderName),
                isSelectionMode = isSelectionMode,
                bubbles = bubbles,
                isDualPane = isDualPane,
                dualPaneMode = dualPaneMode,
                showAddToBubbleMenu = showAddToBubbleMenu,
                onShowAddToBubbleMenu = { showAddToBubbleMenu = it },
                actions =
                    BrowseTopBarActions(
                        onClearSelection = { actionViewModel.clearSelection() },
                        onSelectAll = { actionViewModel.selectAll() },
                        onAddToBubble = { bubbleId -> actionViewModel.addSelectionToBubble(bubbleId) },
                        onSearchQueryChanged = { actionViewModel.onSearchQueryChanged(it) },
                        onToggleSearch = { actionViewModel.toggleSearch(it) },
                        onNavigateBack = {
                            if (!actionViewModel.navigateBack()) {
                                onNavigateBack()
                            }
                        },
                        onSetSortOption = { actionViewModel.setSortOption(it) },
                        onShowHiddenScreen = { showHiddenScreen = true },
                        onToggleDualPaneMode = {
                            useSecondary = false
                            secondaryViewModel.clearSelection()
                            dualPaneMode =
                                if (dualPaneMode == DualPaneMode.DUAL_BROWSE) {
                                    DualPaneMode.PREVIEW
                                } else {
                                    DualPaneMode.DUAL_BROWSE
                                }
                        },
                        onShowNewFolderDialog = { showNewFolderDialog = true },
                        onInvertSelection = { actionViewModel.invertSelection() },
                        onSelectSameType = { actionViewModel.selectSameType() },
                    ),
                tasksButton = {
                    TasksButton(app.container.operationQueue, app.container.wifiShare.transfers, onOpenOperations)
                },
                optionsButton = {
                    BrowseOptionsButton(
                        viewMode = settings.fileViewMode,
                        onViewModeChange = { mode ->
                            app.container.settingsRepository.update { it.copy(fileViewMode = mode) }
                        },
                        sortOption = actionState.sortOption,
                        onSortChange = actionViewModel::setSortOption,
                        filter = actionState.fileFilter,
                        onFilterChange = actionViewModel::setFileFilter,
                        showHidden = settings.showHiddenFiles,
                        onShowHiddenChange = { show ->
                            app.container.settingsRepository.update { it.copy(showHiddenFiles = show) }
                        },
                        onNewFolder = { showNewFolderDialog = true },
                        onShowHiddenFilesScreen = { showHiddenScreen = true },
                    )
                },
                storageSelector = {
                    BrowseStorageShortcuts(volumes, currentStorage, actionState.currentFolderId) { volume ->
                        when {
                            !volume.isMounted -> onNotify("This storage isn't mounted.")
                            volume.rootNodeId != null ->
                                actionViewModel.navigateToStorage(volume.rootNodeId, volume.label)
                            !storage.hasAccess -> storage.requestAccess()
                            else -> onNotify("This storage is unavailable. Reconnect it and try again.")
                        }
                    }
                },
            )
            OperationBanner(uiState.activeOperation, onOpen = onOpenOperations)
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = actionState.isRefreshing,
                onRefresh = { actionViewModel.refresh() },
                state = pullState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (isDualPane) {
                    DualPaneBrowseContent(
                        viewMode = settings.fileViewMode,
                        uiState = uiState,
                        secondaryUiState = secondaryUiState,
                        dualPaneMode = dualPaneMode,
                        viewModel = viewModel,
                        secondaryViewModel = secondaryViewModel,
                        dragController = dragController,
                        quickPeekController = quickPeekController,
                        nodeForPreview = nodeForPreview,
                        previewGallery =
                            if (previewFromSecondary) secondaryUiState.filteredItems else uiState.filteredItems,
                        actions =
                            DualPaneItemActions(
                                onPrimaryActivated = {
                                    useSecondary = false
                                    secondaryViewModel.clearSelection()
                                },
                                onSecondaryActivated = {
                                    useSecondary = true
                                    viewModel.clearSelection()
                                },
                                onOpenFile = onOpenFile,
                                onPreviewNode = { node ->
                                    previewFromSecondary = false
                                    nodeForPreview = node
                                    if (dualPaneMode != DualPaneMode.PREVIEW) {
                                        dualPaneMode = DualPaneMode.PREVIEW
                                    }
                                },
                                onSecondaryPreviewNode = { node ->
                                    previewFromSecondary = true
                                    nodeForPreview = node
                                    dualPaneMode = DualPaneMode.PREVIEW
                                },
                                onClosePreview = { nodeForPreview = null },
                                onShowDetails = { nodeForDetails = it },
                                onRename = { nodeToRename = it },
                                onDelete = { nodeToDelete = it },
                                onHide = { nodeToHide = it },
                                onNewFolder = { showNewFolderDialog = true },
                            ),
                    )
                } else {
                    FileListContent(
                        viewMode = settings.fileViewMode,
                        uiState = uiState,
                        viewModel = viewModel,
                        dragController = dragController,
                        quickPeekController = quickPeekController,
                        onOpenFile = onOpenFile,
                        onPreviewNode = { nodeForPreview = it },
                        onShowDetails = { nodeForDetails = it },
                        onRename = { nodeToRename = it },
                        onDelete = { nodeToDelete = it },
                        onHide = { nodeToHide = it },
                        onNewFolder = { showNewFolderDialog = true },
                    )
                }
            }
            BrowseBottomBar(
                uiState = actionState,
                isSelectionMode = isSelectionMode,
                onCopySelected = { actionViewModel.copySelected() },
                onCutSelected = { actionViewModel.cutSelected() },
                onCompressSelected = { actionViewModel.compressSelected() },
                singleFileActions =
                    actionState.rawItems.singleOrNull { it.id in actionState.selectedIds }?.let { selected ->
                        buildList {
                            add(
                                AtomicStripAction(stringResource(R.string.selection_rename), AtomicIcons.Rename, {
                                    nodeToRename = selected
                                    actionViewModel.clearSelection()
                                }),
                            )
                            add(
                                AtomicStripAction(stringResource(R.string.selection_hide), AtomicIcons.Hide, {
                                    nodeToHide = selected
                                    actionViewModel.clearSelection()
                                }),
                            )
                            if (thumbnailKind(selected) != null) {
                                add(
                                    AtomicStripAction(stringResource(R.string.selection_preview), AtomicIcons.Preview, {
                                        nodeForPreview = selected
                                        previewFromSecondary = useSecondary
                                        if (isDualPane) dualPaneMode = DualPaneMode.PREVIEW
                                        actionViewModel.clearSelection()
                                    }),
                                )
                            }
                            if (ArchiveFormats.kindOf(selected.name) != null) {
                                add(
                                    AtomicStripAction(stringResource(R.string.selection_extract), AtomicIcons.Extract, {
                                        actionViewModel.extractArchive(selected)
                                        actionViewModel.clearSelection()
                                    }),
                                )
                            }
                            val ids = favouriteIds
                            if (ids != null) {
                                val isFavourite = selected.id in ids
                                val failureMessage = stringResource(R.string.selection_favourite_error)
                                add(
                                    AtomicStripAction(
                                        stringResource(
                                            if (isFavourite) {
                                                R.string.selection_favourite_remove
                                            } else {
                                                R.string.selection_favourite_add
                                            },
                                        ),
                                        AtomicIcons.Favourite,
                                        {
                                            coroutineScope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        val repository = app.container.favouritesRepository
                                                        if (isFavourite) {
                                                            repository.remove(selected.id)
                                                        } else {
                                                            repository.add(selected)
                                                        }
                                                    }
                                                    actionViewModel.clearSelection()
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (_: Exception) {
                                                    onNotify(failureMessage)
                                                }
                                            }
                                        },
                                    ),
                                )
                            }
                        }
                    } ?: emptyList(),
                onDeleteSelected = { confirmMultiDelete = true },
                onRenameSelected = { nodes -> batchRename = nodes },
                onOpenInNewTab =
                    onOpenInNewTab?.let { open ->
                        { folder: FileNode ->
                            open(folder)
                            actionViewModel.clearSelection()
                        }
                    },
                onShowDetails = { node ->
                    nodeForDetails = node
                    actionViewModel.clearSelection()
                },
                onClearClipboard = { actionViewModel.clearClipboard() },
                onPaste = { actionViewModel.paste(CollisionPolicy.ASK) },
                onCompare = { a, b -> comparePair = a to b },
                onCompareFolders =
                    onCompareFolders?.let { compare ->
                        { left: FileNodeId, right: FileNodeId ->
                            actionViewModel.clearSelection()
                            compare(left, right)
                        }
                    },
            )
            comparePair?.let { (a, b) -> CompareSheet(first = a, second = b, onDismiss = { comparePair = null }) }
        }

        batchRename?.let { nodes ->
            BatchRenameSheet(
                nodes = nodes,
                folderNames = actionState.rawItems.map { it.name },
                onChanged = {
                    actionViewModel.clearSelection()
                    actionViewModel.refresh()
                },
                onDismiss = { batchRename = null },
            )
        }

        // Floating drag preview follows pointer
        DragFloatingPreview(controller = dragController)

        // Quick Peek overlay
        QuickPeekOverlay(
            controller = quickPeekController,
            imagePreviewHelper = app.container.imagePreviewHelper,
        )

        // Transfer Bubble Rail on the right edge
        TransferBubbleRail(
            bubbles = bubbles,
            dragController = dragController,
            onBubbleClick = { bubble -> bubbleForTransfer = bubble },
            onBubbleLongClick = { bubble -> bubbleForDetails = bubble },
            onCreateBubble = { viewModel.createBubble() },
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
        )

        BrowseDialogs(
            viewModel = actionViewModel,
            secondaryViewModel = if (useSecondary) viewModel else secondaryViewModel,
            isDualPane = isDualPane,
            uiState = actionState,
            bubbles = bubbles,
            dragController = dragController,
            state =
                BrowseDialogState(
                    nodeToRename = nodeToRename,
                    nodeForDetails = nodeForDetails,
                    nodeToDelete = nodeToDelete,
                    confirmMultiDelete = confirmMultiDelete,
                    showNewFolderDialog = showNewFolderDialog,
                    nodeToHide = if (settings.defaultHideMode == null) nodeToHide else null,
                    bubbleForTransfer = bubbleForTransfer,
                    bubbleForDetails = bubbleForDetails,
                    nodeForPreview = if (!isDualPane) nodeForPreview else null,
                ),
            callbacks =
                BrowseDialogCallbacks(
                    onDismissRename = { nodeToRename = null },
                    onDismissDetails = { nodeForDetails = null },
                    onDismissDelete = { nodeToDelete = null },
                    onDismissMultiDelete = { confirmMultiDelete = false },
                    onDismissNewFolder = { showNewFolderDialog = false },
                    onDismissHide = { nodeToHide = null },
                    onDismissTransfer = { bubbleForTransfer = null },
                    onDismissBubbleDetails = { bubbleForDetails = null },
                    onDismissPreview = { nodeForPreview = null },
                    onReviewFiles = { bubble ->
                        bubbleForDetails = bubble
                        bubbleForTransfer = null
                    },
                    onHideNode = { node, mode -> hideNode(node, mode) },
                ),
        )
    }
}

private fun handleBrowseKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    viewModel: BrowseViewModel,
    isSelectionMode: Boolean,
    uiState: BrowseUiState,
    onOpenFile: ((FileNode) -> Unit)?,
    onRequestRename: (FileNode) -> Unit,
    onRequestPreview: (FileNode) -> Unit,
    onRequestMultiDelete: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.isCtrlPressed && event.key == Key.A -> {
            viewModel.selectAll()
            true
        }
        event.isCtrlPressed && event.key == Key.C -> {
            if (isSelectionMode) viewModel.copySelected()
            true
        }
        event.isCtrlPressed && event.key == Key.X -> {
            if (isSelectionMode) viewModel.cutSelected()
            true
        }
        event.isCtrlPressed && event.key == Key.V -> {
            viewModel.paste(CollisionPolicy.ASK)
            true
        }
        event.isCtrlPressed && event.key == Key.F -> {
            viewModel.toggleSearch(true)
            true
        }
        event.key == Key.Delete -> {
            if (isSelectionMode) onRequestMultiDelete()
            true
        }
        event.key == Key.F2 -> {
            if (uiState.selectedIds.size == 1) {
                val single = uiState.rawItems.firstOrNull { it.id in uiState.selectedIds }
                if (single != null) onRequestRename(single)
            }
            true
        }
        event.key == Key.Escape -> {
            if (isSelectionMode) {
                viewModel.clearSelection()
            } else if (uiState.isSearching) {
                viewModel.toggleSearch(false)
            }
            true
        }
        event.key == Key.Backspace || (event.isAltPressed && event.key == Key.DirectionLeft) -> {
            viewModel.navigateUp()
            true
        }
        event.key == Key.Enter -> {
            if (uiState.selectedIds.size == 1) {
                val single = uiState.rawItems.firstOrNull { it.id in uiState.selectedIds }
                if (single != null) {
                    if (single.isDirectory) {
                        viewModel.navigateTo(single)
                    } else if (onOpenFile != null) {
                        onOpenFile(single)
                    } else {
                        onRequestPreview(single)
                    }
                }
            }
            true
        }
        else -> false
    }
}
