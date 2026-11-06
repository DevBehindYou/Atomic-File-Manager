package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import com.devbehindyou.atomicfilemanager.data.operations.FakeOperationJournal
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationSummary
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseDeleteAdmissionTest {
    @Test
    fun `delete and trash are admitted before any Main dispatch or mutation`() =
        runTest {
            withModel { fixture ->
                fixture.model.deleteNodes(listOf(fixture.first))
                fixture.model.deleteNodes(listOf(fixture.second), toTrash = true)

                val accepted = fixture.queue.queued.value
                assertEquals(listOf(OperationType.DELETE, OperationType.TRASH), accepted.map { it.type })
                assertEquals(listOf(listOf(fixture.first), listOf(fixture.second)), accepted.map { it.sources })
                accepted.forEach {
                    assertNull(it.destination)
                    assertEquals(OperationOptions(), it.options)
                }
                assertEquals(2, accepted.map { it.id }.distinct().size)
                assertTrue(fixture.started.isEmpty())
                assertTrue(fixture.journal.rows.isEmpty(), "Admission must not block on journal IO")
            }
        }

    @Test
    fun `clearing the model before Main dispatch preserves accepted delete and trash in the app queue`() =
        runTest {
            withModel { fixture ->
                val modelJob = fixture.model.viewModelScope.coroutineContext.job
                fixture.model.deleteNodes(listOf(fixture.first))
                fixture.model.deleteNodes(listOf(fixture.second), toTrash = true)
                fixture.store.clear()
                assertTrue(modelJob.isCancelled)

                runCurrent()

                assertEquals(listOf(OperationType.DELETE, OperationType.TRASH), fixture.started.map { it.type })
                assertEquals(listOf(listOf(fixture.first), listOf(fixture.second)), fixture.started.map { it.sources })
                assertTrue(fixture.queue.queued.value.isEmpty())
                assertEquals(2, fixture.journal.rows.size)
                assertTrue(fixture.journal.rows.values.all { it.state == OperationJournalState.COMPLETED })
            }
        }

    @Test
    fun `caller list mutation cannot redirect accepted delete or trash targets`() =
        runTest {
            withModel { fixture ->
                val sources = mutableListOf(fixture.first, fixture.second)
                val expected = sources.toList()
                fixture.model.deleteNodes(sources)
                fixture.model.deleteNodes(sources, toTrash = true)
                sources.clear()
                sources += fixture.replacement

                runCurrent()

                assertEquals(listOf(expected, expected), fixture.started.map { it.sources })
                assertEquals(listOf(OperationType.DELETE, OperationType.TRASH), fixture.started.map { it.type })
            }
        }

    @Test
    fun `accepted delete and trash can still be cancelled through the queue before engine work`() =
        runTest {
            withModel { fixture ->
                fixture.model.deleteNodes(listOf(fixture.first))
                fixture.model.deleteNodes(listOf(fixture.second), toTrash = true)
                val accepted = fixture.queue.queued.value
                assertEquals(2, accepted.size)
                accepted.forEach { fixture.queue.cancel(it.id) }
                fixture.store.clear()

                runCurrent()

                assertTrue(fixture.started.isEmpty())
                assertEquals(2, fixture.journal.rows.size)
                assertTrue(fixture.journal.rows.values.all { it.state == OperationJournalState.CANCELLED })
            }
        }

    private suspend fun TestScope.withModel(test: suspend (Fixture) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val memory = InMemoryBackend()
        val journal = FakeOperationJournal()
        val started = mutableListOf<FileOperation>()
        val queue =
            OperationQueue(
                engine = { operation, _ ->
                    flow {
                        assertEquals(OperationJournalState.QUEUED, journal.rows.getValue(operation.id.raw).state)
                        started += operation
                        val summary = OperationSummary(operation.sources, emptyList(), emptyList(), null)
                        emit(OperationSnapshot(operation, OperationStatus.Completed(summary)))
                    }
                },
                journal = journal,
                scope = backgroundScope,
            )
        val store = ViewModelStore()
        val model =
            ViewModelProvider(
                store,
                BrowseViewModel.provideFactory(
                    memory.rootId,
                    GetDirectoryListingUseCase(dispatcher) { memory },
                    GetNodeUseCase(dispatcher) { memory },
                    CreateDirectoryUseCase { memory },
                    RenameFileUseCase { memory },
                    DeleteFileUseCase { memory },
                    queue,
                    DeleteAdmissionBubbles(),
                    startVisible = false,
                ),
            )[BrowseViewModel::class.java]
        try {
            test(Fixture(model, store, queue, journal, started))
        } finally {
            val modelJob = model.viewModelScope.coroutineContext.job
            try {
                store.clear()
                modelJob.join()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    private class Fixture(
        val model: BrowseViewModel,
        val store: ViewModelStore,
        val queue: OperationQueue,
        val journal: FakeOperationJournal,
        val started: List<FileOperation>,
    ) {
        val first = FileNodeId("memory:first")
        val second = FileNodeId("memory:second")
        val replacement = FileNodeId("memory:replacement")
    }
}

private class DeleteAdmissionBubbles : TransferBubbleRepository {
    override val bubbles = MutableStateFlow<List<TransferBubble>>(emptyList())

    override suspend fun createBubble(name: String?): Result<TransferBubble> = error("Unexpected bubble mutation")

    override suspend fun addItemsToBubble(
        bubbleId: String,
        items: List<FileNode>,
    ): Result<Unit> = error("Unexpected bubble mutation")

    override suspend fun removeItemFromBubble(
        bubbleId: String,
        itemId: Long,
    ): Result<Unit> = error("Unexpected bubble mutation")

    override suspend fun clearBubble(bubbleId: String): Result<Unit> = error("Unexpected bubble mutation")

    override suspend fun deleteBubble(bubbleId: String): Result<Unit> = error("Unexpected bubble mutation")

    override suspend fun getBubble(bubbleId: String): TransferBubble? = error("Unexpected bubble lookup")
}
