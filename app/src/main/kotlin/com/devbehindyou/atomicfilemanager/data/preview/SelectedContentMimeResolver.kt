package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

internal sealed interface SelectedContentType {
    val node: FileNode

    data class Ready(override val node: FileNode) : SelectedContentType

    data class Unresolved(override val node: FileNode) : SelectedContentType

    data class Failure(override val node: FileNode, val error: FileError) : SelectedContentType
}

/** Known metadata remains authoritative and never starts a content read. */
internal fun openingMetadataType(node: FileNode): SelectedContentType? =
    when {
        node.isDirectory -> SelectedContentType.Unresolved(node)
        externalMimeType(node) != "application/octet-stream" -> SelectedContentType.Ready(node)
        else -> null
    }

/**
 * Selected-file PNG recognition, not image validation or a disk/network byte budget.
 * The eight-byte signature is defined by https://www.w3.org/TR/png-3/ section 5.2.
 * Network backends need separate prefix-close contracts before they can opt in.
 */
internal class SelectedContentMimeResolver(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val backendFor: (FileNodeId) -> StorageBackend,
) {
    suspend fun resolve(node: FileNode): SelectedContentType {
        val owner = currentCoroutineContext()
        owner.ensureActive()
        openingMetadataType(node)?.let { return it }
        val result =
            withContext(NonCancellable + io) {
                owner.ensureActive()
                try {
                    val backend = backendFor(node.id)
                    if (backend.type !in SUPPORTED_BACKENDS) {
                        SelectedContentType.Unresolved(node)
                    } else {
                        readOwnedPrefix(node, backend, owner)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: SecurityException) {
                    SelectedContentType.Failure(node, FileError.AccessDenied(node.name))
                } catch (_: Exception) {
                    SelectedContentType.Failure(node, FileError.IoFailure(node.name))
                }
            }
        // NonCancellable on an unchanged dispatcher has no prompt return cancellation check.
        owner.ensureActive()
        return result
    }

    private suspend fun readOwnedPrefix(
        node: FileNode,
        backend: StorageBackend,
        owner: CoroutineContext,
    ): SelectedContentType =
        when (val opened = backend.openInput(node.id)) {
            is FileResult.Failure -> SelectedContentType.Failure(node, opened.error)
            is FileResult.Success -> {
                // Some providers already own resources. Convert exactly once before checking Cancel.
                opened.value.stream().use { input ->
                    owner.ensureActive()
                    val prefix = readPrefix(input, owner)
                    if (prefix.contentEquals(PNG_SIGNATURE)) {
                        SelectedContentType.Ready(node.copy(mimeType = "image/png"))
                    } else {
                        SelectedContentType.Unresolved(node)
                    }
                }
            }
        }

    private fun readPrefix(
        input: InputStream,
        owner: CoroutineContext,
    ): ByteArray {
        val prefix = ByteArray(PNG_SIGNATURE.size)
        var size = 0
        while (size < prefix.size) {
            owner.ensureActive()
            var count = input.read(prefix, size, prefix.size - size)
            owner.ensureActive()
            if (count == 0) {
                val value = input.read()
                owner.ensureActive()
                count = if (value == -1) -1 else 1
                if (value != -1) prefix[size] = value.toByte()
            }
            if (count == -1) break
            size += count
        }
        return if (size == prefix.size) prefix else prefix.copyOf(size)
    }

    private companion object {
        val SUPPORTED_BACKENDS = setOf(BackendType.FILE, BackendType.SAF, BackendType.MEDIASTORE)
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
