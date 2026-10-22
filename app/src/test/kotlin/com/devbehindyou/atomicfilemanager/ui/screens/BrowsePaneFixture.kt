package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Handler
import android.os.Looper
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
import com.devbehindyou.atomicfilemanager.domain.repository.FolderSortMemory
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** Real BrowseViewModels and queue, with a listing suspended until its owner cancels it. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowsePaneFixture : ExternalResource() {
    private val memory = InMemoryBackend()
    val root = memory.rootId
    val started = AtomicInteger()
    val cancelled = AtomicInteger()
    val completed = AtomicInteger()
    val listedFolders = ConcurrentLinkedQueue<FileNodeId>()
    var listingItems: List<FileNode>? = null
    var listingUpdates: Flow<FileResult<List<FileNode>>>? = null
    var factories = 0
        private set

    private lateinit var scope: CoroutineScope
    private lateinit var queue: OperationQueue

    private val backend =
        object : StorageBackend by memory {
            override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
                flow {
                    listedFolders += id
                    started.incrementAndGet()
                    try {
                        val updates = listingUpdates
                        if (updates == null) listingItems?.let { emit(FileResult.Success(it)) } else emitAll(updates)
                        awaitCancellation()
                    } finally {
                        cancelled.incrementAndGet()
                    }
                }
        }

    override fun before() {
        // Multi-SDK Robolectric runs can leave the default Main dispatcher bound to an older sandbox's looper.
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher().immediate)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        queue =
            OperationQueue(
                engine = { operation, _ -> flowOf(OperationSnapshot(operation, OperationStatus.Cancelled)) },
                journal = null,
                scope = scope,
            )
        scope.launch { queue.finished.collect { completed.incrementAndGet() } }
    }

    override fun after() {
        scope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
    }

    fun factory(
        folder: FileNodeId,
        startVisible: Boolean = true,
        folderSorts: FolderSortMemory? = null,
    ): ViewModelProvider.Factory {
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
            folderSorts = folderSorts,
            startVisible = startVisible,
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
