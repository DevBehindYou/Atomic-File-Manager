package com.devbehindyou.atomicfilemanager.data.preview

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.devbehindyou.atomicfilemanager.data.backend.toMediaRef
import com.devbehindyou.atomicfilemanager.data.backend.toSafRef
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext

internal data class ExternalPreviewFile(val uri: Uri, val mimeType: String)

/** Read-only URI grants; remote exports are made only after the user chooses Open with. */
internal class ExternalPreviewHelper(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxExportBytes: Long = MAX_EXPORT_BYTES,
    private val backendFor: (FileNodeId) -> StorageBackend,
) {
    suspend fun open(
        node: FileNode,
        launch: (ExternalPreviewFile) -> Unit,
    ): FileResult<Unit> {
        var staged: File? = null
        var launched = false
        return try {
            val prepared =
                withContext(io) {
                    val uri =
                        when (node.id.prefix) {
                            FileNodeId.Prefix.FILE -> {
                                val file = File(node.id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme))
                                check(file.isFile && file.canRead())
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            }
                            FileNodeId.Prefix.SAF -> node.id.toSafRef().documentUri
                            FileNodeId.Prefix.MEDIA -> {
                                val ref = node.id.toMediaRef()
                                require(!ref.isCollectionRoot)
                                ContentUris.withAppendedId(ref.collectionUri, ref.rowId)
                            }
                            else -> {
                                val directory = File(context.cacheDir, "external-preview").apply { mkdirs() }
                                pruneExpired(directory)
                                val file = File.createTempFile("open-", ".${safeExtension(node.name)}", directory)
                                staged = file
                                when (val input = backendFor(node.id).openInput(node.id)) {
                                    is FileResult.Failure -> return@withContext input
                                    is FileResult.Success ->
                                        input.value.stream().use { stream ->
                                            file.outputStream().use { output ->
                                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                                var copied = 0L
                                                while (true) {
                                                    coroutineContext.ensureActive()
                                                    val count = stream.read(buffer)
                                                    if (count == -1) break
                                                    copied += count
                                                    if (copied > maxExportBytes) {
                                                        return@withContext FileResult.Failure(
                                                            FileError.FileTooLarge(node.name, maxExportBytes),
                                                        )
                                                    }
                                                    output.write(buffer, 0, count)
                                                }
                                            }
                                        }
                                }
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            }
                        }
                    coroutineContext.ensureActive()
                    FileResult.Success(ExternalPreviewFile(uri, externalMimeType(node)))
                }
            when (prepared) {
                is FileResult.Failure -> prepared
                is FileResult.Success -> {
                    coroutineContext.ensureActive()
                    launch(prepared.value)
                    launched = true
                    FileResult.Success(Unit)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FileResult.Failure(FileError.IoFailure(node.name))
        } finally {
            // Successful external readers may open asynchronously; keep their copy until expiry.
            if (!launched) withContext(NonCancellable + io) { staged?.delete() }
        }
    }

    private fun pruneExpired(directory: File) {
        val expiresBefore = System.currentTimeMillis() - EXPORT_RETENTION_MS
        java.nio.file.Files.newDirectoryStream(directory.toPath()).use { entries ->
            entries.take(MAX_PRUNE_ENTRIES).forEach { entry ->
                val file = entry.toFile()
                if (file.isFile && file.lastModified() < expiresBefore) file.delete()
            }
        }
    }

    private companion object {
        const val MAX_EXPORT_BYTES = 64L * 1024 * 1024
        const val EXPORT_RETENTION_MS = 24L * 60 * 60 * 1000
        const val MAX_PRUNE_ENTRIES = 128
    }
}

internal fun externalMimeType(node: FileNode): String {
    val supplied = node.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    return supplied?.takeUnless { it.isBlank() || it == "application/octet-stream" }
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(safeExtension(node.name))
        ?: "application/octet-stream"
}

private fun safeExtension(name: String): String =
    name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        .takeIf { it.length in 1..12 && it.all { char -> char in 'a'..'z' || char in '0'..'9' } } ?: "bin"
