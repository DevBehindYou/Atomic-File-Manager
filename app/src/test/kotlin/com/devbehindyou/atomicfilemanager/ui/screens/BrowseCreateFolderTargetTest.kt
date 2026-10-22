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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseCreateFolderTargetTest {
    @Test
    fun `accepted create keeps the visible parent when pending navigation runs before create work`() =
        runTest {
            withModel { fixture ->
                val model = fixture.model
                fixture.awaitFolder(fixture.memory.rootId)
                // Navigation is queued, but its worker has not yet changed the visible folder.
                model.navigateTo(fixture.destination)
                assertEquals(fixture.memory.rootId, model.uiState.value.currentFolderId)
                model.createFolder("Accepted")
                assertTrue(fixture.createParents.isEmpty())

                fixture.awaitCreatedFolder()
                fixture.awaitFolder(fixture.destination.id)
                assertEquals(listOf(fixture.memory.rootId), fixture.createParents)
                assertEquals(listOf("Accepted", "Destination"), fixture.children(fixture.memory.rootId).sorted())
                assertTrue(fixture.children(fixture.destination.id).isEmpty())
                assertEquals(fixture.destination.id, model.uiState.value.currentFolderId)
            }
        }

    @Test
    fun `late create success refreshes the current pane without navigating or reloading its original parent`() =
        runTest {
            val entered = CompletableDeferred<FileNodeId>()
            val release = CompletableDeferred<Unit>()
            withModel(
                beforeCreate = { parent ->
                    entered.complete(parent)
                    release.await()
                },
            ) { fixture ->
                fixture.awaitFolder(fixture.memory.rootId)
                fixture.model.createFolder("Accepted")
                assertEquals(fixture.memory.rootId, entered.await())
                fixture.model.navigateTo(fixture.destination)
                fixture.awaitFolder(fixture.destination.id)
                val originalReads = fixture.reads.value.count { it == fixture.memory.rootId }
                val currentReads = fixture.reads.value.count { it == fixture.destination.id }

                release.complete(Unit)
                fixture.awaitCreatedFolder()
                fixture.reads.first { requests -> requests.count { it == fixture.destination.id } > currentReads }
                fixture.awaitFolder(fixture.destination.id)
                assertEquals(originalReads, fixture.reads.value.count { it == fixture.memory.rootId })
                assertEquals(listOf("Accepted", "Destination"), fixture.children(fixture.memory.rootId).sorted())
                assertTrue(fixture.children(fixture.destination.id).isEmpty())
                assertEquals(fixture.destination.id, fixture.model.uiState.value.currentFolderId)
                assertTrue(fixture.model.navigateBack())
                fixture.awaitFolder(fixture.memory.rootId)
            }
        }

    private suspend fun TestScope.withModel(
        beforeCreate: suspend (FileNodeId) -> Unit = {},
        test: suspend (Fixture) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val memory = InMemoryBackend()
        val destination = checkNotNull(memory.createDirectory(memory.rootId, "Destination").getOrNull())
        val reads = MutableStateFlow<List<FileNodeId>>(emptyList())
        val createParents = mutableListOf<FileNodeId>()
        val created = CompletableDeferred<FileNode>()
        val backend =
            object : StorageBackend by memory {
                override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
                    flow {
                        reads.update { it + id }
                        emitAll(memory.listChildren(id))
                    }

                override suspend fun createDirectory(
                    parent: FileNodeId,
                    name: String,
                ): FileResult<FileNode> {
                    createParents += parent
                    beforeCreate(parent)
                    val result = memory.createDirectory(parent, name)
                    created.complete(checkNotNull(result.getOrNull()))
                    return result
                }
            }
        val queue =
            OperationQueue(
                engine = { operation, _ -> flowOf(OperationSnapshot(operation, OperationStatus.Cancelled)) },
                journal = null,
                scope = backgroundScope,
            )
        val model =
            BrowseViewModel(
                memory.rootId,
                GetDirectoryListingUseCase(dispatcher) { backend },
                GetNodeUseCase(dispatcher) { backend },
                CreateDirectoryUseCase { backend },
                RenameFileUseCase { backend },
                DeleteFileUseCase { backend },
                queue,
                CreateTargetBubbles(),
            )
        try {
            test(Fixture(model, memory, destination, reads, createParents, created))
        } finally {
            // Keep test Main installed until Default filtering hands cancellation back to its owner.
            try {
                model.viewModelScope.coroutineContext.job.cancelAndJoin()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    private class Fixture(
        val model: BrowseViewModel,
        val memory: InMemoryBackend,
        val destination: FileNode,
        val reads: MutableStateFlow<List<FileNodeId>>,
        val createParents: List<FileNodeId>,
        private val created: CompletableDeferred<FileNode>,
    ) {
        suspend fun awaitFolder(id: FileNodeId) {
            model.uiState.first { it.currentFolderId == id && !it.isLoading && !it.isRefreshing }
        }

        suspend fun awaitCreatedFolder(): FileNode = created.await()

        suspend fun children(id: FileNodeId): List<String> =
            checkNotNull(memory.listChildren(id).first().getOrNull()).map { it.name }
    }
}

private class CreateTargetBubbles : TransferBubbleRepository {
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
