package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.model.getOrNull
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import com.devbehindyou.atomicfilemanager.ui.components.BreadcrumbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseNavigationIdentityTest {
    @Test
    fun switchingStorageKeepsCopyAndMoveClipboardsAndBackHistory() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val backend = InMemoryBackend()
            val internal = checkNotNull(backend.createDirectory(backend.rootId, "Internal").getOrNull())
            val sd = checkNotNull(backend.createDirectory(backend.rootId, "SD").getOrNull())
            val source = checkNotNull(backend.createDirectory(internal.id, "Photos").getOrNull())
            val queue =
                OperationQueue(
                    engine = { operation, _ -> flowOf(OperationSnapshot(operation, OperationStatus.Cancelled)) },
                    journal = null,
                    scope = backgroundScope,
                )
            val model =
                BrowseViewModel(
                    internal.id,
                    GetDirectoryListingUseCase { backend },
                    GetNodeUseCase { backend },
                    CreateDirectoryUseCase { backend },
                    RenameFileUseCase { backend },
                    DeleteFileUseCase { backend },
                    queue,
                    NavigationBubbles(),
                )

            suspend fun awaitFolder(id: FileNodeId) = model.uiState.first { it.currentFolderId == id && !it.isLoading }
            try {
                awaitFolder(internal.id)
                for (operation in ClipboardOp.entries) {
                    model.longPressSelect(source.id)
                    if (operation == ClipboardOp.COPY) model.copySelected() else model.cutSelected()
                    val clipboard = model.uiState.value.clipboard
                    assertEquals(operation, clipboard?.operation)
                    model.onSearchQueryChanged("missing")
                    model.setFileFilter(BrowseFileFilter.FILES)
                    model.navigateToStorage(sd.id, "Samsung SD card")
                    val state = awaitFolder(sd.id)
                    assertSame(clipboard, state.clipboard)
                    assertEquals("", state.searchQuery)
                    assertEquals(BrowseFileFilter.ALL, state.fileFilter)
                    assertEquals(sd.id.raw, state.breadcrumbs.last().path)
                    assertTrue(model.navigateBack())
                    awaitFolder(internal.id)
                    assertSame(clipboard, model.uiState.value.clipboard)
                }
            } finally {
                model.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun safNavigationUpBackAndBreadcrumbJumpKeepVisitedIds() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val root = folder(FileNodeId.saf("content://provider/tree/opaque%2Froot"), "Root")
            val child = folder(FileNodeId.saf("content://provider/document/opaque%2Fchild"), "Child")
            val leaf = folder(FileNodeId.saf("content://provider/document/other%3Aleaf"), "Leaf")
            val nodes = listOf(root, child, leaf).associateBy { it.id }
            val backend =
                object : StorageBackend by InMemoryBackend() {
                    override suspend fun getNode(id: FileNodeId) = FileResult.Success(checkNotNull(nodes[id]))

                    override fun listChildren(id: FileNodeId) = flowOf(FileResult.Success(emptyList<FileNode>()))
                }
            val queue =
                OperationQueue(
                    engine = { operation, _ -> flowOf(OperationSnapshot(operation, OperationStatus.Cancelled)) },
                    journal = null,
                    scope = backgroundScope,
                )
            val model =
                BrowseViewModel(
                    root.id,
                    GetDirectoryListingUseCase { backend },
                    GetNodeUseCase { backend },
                    CreateDirectoryUseCase { backend },
                    RenameFileUseCase { backend },
                    DeleteFileUseCase { backend },
                    queue,
                    NavigationBubbles(),
                )

            suspend fun awaitFolder(id: FileNodeId): BrowseUiState =
                model.uiState.first { it.currentFolderId == id && !it.isLoading }
            try {
                awaitFolder(root.id)
                model.navigateTo(child)
                awaitFolder(child.id)
                model.navigateTo(leaf)
                assertEquals(
                    listOf(root.id.raw, child.id.raw, leaf.id.raw),
                    awaitFolder(leaf.id).breadcrumbs.map { it.path },
                )
                assertTrue(model.navigateUp())
                assertEquals(listOf(root.id.raw, child.id.raw), awaitFolder(child.id).breadcrumbs.map { it.path })
                assertTrue(model.navigateBack())
                assertEquals(
                    listOf(root.id.raw, child.id.raw, leaf.id.raw),
                    awaitFolder(leaf.id).breadcrumbs.map { it.path },
                )
                model.navigateToBreadcrumb(model.uiState.value.breadcrumbs.first())
                assertEquals(listOf(root.id.raw), awaitFolder(root.id).breadcrumbs.map { it.path })
                assertTrue(model.navigateBack())
                assertEquals(
                    listOf(root.id.raw, child.id.raw, leaf.id.raw),
                    awaitFolder(leaf.id).breadcrumbs.map { it.path },
                )
                model.navigateToBreadcrumb(BreadcrumbItem("Invented", "saf:invalid/parent"))
                assertEquals(leaf.id, model.uiState.value.currentFolderId)
            } finally {
                model.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }
}

private class NavigationBubbles : TransferBubbleRepository {
    override val bubbles = MutableStateFlow<List<TransferBubble>>(emptyList())

    override suspend fun createBubble(name: String?): Result<TransferBubble> = error("Unused")

    override suspend fun addItemsToBubble(
        bubbleId: String,
        items: List<FileNode>,
    ): Result<Unit> = error("Unused")

    override suspend fun removeItemFromBubble(
        bubbleId: String,
        itemId: Long,
    ): Result<Unit> = error("Unused")

    override suspend fun clearBubble(bubbleId: String): Result<Unit> = error("Unused")

    override suspend fun deleteBubble(bubbleId: String): Result<Unit> = error("Unused")

    override suspend fun getBubble(bubbleId: String): TransferBubble? = error("Unused")
}
