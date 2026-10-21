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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrimaryBrowseStateTest {
    @Test
    fun returningRetainsClipboardLocationAndBackHistoryWhileExplicitReopenResetsOnlyNavigation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val memory = InMemoryBackend()
        val child = checkNotNull(memory.createDirectory(memory.rootId, "Child").getOrNull())
        val inside = checkNotNull(memory.createDirectory(child.id, "Inside").getOrNull())
        val source = checkNotNull(memory.createDirectory(memory.rootId, "Source").getOrNull())
        val reads = mutableListOf<FileNodeId>()
        val backend = object : StorageBackend by memory {
            override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> = flow {
                synchronized(reads) { reads += id }
                emitAll(memory.listChildren(id))
            }
        }
        val queue = OperationQueue(
            engine = { operation, _ -> flowOf(OperationSnapshot(operation, OperationStatus.Cancelled)) },
            journal = null,
            scope = backgroundScope,
        )
        val model = BrowseViewModel(
            memory.rootId,
            GetDirectoryListingUseCase { backend },
            GetNodeUseCase { backend },
            CreateDirectoryUseCase { backend },
            RenameFileUseCase { backend },
            DeleteFileUseCase { backend },
            queue,
            StateBubbles(),
            startVisible = false,
        )
        suspend fun awaitFolder(id: FileNodeId) = model.uiState.first {
            it.currentFolderId == id && !it.isLoading && !it.isRefreshing
        }
        fun count() = synchronized(reads) { reads.size }
        try {
            assertEquals(0, count())
            model.onVisible(0)
            testScheduler.runCurrent()
            awaitFolder(memory.rootId)
            model.toggleSelection(source.id)
            model.copySelected()
            val clipboard = checkNotNull(model.uiState.value.clipboard)
            model.navigateTo(child)
            awaitFolder(child.id)
            model.toggleSelection(inside.id)
            model.onHidden()
            val before = count()
            // A late accepted action may ask for a refresh after the screen was removed.
            model.refresh()
            assertEquals(before, count())
            model.onVisible(0)
            testScheduler.runCurrent()
            awaitFolder(child.id)
            assertEquals(before + 1, count())
            assertSame(clipboard, model.uiState.value.clipboard)
            assertEquals(setOf(inside.id), model.uiState.value.selectedIds)
            model.onHidden()
            memory.delete(inside.id)
            model.onVisible(0)
            testScheduler.runCurrent()
            awaitFolder(child.id)
            assertTrue(model.uiState.value.selectedIds.isEmpty())
            assertSame(clipboard, model.uiState.value.clipboard)
            assertTrue(model.navigateBack())
            awaitFolder(memory.rootId)
            model.navigateTo(child)
            awaitFolder(child.id)
            model.onHidden()
            val beforeReopen = count()
            model.refresh()
            model.onVisible(1)
            awaitFolder(memory.rootId)
            assertEquals(beforeReopen + 1, count())
            assertSame(clipboard, model.uiState.value.clipboard)
            assertFalse(model.navigateBack())
        } finally {
            val owner = model.viewModelScope.coroutineContext.job
            model.viewModelScope.cancel()
            owner.join()
            Dispatchers.resetMain()
        }
    }
}

private class StateBubbles : TransferBubbleRepository {
    override val bubbles = MutableStateFlow<List<TransferBubble>>(emptyList())

    override suspend fun createBubble(name: String?): Result<TransferBubble> = error("Not used")

    override suspend fun addItemsToBubble(bubbleId: String, items: List<FileNode>): Result<Unit> = error("Not used")

    override suspend fun removeItemFromBubble(bubbleId: String, itemId: Long): Result<Unit> = error("Not used")

    override suspend fun clearBubble(bubbleId: String): Result<Unit> = error("Not used")

    override suspend fun deleteBubble(bubbleId: String): Result<Unit> = error("Not used")

    override suspend fun getBubble(bubbleId: String): TransferBubble? = error("Not used")
}
