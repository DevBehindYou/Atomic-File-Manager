package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationSummary
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubbleItem
import com.devbehindyou.atomicfilemanager.domain.model.getOrNull
import com.devbehindyou.atomicfilemanager.domain.repository.FolderSortMemory
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseAcceptedWorkRetirementTest {
    @Test
    fun `deferred create and rename are retained through abandonment until the last accepted job drains`() =
        runTest {
            val create = CompletableDeferred<Unit>()
            val rename = CompletableDeferred<Unit>()
            withModel(beforeCreate = { create.await() }, beforeRename = { rename.await() }) { fixture ->
                val model = fixture.entry.viewModel
                model.createFolder("accepted")
                model.rename(fixture.target, "renamed")
                fixture.entry.onAbandoned()
                fixture.entry.onForgotten()
                assertTrue(model.viewModelScope.isActive)
                assertTrue(fixture.completed.isEmpty())
                runCurrent()
                assertTrue(model.viewModelScope.isActive)
                create.complete(Unit)
                runCurrent()
                assertEquals(listOf("create"), fixture.completed)
                assertTrue(model.viewModelScope.isActive)
                rename.complete(Unit)
                runCurrent()
                assertEquals(listOf("create", "rename"), fixture.completed)
                assertTrue(fixture.listingRequests.isEmpty())
                assertFalse(model.viewModelScope.isActive)
                model.onVisible(1)
                model.loadDirectory(fixture.root)
                model.createFolder("after retirement")
                runCurrent()
                assertEquals(listOf("create", "rename"), fixture.completed)
            }
        }

    @Test
    fun `cancelled accepted job releases ownership and idle retirement callback is one shot`() =
        runTest {
            withModel(beforeCreate = { throw CancellationException("backend cancelled") }) { fixture ->
                val model = fixture.entry.viewModel
                model.createFolder("accepted")
                var drained = 0
                model.retireAfterAcceptedWork { drained++ }
                model.retireAfterAcceptedWork { error("A duplicate retirement must not replace the owner") }
                assertEquals(0, drained)
                runCurrent()
                assertEquals(1, drained)
                fixture.entry.onForgotten()
                assertEquals(1, drained)
            }
        }

    @Test
    fun `already cancelled scope does not leak an accepted work count`() =
        runTest {
            withModel { fixture ->
                val model = fixture.entry.viewModel
                model.viewModelScope.cancel()
                model.createFolder("cannot start")
                var drained = 0
                model.retireAfterAcceptedWork { drained++ }
                runCurrent()
                assertEquals(1, drained)
                assertTrue(fixture.completed.isEmpty())
            }
        }

    @Test
    fun `two ordered noncancellable sort saves drain before their secondary store clears`() =
        runTest {
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            val entered = mutableListOf<String>()
            val finished = mutableListOf<String>()
            val sorts =
                object : FolderSortMemory {
                    override suspend fun get(folderRaw: String): String? = null

                    override suspend fun set(
                        folderRaw: String,
                        value: String,
                    ) {
                        entered += value
                        if (entered.size == 1) first.await() else second.await()
                        finished += value
                    }
                }
            withModel(sorts = sorts) { fixture ->
                val model = fixture.entry.viewModel
                try {
                    model.setSortOption(SortOption.SIZE)
                    model.setSortOption(SortOption.DATE_MODIFIED)
                    fixture.entry.onForgotten()
                    assertEquals(listOf(SortOption.SIZE.name), entered)
                    assertTrue(model.viewModelScope.isActive)
                    first.complete(Unit)
                    runCurrent()
                    assertEquals(listOf(SortOption.SIZE.name, SortOption.DATE_MODIFIED.name), entered)
                    assertEquals(listOf(SortOption.SIZE.name), finished)
                    assertTrue(model.viewModelScope.isActive)
                    second.complete(Unit)
                    runCurrent()
                    assertEquals(entered, finished)
                    assertFalse(model.viewModelScope.isActive)
                } finally {
                    first.complete(Unit)
                    second.complete(Unit)
                    runCurrent()
                }
            }
        }

    @Test
    fun `bubble transfer keeps ownership through queue success and suspended metadata cleanup`() =
        runTest {
            val engine = CompletableDeferred<Unit>()
            val cleanup = CompletableDeferred<Unit>()
            val bubbles = RetirementBubbles()
            bubbles.beforeClear = { cleanup.await() }
            withModel(bubbles = bubbles, beforeEngine = { engine.await() }) { fixture ->
                val model = fixture.entry.viewModel
                val bubble =
                    TransferBubble(
                        "bubble", "Accepted",
                        items = listOf(TransferBubbleItem(1, "bubble", fixture.target, "memory", "target", 0, null)),
                    )
                model.transferBubble(bubble, isMove = true, clearAfter = false)
                fixture.entry.onForgotten()
                runCurrent()
                assertTrue(model.viewModelScope.isActive)
                assertTrue(bubbles.cleared.isEmpty())
                engine.complete(Unit)
                runCurrent()
                assertEquals(listOf("bubble"), bubbles.clearEntered)
                assertTrue(model.viewModelScope.isActive)
                cleanup.complete(Unit)
                runCurrent()
                assertEquals(listOf("bubble"), bubbles.cleared)
                assertFalse(model.viewModelScope.isActive)
            }
        }

    @Test
    fun `bubble metadata admission drains through a returned failure without keeping the store`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val bubbles = RetirementBubbles()
            bubbles.beforeCreate = { release.await() }
            withModel(bubbles = bubbles) { fixture ->
                val model = fixture.entry.viewModel
                model.createBubble("accepted")
                fixture.entry.onForgotten()
                runCurrent()
                assertTrue(model.viewModelScope.isActive)
                release.complete(Unit)
                runCurrent()
                assertEquals(listOf("accepted"), bubbles.created)
                assertFalse(model.viewModelScope.isActive)
            }
        }

    @Test
    fun `idle abandoned store clears immediately and admitted queue work still executes`() =
        runTest {
            withModel { fixture ->
                val model = fixture.entry.viewModel
                model.deleteNodes(listOf(fixture.target))
                assertEquals(1, fixture.queue.queued.value.size)
                fixture.entry.onAbandoned()
                fixture.entry.onForgotten()
                assertFalse(model.viewModelScope.isActive)
                runCurrent()
                assertEquals(listOf("engine"), fixture.completed)
            }
        }

    private suspend fun TestScope.withModel(
        beforeCreate: suspend () -> Unit = {},
        beforeRename: suspend () -> Unit = {},
        beforeEngine: suspend () -> Unit = {},
        sorts: FolderSortMemory? = null,
        bubbles: RetirementBubbles = RetirementBubbles(),
        test: suspend (Fixture) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val memory = InMemoryBackend()
        val target = checkNotNull(memory.createDirectory(memory.rootId, "rename-target").getOrNull()).id
        val completed = mutableListOf<String>()
        val listingRequests = mutableListOf<FileNodeId>()
        val backend =
            object : StorageBackend by memory {
                override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
                    flow {
                        listingRequests += id
                        emitAll(memory.listChildren(id))
                    }

                override suspend fun createDirectory(
                    parent: FileNodeId,
                    name: String,
                ): FileResult<FileNode> {
                    beforeCreate()
                    completed += "create"
                    return memory.createDirectory(parent, name)
                }

                override suspend fun rename(
                    id: FileNodeId,
                    newName: String,
                ): FileResult<FileNode> {
                    beforeRename()
                    completed += "rename"
                    return memory.rename(id, newName)
                }
            }
        val queue =
            OperationQueue(
                engine = { operation, _ ->
                    flow {
                        beforeEngine()
                        completed += "engine"
                        val summary = OperationSummary(operation.sources, emptyList(), emptyList(), null)
                        emit(OperationSnapshot(operation, OperationStatus.Completed(summary)))
                    }
                },
                journal = null,
                scope = backgroundScope,
            )
        val entry =
            SecondaryBrowseModel(
                BrowseViewModel.provideFactory(
                    memory.rootId,
                    GetDirectoryListingUseCase(dispatcher) { backend },
                    GetNodeUseCase(dispatcher) { backend },
                    CreateDirectoryUseCase { backend },
                    RenameFileUseCase { backend },
                    DeleteFileUseCase { backend },
                    queue,
                    bubbles,
                    folderSorts = sorts,
                    startVisible = false,
                ),
            )
        val model = entry.viewModel
        val modelJob = model.viewModelScope.coroutineContext.job
        try {
            test(Fixture(entry, queue, memory.rootId, target, completed, listingRequests))
        } finally {
            try {
                modelJob.cancel()
                modelJob.join()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    private class Fixture(
        val entry: SecondaryBrowseModel,
        val queue: OperationQueue,
        val root: FileNodeId,
        val target: FileNodeId,
        val completed: List<String>,
        val listingRequests: List<FileNodeId>,
    )
}

private class RetirementBubbles : TransferBubbleRepository {
    override val bubbles = MutableStateFlow<List<TransferBubble>>(emptyList())
    val clearEntered = mutableListOf<String>()
    val cleared = mutableListOf<String>()
    val created = mutableListOf<String?>()
    var beforeClear: suspend () -> Unit = {}
    var beforeCreate: suspend () -> Unit = {}

    override suspend fun createBubble(name: String?): Result<TransferBubble> {
        beforeCreate()
        created += name
        return Result.failure(IllegalStateException("metadata unavailable"))
    }

    override suspend fun clearBubble(bubbleId: String): Result<Unit> {
        clearEntered += bubbleId
        beforeClear()
        cleared += bubbleId
        return Result.success(Unit)
    }

    override suspend fun addItemsToBubble(
        bubbleId: String,
        items: List<FileNode>,
    ): Result<Unit> = error("Unexpected bubble mutation")

    override suspend fun removeItemFromBubble(
        bubbleId: String,
        itemId: Long,
    ): Result<Unit> = error("Unexpected bubble mutation")

    override suspend fun deleteBubble(bubbleId: String): Result<Unit> = error("Unexpected bubble mutation")

    override suspend fun getBubble(bubbleId: String): TransferBubble? = error("Unexpected bubble lookup")
}
