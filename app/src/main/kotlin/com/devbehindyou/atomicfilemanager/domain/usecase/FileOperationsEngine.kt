package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.Conflict
import com.devbehindyou.atomicfilemanager.domain.model.ConflictChoice
import com.devbehindyou.atomicfilemanager.domain.model.ConflictResolver
import com.devbehindyou.atomicfilemanager.domain.model.FailedItem
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationProgress
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationSummary
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class FileOperationsEngine
    @Inject
    constructor(
        /** Needed for [OperationType.TRASH] and [OperationType.RESTORE_FROM_TRASH]; both fail without it. */
        private val trashManager: TrashManager? = null,
        /** Random-access copies for extracting 7z archives. */
        private val archiveSpool: SeekableSpool? = null,
        /** Monotonic time keeps throttling independent of wall-clock adjustments. */
        private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
        // Last, so `FileOperationsEngine { backend }` keeps working.
        private val backendSelector: (FileNodeId) -> StorageBackend,
    ) {
        companion object {
            /**
             * Bounded streaming buffer (plan 16.2 H8). 256 KB cuts per-read overhead on SD, USB and
             * network streams to a quarter of 64 KB while staying small enough to allocate per copy.
             * `FileChannel.transferTo` is not used: the source is hashed while it is copied, and a
             * kernel-side copy would force a second full read of the source to verify it.
             */
            const val BUFFER_SIZE = 256 * 1024
            const val PROGRESS_THROTTLE_MS = 250L // 4 Hz progress reporting
        }

        /**
         * Executes [operation] and emits [OperationSnapshot] progress updates.
         *
         * With [CollisionPolicy.ASK], each name clash emits [OperationStatus.AwaitingInput] and
         * waits for [conflictResolver]. Without a resolver ASK keeps both, as it always has.
         */
        fun execute(
            operation: FileOperation,
            conflictResolver: ConflictResolver? = null,
        ): Flow<OperationSnapshot> =
            flow {
                emit(OperationSnapshot(operation, OperationStatus.Queued))

                val succeeded = mutableListOf<FileNodeId>()
                val skipped = mutableListOf<FileNodeId>()
                val failed = mutableListOf<FailedItem>()

                var itemsDone = 0
                var bytesDone = 0L
                val itemsTotal = operation.sources.size
                var totalBytesEstimate = 0L

                // Initial scan for size estimation
                for (sourceId in operation.sources) {
                    val backend = backendSelector(sourceId)
                    when (val nodeResult = backend.getNode(sourceId)) {
                        is FileResult.Success -> totalBytesEstimate += nodeResult.value.size
                        is FileResult.Failure -> { /* size unknown, best effort */ }
                    }
                }

                var lastProgressTime = clockMillis()
                var progressEmitted = false
                var bytesSinceLastSpeedCheck = 0L
                var currentSpeed = 0L

                // "Apply to all remaining" from an earlier conflict in this operation.
                var rememberedChoice: ConflictChoice? = null

                suspend fun decideCollision(
                    source: FileNode,
                    destParent: FileNodeId,
                    destBackend: StorageBackend,
                ): CollisionPolicy {
                    val policy = operation.options.collisionPolicy
                    if (policy != CollisionPolicy.ASK) return policy
                    val resolver = conflictResolver ?: return CollisionPolicy.RENAME_AUTO
                    val existing = findChild(destBackend, destParent, source.name)
                    // Pasting a file into its own folder duplicates it; there is nothing to ask.
                    if (existing == null || existing.id == source.id) return CollisionPolicy.RENAME_AUTO
                    val remembered = rememberedChoice
                    if (remembered != null && (remembered != ConflictChoice.REPLACE || !source.isDirectory)) {
                        return remembered.toPolicy()
                    }
                    val conflict = Conflict(source, existing)
                    emit(OperationSnapshot(operation, OperationStatus.AwaitingInput(conflict)))
                    val decision = resolver.resolve(operation, conflict)
                    // Replacing a folder would need a merge, which does not exist yet; keep both instead.
                    val choice =
                        if (decision.choice == ConflictChoice.REPLACE && source.isDirectory) {
                            ConflictChoice.KEEP_BOTH
                        } else {
                            decision.choice
                        }
                    if (decision.applyToAll) rememberedChoice = decision.choice
                    return choice.toPolicy()
                }

                suspend fun emitProgress(
                    currentName: String?,
                    force: Boolean = false,
                ) {
                    val now = clockMillis()
                    val timeDelta = now - lastProgressTime
                    if (progressEmitted && !force && timeDelta < PROGRESS_THROTTLE_MS) return
                    currentSpeed = if (timeDelta > 0) (bytesSinceLastSpeedCheck * 1000L) / timeDelta else 0L
                    lastProgressTime = now
                    bytesSinceLastSpeedCheck = 0L
                    progressEmitted = true

                    val eta =
                        if (currentSpeed > 0L && totalBytesEstimate > bytesDone) {
                            ((totalBytesEstimate - bytesDone) * 1000L) / currentSpeed
                        } else {
                            null
                        }

                    emit(
                        OperationSnapshot(
                            operation,
                            OperationStatus.Running(
                                OperationProgress(
                                    itemsDone = itemsDone,
                                    itemsTotal = itemsTotal,
                                    bytesDone = bytesDone,
                                    bytesTotal = totalBytesEstimate,
                                    currentName = currentName,
                                    bytesPerSecond = currentSpeed,
                                    etaMillis = eta,
                                ),
                            ),
                        ),
                    )
                }

                try {
                    when (operation.type) {
                        OperationType.DELETE -> {
                            for (sourceId in operation.sources) {
                                if (!coroutineContext.isActive) throw CancellationException()
                                val backend = backendSelector(sourceId)
                                val node = (backend.getNode(sourceId) as? FileResult.Success)?.value
                                val name = node?.name ?: sourceId.raw
                                emitProgress(name)

                                when (val result = backend.delete(sourceId)) {
                                    is FileResult.Success -> {
                                        succeeded.add(sourceId)
                                        itemsDone++
                                    }
                                    is FileResult.Failure -> {
                                        failed.add(FailedItem(sourceId, name, result.error))
                                    }
                                }
                            }
                        }
                        OperationType.COPY, OperationType.MOVE -> {
                            val destParent =
                                requireNotNull(operation.destination) {
                                    "Destination must be provided for ${operation.type}"
                                }
                            val isMove = operation.type == OperationType.MOVE

                            for (sourceId in operation.sources) {
                                if (!coroutineContext.isActive) throw CancellationException()
                                val sourceBackend = backendSelector(sourceId)
                                val destBackend = backendSelector(destParent)

                                val nodeResult = sourceBackend.getNode(sourceId)
                                if (nodeResult is FileResult.Failure) {
                                    failed.add(FailedItem(sourceId, sourceId.raw, nodeResult.error))
                                    continue
                                }
                                val sourceNode = (nodeResult as FileResult.Success).value
                                emitProgress(sourceNode.name)

                                val copyResult =
                                    copyOrMoveItem(
                                        sourceNode = sourceNode,
                                        destParentId = destParent,
                                        sourceBackend = sourceBackend,
                                        destBackend = destBackend,
                                        isMove = isMove,
                                        collisionPolicy = operation.options.collisionPolicy,
                                        onBytesCopied = { copied ->
                                            bytesDone += copied
                                            bytesSinceLastSpeedCheck += copied
                                        },
                                        emitProgress = { name -> emitProgress(name) },
                                        resolveCollision = { node, parent, target ->
                                            decideCollision(node, parent, target)
                                        },
                                    )

                                when (copyResult) {
                                    is ItemResult.Success -> {
                                        succeeded.add(sourceId)
                                        itemsDone++
                                    }
                                    is ItemResult.Skipped -> {
                                        skipped.add(sourceId)
                                        itemsDone++
                                    }
                                    is ItemResult.Failure -> {
                                        failed.add(FailedItem(sourceId, sourceNode.name, copyResult.error))
                                    }
                                }
                            }
                        }
                        OperationType.TRASH -> {
                            for (sourceId in operation.sources) {
                                if (!coroutineContext.isActive) throw CancellationException()
                                val node = (backendSelector(sourceId).getNode(sourceId) as? FileResult.Success)?.value
                                val name = node?.name ?: sourceId.raw.substringAfterLast('/')
                                emitProgress(name)
                                val result =
                                    when {
                                        node == null -> FileResult.Failure(FileError.FileNotFound(name))
                                        trashManager == null -> FileResult.Failure(FileError.InvalidDestination(name))
                                        else -> trashManager.trash(node, operation.id.raw)
                                    }
                                when (result) {
                                    is FileResult.Success -> {
                                        succeeded.add(sourceId)
                                        itemsDone++
                                    }
                                    is FileResult.Failure -> failed.add(FailedItem(sourceId, name, result.error))
                                }
                            }
                        }
                        OperationType.RESTORE_FROM_TRASH -> {
                            for (sourceId in operation.sources) {
                                if (!coroutineContext.isActive) throw CancellationException()
                                val entry = trashManager?.entryFor(sourceId)
                                val name = entry?.name ?: sourceId.raw.substringAfterLast('/')
                                emitProgress(name)
                                val result =
                                    if (entry == null || trashManager == null) {
                                        FileResult.Failure(FileError.FileNotFound(name))
                                    } else {
                                        trashManager.restore(entry)
                                    }
                                when (result) {
                                    is FileResult.Success -> {
                                        succeeded.add(sourceId)
                                        itemsDone++
                                    }
                                    is FileResult.Failure -> failed.add(FailedItem(sourceId, name, result.error))
                                }
                            }
                        }
                        OperationType.RENAME -> {
                            for (sourceId in operation.sources) {
                                val backend = backendSelector(sourceId)
                                val newName = operation.destination?.raw ?: continue
                                when (val result = backend.rename(sourceId, newName)) {
                                    is FileResult.Success -> {
                                        succeeded.add(sourceId)
                                        itemsDone++
                                    }
                                    is FileResult.Failure -> {
                                        failed.add(FailedItem(sourceId, newName, result.error))
                                    }
                                }
                            }
                        }
                        OperationType.COMPRESS -> {
                            val destParent =
                                requireNotNull(operation.destination) {
                                    "Destination must be provided for COMPRESS"
                                }
                            val destBackend = backendSelector(destParent)
                            val baseName =
                                if (operation.sources.size == 1) {
                                    val firstSourceNode =
                                        (
                                            backendSelector(
                                                operation.sources[0],
                                            ).getNode(operation.sources[0]) as? FileResult.Success
                                        )?.value
                                    val rawName = firstSourceNode?.name ?: "archive"
                                    if (rawName.endsWith(".zip", ignoreCase = true)) rawName else "$rawName.zip"
                                } else {
                                    "Archive.zip"
                                }
                            val zipName = resolveUniqueName(destBackend, destParent, baseName)
                            emitProgress(zipName)

                            val compressResult =
                                ArchiveOperationsHelper.compressItems(
                                    sources = operation.sources,
                                    destParentId = destParent,
                                    zipName = zipName,
                                    compressionLevel = operation.options.compressionLevel,
                                    destBackend = destBackend,
                                    backendSelector = backendSelector,
                                    onBytesCopied = { copied ->
                                        bytesDone += copied
                                        bytesSinceLastSpeedCheck += copied
                                    },
                                    emitProgress = { name -> emitProgress(name) },
                                )

                            when (compressResult) {
                                is ItemResult.Success -> {
                                    succeeded.addAll(operation.sources)
                                    itemsDone += operation.sources.size
                                }
                                is ItemResult.Skipped -> {
                                    skipped.addAll(operation.sources)
                                    itemsDone += operation.sources.size
                                }
                                is ItemResult.Failure -> {
                                    failed.add(FailedItem(operation.sources.first(), zipName, compressResult.error))
                                }
                            }
                        }
                        OperationType.EXTRACT -> {
                            val destParent =
                                requireNotNull(operation.destination) {
                                    "Destination must be provided for EXTRACT"
                                }
                            val destBackend = backendSelector(destParent)

                            for (sourceId in operation.sources) {
                                if (!coroutineContext.isActive) throw CancellationException()
                                val sourceBackend = backendSelector(sourceId)
                                val sourceNode = (sourceBackend.getNode(sourceId) as? FileResult.Success)?.value
                                val name = sourceNode?.name ?: sourceId.raw
                                emitProgress(name)

                                val extractResult =
                                    ArchiveOperationsHelper.extractArchive(
                                        sourceId = sourceId,
                                        destParentId = destParent,
                                        sourceBackend = sourceBackend,
                                        destBackend = destBackend,
                                        onBytesCopied = { copied ->
                                            bytesDone += copied
                                            bytesSinceLastSpeedCheck += copied
                                        },
                                        emitProgress = { entryName -> emitProgress(entryName) },
                                        spool = archiveSpool,
                                    )

                                when (extractResult) {
                                    is ItemResult.Success -> {
                                        succeeded.add(sourceId)
                                        itemsDone++
                                    }
                                    is ItemResult.Skipped -> {
                                        skipped.add(sourceId)
                                        itemsDone++
                                    }
                                    is ItemResult.Failure -> {
                                        failed.add(FailedItem(sourceId, name, extractResult.error))
                                    }
                                }
                            }
                        }
                        OperationType.HIDE_GALLERY,
                        OperationType.UNHIDE_GALLERY,
                        OperationType.FAST_OBSCURE,
                        OperationType.RESTORE_OBSCURE,
                        OperationType.MOVE_TO_PRIVATE,
                        -> {
                            val destParent = operation.destination
                            if (destParent != null) {
                                for (sourceId in operation.sources) {
                                    if (!coroutineContext.isActive) throw CancellationException()
                                    val sourceBackend = backendSelector(sourceId)
                                    val destBackend = backendSelector(destParent)
                                    val nodeResult = sourceBackend.getNode(sourceId)
                                    if (nodeResult is FileResult.Failure) {
                                        failed.add(FailedItem(sourceId, sourceId.raw, nodeResult.error))
                                        continue
                                    }
                                    val sourceNode = (nodeResult as FileResult.Success).value
                                    emitProgress(sourceNode.name)
                                    val copyResult =
                                        copyOrMoveItem(
                                            sourceNode = sourceNode,
                                            destParentId = destParent,
                                            sourceBackend = sourceBackend,
                                            destBackend = destBackend,
                                            isMove = true,
                                            collisionPolicy = operation.options.collisionPolicy,
                                            onBytesCopied = { copied ->
                                                bytesDone += copied
                                                bytesSinceLastSpeedCheck += copied
                                            },
                                            emitProgress = { name -> emitProgress(name) },
                                            resolveCollision = { node, parent, target ->
                                                decideCollision(node, parent, target)
                                            },
                                        )
                                    when (copyResult) {
                                        is ItemResult.Success -> {
                                            succeeded.add(sourceId)
                                            itemsDone++
                                        }
                                        is ItemResult.Skipped -> {
                                            skipped.add(sourceId)
                                            itemsDone++
                                        }
                                        is ItemResult.Failure -> {
                                            failed.add(FailedItem(sourceId, sourceNode.name, copyResult.error))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (progressEmitted) emitProgress(null, force = true)
                    val summary =
                        OperationSummary(
                            succeeded = succeeded,
                            skipped = skipped,
                            failed = failed,
                            undoToken = null,
                        )

                    if (failed.isEmpty()) {
                        emit(OperationSnapshot(operation, OperationStatus.Completed(summary)))
                    } else if (succeeded.isNotEmpty() || skipped.isNotEmpty()) {
                        emit(OperationSnapshot(operation, OperationStatus.PartiallyCompleted(summary)))
                    } else {
                        emit(
                            OperationSnapshot(
                                operation,
                                OperationStatus.Failed(
                                    failed.firstOrNull()?.error ?: FileError.IoFailure("Operation failed"),
                                    summary,
                                ),
                            ),
                        )
                    }
                } catch (e: CancellationException) {
                    emit(OperationSnapshot(operation, OperationStatus.Cancelled))
                    throw e
                } catch (e: Exception) {
                    val summary = OperationSummary(succeeded, skipped, failed, null)
                    emit(
                        OperationSnapshot(
                            operation,
                            OperationStatus.Failed(FileError.IoFailure(e.message ?: "Unknown error"), summary),
                        ),
                    )
                }
            }.flowOn(Dispatchers.IO)

        internal sealed interface ItemResult {
            data object Success : ItemResult

            data object Skipped : ItemResult

            data class Failure(val error: FileError) : ItemResult
        }

        private suspend fun copyOrMoveItem(
            sourceNode: FileNode,
            destParentId: FileNodeId,
            sourceBackend: StorageBackend,
            destBackend: StorageBackend,
            isMove: Boolean,
            collisionPolicy: CollisionPolicy,
            onBytesCopied: (Long) -> Unit,
            emitProgress: suspend (String) -> Unit,
            resolveCollision: suspend (FileNode, FileNodeId, StorageBackend) -> CollisionPolicy,
            visited: MutableSet<FileNodeId> = mutableSetOf(),
        ): ItemResult {
            coroutineContext.ensureActive()
            val isRoot = visited.isEmpty()
            if (!visited.add(sourceNode.id)) return ItemResult.Failure(FileError.InvalidDestination(sourceNode.name))
            if (sourceBackend === destBackend) {
                if (isRoot && sourceNode.isDirectory) {
                    when (val ancestry = sourceBackend.isSameOrDescendant(sourceNode.id, destParentId)) {
                        is FileResult.Failure -> return ItemResult.Failure(ancestry.error)
                        is FileResult.Success ->
                            if (ancestry.value) {
                                return ItemResult.Failure(FileError.InvalidDestination(sourceNode.name))
                            }
                    }
                }
                if (sourceNode.parentId == destParentId && collisionPolicy == CollisionPolicy.OVERWRITE) {
                    return ItemResult.Failure(FileError.InvalidDestination(sourceNode.name))
                }
                var ancestor: FileNodeId? = destParentId
                val ancestors = mutableSetOf<FileNodeId>()
                while (ancestor != null && ancestors.add(ancestor)) {
                    if (ancestor == sourceNode.id) {
                        return ItemResult.Failure(
                            FileError.InvalidDestination(sourceNode.name),
                        )
                    }
                    ancestor = (destBackend.getNode(ancestor) as? FileResult.Success)?.value?.parentId
                }
            }
            // Check read capability
            if (!sourceBackend.capabilities.canRead) {
                return ItemResult.Failure(FileError.AccessDenied("Source is unreadable"))
            }

            // Fast path for same-backend move without conflict when atomic move is supported
            if (isMove && sourceBackend == destBackend && destBackend.capabilities.supportsAtomicMove) {
                val exists = destBackend.exists(destParentId, sourceNode.name)
                if (!exists) {
                    val moveRes = destBackend.moveWithin(sourceNode.id, destParentId)
                    if (moveRes is FileResult.Success) return ItemResult.Success
                    // A filesystem backend can span volumes. Fall back to a verified copy.
                }
            }

            // Check write capability on destination
            if (!destBackend.capabilities.canWrite && !destBackend.capabilities.canCreate) {
                return ItemResult.Failure(FileError.AccessDenied("Destination is read-only"))
            }

            // Resolving destination name based on collision policy (ASK asks here, only on a real clash)
            val exists = destBackend.exists(destParentId, sourceNode.name)
            val policy = if (exists) resolveCollision(sourceNode, destParentId, destBackend) else collisionPolicy
            val targetName =
                when {
                    !exists -> sourceNode.name
                    policy == CollisionPolicy.SKIP -> return ItemResult.Skipped
                    policy == CollisionPolicy.OVERWRITE -> sourceNode.name
                    else -> resolveUniqueName(destBackend, destParentId, sourceNode.name)
                }

            if (sourceNode.isDirectory) {
                // Create destination directory
                val createDirResult = destBackend.createDirectory(destParentId, targetName)
                val newDirNode =
                    when (createDirResult) {
                        is FileResult.Success -> createDirResult.value
                        is FileResult.Failure -> return ItemResult.Failure(createDirResult.error)
                    }

                // Copy/move children recursively
                var allChildrenSucceeded = true
                try {
                    sourceBackend.listChildren(sourceNode.id).collect { chunkResult ->
                        when (chunkResult) {
                            is FileResult.Success -> {
                                for (child in chunkResult.value) {
                                    if (!coroutineContext.isActive) throw CancellationException()
                                    emitProgress(child.name)
                                    val childRes =
                                        copyOrMoveItem(
                                            sourceNode = child,
                                            destParentId = newDirNode.id,
                                            sourceBackend = sourceBackend,
                                            destBackend = destBackend,
                                            isMove = false,
                                            collisionPolicy = collisionPolicy,
                                            onBytesCopied = onBytesCopied,
                                            emitProgress = emitProgress,
                                            resolveCollision = resolveCollision,
                                            visited = visited,
                                        )
                                    if (childRes !is ItemResult.Success) {
                                        allChildrenSucceeded = false
                                    }
                                }
                            }
                            is FileResult.Failure -> {
                                allChildrenSucceeded = false
                            }
                        }
                    }

                    if (!allChildrenSucceeded) {
                        withContext(NonCancellable) { destBackend.delete(newDirNode.id) }
                        return ItemResult.Failure(FileError.IoFailure("Failed copying all children"))
                    }
                } catch (error: Exception) {
                    withContext(NonCancellable) { destBackend.delete(newDirNode.id) }
                    throw error
                }
                if (isMove) {
                    val deleted = sourceBackend.delete(sourceNode.id)
                    if (deleted is FileResult.Failure) return ItemResult.Failure(deleted.error)
                }
                return ItemResult.Success
            }
            return VerifiedFileTransfer.copy(
                sourceNode,
                destParentId,
                targetName,
                sourceBackend,
                destBackend,
                isMove,
                onBytesCopied,
                emitProgress,
            )
        }

        /** The child of [parent] called [name], or null; only read when a clash was found. */
        private suspend fun findChild(
            backend: StorageBackend,
            parent: FileNodeId,
            name: String,
        ): FileNode? {
            var found: FileNode? = null
            backend.listChildren(parent).takeWhile { found == null }.collect { chunk ->
                if (chunk is FileResult.Success) found = chunk.value.firstOrNull { it.name == name }
            }
            return found
        }

        private fun ConflictChoice.toPolicy(): CollisionPolicy =
            when (this) {
                ConflictChoice.REPLACE -> CollisionPolicy.OVERWRITE
                ConflictChoice.KEEP_BOTH -> CollisionPolicy.KEEP_BOTH
                ConflictChoice.SKIP -> CollisionPolicy.SKIP
            }

        private suspend fun resolveUniqueName(
            backend: StorageBackend,
            parent: FileNodeId,
            name: String,
        ): String {
            val dotIndex = name.lastIndexOf('.')
            val hasExtension = dotIndex > 0 && dotIndex < name.length - 1
            val base = if (hasExtension) name.substring(0, dotIndex) else name
            val ext = if (hasExtension) name.substring(dotIndex) else ""
            var counter = 1
            var candidate = "$base ($counter)$ext"
            while (backend.exists(parent, candidate)) {
                counter++
                candidate = "$base ($counter)$ext"
            }
            return candidate
        }
    }
