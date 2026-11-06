package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.TrashEntry
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.repository.TrashStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * App-managed Trash (ALL_IN_ONE_PLAN.md 1.1). Each volume has a hidden trash folder at its root
 * (`.Atomic File Manager/Trash`), so moving to Trash is a same-volume rename: instant, no copy.
 * Every item gets its own holder folder named by its entry id and keeps its own name inside it.
 *
 * Only storage that can rename within a volume and has a known volume root can use it; for
 * anything else [canTrash] is false and the caller offers permanent delete with a warning.
 */
class TrashManager(
    private val backendFor: (FileNodeId) -> StorageBackend,
    private val store: TrashStore,
    /** The volume root holding [FileNodeId], or null when it has none (SAF, network, app storage). */
    private val volumeRootOf: (FileNodeId) -> FileNodeId?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** True when [node] can go to the Trash rather than being deleted for good. */
    suspend fun canTrash(node: FileNode): Boolean {
        val backend = backendFor(node.id)
        if (!backend.capabilities.canRename || !backend.capabilities.supportsAtomicMove) return false
        val root = volumeRootOf(node.id) ?: return false
        return node.id != root && !isInsideTrash(node.id, root)
    }

    /** Moves [node] into the Trash and records it under [operationId]. */
    suspend fun trash(
        node: FileNode,
        operationId: String,
    ): FileResult<TrashEntry> {
        val root = volumeRootOf(node.id)
        val parent = node.parentId
        if (root == null || parent == null || !canTrash(node)) {
            return FileResult.Failure(FileError.InvalidDestination(node.name))
        }
        val backend = backendFor(node.id)
        val trashFolder =
            when (val folder = trashFolder(backend, root)) {
                is FileResult.Success -> folder.value
                is FileResult.Failure -> return folder
            }
        val id = newId()
        val holder =
            when (val created = backend.createDirectory(trashFolder, id)) {
                is FileResult.Success -> created.value
                is FileResult.Failure -> return created
            }
        // Same-volume metadata changes and their record form one in-process critical section.
        // Cancellation resumes after it; this is not a process-death transaction.
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            moveToTrash(backend, node, parent, root, holder, id, operationId)
        }
    }

    private suspend fun moveToTrash(
        backend: StorageBackend,
        node: FileNode,
        parent: FileNodeId,
        root: FileNodeId,
        holder: FileNode,
        id: String,
        operationId: String,
    ): FileResult<TrashEntry> {
        val moved =
            when (val result = backend.moveWithin(node.id, holder.id)) {
                is FileResult.Success -> result.value
                is FileResult.Failure -> {
                    withContext(NonCancellable) { backend.delete(holder.id) }
                    return result
                }
            }
        val entry =
            TrashEntry(
                id = id,
                name = node.name,
                isDirectory = node.isDirectory,
                size = if (node.isDirectory) -1 else node.size,
                originalParent = parent,
                trashedId = moved.id,
                holderId = holder.id,
                volumeRoot = root,
                deletedAt = clock(),
                operationId = operationId,
            )
        return try {
            store.insert(entry)
            FileResult.Success(entry)
        } catch (error: Exception) {
            // A store may commit before reporting failure/cancellation. Keep that valid record.
            runCatching { reconcileFailedInsert(backend, entry) }
            if (error is CancellationException) throw error
            FileResult.Failure(FileError.IoFailure(node.name))
        }
    }

    private suspend fun reconcileFailedInsert(
        backend: StorageBackend,
        entry: TrashEntry,
    ) {
        val recorded = runCatching { store.byTrashedId(entry.trashedId) }
        if (recorded.isFailure) {
            // An unavailable lookup cannot establish that insertion did not commit. Keep the
            // current location and retry its record rather than invalidate a committed record.
            runCatching { store.insert(entry) }
            return
        }
        if (recorded.getOrNull() != null) return
        if (backend.moveWithin(entry.trashedId, entry.originalParent) is FileResult.Success) {
            backend.delete(entry.holderId)
        } else {
            // A newly occupied original name can prevent rollback. Retry the record, never
            // delete the holder containing the only copy. Persistent failure of both storage
            // and the database still requires reconciliation; no crash-atomic claim is made.
            runCatching { store.insert(entry) }
        }
    }

    /**
     * Puts [entry] back in its original folder, recreating that folder if it is gone. A name that
     * is taken again gets a number ("notes (1).txt"), so nothing in the folder is overwritten.
     */
    suspend fun restore(entry: TrashEntry): FileResult<FileNode> {
        val backend = backendFor(entry.trashedId)
        val parent =
            when (val folder = originalFolder(backend, entry)) {
                is FileResult.Success -> folder.value
                is FileResult.Failure -> return folder
            }
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) { restoreInto(backend, entry, parent) }
    }

    private suspend fun restoreInto(
        backend: StorageBackend,
        entry: TrashEntry,
        parent: FileNodeId,
    ): FileResult<FileNode> {
        var current = entry
        if (backend.exists(parent, entry.name)) {
            val free = freeName(backend, parent, entry.name)
            val renamed =
                when (val renamed = backend.rename(current.trashedId, free)) {
                    is FileResult.Success -> renamed.value
                    is FileResult.Failure -> return renamed
                }
            current = entry.copy(name = free, trashedId = renamed.id)
            try {
                // Rename can change opaque IDs. Persist that identity before attempting the
                // move so a denied destination leaves a retryable entry, including after restart.
                store.insert(current)
            } catch (error: Exception) {
                runCatching { reconcileFailedRename(backend, entry, current) }
                if (error is CancellationException) throw error
                return FileResult.Failure(FileError.IoFailure(entry.name))
            }
        }
        val restored =
            when (val moved = backend.moveWithin(current.trashedId, parent)) {
                is FileResult.Success -> moved.value
                is FileResult.Failure -> return moved
            }
        withContext(NonCancellable) {
            backend.delete(entry.holderId)
            store.delete(entry.id)
        }
        return FileResult.Success(restored)
    }

    private suspend fun reconcileFailedRename(
        backend: StorageBackend,
        original: TrashEntry,
        renamed: TrashEntry,
    ) {
        // A committed update is already safe to retry; don't undo its path behind the store.
        val recorded = runCatching { store.byTrashedId(renamed.trashedId) }
        if (recorded.isFailure) {
            runCatching { store.insert(renamed) }
            return
        }
        if (recorded.getOrNull() == renamed) return
        when (val rollback = backend.rename(renamed.trashedId, original.name)) {
            is FileResult.Success -> {
                if (rollback.value.id != original.trashedId) {
                    runCatching { store.insert(original.copy(trashedId = rollback.value.id)) }
                }
            }
            is FileResult.Failure -> runCatching { store.insert(renamed) }
        }
    }

    /** The entry for an item currently in the Trash. */
    suspend fun entryFor(trashedId: FileNodeId): TrashEntry? = store.byTrashedId(trashedId)

    /** Deletes [entry] for good. */
    suspend fun deleteForever(entry: TrashEntry): FileResult<Unit> {
        val backend = backendFor(entry.holderId)
        val result = backend.delete(entry.holderId)
        // Already gone (deleted outside the app) counts as done; the record would otherwise linger.
        if (result is FileResult.Success || (result as FileResult.Failure).error is FileError.FileNotFound) {
            store.delete(entry.id)
            return FileResult.Success(Unit)
        }
        return result
    }

    /** Deletes everything trashed before [cutoff]; returns how many entries went. */
    suspend fun purgeDeletedBefore(cutoff: Long): Int =
        store.deletedBefore(cutoff).count { deleteForever(it) is FileResult.Success }

    private fun isInsideTrash(
        id: FileNodeId,
        root: FileNodeId,
    ): Boolean = id.raw.startsWith("${root.raw}/$APP_FOLDER/$TRASH_FOLDER")

    private suspend fun trashFolder(
        backend: StorageBackend,
        root: FileNodeId,
    ): FileResult<FileNodeId> {
        val app =
            when (val folder = childFolder(backend, root, APP_FOLDER)) {
                is FileResult.Success -> folder.value
                is FileResult.Failure -> return folder
            }
        val trash =
            when (val folder = childFolder(backend, app, TRASH_FOLDER)) {
                is FileResult.Success -> folder.value
                is FileResult.Failure -> return folder
            }
        // Keeps trashed photos and videos out of gallery apps.
        if (!backend.exists(trash, NO_MEDIA)) {
            (backend.openOutput(trash, NO_MEDIA, null) as? FileResult.Success)?.value?.let { output ->
                output.stream().close()
                output.sync()
                output.toNode()
            }
        }
        return FileResult.Success(trash)
    }

    private suspend fun originalFolder(
        backend: StorageBackend,
        entry: TrashEntry,
    ): FileResult<FileNodeId> {
        if (backend.getNode(entry.originalParent) is FileResult.Success) return FileResult.Success(entry.originalParent)
        // The folder was deleted meanwhile: recreate it below the volume root, one level at a time.
        val prefix = "${entry.volumeRoot.raw}/"
        if (!entry.originalParent.raw.startsWith(prefix)) {
            return FileResult.Failure(FileError.FileNotFound(entry.originalParent.raw.substringAfterLast('/')))
        }
        var folder = entry.volumeRoot
        for (segment in entry.originalParent.raw.removePrefix(prefix).split('/').filter { it.isNotEmpty() }) {
            folder =
                when (val next = childFolder(backend, folder, segment)) {
                    is FileResult.Success -> next.value
                    is FileResult.Failure -> return next
                }
        }
        return FileResult.Success(folder)
    }

    /** The folder [name] in [parent], created when missing. */
    private suspend fun childFolder(
        backend: StorageBackend,
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNodeId> {
        if (!backend.exists(parent, name)) {
            return when (val created = backend.createDirectory(parent, name)) {
                is FileResult.Success -> FileResult.Success(created.value.id)
                is FileResult.Failure -> created
            }
        }
        var found: FileNode? = null
        backend.listChildren(parent).takeWhile { found == null }.collect { chunk ->
            if (chunk is FileResult.Success) found = chunk.value.firstOrNull { it.name == name }
        }
        val existing = found ?: return FileResult.Failure(FileError.FileNotFound(name))
        return if (existing.isDirectory) {
            FileResult.Success(existing.id)
        } else {
            FileResult.Failure(FileError.InvalidDestination(name))
        }
    }

    private suspend fun freeName(
        backend: StorageBackend,
        parent: FileNodeId,
        name: String,
    ): String {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var number = 1
        while (backend.exists(parent, "$stem ($number)$extension")) number++
        return "$stem ($number)$extension"
    }

    companion object {
        const val APP_FOLDER = ".Atomic File Manager"
        const val TRASH_FOLDER = "Trash"
        private const val NO_MEDIA = ".nomedia"
    }
}
