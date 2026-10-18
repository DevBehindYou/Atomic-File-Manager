package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.model.TrashEntry
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.repository.TrashStore
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class TrashManagerTest {
    private class FakeTrashStore : TrashStore {
        val entries = linkedMapOf<String, TrashEntry>()
        var failInsert = false
        var failLookup = false
        var beforeInsert: suspend () -> Unit = {}
        var afterInsert: suspend () -> Unit = {}

        override fun observeAll(): Flow<List<TrashEntry>> = MutableStateFlow(entries.values.toList())

        override suspend fun insert(entry: TrashEntry) {
            beforeInsert()
            check(!failInsert) { "disk full" }
            entries[entry.id] = entry
            afterInsert()
        }

        override suspend fun delete(id: String) {
            entries.remove(id)
        }

        override suspend fun byTrashedId(trashedId: FileNodeId): TrashEntry? {
            check(!failLookup) { "lookup unavailable" }
            return entries.values.firstOrNull { it.trashedId == trashedId }
        }

        override suspend fun byOperation(operationId: String) = entries.values.filter { it.operationId == operationId }

        override suspend fun deletedBefore(cutoff: Long) = entries.values.filter { it.deletedAt < cutoff }
    }

    private val backend = InMemoryBackend()
    private val store = FakeTrashStore()
    private var now = 1_000L
    private var ids = 0
    private val trash =
        TrashManager(
            backendFor = { backend },
            store = store,
            volumeRootOf = { backend.rootId },
            clock = { now },
            newId = { "entry-${ids++}" },
        )

    private suspend fun node(id: FileNodeId): FileNode = (backend.getNode(id) as FileResult.Success).value

    private suspend fun children(parent: FileNodeId): List<FileNode> {
        val all = mutableListOf<FileNode>()
        backend.listChildren(parent).collect { if (it is FileResult.Success) all += it.value }
        return all
    }

    private suspend fun child(
        parent: FileNodeId,
        name: String,
    ): FileNode = children(parent).single { it.name == name }

    private suspend fun text(id: FileNodeId): String {
        val out = ByteArrayOutputStream()
        (backend.openInput(id) as FileResult.Success).value.stream().use { it.copyTo(out) }
        return out.toString(Charsets.UTF_8)
    }

    private suspend fun folder(name: String): FileNodeId =
        (backend.createDirectory(backend.rootId, name) as FileResult.Success).value.id

    @Test
    fun `trash moves the item into its own holder and records where it came from`() =
        runTest {
            val docs = folder("Docs")
            val file = backend.putFile(docs, "notes.txt", "hello".toByteArray())

            val entry = (trash.trash(node(file), "op-1") as FileResult.Success).value

            assertTrue(children(docs).isEmpty())
            val trashFolder = child(child(backend.rootId, TrashManager.APP_FOLDER).id, TrashManager.TRASH_FOLDER)
            assertEquals(setOf("entry-0", ".nomedia"), children(trashFolder.id).map { it.name }.toSet())
            assertEquals("notes.txt", node(entry.trashedId).name)
            assertEquals(docs, entry.originalParent)
            assertEquals(1_000L, entry.deletedAt)
            assertEquals(entry, store.byOperation("op-1").single())
        }

    @Test
    fun `restore puts the same bytes back in the original folder and clears the entry`() =
        runTest {
            val docs = folder("Docs")
            val file = backend.putFile(docs, "notes.txt", "hello".toByteArray())
            val entry = (trash.trash(node(file), "op") as FileResult.Success).value

            val restored = (trash.restore(entry) as FileResult.Success).value

            assertEquals(docs, restored.parentId)
            assertEquals("hello", text(child(docs, "notes.txt").id))
            assertTrue(store.entries.isEmpty())
            val trashFolder = child(child(backend.rootId, TrashManager.APP_FOLDER).id, TrashManager.TRASH_FOLDER)
            assertEquals(listOf(".nomedia"), children(trashFolder.id).map { it.name })
        }

    @Test
    fun `restore never overwrites a file that took the name meanwhile`() =
        runTest {
            val docs = folder("Docs")
            val file = backend.putFile(docs, "notes.txt", "old".toByteArray())
            val entry = (trash.trash(node(file), "op") as FileResult.Success).value
            backend.putFile(docs, "notes.txt", "new".toByteArray())

            trash.restore(entry)

            assertEquals("new", text(child(docs, "notes.txt").id))
            assertEquals("old", text(child(docs, "notes (1).txt").id))
        }

    @Test
    fun `restore without a reachable original folder keeps the item in the trash`() =
        runTest {
            val docs = folder("Docs")
            val file = backend.putFile(docs, "notes.txt", "x".toByteArray())
            val entry = (trash.trash(node(file), "op") as FileResult.Success).value
            backend.delete(docs)

            assertTrue(trash.restore(entry) is FileResult.Failure)
            assertEquals(entry, store.entries[entry.id])
            assertEquals("notes.txt", node(entry.trashedId).name)
        }

    @Test
    fun `a failed record puts the item straight back`() =
        runTest {
            val docs = folder("Docs")
            val file = backend.putFile(docs, "notes.txt", "x".toByteArray())
            store.failInsert = true

            assertTrue(trash.trash(node(file), "op") is FileResult.Failure)
            assertEquals(listOf("notes.txt"), children(docs).map { it.name })
        }

    @Test
    fun `cancellation after the move finishes recording the hidden item`() =
        runTest {
            val docs = folder("Docs")
            val file = node(backend.putFile(docs, "notes.txt", "safe".toByteArray()))
            lateinit var operation: Job
            store.beforeInsert = {
                operation.cancel()
                yield()
            }

            operation = launch { trash.trash(file, "cancelled") }
            operation.join()

            assertTrue(operation.isCancelled)
            assertTrue(children(docs).isEmpty())
            val recorded = store.byOperation("cancelled").single()
            assertEquals("safe", text(recorded.trashedId))
        }

    @Test
    fun `insert cancellation before commit rolls the file back and propagates`() =
        runTest {
            val docs = folder("Docs")
            val file = node(backend.putFile(docs, "notes.txt", "safe".toByteArray()))
            store.beforeInsert = { throw CancellationException("insert cancelled") }

            val operation = launch { trash.trash(file, "cancelled") }
            operation.join()

            assertTrue(operation.isCancelled)
            assertTrue(store.entries.isEmpty())
            assertEquals("safe", text(child(docs, "notes.txt").id))
        }

    @Test
    fun `insert cancellation after commit retains the valid record and propagates`() =
        runTest {
            val docs = folder("Docs")
            val file = node(backend.putFile(docs, "notes.txt", "safe".toByteArray()))
            store.afterInsert = { throw CancellationException("committed then cancelled") }

            val operation = launch { trash.trash(file, "cancelled") }
            operation.join()

            assertTrue(operation.isCancelled)
            val recorded = store.byOperation("cancelled").single()
            assertEquals("safe", text(recorded.trashedId))
            assertTrue(children(docs).isEmpty())
        }

    @Test
    fun `failed lookup after committed trash insertion does not invalidate the record`() =
        runTest {
            val docs = folder("Docs")
            val file = node(backend.putFile(docs, "notes.txt", "safe".toByteArray()))
            store.failLookup = true
            store.afterInsert = { error("committed then failed") }

            assertTrue(trash.trash(file, "op") is FileResult.Failure)

            val recorded = store.byOperation("op").single()
            assertEquals("safe", text(recorded.trashedId))
            assertTrue(children(docs).isEmpty())
        }

    /** Model a path-based backend whose rename returns a different identity. */
    private fun changingIdentityBackend(denyMoveTo: () -> FileNodeId?): StorageBackend =
        object : StorageBackend by backend {
            override suspend fun rename(
                id: FileNodeId,
                newName: String,
            ): FileResult<FileNode> {
                val original = node(id)
                val renamed = backend.rename(id, newName)
                if (renamed is FileResult.Failure) return renamed
                val bytes = text(id).toByteArray()
                backend.delete(id)
                return backend.getNode(backend.putFile(requireNotNull(original.parentId), newName, bytes))
            }

            override suspend fun moveWithin(
                id: FileNodeId,
                newParent: FileNodeId,
            ): FileResult<FileNode> =
                if (newParent == denyMoveTo()) {
                    FileResult.Failure(FileError.AccessDenied("restore destination"))
                } else {
                    backend.moveWithin(id, newParent)
                }
        }

    @Test
    fun `failed restore after collision rename persists the new identity for retry`() =
        runTest {
            val docs = folder("Docs")
            val file = node(backend.putFile(docs, "notes.txt", "old".toByteArray()))
            var denied: FileNodeId? = null
            val changingBackend = changingIdentityBackend { denied }
            val manager = TrashManager({ changingBackend }, store, { backend.rootId })
            val entry = (manager.trash(file, "op") as FileResult.Success).value
            backend.putFile(docs, "notes.txt", "new".toByteArray())
            denied = docs

            assertTrue(manager.restore(entry) is FileResult.Failure)

            val retry = store.entries.getValue(entry.id)
            assertTrue(retry.trashedId != entry.trashedId)
            assertEquals("notes (1).txt", retry.name)
            assertEquals("old", text(retry.trashedId))
            denied = null
            val restarted = TrashManager({ changingBackend }, store, { backend.rootId })
            assertTrue(restarted.restore(retry) is FileResult.Success)
            assertEquals("old", text(child(docs, "notes (1).txt").id))
            assertEquals("new", text(child(docs, "notes.txt").id))
            assertTrue(store.entries.isEmpty())
        }

    @Test
    fun `rename record failure rolls back and persists a changed rollback identity`() =
        runTest {
            val docs = folder("Docs")
            val file = node(backend.putFile(docs, "notes.txt", "old".toByteArray()))
            val changingBackend = changingIdentityBackend { null }
            val manager = TrashManager({ changingBackend }, store, { backend.rootId })
            val entry = (manager.trash(file, "op") as FileResult.Success).value
            backend.putFile(docs, "notes.txt", "new".toByteArray())
            var failNext = true
            store.beforeInsert = {
                if (failNext) {
                    failNext = false
                    error("one failed update")
                }
            }

            assertTrue(manager.restore(entry) is FileResult.Failure)

            val retry = store.entries.getValue(entry.id)
            assertEquals("notes.txt", retry.name)
            assertEquals("old", text(retry.trashedId))
            assertTrue(manager.restore(retry) is FileResult.Success)
            assertEquals("old", text(child(docs, "notes (1).txt").id))
            assertEquals("new", text(child(docs, "notes.txt").id))
        }

    @Test
    fun `failed lookup after committed rename update keeps its identity retryable`() =
        runTest {
            val docs = folder("Docs")
            val file = node(backend.putFile(docs, "notes.txt", "old".toByteArray()))
            val changingBackend = changingIdentityBackend { null }
            val manager = TrashManager({ changingBackend }, store, { backend.rootId })
            val entry = (manager.trash(file, "op") as FileResult.Success).value
            backend.putFile(docs, "notes.txt", "new".toByteArray())
            store.failLookup = true
            store.afterInsert = { error("committed then failed") }

            assertTrue(manager.restore(entry) is FileResult.Failure)

            val retry = store.entries.getValue(entry.id)
            assertEquals("notes (1).txt", retry.name)
            assertEquals("old", text(retry.trashedId))
            store.failLookup = false
            store.afterInsert = {}
            assertTrue(manager.restore(retry) is FileResult.Success)
            assertEquals("old", text(child(docs, "notes (1).txt").id))
            assertEquals("new", text(child(docs, "notes.txt").id))
        }

    @Test
    fun `delete forever and purge only remove what they should`() =
        runTest {
            val docs = folder("Docs")
            val old =
                (
                    trash.trash(
                        node(backend.putFile(docs, "old.txt", "o".toByteArray())),
                        "a",
                    ) as FileResult.Success
                ).value
            now = 50_000L
            val recent =
                (
                    trash.trash(
                        node(backend.putFile(docs, "new.txt", "n".toByteArray())),
                        "b",
                    ) as FileResult.Success
                ).value

            assertEquals(1, trash.purgeDeletedBefore(10_000L))

            assertNull(store.entries[old.id])
            assertTrue(backend.getNode(old.holderId) is FileResult.Failure)
            assertEquals(recent, store.entries[recent.id])
        }

    @Test
    fun `storage without a volume root has no trash`() =
        runTest {
            val noTrash = TrashManager({ backend }, store, volumeRootOf = { null })
            val file = backend.putFile(backend.rootId, "a.txt", "x".toByteArray())

            assertFalse(noTrash.canTrash(node(file)))
            assertFalse(trash.canTrash(node(backend.rootId)))
            assertTrue(trash.canTrash(node(file)))
        }

    @Test
    fun `trash and restore run as queue operations through the engine`() =
        runTest {
            val engine = FileOperationsEngine(backendSelector = { backend }, trashManager = trash)
            val docs = folder("Docs")
            val a = backend.putFile(docs, "a.txt", "a".toByteArray())
            val b = backend.putFile(docs, "b.txt", "b".toByteArray())

            fun op(
                type: OperationType,
                sources: List<FileNodeId>,
            ) = FileOperation(OperationId.random(), type, sources, null, OperationOptions(), 0L)

            val trashed = engine.execute(op(OperationType.TRASH, listOf(a, b))).toList().last().status
            assertTrue(trashed is OperationStatus.Completed, "$trashed")
            assertTrue(children(docs).isEmpty())

            val restoreIds = store.entries.values.map { it.trashedId }
            val restored = engine.execute(op(OperationType.RESTORE_FROM_TRASH, restoreIds)).toList().last().status
            assertTrue(restored is OperationStatus.Completed, "$restored")
            assertEquals(setOf("a.txt", "b.txt"), children(docs).map { it.name }.toSet())
        }
}
