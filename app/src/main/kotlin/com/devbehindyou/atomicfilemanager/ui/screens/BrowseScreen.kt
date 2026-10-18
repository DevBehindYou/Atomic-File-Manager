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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.ui.components.BatchRenameSheet
import com.devbehindyou.atomicfilemanager.ui.components.CompareSheet
import com.devbehindyou.atomicfilemanager.ui.interaction.bubble.TransferBubbleRail
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DragFloatingPreview
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.QuickPeekController
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.QuickPeekOverlay
import com.devbehindyou.atomicfilemanager.ui.security.AuthGate
import kotlinx.coroutines.launch

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
                    ),
                )["browse:$tabKey${initialFolderId.raw}", BrowseViewModel::class.java]
            }
        },
) {
    val app = LocalContext.current.applicationContext as AtomicApp
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
    var showSortMenu by remember { mutableStateOf(false) }

    val isSelectionMode = uiState.selectedIds.isNotEmpty()
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

    val bubbles by viewModel.bubbles.collectAsState()
    var bubbleForDetails by remember { mutableStateOf<TransferBubble?>(null) }
    var bubbleForTransfer by remember { mutableStateOf<TransferBubble?>(null) }
    var showAddToBubbleMenu by remember { mutableStateOf(false) }

    var nodeToHide by remember { mutableStateOf<FileNode?>(null) }
    var showHiddenScreen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun hideNode(
        node: FileNode,
        mode: HideMode,
    ) {
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
            viewModel.refresh()
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

    LaunchedEffect(viewModel, openRequest) {
        viewModel.onOpenRequest(openRequest)
    }

    LaunchedEffect(uiState.currentFolderName) {
        if (uiState.currentFolderName.isNotBlank()) onLocationChange(uiState.currentFolderName)
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
            isSelectionMode -> viewModel.clearSelection()
            uiState.isSearching -> viewModel.toggleSearch(false)
            !viewModel.navigateBack() -> onNavigateBack()
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
                        viewModel = viewModel,
                        isSelectionMode = isSelectionMode,
                        uiState = uiState,
                        onOpenFile = onOpenFile,
                        onRequestRename = { nodeToRename = it },
                        onRequestPreview = { nodeForPreview = it },
                        onRequestMultiDelete = { confirmMultiDelete = true },
                    )
                },
    ) {
        Column(Modifier.fillMaxSize().background(Atomic.colors.background)) {
            tabStrip()
            BrowseTopBar(
                uiState = uiState,
                isSelectionMode = isSelectionMode,
                bubbles = bubbles,
                isDualPane = isDualPane,
                dualPaneMode = dualPaneMode,
                showSortMenu = showSortMenu,
                onShowSortMenu = { showSortMenu = it },
                showAddToBubbleMenu = showAddToBubbleMenu,
                onShowAddToBubbleMenu = { showAddToBubbleMenu = it },
                actions =
                    BrowseTopBarActions(
                        onClearSelection = { viewModel.clearSelection() },
                        onSelectAll = { viewModel.selectAll() },
                        onAddToBubble = { bubbleId -> viewModel.addSelectionToBubble(bubbleId) },
                        onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onToggleSearch = { viewModel.toggleSearch(it) },
                        onNavigateBack = {
                            if (!viewModel.navigateBack()) {
                                onNavigateBack()
                            }
                        },
                        onSetSortOption = { viewModel.setSortOption(it) },
                        onShowHiddenScreen = { showHiddenScreen = true },
                        onToggleDualPaneMode = {
                            dualPaneMode =
                                if (dualPaneMode == DualPaneMode.DUAL_BROWSE) {
                                    DualPaneMode.PREVIEW
                                } else {
                                    DualPaneMode.DUAL_BROWSE
                                }
                        },
                        onShowNewFolderDialog = { showNewFolderDialog = true },
                        onInvertSelection = { viewModel.invertSelection() },
                        onSelectSameType = { viewModel.selectSameType() },
                    ),
            )
            OperationBanner(uiState.activeOperation, onOpen = onOpenOperations)
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (isDualPane) {
                    DualPaneBrowseContent(
                        uiState = uiState,
                        secondaryUiState = secondaryUiState,
                        dualPaneMode = dualPaneMode,
                        viewModel = viewModel,
                        secondaryViewModel = secondaryViewModel,
                        dragController = dragController,
                        quickPeekController = quickPeekController,
                        nodeForPreview = nodeForPreview,
                        actions =
                            DualPaneItemActions(
                                onOpenFile = onOpenFile,
                                onPreviewNode = { node ->
                                    nodeForPreview = node
                                    if (dualPaneMode != DualPaneMode.PREVIEW) {
                                        dualPaneMode = DualPaneMode.PREVIEW
                                    }
                                },
                                onSecondaryPreviewNode = { node ->
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
                        uiState = uiState,
                        isSelectionMode = isSelectionMode,
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
                uiState = uiState,
                isSelectionMode = isSelectionMode,
                onCopySelected = { viewModel.copySelected() },
                onCutSelected = { viewModel.cutSelected() },
                onCompressSelected = { viewModel.compressSelected() },
                onDeleteSelected = { confirmMultiDelete = true },
                onRenameSelected = { nodes -> batchRename = nodes },
                onOpenInNewTab =
                    onOpenInNewTab?.let { open ->
                        { folder: FileNode ->
                            open(folder)
                            viewModel.clearSelection()
                        }
                    },
                onShowDetails = { node ->
                    nodeForDetails = node
                    viewModel.clearSelection()
                },
                onClearClipboard = { viewModel.clearClipboard() },
                onPaste = { viewModel.paste(CollisionPolicy.ASK) },
                onCompare = { a, b -> comparePair = a to b },
                onCompareFolders =
                    onCompareFolders?.let { compare ->
                        { left: FileNodeId, right: FileNodeId ->
                            viewModel.clearSelection()
                            compare(left, right)
                        }
                    },
            )
            comparePair?.let { (a, b) -> CompareSheet(first = a, second = b, onDismiss = { comparePair = null }) }
        }

        batchRename?.let { nodes ->
            BatchRenameSheet(
                nodes = nodes,
                folderNames = uiState.rawItems.map { it.name },
                onChanged = {
                    viewModel.clearSelection()
                    viewModel.refresh()
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
            viewModel = viewModel,
            secondaryViewModel = secondaryViewModel,
            isDualPane = isDualPane,
            uiState = uiState,
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
