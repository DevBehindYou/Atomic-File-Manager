package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
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
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrimaryBrowseStoreRegistryTest {
    @Test
    fun `all saved sessions reconcile lazily and closing an inactive tab clears only its child store`() =
        runTest {
            withRegistry { fixture ->
                var tabs = BrowseTabsState.single(fixture.root.raw)
                repeat(3) { tabs = tabs.open(fixture.root.raw) }
                fixture.registry.reconcile(tabs)
                assertEquals(0, fixture.constructed.size)
                val survivor = fixture.model(tabs.active)
                assertEquals(1, fixture.constructed.size)
                val inactive = tabs.tabs.first()
                val old = fixture.model(inactive)
                old.onSearchQueryChanged("closed")
                val restored = BrowseTabsState.decode(tabs.encode(), fixture.root.raw)
                fixture.registry.reconcile(restored)
                assertSame(survivor, fixture.model(restored.active))
                assertSame(old, fixture.model(inactive))
                assertEquals(2, fixture.constructed.size)

                fixture.registry.reconcile(restored.close(inactive.id))

                assertNull(fixture.registry.existing(inactive.stateKey, fixture.root))
                assertFalse(old.viewModelScope.isActive)
                assertTrue(survivor.viewModelScope.isActive)
                assertSame(survivor, fixture.model(restored.active))
                assertThrows(IllegalStateException::class.java) { fixture.model(inactive) }
                assertEquals(2, fixture.constructed.size)
            }
        }

    @Test
    fun `temporary hiding and retained owner keep state while same root explicit opens reset only navigation`() =
        runTest {
            withRegistry { fixture ->
                val tabs = BrowseTabsState.single(fixture.root.raw)
                fixture.registry.reconcile(tabs)
                val model = fixture.model(tabs.active)
                model.onVisible(0)
                fixture.awaitFolder(model, fixture.root)
                model.toggleSelection(fixture.source.id)
                model.copySelected()
                val clipboard = checkNotNull(model.uiState.value.clipboard)
                model.navigateTo(fixture.child)
                fixture.awaitFolder(model, fixture.child.id)
                model.onSearchQueryChanged("remember")
                model.onHidden()

                // Home and pushed routes keep the same saved tabs; a recreated view gets the retained owner.
                fixture.registry.reconcile(tabs)
                val retained = PrimaryBrowseStoreRegistry.obtain(fixture.owner)
                assertSame(fixture.registry, retained)
                assertSame(model, fixture.model(tabs.active))
                model.onVisible(0)
                fixture.awaitFolder(model, fixture.child.id)
                assertSame(clipboard, model.uiState.value.clipboard)
                assertEquals("remember", model.uiState.value.searchQuery)

                val reopened = tabs.openInActive(fixture.root.raw)
                fixture.registry.reconcile(reopened)
                assertSame(model, fixture.model(reopened.active))
                model.onVisible(reopened.active.openRequest)
                fixture.awaitFolder(model, fixture.root)
                assertSame(clipboard, model.uiState.value.clipboard)
                assertEquals("remember", model.uiState.value.searchQuery)
                assertFalse(model.navigateBack())
            }
        }

    @Test
    fun `closing and reusing a numeric tab id never revives its retired session`() =
        runTest {
            withRegistry { fixture ->
                val opened = BrowseTabsState.single(fixture.root.raw).open(fixture.root.raw)
                fixture.registry.reconcile(opened)
                val oldTab = opened.active
                val old = fixture.model(oldTab)
                old.onSearchQueryChanged("closed")
                val closed = opened.close(oldTab.id)
                fixture.registry.reconcile(closed)
                val reopened = closed.open(fixture.root.raw)
                fixture.registry.reconcile(reopened)
                val fresh = fixture.model(reopened.active)

                assertEquals(oldTab.id, reopened.active.id)
                assertNotSame(old, fresh)
                assertFalse(old.viewModelScope.isActive)
                assertEquals("", fresh.uiState.value.searchQuery)
                assertThrows(IllegalStateException::class.java) { fixture.model(oldTab) }
            }
        }

    @Test
    fun `held accepted work on replaced A cannot clear fresh A after two root updates before dispatch`() =
        runTest {
            val create = CompletableDeferred<Unit>()
            val rename = CompletableDeferred<Unit>()
            withRegistry(beforeCreate = { create.await() }, beforeRename = { rename.await() }) { fixture ->
                var saved = BrowseTabsState.single(fixture.root.raw)
                fixture.registry.reconcile(saved)
                val old = fixture.model(saved.active)
                old.createFolder("accepted")
                old.rename(fixture.source.id, "renamed")
                old.onSearchQueryChanged("old")
                fun openRoot(root: FileNodeId) {
                    val next = saved.openInActive(root.raw)
                    fixture.registry.reconcile(next)
                    saved = next
                }
                openRoot(fixture.child.id)
                assertNull(fixture.registry.existing("", fixture.root))
                assertTrue(old.viewModelScope.isActive)
                assertThrows(IllegalStateException::class.java) {
                    fixture.model(BrowseTab(0, fixture.root.raw))
                }
                openRoot(fixture.root)
                val fresh = fixture.model(saved.active)
                assertNotSame(old, fresh)
                runCurrent()
                create.complete(Unit)
                runCurrent()
                assertTrue(old.viewModelScope.isActive)
                rename.complete(Unit)
                runCurrent()

                assertFalse(old.viewModelScope.isActive)
                assertTrue(fresh.viewModelScope.isActive)
                assertSame(fresh, fixture.registry.existing("", fixture.root))
                assertEquals("", fresh.uiState.value.searchQuery)
                assertEquals(listOf("create", "rename"), fixture.completed)
                old.onVisible(99)
                runCurrent()
                assertTrue(fixture.listed.isEmpty())
            }
        }

    @Test
    fun `actual retained owner clear drains accepted work clears idle siblings and leaves app queue independent`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            withRegistry(beforeCreate = { release.await() }) { fixture ->
                val tabs = BrowseTabsState.single(fixture.root.raw).open(fixture.child.id.raw)
                fixture.registry.reconcile(tabs)
                val busy = fixture.model(tabs.tabs.first())
                val idle = fixture.model(tabs.active)
                busy.createFolder("accepted")
                busy.deleteNodes(listOf(fixture.source.id))
                fixture.owner.viewModelStore.clear()

                assertTrue(busy.viewModelScope.isActive)
                assertFalse(idle.viewModelScope.isActive)
                assertNull(fixture.registry.existing("", fixture.root))
                assertThrows(IllegalStateException::class.java) { fixture.model(tabs.active) }
                fixture.registry.reconcile(tabs)
                runCurrent()
                assertEquals(listOf("engine"), fixture.completed)
                release.complete(Unit)
                runCurrent()
                assertEquals(listOf("engine", "create"), fixture.completed)
                assertFalse(busy.viewModelScope.isActive)
                assertNull(fixture.registry.existing("", fixture.root))
            }
        }

    @Test
    fun `ordered noncancellable sort saves keep their retired child store until the last save finishes`() =
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
            withRegistry(sorts = sorts) { fixture ->
                val tabs = BrowseTabsState.single(fixture.root.raw)
                fixture.registry.reconcile(tabs)
                val model = fixture.model(tabs.active)
                try {
                    model.setSortOption(SortOption.SIZE)
                    model.setSortOption(SortOption.DATE_MODIFIED)
                    fixture.registry.reconcile(tabs.openInActive(fixture.child.id.raw))
                    assertTrue(model.viewModelScope.isActive)
                    assertEquals(listOf(SortOption.SIZE.name), entered)
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

    private suspend fun TestScope.withRegistry(
        beforeCreate: suspend () -> Unit = {},
        beforeRename: suspend () -> Unit = {},
        sorts: FolderSortMemory? = null,
        test: suspend (Fixture) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val memory = InMemoryBackend()
        val child = checkNotNull(memory.createDirectory(memory.rootId, "Child").getOrNull())
        val source = checkNotNull(memory.createDirectory(memory.rootId, "Source").getOrNull())
        val completed = mutableListOf<String>()
        val listed = mutableListOf<FileNodeId>()
        val backend =
            object : StorageBackend by memory {
                override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
                    flow {
                        listed += id
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
                        completed += "engine"
                        emit(OperationSnapshot(operation, OperationStatus.Cancelled))
                    }
                },
                journal = null,
                scope = backgroundScope,
            )
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        val constructed = mutableListOf<BrowseViewModel>()
        val registry = PrimaryBrowseStoreRegistry.obtain(owner)
        val factory: (FileNodeId) -> ViewModelProvider.Factory = { root ->
            val delegate =
                BrowseViewModel.provideFactory(
                    root,
                    GetDirectoryListingUseCase(dispatcher) { backend },
                    GetNodeUseCase(dispatcher) { backend },
                    CreateDirectoryUseCase { backend },
                    RenameFileUseCase { backend },
                    DeleteFileUseCase { backend },
                    queue,
                    RegistryBubbles(),
                    folderSorts = sorts,
                    startVisible = false,
                )
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    delegate.create(modelClass).also { constructed += it as BrowseViewModel }
            }
        }
        try {
            test(Fixture(owner, registry, memory.rootId, source, child, constructed, completed, listed, factory))
        } finally {
            try {
                owner.viewModelStore.clear()
                constructed.forEach { it.viewModelScope.coroutineContext.job.cancel() }
                constructed.forEach { it.viewModelScope.coroutineContext.job.join() }
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    private class Fixture(
        val owner: ViewModelStoreOwner,
        val registry: PrimaryBrowseStoreRegistry,
        val root: FileNodeId,
        val source: FileNode,
        val child: FileNode,
        val constructed: List<BrowseViewModel>,
        val completed: List<String>,
        val listed: List<FileNodeId>,
        private val factory: (FileNodeId) -> ViewModelProvider.Factory,
    ) {
        fun model(tab: BrowseTab): BrowseViewModel {
            val root = FileNodeId(tab.folderRaw)
            return registry.model(tab.stateKey, root, factory(root))
        }

        suspend fun awaitFolder(
            model: BrowseViewModel,
            folder: FileNodeId,
        ) {
            model.uiState.first { it.currentFolderId == folder && !it.isLoading && !it.isRefreshing }
        }
    }
}

private class RegistryBubbles : TransferBubbleRepository {
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
