package com.devbehindyou.atomicfilemanager.data.backend

import android.content.ContentResolver
import android.database.Cursor
import android.os.Build
import android.provider.DocumentsContract
import com.devbehindyou.atomicfilemanager.data.mapping.ErrorContext
import com.devbehindyou.atomicfilemanager.data.mapping.toFileError
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

/** Provider IDs are opaque and provider-wide, even when reached through different tree grants. */
internal class SafAncestry(private val resolver: ContentResolver) {
    suspend fun isSameOrDescendant(
        ancestor: FileNodeId,
        candidate: FileNodeId,
    ): FileResult<Boolean> {
        currentCoroutineContext().ensureActive()
        if (candidate.prefix != FileNodeId.Prefix.SAF) return FileResult.Success(false)
        return try {
            val source = ancestor.toSafRef()
            val destination = candidate.toSafRef()
            val result =
                when {
                    source.documentUri.authority != destination.documentUri.authority -> FileResult.Success(false)
                    source.documentId == destination.documentId -> FileResult.Success(true)
                    providerConfirmsDescendant(source, destination) -> FileResult.Success(true)
                    else -> enumerateDescendants(source, destination.documentId)
                }
            currentCoroutineContext().ensureActive()
            result
        } catch (e: SecurityException) {
            FileResult.Failure(FileError.AccessDenied(null))
        } catch (e: IOException) {
            FileResult.Failure(e.toFileError(ErrorContext(null)))
        } catch (e: RuntimeException) {
            if (e is CancellationException) throw e
            FileResult.Failure(FileError.ProviderUnavailable(null))
        }
    }

    private fun providerConfirmsDescendant(
        source: SafRef,
        destination: SafRef,
    ): Boolean {
        // Public since API 29. A provider may return false when isChildDocument is not
        // implemented (the default DocumentsProvider implementation does exactly that).
        // Only a positive answer is conclusive; otherwise verify through child listings.
        // https://developer.android.com/reference/android/provider/DocumentsContract#isChildDocument
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            DocumentsContract.isChildDocument(resolver, source.documentUri, destination.documentUri)
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    private suspend fun enumerateDescendants(
        source: SafRef,
        targetDocumentId: String,
    ): FileResult<Boolean> {
        // One authority and the source grant throughout. IDs, never grant URIs or path
        // prefixes, form the visited key. A document may occur under multiple parents.
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf(source.documentId)
        pending.addLast(source.documentId)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(source.treeUri, pending.removeLast())
            val cursor =
                resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
                    ?: return FileResult.Failure(FileError.ProviderUnavailable(source.documentUri.authority))
            cursor.use {
                if (it.hasIncompleteResults()) {
                    return FileResult.Failure(FileError.ProviderUnavailable(source.documentUri.authority))
                }
                val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val childId = requireNotNull(it.getString(idColumn))
                    if (childId == targetDocumentId) return FileResult.Success(true)
                    val mime = requireNotNull(it.getString(mimeColumn))
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR && visited.add(childId)) {
                        pending.addLast(childId)
                    }
                }
                if (it.hasIncompleteResults()) {
                    return FileResult.Failure(FileError.ProviderUnavailable(source.documentUri.authority))
                }
            }
        }
        currentCoroutineContext().ensureActive()
        return FileResult.Success(false)
    }

    private fun Cursor.hasIncompleteResults(): Boolean =
        extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) ||
            extras.containsKey(DocumentsContract.EXTRA_ERROR)

    companion object {
        private val CHILD_PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
    }
}
