package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.repository.FolderSortMemory
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import com.devbehindyou.atomicfilemanager.ui.components.BreadcrumbItem
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DropDecision
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SortOption(val label: String) {
    NAME("Name"),
    DATE_MODIFIED("Date modified"),
    SIZE("Size"),
}

enum class ClipboardOp { COPY, MOVE }

data class ClipboardState(val items: List<FileNode>, val operation: ClipboardOp)

data class BrowseUiState(
    val currentFolderId: FileNodeId,
    val currentFolderName: String = "",
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val rawItems: List<FileNode> = emptyList(),
    val filteredItems: List<FileNode> = emptyList(),
    val selectedIds: Set<FileNodeId> = emptySet(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val sortOption: SortOption = SortOption.NAME,
    val showHiddenFiles: Boolean = true,
    val fileFilter: BrowseFileFilter = BrowseFileFilter.ALL,
    val clipboard: ClipboardState? = null,
    val activeOperation: OperationSnapshot? = null,
)

class BrowseViewModel(
    private val initialFolderId: FileNodeId,
    private val getDirectoryListingUseCase: GetDirectoryListingUseCase,
    private val getNodeUseCase: GetNodeUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val operationQueue: OperationQueue,
    private val transferBubbleRepository: TransferBubbleRepository,
    private val folderSorts: FolderSortMemory? = null,
    startVisible: Boolean = true,
) : ViewModel() {
    /** The last row the user selected or unselected; a long-press selects the range from here. */
    private var selectionAnchor: FileNodeId? = null

    private val folderHistory = mutableListOf<BrowseLocation>()
    private val _uiState = MutableStateFlow(BrowseUiState(currentFolderId = initialFolderId))
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()
    val bubbles: StateFlow<List<TransferBubble>> = transferBubbleRepository.bubbles

    private var listingJob: Job? = null
    private var filterJob: Job? = null
    private var appliedOpenRequest: Int? = null
    private var sortRevision = 0L
    private val sortWrites = Mutex()
    private var visible = startVisible
    private var pendingLocation: BrowseLocation? = null
    private var activeOperationJob: Job? = null
    private var finishedOperationsJob: Job? = null

    init {
        if (startVisible) {
            loadDirectory(initialFolderId)
            observeOperations()
        }
    }

    private fun observeOperations() {
        // Operations run in the app-wide queue, so they outlive this screen and never cancel each other.
        activeOperationJob = viewModelScope.launch {
            operationQueue.active.collect { snapshot ->
                if (visible) _uiState.update { it.copy(activeOperation = snapshot) }
            }
        }
        finishedOperationsJob = viewModelScope.launch { operationQueue.finished.collect { if (visible) refresh() } }
    }

    /** Retains navigation/clipboard state while expensive screen work follows actual visibility. */
    internal fun onVisible(request: Int) {
        val reopenInitial = recordOpenRequest(request)
        val wasHidden = !visible
        if (!wasHidden && !reopenInitial) return
        visible = true
        if (wasHidden) observeOperations()
        val pending = pendingLocation
        pendingLocation = null
        if (reopenInitial) {
            loadDirectory(initialFolderId)
        } else {
            val current = _uiState.value
            val target = pending?.id ?: current.currentFolderId
            loadDirectory(
                target,
                isRefresh = !current.isLoading && target == current.currentFolderId,
                knownBreadcrumbs = pending?.breadcrumbs ?: current.breadcrumbs,
                retainSelection = target == current.currentFolderId,
            )
        }
    }

    internal fun onHidden() {
        if (!visible) return
        visible = false
        listingJob?.cancel()
        filterJob?.cancel()
        activeOperationJob?.cancel()
        finishedOperationsJob?.cancel()
    }

    fun loadDirectory(
        folderId: FileNodeId,
        isRefresh: Boolean = false,
        knownBreadcrumbs: List<BreadcrumbItem> = emptyList(),
        retainSelection: Boolean = false,
    ) {
        if (!visible) {
            pendingLocation = BrowseLocation(folderId, knownBreadcrumbs)
            return
        }
        listingJob?.cancel()
        filterJob?.cancel()
        if (!retainSelection) selectionAnchor = null
        val requestedSortRevision = sortRevision
        listingJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        currentFolderId = folderId,
                        breadcrumbs = knownBreadcrumbs,
                        isLoading = !isRefresh,
                        isRefreshing = isRefresh,
                        errorMessage = null,
                        selectedIds = if (retainSelection) it.selectedIds else emptySet(),
                    )
                }

                val rememberedSort =
                    sortWrites.withLock { folderSorts?.get(folderId.raw) }
                        ?.let { name -> SortOption.entries.firstOrNull { it.name == name } }
                if (sortRevision == requestedSortRevision && rememberedSort != null) {
                    _uiState.update { it.copy(sortOption = rememberedSort) }
                }

                val folder = (getNodeUseCase(folderId) as? FileResult.Success)?.value
                val crumbs = resolveBrowseBreadcrumbs(folderId, folder, knownBreadcrumbs) { getNodeUseCase(it) }
                _uiState.update {
                    it.copy(currentFolderName = folder?.displayName ?: crumbs.last().name, breadcrumbs = crumbs)
                }
                val accumulated = mutableListOf<FileNode>()
                var lastPush = 0L
                var pushedAny = false
                var sawFailure = false

                getDirectoryListingUseCase(folderId).collect { result ->
                    when (result) {
                        is FileResult.Success -> {
                            accumulated.addAll(result.value)
                            val now = System.currentTimeMillis()
                            val isFirst = !pushedAny
                            if (isFirst || now - lastPush >= 120L) {
                                applyNewItems(accumulated.toList())
                                lastPush = now
                                pushedAny = true
                            }
                        }
                        is FileResult.Failure -> {
                            sawFailure = true
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    errorMessage = result.error.message(),
                                )
                            }
                        }
                    }
                }

                if (!sawFailure) {
                    applyNewItems(accumulated.toList())
                    val shouldRetainSelection =
                        retainSelection && (_uiState.value.selectedIds.isNotEmpty() || selectionAnchor != null)
                    val retainedIds = if (shouldRetainSelection) {
                        accumulated.mapTo(HashSet()) { it.id }
                    } else {
                        null
                    }
                    if (retainedIds != null && selectionAnchor !in retainedIds) selectionAnchor = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            selectedIds = retainedIds?.let(it.selectedIds::intersect) ?: it.selectedIds,
                        )
                    }
                }
            }
    }

    private suspend fun applyNewItems(items: List<FileNode>) {
        val query = _uiState.value.searchQuery
        val sort = _uiState.value.sortOption
        val showHidden = _uiState.value.showHiddenFiles
        val fileFilter = _uiState.value.fileFilter
        val filtered =
            withContext(Dispatchers.Default) {
                filterAndSort(items, query, sort, showHidden, fileFilter)
            }
        _uiState.update {
            it.copy(
                rawItems = items,
                filteredItems = filtered,
                isLoading = false,
            )
        }
        // The query or sort may have changed while the list was being sorted.
        val current = _uiState.value
        if (current.searchQuery != query || current.sortOption != sort || current.showHiddenFiles != showHidden ||
            current.fileFilter != fileFilter
        ) {
            refilter()
        }
    }

    /**
     * Recomputes [BrowseUiState.filteredItems] off the main thread. Only the newest request may
     * publish: an older, slower run would otherwise overwrite the result for the latest query.
     */
    private fun refilter() {
        filterJob?.cancel()
        filterJob =
            viewModelScope.launch {
                val requested = _uiState.value
                val filtered =
                    withContext(Dispatchers.Default) {
                        filterAndSort(
                            requested.rawItems,
                            requested.searchQuery,
                            requested.sortOption,
                            requested.showHiddenFiles,
                            requested.fileFilter,
                        )
                    }
                _uiState.update { current ->
                    if (current.rawItems === requested.rawItems &&
                        current.currentFolderId == requested.currentFolderId &&
                        current.searchQuery == requested.searchQuery &&
                        current.sortOption == requested.sortOption &&
                        current.showHiddenFiles == requested.showHiddenFiles &&
                        current.fileFilter == requested.fileFilter
                    ) {
                        current.copy(filteredItems = filtered)
                    } else {
                        current
                    }
                }
            }
    }

    private fun filterAndSort(
        items: List<FileNode>,
        query: String,
        sort: SortOption,
        showHidden: Boolean,
        fileFilter: BrowseFileFilter,
    ): List<FileNode> {
        val visible = items.filter { (showHidden || !it.isHidden) && fileFilter.matches(it) }
        val filtered =
            if (query.isBlank()) {
                visible
            } else {
                visible.filter { it.name.contains(query, ignoreCase = true) }
            }
        val comparator = comparatorFor(sort)
        return filtered.sortedWith(comparator)
    }

    private fun comparatorFor(sortOption: SortOption): Comparator<FileNode> {
        val byField: Comparator<FileNode> =
            when (sortOption) {
                SortOption.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortOption.DATE_MODIFIED -> compareByDescending { it.modifiedAt }
                SortOption.SIZE -> compareByDescending { it.size }
            }
        return compareByDescending<FileNode> { it.isDirectory }.then(byField)
    }

    /**
     * Returns to the start folder when [request] differs from the last one seen, meaning the user
     * opened this folder again from outside Browse. The first call only records the value, so a new
     * or recreated view model keeps its state.
     */
    fun onOpenRequest(request: Int) {
        if (recordOpenRequest(request)) loadDirectory(initialFolderId)
    }

    private fun recordOpenRequest(request: Int): Boolean {
        val previous = appliedOpenRequest
        appliedOpenRequest = request
        if (previous != null && previous != request) {
            folderHistory.clear()
            return true
        }
        return false
    }

    fun navigateTo(folder: FileNode) {
        if (!folder.isDirectory) return
        val current = _uiState.value
        folderHistory.add(BrowseLocation(current.currentFolderId, current.breadcrumbs))
        loadDirectory(
            folder.id,
            knownBreadcrumbs = current.breadcrumbs + BreadcrumbItem(folder.displayName, folder.id.raw),
        )
    }

    /** Switch within this model so the clipboard and pane ownership survive changing drives. */
    fun navigateToStorage(
        root: FileNodeId,
        label: String,
    ) {
        val current = _uiState.value
        if (root != current.currentFolderId) {
            folderHistory.add(BrowseLocation(current.currentFolderId, current.breadcrumbs))
        }
        _uiState.update { it.copy(searchQuery = "", isSearching = false, fileFilter = BrowseFileFilter.ALL) }
        loadDirectory(root, knownBreadcrumbs = listOf(BreadcrumbItem(label, root.raw)))
    }

    fun setFileFilter(filter: BrowseFileFilter) {
        _uiState.update { it.copy(fileFilter = filter) }
        refilter()
    }

    fun navigateBack(): Boolean {
        if (folderHistory.isNotEmpty()) {
            val previous = folderHistory.removeAt(folderHistory.lastIndex)
            loadDirectory(previous.id, knownBreadcrumbs = previous.breadcrumbs)
            return true
        }
        return false
    }

    fun navigateUp(): Boolean {
        val crumbs = _uiState.value.breadcrumbs
        if (crumbs.size > 1) {
            val parentCrumb = crumbs[crumbs.size - 2]
            navigateToBreadcrumb(parentCrumb)
            return true
        }
        return navigateBack()
    }

    fun navigateToBreadcrumb(crumb: BreadcrumbItem) {
        val current = _uiState.value
        val index = current.breadcrumbs.indexOf(crumb)
        if (index < 0) return
        val targetId = FileNodeId(crumb.path)
        if (targetId != current.currentFolderId) {
            folderHistory.add(BrowseLocation(current.currentFolderId, current.breadcrumbs))
            loadDirectory(targetId, knownBreadcrumbs = current.breadcrumbs.take(index + 1))
        }
    }

    fun refresh() {
        val current = _uiState.value
        loadDirectory(current.currentFolderId, isRefresh = true, knownBreadcrumbs = current.breadcrumbs)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refilter()
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update { it.copy(isSearching = active, searchQuery = if (!active) "" else it.searchQuery) }
        if (!active) {
            onSearchQueryChanged("")
        }
    }

    fun setShowHiddenFiles(show: Boolean) {
        if (_uiState.value.showHiddenFiles == show) return
        _uiState.update { it.copy(showHiddenFiles = show) }
        refilter()
    }

    /** Applies [option] and remembers it for the current folder (FR-3.3). */
    fun setSortOption(option: SortOption) {
        sortRevision++
        val folder = _uiState.value.currentFolderId
        _uiState.update { it.copy(sortOption = option) }
        if (folderSorts != null) {
            // Accept a small metadata save immediately, in order, even if the pane closes.
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) {
                    sortWrites.withLock { folderSorts.set(folder.raw, option.name) }
                }
            }
        }
        refilter()
    }

    fun toggleSelection(nodeId: FileNodeId) {
        selectionAnchor = nodeId
        _uiState.update { state -> state.copy(selectedIds = SelectionRules.toggle(state.selectedIds, nodeId)) }
    }

    /** Long-press: starts a selection, or with one open selects the range from the last touched row. */
    fun longPressSelect(nodeId: FileNodeId) {
        val anchor = selectionAnchor
        selectionAnchor = nodeId
        _uiState.update { state ->
            state.copy(selectedIds = SelectionRules.longPress(state.filteredItems, state.selectedIds, anchor, nodeId))
        }
    }

    fun invertSelection() {
        _uiState.update {
                state ->
            state.copy(selectedIds = SelectionRules.invert(state.filteredItems, state.selectedIds))
        }
    }

    fun selectSameType() {
        _uiState.update {
                state ->
            state.copy(selectedIds = SelectionRules.sameType(state.filteredItems, state.selectedIds))
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.filteredItems.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        selectionAnchor = null
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            when (val result = createDirectoryUseCase(_uiState.value.currentFolderId, name)) {
                is FileResult.Success -> refresh()
                is FileResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.message()) }
            }
        }
    }

    fun rename(
        nodeId: FileNodeId,
        newName: String,
    ) {
        viewModelScope.launch {
            when (val result = renameFileUseCase(nodeId, newName)) {
                is FileResult.Success -> refresh()
                is FileResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.message()) }
            }
        }
    }

    /** [toTrash] moves them to the Trash (restorable); otherwise they are deleted for good. */
    fun deleteNodes(
        nodeIds: List<FileNodeId>,
        toTrash: Boolean = false,
    ) {
        viewModelScope.launch {
            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = if (toTrash) OperationType.TRASH else OperationType.DELETE,
                    sources = nodeIds,
                    destination = null,
                    options = OperationOptions(),
                    createdAt = System.currentTimeMillis(),
                )
            runOperation(op)
        }
    }

    fun copySelected() {
        val selectedNodes = _uiState.value.rawItems.filter { it.id in _uiState.value.selectedIds }
        if (selectedNodes.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboard = ClipboardState(selectedNodes, ClipboardOp.COPY),
                    selectedIds = emptySet(),
                )
            }
        }
    }

    fun cutSelected() {
        val selectedNodes = _uiState.value.rawItems.filter { it.id in _uiState.value.selectedIds }
        if (selectedNodes.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboard = ClipboardState(selectedNodes, ClipboardOp.MOVE),
                    selectedIds = emptySet(),
                )
            }
        }
    }

    fun clearClipboard() {
        _uiState.update { it.copy(clipboard = null) }
    }

    fun paste(collisionPolicy: CollisionPolicy = CollisionPolicy.ASK) {
        val clip = _uiState.value.clipboard ?: return
        val currentFolder = _uiState.value.currentFolderId

        val opType =
            when (clip.operation) {
                ClipboardOp.COPY -> OperationType.COPY
                ClipboardOp.MOVE -> OperationType.MOVE
            }

        val op =
            FileOperation(
                id = OperationId.random(),
                type = opType,
                sources = clip.items.map { it.id },
                destination = currentFolder,
                options = OperationOptions(collisionPolicy = collisionPolicy),
                createdAt = System.currentTimeMillis(),
            )

        runOperation(op)
        _uiState.update { it.copy(clipboard = null) }
    }

    fun compressSelected() {
        val selectedNodes = _uiState.value.rawItems.filter { it.id in _uiState.value.selectedIds }
        if (selectedNodes.isEmpty()) return
        val operation =
            FileOperation(
                id = OperationId.random(),
                type = OperationType.COMPRESS,
                sources = selectedNodes.map { it.id },
                destination = _uiState.value.currentFolderId,
                options = OperationOptions(),
                createdAt = System.currentTimeMillis(),
            )
        clearSelection()
        runOperation(operation)
    }

    fun compressSingle(node: FileNode) {
        val operation =
            FileOperation(
                id = OperationId.random(),
                type = OperationType.COMPRESS,
                sources = listOf(node.id),
                destination = _uiState.value.currentFolderId,
                options = OperationOptions(),
                createdAt = System.currentTimeMillis(),
            )
        runOperation(operation)
    }

    fun extractArchive(node: FileNode) {
        val operation =
            FileOperation(
                id = OperationId.random(),
                type = OperationType.EXTRACT,
                sources = listOf(node.id),
                destination = _uiState.value.currentFolderId,
                options = OperationOptions(),
                createdAt = System.currentTimeMillis(),
            )
        runOperation(operation)
    }

    private fun runOperation(operation: FileOperation) {
        operationQueue.enqueue(operation)
    }

    fun executeDrop(decision: DropDecision) {
        when (decision) {
            is DropDecision.Move -> {
                val operation =
                    FileOperation(
                        id = OperationId.random(),
                        type = OperationType.MOVE,
                        sources = decision.payload.itemIds,
                        destination = decision.destination,
                        options = OperationOptions(collisionPolicy = CollisionPolicy.ASK),
                        createdAt = System.currentTimeMillis(),
                    )
                clearSelection()
                runOperation(operation)
            }
            is DropDecision.Copy -> {
                val operation =
                    FileOperation(
                        id = OperationId.random(),
                        type = OperationType.COPY,
                        sources = decision.payload.itemIds,
                        destination = decision.destination,
                        options = OperationOptions(collisionPolicy = CollisionPolicy.ASK),
                        createdAt = System.currentTimeMillis(),
                    )
                clearSelection()
                runOperation(operation)
            }
            is DropDecision.AddToBubble -> {
                viewModelScope.launch {
                    transferBubbleRepository.addItemsToBubble(decision.bubbleId, decision.payload.items)
                }
            }
            is DropDecision.Cancel -> {}
        }
    }

    fun createBubble(name: String? = null) {
        viewModelScope.launch {
            transferBubbleRepository.createBubble(name)
        }
    }

    fun addFilesToBubble(
        bubbleId: String,
        files: List<FileNode>,
    ) {
        viewModelScope.launch {
            transferBubbleRepository.addItemsToBubble(bubbleId, files)
        }
    }

    fun addSelectionToBubble(bubbleId: String) {
        val selectedNodes = _uiState.value.rawItems.filter { it.id in _uiState.value.selectedIds }
        if (selectedNodes.isNotEmpty()) {
            addFilesToBubble(bubbleId, selectedNodes)
            clearSelection()
        }
    }

    fun removeBubbleItem(
        bubbleId: String,
        itemId: Long,
    ) {
        viewModelScope.launch {
            transferBubbleRepository.removeItemFromBubble(bubbleId, itemId)
        }
    }

    fun clearBubble(bubbleId: String) {
        viewModelScope.launch {
            transferBubbleRepository.clearBubble(bubbleId)
        }
    }

    fun deleteBubble(bubbleId: String) {
        viewModelScope.launch {
            transferBubbleRepository.deleteBubble(bubbleId)
        }
    }

    fun transferBubble(
        bubble: TransferBubble,
        isMove: Boolean,
        clearAfter: Boolean,
    ) {
        if (bubble.items.isEmpty()) return
        val currentFolder = _uiState.value.currentFolderId
        val operation =
            FileOperation(
                id = OperationId.random(),
                type = if (isMove) OperationType.MOVE else OperationType.COPY,
                sources = bubble.items.map { it.fileNodeId },
                destination = currentFolder,
                options = OperationOptions(collisionPolicy = CollisionPolicy.ASK),
                createdAt = System.currentTimeMillis(),
            )
        viewModelScope.launch {
            val result = operationQueue.runAndAwait(operation)
            // Empty the bubble only when every item made it; otherwise its list is the record of what's left.
            if ((isMove || clearAfter) && result.status is OperationStatus.Completed) {
                transferBubbleRepository.clearBubble(bubble.id)
            }
        }
    }

    fun cancelActiveOperation() {
        operationQueue.active.value?.let { operationQueue.cancel(it.operation.id) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun FileError.message(): String =
        when (this) {
            is FileError.FileNotFound -> "File not found: ${name.orEmpty()}"
            is FileError.FileAlreadyExists -> "File already exists: $name"
            is FileError.AccessDenied -> "Access denied: ${name.orEmpty()}"
            is FileError.PermissionDenied -> "Permission denied"
            is FileError.PlatformRestricted -> "Access restricted by Android OS"
            is FileError.ProviderUnavailable -> "Storage provider unavailable"
            is FileError.StorageUnavailable ->
                volumeLabel?.let { "Storage volume unavailable: $it" }
                    ?: "The storage was removed or can't be reached. Reconnect it and try again."
            is FileError.ReadOnlyStorage -> "Storage is read-only"
            is FileError.DiskFull -> "Insufficient storage space"
            is FileError.OutOfMemory -> "Out of memory"
            is FileError.PathTooLong -> "Path is too long: ${name.orEmpty()}"
            is FileError.InvalidDestination -> "Invalid destination: ${name.orEmpty()}"
            is FileError.InvalidName -> "Invalid name: $name"
            is FileError.OperationCancelled -> "Operation cancelled"
            is FileError.IncompleteWrite -> "Write incomplete for $name"
            is FileError.PartialFailure -> "Partial failure: $failedCount of $totalCount failed"
            is FileError.IoFailure -> "I/O failure: ${name.orEmpty()}"
            is FileError.UnsupportedFormat -> "Unsupported format: ${mimeType.orEmpty()}"
            is FileError.CorruptedArchive -> "Corrupted archive: ${name.orEmpty()}"
            is FileError.SuspiciousArchive -> "Suspicious archive: $reason"
            is FileError.FileTooLarge -> "File too large: $name"
            is FileError.Unknown -> "Unknown error ($marker)"
        }

    companion object {
        fun provideFactory(
            initialFolderId: FileNodeId,
            getDirectoryListingUseCase: GetDirectoryListingUseCase,
            getNodeUseCase: GetNodeUseCase,
            createDirectoryUseCase: CreateDirectoryUseCase,
            renameFileUseCase: RenameFileUseCase,
            deleteFileUseCase: DeleteFileUseCase,
            operationQueue: OperationQueue,
            transferBubbleRepository: TransferBubbleRepository,
            folderSorts: FolderSortMemory? = null,
            startVisible: Boolean = true,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BrowseViewModel(
                        initialFolderId = initialFolderId,
                        getDirectoryListingUseCase = getDirectoryListingUseCase,
                        getNodeUseCase = getNodeUseCase,
                        createDirectoryUseCase = createDirectoryUseCase,
                        renameFileUseCase = renameFileUseCase,
                        deleteFileUseCase = deleteFileUseCase,
                        operationQueue = operationQueue,
                        transferBubbleRepository = transferBubbleRepository,
                        folderSorts = folderSorts,
                        startVisible = startVisible,
                    ) as T
                }
            }
    }
}
