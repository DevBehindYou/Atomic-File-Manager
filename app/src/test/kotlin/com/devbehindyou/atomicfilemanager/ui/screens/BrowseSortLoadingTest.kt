package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.repository.FolderSortMemory
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseSortLoadingTest {
    @ParameterizedTest
    @ValueSource(
        strings = ["NAME", "DATE_MODIFIED", "SIZE", "NAME_DESCENDING", "DATE_MODIFIED_ASCENDING", "SIZE_ASCENDING"],
    )
    fun `legacy and inverse direction names restore and save unchanged`(persisted: String) =
        runTest {
            var saved: String? = null
            val memory =
                object : FolderSortMemory {
                    override suspend fun get(folderRaw: String) = persisted

                    override suspend fun set(
                        folderRaw: String,
                        value: String,
                    ) {
                        saved = value
                    }
                }
            withModel(memory) { model ->
                model.uiState.first { !it.isLoading }
                val expected = SortOption.valueOf(persisted)
                assertEquals(expected, model.uiState.value.sortOption)
                model.setSortOption(expected)
                runCurrent()
                assertEquals(persisted, saved)
            }
        }

    @Test
    fun `delayed stored order does not overwrite a newer user choice`() =
        runTest {
            val loaded = CompletableDeferred<String?>()
            val writes = mutableListOf<String>()
            val memory =
                object : FolderSortMemory {
                    override suspend fun get(folderRaw: String) = loaded.await()

                    override suspend fun set(
                        folderRaw: String,
                        value: String,
                    ) {
                        writes += value
                    }
                }
            withModel(memory) { model ->
                runCurrent()
                assertTrue(model.uiState.value.isLoading)
                model.setSortOption(SortOption.DATE_MODIFIED)
                assertEquals(SortOption.DATE_MODIFIED, model.uiState.value.sortOption)
                loaded.complete(SortOption.SIZE.name)
                model.uiState.first { !it.isLoading }
                assertEquals(SortOption.DATE_MODIFIED, model.uiState.value.sortOption)
                assertEquals(listOf(SortOption.DATE_MODIFIED.name), writes)
                assertFalse(model.uiState.value.isLoading)
            }
        }

    @Test
    fun `stored order is applied before initial items are sorted`() =
        runTest {
            withModel(
                object : FolderSortMemory {
                    override suspend fun get(folderRaw: String) = SortOption.SIZE.name

                    override suspend fun set(
                        folderRaw: String,
                        value: String,
                    ) = Unit
                },
            ) { model ->
                model.uiState.first { !it.isLoading }
                assertEquals(SortOption.SIZE, model.uiState.value.sortOption)
                assertFalse(model.uiState.value.isLoading)
            }
        }

    @Test
    fun `navigating away cancels the old preference read`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            var calls = 0
            val memory =
                object : FolderSortMemory {
                    override suspend fun get(folderRaw: String): String? {
                        if (calls++ == 0) {
                            entered.complete(Unit)
                            try {
                                CompletableDeferred<Unit>().await()
                            } finally {
                                cancelled.complete(Unit)
                            }
                        }
                        return SortOption.DATE_MODIFIED.name
                    }

                    override suspend fun set(
                        folderRaw: String,
                        value: String,
                    ) = Unit
                }
            withModel(memory) { model ->
                entered.await()
                val other = FileNodeId.file("/other")
                model.loadDirectory(other)
                cancelled.await()
                model.uiState.first { it.currentFolderId == other && !it.isLoading }
                assertEquals(SortOption.DATE_MODIFIED, model.uiState.value.sortOption)
            }
        }

    @Test
    fun `refresh waits for accepted saves before restoring the remembered order`() =
        runTest {
            val firstWrite = CompletableDeferred<Unit>()
            var stored = SortOption.NAME.name
            val memory =
                object : FolderSortMemory {
                    override suspend fun get(folderRaw: String) = stored

                    override suspend fun set(
                        folderRaw: String,
                        value: String,
                    ) {
                        if (value == SortOption.SIZE.name) firstWrite.await()
                        stored = value
                    }
                }
            withModel(memory) { model ->
                model.uiState.first { !it.isLoading }
                model.setSortOption(SortOption.SIZE)
                model.setSortOption(SortOption.DATE_MODIFIED)
                model.refresh()
                runCurrent()
                assertTrue(model.uiState.value.isRefreshing)
                firstWrite.complete(Unit)
                model.uiState.first { !it.isRefreshing }
                assertEquals(SortOption.DATE_MODIFIED, model.uiState.value.sortOption)
                assertEquals(SortOption.DATE_MODIFIED.name, stored)
            }
        }

    @Test
    fun `accepted saves finish in order when pane closes`() =
        runTest {
            val firstWrite = CompletableDeferred<Unit>()
            val writes = mutableListOf<String>()
            val memory =
                object : FolderSortMemory {
                    override suspend fun get(folderRaw: String): String? = null

                    override suspend fun set(
                        folderRaw: String,
                        value: String,
                    ) {
                        if (value == SortOption.SIZE.name) firstWrite.await()
                        writes += value
                    }
                }
            withModel(memory) { model ->
                model.uiState.first { !it.isLoading }
                model.setSortOption(SortOption.SIZE)
                model.setSortOption(SortOption.DATE_MODIFIED)
                assertTrue(writes.isEmpty())
                model.viewModelScope.cancel()
                firstWrite.complete(Unit)
                advanceUntilIdle()
                assertEquals(listOf(SortOption.SIZE.name, SortOption.DATE_MODIFIED.name), writes)
            }
        }

    private suspend fun TestScope.withModel(
        memory: FolderSortMemory,
        test: suspend (BrowseViewModel) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val backend = InMemoryBackend()
        val queue =
            OperationQueue(
                engine = { operation, _ -> flowOf(OperationSnapshot(operation, OperationStatus.Cancelled)) },
                journal = null,
                scope = backgroundScope,
            )
        val model =
            BrowseViewModel(
                backend.rootId,
                GetDirectoryListingUseCase { backend },
                GetNodeUseCase { backend },
                CreateDirectoryUseCase { backend },
                RenameFileUseCase { backend },
                DeleteFileUseCase { backend },
                queue,
                SortTestBubbles(),
                memory,
            )
        try {
            test(model)
        } finally {
            // Listing/filter work can still be returning from Dispatchers.Default after cancel().
            // Keep the test Main installed until those children finish their cancellation handoff.
            model.viewModelScope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }
}

private class SortTestBubbles : TransferBubbleRepository {
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
