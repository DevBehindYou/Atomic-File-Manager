package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.lifecycle.ViewModelProvider
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
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
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.rules.ExternalResource
import java.util.concurrent.atomic.AtomicInteger

/** Real BrowseViewModels and queue, with a listing suspended until its owner cancels it. */
class BrowsePaneFixture : ExternalResource() {
    private val memory = InMemoryBackend()
    val root = memory.rootId
    val started = AtomicInteger()
    val cancelled = AtomicInteger()
    val completed = AtomicInteger()
    var factories = 0
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue =
        OperationQueue(
            engine = { operation, _ -> flowOf(OperationSnapshot(operation, OperationStatus.Cancelled)) },
            journal = null,
            scope = scope,
        )

    private val backend =
        object : StorageBackend by memory {
            override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
                flow {
                    started.incrementAndGet()
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.incrementAndGet()
                    }
                }
        }

    override fun before() {
        scope.launch { queue.finished.collect { completed.incrementAndGet() } }
    }

    override fun after() {
        scope.cancel()
    }

    fun factory(folder: FileNodeId): ViewModelProvider.Factory {
        factories++
        return BrowseViewModel.provideFactory(
            initialFolderId = folder,
            getDirectoryListingUseCase = GetDirectoryListingUseCase { backend },
            getNodeUseCase = GetNodeUseCase { backend },
            createDirectoryUseCase = CreateDirectoryUseCase { backend },
            renameFileUseCase = RenameFileUseCase { backend },
            deleteFileUseCase = DeleteFileUseCase { backend },
            operationQueue = queue,
            transferBubbleRepository = UnusedBubbles(),
        )
    }

    fun finishOperation() {
        queue.enqueue(
            FileOperation(
                OperationId.random(),
                OperationType.DELETE,
                listOf(root),
                null,
                OperationOptions(),
                0L,
            ),
        )
    }
}

private class UnusedBubbles : TransferBubbleRepository {
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
