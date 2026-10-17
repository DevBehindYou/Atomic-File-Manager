package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.OutputTarget
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

internal object ArchiveOperationsHelper {
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_EXTRACT_BYTES = 10L * 1024 * 1024 * 1024 // 10 GB safe ceiling
    private const val MAX_COMPRESS_DEPTH = 256

    suspend fun compressItems(
        sources: List<FileNodeId>,
        destParentId: FileNodeId,
        zipName: String,
        compressionLevel: Int?,
        destBackend: StorageBackend,
        backendSelector: (FileNodeId) -> StorageBackend,
        onBytesCopied: (Long) -> Unit,
        emitProgress: suspend (String) -> Unit,
    ): FileOperationsEngine.ItemResult {
        if (destBackend.exists(destParentId, zipName)) {
            return FileOperationsEngine.ItemResult.Failure(FileError.FileAlreadyExists(zipName))
        }
        if (!destBackend.capabilities.canRename) {
            return FileOperationsEngine.ItemResult.Failure(FileError.InvalidDestination(zipName))
        }
        // Resolve every source and reject self-inclusion before the staging file exists.
        val preparedSources =
            try {
                sources.map { sourceId ->
                    coroutineContext.ensureActive()
                    val sourceBackend = backendSelector(sourceId)
                    val node = sourceBackend.getNode(sourceId).requireSuccess()
                    if (node.isDirectory && sourceBackend === destBackend) {
                        checkCompressionDestination(node, destParentId, destBackend)
                    }
                    node to sourceBackend
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                return FileOperationsEngine.ItemResult.Failure(
                    (error as? ArchiveFailure)?.error ?: FileError.IoFailure(zipName),
                )
            }
        val stagingName = InterruptedTransferCleanup.stagingName()
        val outRes = destBackend.openOutput(destParentId, stagingName, "application/zip")
        val outputTarget =
            when (outRes) {
                is FileResult.Success -> outRes.value
                is FileResult.Failure -> return FileOperationsEngine.ItemResult.Failure(outRes.error)
            }

        try {
            outputTarget.stream().use { outStream ->
                ZipOutputStream(BufferedOutputStream(outStream)).use { zipOut ->
                    if (compressionLevel != null) {
                        zipOut.setLevel(compressionLevel)
                    }

                    for ((node, sourceBackend) in preparedSources) {
                        if (!coroutineContext.isActive) throw CancellationException()
                        emitProgress(node.name)

                        if (node.isDirectory) {
                            compressDirectoryRecursive(
                                dirNode = node,
                                relativePrefix = node.name,
                                sourceBackend = sourceBackend,
                                zipOut = zipOut,
                                onBytesCopied = onBytesCopied,
                                emitProgress = emitProgress,
                                visited = mutableSetOf(),
                                depth = 0,
                                stagingName = stagingName,
                            )
                        } else {
                            compressSingleFile(
                                fileNode = node,
                                entryPath = node.name,
                                sourceBackend = sourceBackend,
                                zipOut = zipOut,
                                onBytesCopied = onBytesCopied,
                            )
                        }
                    }
                    zipOut.finish()
                    zipOut.flush()
                }
            }
            outputTarget.sync()
            val staged = outputTarget.toNode().requireSuccess()
            coroutineContext.ensureActive()
            if (destBackend.exists(destParentId, zipName)) throw ArchiveFailure(FileError.FileAlreadyExists(zipName))
            destBackend.rename(staged.id, zipName).requireSuccess()
            return FileOperationsEngine.ItemResult.Success
        } catch (e: Exception) {
            discardAfterFailure(outputTarget, e)
            if (e is CancellationException) throw e
            return FileOperationsEngine.ItemResult.Failure(
                (e as? ArchiveFailure)?.error ?: FileError.IoFailure(zipName),
            )
        }
    }

    private suspend fun checkCompressionDestination(
        source: FileNode,
        destination: FileNodeId,
        backend: StorageBackend,
    ) {
        if (backend.isSameOrDescendant(source.id, destination).requireSuccess()) {
            throw ArchiveFailure(FileError.InvalidDestination(source.name))
        }
        var ancestor: FileNodeId? = destination
        val ancestors = mutableSetOf<FileNodeId>()
        while (ancestor != null) {
            coroutineContext.ensureActive()
            if (ancestor == source.id || !ancestors.add(ancestor)) {
                throw ArchiveFailure(FileError.InvalidDestination(source.name))
            }
            ancestor = backend.getNode(ancestor).requireSuccess().parentId
        }
    }

    private suspend fun compressSingleFile(
        fileNode: FileNode,
        entryPath: String,
        sourceBackend: StorageBackend,
        zipOut: ZipOutputStream,
        onBytesCopied: (Long) -> Unit,
    ) {
        val input = sourceBackend.openInput(fileNode.id).requireSuccess()

        val entry = ZipEntry(entryPath)
        entry.time = fileNode.modifiedAt
        zipOut.putNextEntry(entry)

        var copied = 0L
        input.stream().use { inStream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                if (!coroutineContext.isActive) throw CancellationException()
                zipOut.write(buffer, 0, bytesRead)
                copied += bytesRead
                onBytesCopied(bytesRead.toLong())
            }
        }
        if (fileNode.size >= 0 && copied != fileNode.size) {
            throw ArchiveFailure(FileError.IncompleteWrite(fileNode.name, copied, fileNode.size))
        }
        zipOut.closeEntry()
    }

    private suspend fun compressDirectoryRecursive(
        dirNode: FileNode,
        relativePrefix: String,
        sourceBackend: StorageBackend,
        zipOut: ZipOutputStream,
        onBytesCopied: (Long) -> Unit,
        emitProgress: suspend (String) -> Unit,
        visited: MutableSet<FileNodeId>,
        depth: Int,
        stagingName: String,
    ) {
        coroutineContext.ensureActive()
        // Providers may return cycles; a depth bound also catches aliases with changing IDs.
        if (depth >= MAX_COMPRESS_DEPTH || !visited.add(dirNode.id)) {
            throw ArchiveFailure(FileError.InvalidDestination(dirNode.name))
        }
        val dirEntry = ZipEntry("$relativePrefix/")
        dirEntry.time = dirNode.modifiedAt
        zipOut.putNextEntry(dirEntry)
        zipOut.closeEntry()

        sourceBackend.listChildren(dirNode.id).collect { chunkResult ->
            for (child in chunkResult.requireSuccess()) {
                if (!coroutineContext.isActive) throw CancellationException()
                // A directory alias can expose the output even when the root ancestry differs.
                if (child.name == stagingName) throw ArchiveFailure(FileError.InvalidDestination(child.name))
                emitProgress(child.name)
                val childPath = "$relativePrefix/${child.name}"
                if (child.isDirectory) {
                    compressDirectoryRecursive(
                        dirNode = child,
                        relativePrefix = childPath,
                        sourceBackend = sourceBackend,
                        zipOut = zipOut,
                        onBytesCopied = onBytesCopied,
                        emitProgress = emitProgress,
                        visited = visited,
                        depth = depth + 1,
                        stagingName = stagingName,
                    )
                } else {
                    compressSingleFile(
                        fileNode = child,
                        entryPath = childPath,
                        sourceBackend = sourceBackend,
                        zipOut = zipOut,
                        onBytesCopied = onBytesCopied,
                    )
                }
            }
        }
    }

    suspend fun extractArchive(
        sourceId: FileNodeId,
        destParentId: FileNodeId,
        sourceBackend: StorageBackend,
        destBackend: StorageBackend,
        onBytesCopied: (Long) -> Unit,
        emitProgress: suspend (String) -> Unit,
        spool: SeekableSpool? = null,
    ): FileOperationsEngine.ItemResult {
        val inRes = sourceBackend.openInput(sourceId)
        val inProvider =
            when (inRes) {
                is FileResult.Success -> inRes.value
                is FileResult.Failure -> return FileOperationsEngine.ItemResult.Failure(inRes.error)
            }

        val node =
            when (val result = sourceBackend.getNode(sourceId)) {
                is FileResult.Success -> result.value
                is FileResult.Failure -> return FileOperationsEngine.ItemResult.Failure(result.error)
            }
        val archiveName = node.name
        // Anything not recognised by name is read as ZIP, as before.
        val kind = ArchiveFormats.kindOf(archiveName) ?: ArchiveFormats.Kind.ZIP
        // Given once from the preview, never stored; gone after this extract whatever happens.
        val password = ArchivePasswords.take(sourceId)
        var totalExtractedBytes = 0L
        var entryCount = 0

        try {
            inProvider.stream().use { rawIn ->
                ArchiveFormats.open(
                    kind,
                    archiveName,
                    rawIn,
                    node.size,
                    password = password,
                    spool = spool,
                ).use {
                        archive ->
                    var entry = archive.next()
                    while (entry != null) {
                        if (!coroutineContext.isActive) throw CancellationException()
                        val normalized = entry.path.replace('\\', '/').trimStart('/')

                        if (escapesTarget(entry.path)) {
                            return FileOperationsEngine.ItemResult.Failure(
                                FileError.SuspiciousArchive(entry.path, "Path traversal sequence detected"),
                            )
                        }
                        if (++entryCount > ArchiveFormats.MAX_ENTRIES) {
                            return FileOperationsEngine.ItemResult.Failure(
                                FileError.SuspiciousArchive(
                                    archiveName,
                                    "More than ${ArchiveFormats.MAX_ENTRIES} entries",
                                ),
                            )
                        }

                        emitProgress(normalized)

                        if (entry.isDirectory) {
                            ensureDirectoryPath(
                                destBackend,
                                destParentId,
                                normalized.split('/').filter { it.isNotEmpty() },
                            )
                        } else {
                            val segments = normalized.split('/').filter { it.isNotEmpty() }
                            val parentDirId =
                                if (segments.size > 1) {
                                    ensureDirectoryPath(destBackend, destParentId, segments.dropLast(1))
                                } else {
                                    destParentId
                                }
                            val fileName = segments.last()
                            val entryPath = entry.path

                            extractFile(archive, entry, destBackend, parentDirId, fileName) { read ->
                                totalExtractedBytes += read
                                if (totalExtractedBytes > MAX_EXTRACT_BYTES) {
                                    throw ArchiveFailure(
                                        FileError.SuspiciousArchive(
                                            entryPath,
                                            "Archive bomb detected: exceeded max size limit",
                                        ),
                                    )
                                }
                                onBytesCopied(read)
                            }
                        }

                        entry = archive.next()
                    }
                }
            }
            return FileOperationsEngine.ItemResult.Success
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is ArchiveFailure) return FileOperationsEngine.ItemResult.Failure(e.error)
            if (e is ArchiveWrongPassword) {
                return FileOperationsEngine.ItemResult.Failure(FileError.UnsupportedFormat(WRONG_PASSWORD))
            }
            if (e is ArchivePasswordRequired) {
                return FileOperationsEngine.ItemResult.Failure(FileError.UnsupportedFormat(PASSWORD_PROTECTED))
            }
            return FileOperationsEngine.ItemResult.Failure(FileError.CorruptedArchive(sourceId.raw))
        }
    }

    /** Publication relies on backend rename semantics; the interface has no atomic no-replace contract. */
    private suspend fun extractFile(
        archive: ArchiveStream,
        entry: ArchiveHeader,
        backend: StorageBackend,
        parent: FileNodeId,
        name: String,
        onBytes: (Long) -> Unit,
    ) {
        if (backend.exists(parent, name)) throw ArchiveFailure(FileError.FileAlreadyExists(name))
        if (!backend.capabilities.canRename) throw ArchiveFailure(FileError.InvalidDestination(name))
        val output = backend.openOutput(parent, InterruptedTransferCleanup.stagingName(), null).requireSuccess()
        try {
            var written = 0L
            output.stream().use { stream ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    if (!coroutineContext.isActive) throw CancellationException()
                    val count = archive.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count == 0) continue
                    onBytes(count.toLong())
                    stream.write(buffer, 0, count)
                    written += count
                }
                stream.flush()
            }
            if (entry.size >= 0 && written != entry.size) {
                throw ArchiveFailure(FileError.IncompleteWrite(name, written, entry.size))
            }
            output.sync()
            val staged = output.toNode().requireSuccess()
            coroutineContext.ensureActive()
            if (backend.exists(parent, name)) throw ArchiveFailure(FileError.FileAlreadyExists(name))
            backend.rename(staged.id, name).requireSuccess()
        } catch (error: Exception) {
            discardAfterFailure(output, error)
            throw error
        }
    }

    private suspend fun ensureDirectoryPath(
        backend: StorageBackend,
        rootParentId: FileNodeId,
        segments: List<String>,
    ): FileNodeId {
        var currentParent = rootParentId
        for (segment in segments) {
            coroutineContext.ensureActive()
            var existing: FileNode? = null
            backend.listChildren(currentParent).collect { chunk ->
                chunk.requireSuccess().filter { it.name == segment }.forEach { child ->
                    if (existing != null) throw ArchiveFailure(FileError.InvalidDestination(segment))
                    existing = child
                }
            }
            coroutineContext.ensureActive()
            val child = existing ?: backend.createDirectory(currentParent, segment).requireSuccess()
            if (!child.isDirectory) throw ArchiveFailure(FileError.InvalidDestination(segment))
            currentParent = child.id
        }
        return currentParent
    }

    private class ArchiveFailure(val error: FileError) : Exception()

    private suspend fun discardAfterFailure(
        output: OutputTarget,
        failure: Exception,
    ) {
        withContext(NonCancellable) {
            try {
                output.discard()
            } catch (cleanupFailure: Exception) {
                failure.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun <T> FileResult<T>.requireSuccess(): T =
        when (this) {
            is FileResult.Success -> value
            is FileResult.Failure -> throw ArchiveFailure(error)
        }
}
