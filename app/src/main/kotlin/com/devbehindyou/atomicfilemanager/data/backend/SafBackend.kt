package com.devbehindyou.atomicfilemanager.data.backend

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.devbehindyou.atomicfilemanager.data.mapping.ErrorContext
import com.devbehindyou.atomicfilemanager.data.mapping.toFileError
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.OutputTarget
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject

/**
 * Storage Access Framework, via direct `ContentResolver.query()` against
 * `DocumentsContract`'s child-documents URI — **never** `DocumentFile.listFiles()`
 * (`architecture/DATA_LAYER.md` §5 is explicit and specific about why: it does a separate
 * round-trip per child instead of one query for the whole directory).
 *
 * **Confidence note:** this is the medium-confidence tier of this phase. The query/URI
 * plumbing below follows well-established, stable `DocumentsContract` APIs I have real
 * confidence in the shape of, but Robolectric's SAF shadow implementations are the least
 * proven part of this project's test story so far — passing a Robolectric-based contract
 * test here is meaningfully weaker evidence than the same test passing for
 * [FileSystemBackend]. `ANDROID_STORAGE_RESEARCH.md` §10's still-open items (SAF picker
 * behaviour across OEM skins) are about the *permission-granting* flow, not this backend's
 * read/write logic once a grant already exists — but OEM DocumentsProvider implementations
 * are also exactly the kind of thing that varies in the field in ways a shadow can't catch.
 */
class SafBackend
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : StorageBackend {
        override val type: BackendType = BackendType.SAF

        private val resolver: ContentResolver get() = context.contentResolver

        override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.SAF

        override suspend fun isSameOrDescendant(
            ancestor: FileNodeId,
            candidate: FileNodeId,
        ): FileResult<Boolean> = SafAncestry(resolver).isSameOrDescendant(ancestor, candidate)

        override suspend fun getNode(id: FileNodeId): FileResult<FileNode> {
            val ref = id.toSafRef()
            val cursor =
                try {
                    resolver.query(ref.documentUri, PROJECTION, null, null, null)
                } catch (e: SecurityException) {
                    return FileResult.Failure(FileError.AccessDenied(null))
                } ?: return FileResult.Failure(FileError.ProviderUnavailable(ref.documentUri.authority))

            return cursor.use {
                if (it.moveToFirst()) {
                    FileResult.Success(it.toFileNode(ref.treeUri, parentId = null))
                } else {
                    FileResult.Failure(FileError.FileNotFound(null))
                }
            }
        }

        override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
            flow {
                val ref = id.toSafRef()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(ref.treeUri, ref.documentId)

                val cursor =
                    try {
                        resolver.query(childrenUri, PROJECTION, null, null, null)
                    } catch (e: SecurityException) {
                        emit(FileResult.Failure(FileError.AccessDenied(null)))
                        return@flow
                    }
                if (cursor == null) {
                    emit(FileResult.Failure(FileError.ProviderUnavailable(childrenUri.authority)))
                    return@flow
                }

                var emittedAny = false
                val chunk = ArrayList<FileNode>(LISTING_CHUNK_SIZE)
                try {
                    cursor.use {
                        while (it.moveToNext()) {
                            chunk.add(it.toFileNode(ref.treeUri, parentId = id))
                            if (chunk.size >= LISTING_CHUNK_SIZE) {
                                emit(FileResult.Success(chunk.toList()))
                                emittedAny = true
                                chunk.clear()
                            }
                        }
                    }
                    if (chunk.isNotEmpty() || !emittedAny) {
                        emit(FileResult.Success(chunk.toList()))
                    }
                } catch (e: RuntimeException) {
                    if (e is CancellationException) throw e
                    // A provider crash surfaces as a RuntimeException wrapping DeadObjectException
                    // here, not a clean checked exception (architecture/DATA_LAYER.md §5). Caught
                    // specifically so one flaky provider doesn't crash the whole listing flow.
                    emit(FileResult.Failure(FileError.ProviderUnavailable(childrenUri.authority)))
                }
            }

        override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
            val ref = id.toSafRef()
            return try {
                // Confirm the document is actually openable before handing back a provider —
                // ContentResolver.openInputStream can throw or return null lazily otherwise.
                val checkStream =
                    resolver.openInputStream(ref.documentUri)
                        ?: return FileResult.Failure(FileError.IoFailure(null))
                checkStream.close()
                FileResult.Success(
                    InputStreamProvider {
                        resolver.openInputStream(ref.documentUri)
                            ?: error("SAF document vanished between check and read: ${ref.documentUri}")
                    },
                )
            } catch (e: SecurityException) {
                FileResult.Failure(FileError.AccessDenied(null))
            } catch (e: IOException) {
                FileResult.Failure(e.toFileError(ErrorContext(null)))
            }
        }

        override suspend fun openOutput(
            parent: FileNodeId,
            name: String,
            mime: String?,
        ): FileResult<OutputTarget> {
            val parentRef = parent.toSafRef()
            val existingId = findChildDocumentId(parentRef, name)
            val documentUri =
                if (existingId != null) {
                    DocumentsContract.buildDocumentUriUsingTree(parentRef.treeUri, existingId)
                } else {
                    try {
                        DocumentsContract.createDocument(
                            resolver,
                            parentRef.documentUri,
                            mime ?: "application/octet-stream",
                            name,
                        ) ?: return FileResult.Failure(FileError.AccessDenied(name))
                    } catch (e: SecurityException) {
                        return FileResult.Failure(FileError.AccessDenied(name))
                    }
                }
            return FileResult.Success(SafOutputTarget(resolver, documentUri, name))
        }

        override suspend fun createDirectory(
            parent: FileNodeId,
            name: String,
        ): FileResult<FileNode> {
            val parentRef = parent.toSafRef()
            if (findChildDocumentId(parentRef, name) != null) {
                return FileResult.Failure(FileError.FileAlreadyExists(name))
            }
            val newUri =
                try {
                    DocumentsContract.createDocument(
                        resolver,
                        parentRef.documentUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        name,
                    )
                } catch (e: SecurityException) {
                    return FileResult.Failure(FileError.AccessDenied(name))
                } ?: return FileResult.Failure(FileError.AccessDenied(name))

            return getNode(FileNodeId.saf(newUri.toString()))
        }

        override suspend fun delete(id: FileNodeId): FileResult<Unit> {
            val ref = id.toSafRef()
            return try {
                if (DocumentsContract.deleteDocument(resolver, ref.documentUri)) {
                    FileResult.Success(Unit)
                } else {
                    FileResult.Failure(FileError.AccessDenied(null))
                }
            } catch (e: SecurityException) {
                FileResult.Failure(FileError.AccessDenied(null))
            } catch (e: IOException) {
                FileResult.Failure(e.toFileError(ErrorContext(null)))
            }
        }

        override suspend fun rename(
            id: FileNodeId,
            newName: String,
        ): FileResult<FileNode> {
            val ref = id.toSafRef()
            val renamed =
                try {
                    DocumentsContract.renameDocument(resolver, ref.documentUri, newName)
                } catch (e: SecurityException) {
                    return FileResult.Failure(FileError.AccessDenied(newName))
                } ?: return FileResult.Failure(FileError.AccessDenied(newName))

            // renameDocument can return a *different* URI than the one passed in — some
            // providers reassign the document id on rename (architecture/DATA_LAYER.md §5
            // implies this via the general "provider optionality" theme, though this specific
            // detail is this file's own extrapolation from DocumentsContract's own documented
            // return-value contract, not a direct doc quote).
            return getNode(FileNodeId.saf(renamed.toString()))
        }

        override suspend fun moveWithin(
            id: FileNodeId,
            newParent: FileNodeId,
        ): FileResult<FileNode> {
            val ref = id.toSafRef()
            val newParentRef = newParent.toSafRef()
            val currentParentId =
                currentParentDocumentId(ref)
                    ?: return FileResult.Failure(FileError.InvalidDestination(null))
            val currentParentUri = DocumentsContract.buildDocumentUriUsingTree(ref.treeUri, currentParentId)

            val moved =
                try {
                    DocumentsContract.moveDocument(
                        resolver,
                        ref.documentUri,
                        currentParentUri,
                        newParentRef.documentUri,
                    )
                } catch (e: UnsupportedOperationException) {
                    null
                } catch (e: SecurityException) {
                    return FileResult.Failure(FileError.AccessDenied(null))
                }

            if (moved != null) {
                return getNode(FileNodeId.saf(moved.toString()))
            }

            // FLAG_SUPPORTS_MOVE isn't set, or the provider doesn't support moveDocument at all
            // — fall back to copy+delete, per architecture/DATA_LAYER.md §5's explicit guidance.
            return copyThenDeleteFallback(ref, newParentRef)
        }

        override suspend fun exists(
            parent: FileNodeId,
            name: String,
        ): Boolean {
            val parentRef = parent.toSafRef()
            return findChildDocumentId(parentRef, name) != null
        }

        override suspend fun freeSpace(id: FileNodeId): Long = 0L
        // SAF exposes no portable free-space query on the document/tree URI itself — a real
        // number would need DocumentsContract.EXTRA_LOADING / provider-specific extras that
        // aren't guaranteed to exist. 0L is an honest "unknown", not a fabricated estimate.

        /**
         * SAF has no reliable, provider-portable "does a child with this name exist" query —
         * `selection`/`selectionArgs` on `ContentResolver.query()` aren't required to be
         * honoured by a `DocumentsProvider` implementation. This lists all children and checks
         * names client-side, same as [exists] and the duplicate-name checks in [openOutput] and
         * [createDirectory] — an honest simplification given SAF's provider heterogeneity, not
         * an oversight.
         */
        private suspend fun findChildDocumentId(
            parentRef: SafRef,
            name: String,
        ): String? {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentRef.treeUri, parentRef.documentId)
            val cursor =
                try {
                    resolver.query(childrenUri, PROJECTION, null, null, null)
                } catch (e: SecurityException) {
                    null
                } ?: return null

            return cursor.use {
                while (it.moveToNext()) {
                    val displayName =
                        it.getString(
                            it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        )
                    if (displayName == name) {
                        return@use it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    }
                }
                null
            }
        }

        /**
         * `DocumentsContract` has no query for "what is this document's parent" — the same
         * heterogeneity problem as [findChildDocumentId]. [moveWithin] needs the *current*
         * parent to call `moveDocument`, so this walks up from the tree root, which is the only
         * portable reference point available, rather than assuming any hierarchical structure
         * in the document id itself (some providers use opaque, non-hierarchical ids).
         */
        private suspend fun currentParentDocumentId(ref: SafRef): String? {
            val rootDocId = DocumentsContract.getTreeDocumentId(ref.treeUri)
            return findParentRecursively(ref.treeUri, rootDocId, ref.documentId)
        }

        private suspend fun findParentRecursively(
            treeUri: Uri,
            candidateParentId: String,
            targetId: String,
        ): String? {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, candidateParentId)
            val cursor =
                try {
                    resolver.query(
                        childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null,
                    )
                } catch (e: SecurityException) {
                    null
                } ?: return null

            val childIds =
                cursor.use {
                    buildList {
                        while (it.moveToNext()) {
                            add(it.getString(it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)))
                        }
                    }
                }
            if (targetId in childIds) return candidateParentId
            for (childId in childIds) {
                findParentRecursively(treeUri, childId, targetId)?.let { return it }
            }
            return null
        }

        private suspend fun copyThenDeleteFallback(
            source: SafRef,
            newParentRef: SafRef,
        ): FileResult<FileNode> {
            val sourceNode =
                when (val result = getNode(FileNodeId.saf(source.documentUri.toString()))) {
                    is FileResult.Success -> result.value
                    is FileResult.Failure -> return result
                }
            val target =
                when (
                    val result =
                        openOutput(
                            FileNodeId.saf(newParentRef.documentUri.toString()),
                            sourceNode.name,
                            sourceNode.mimeType,
                        )
                ) {
                    is FileResult.Success -> result.value
                    is FileResult.Failure -> return result
                }
            val inputResult = openInput(FileNodeId.saf(source.documentUri.toString()))
            val input =
                when (inputResult) {
                    is FileResult.Success -> inputResult.value
                    is FileResult.Failure -> {
                        target.discard()
                        return inputResult
                    }
                }
            try {
                input.stream().use { inStream -> target.stream().use { outStream -> inStream.copyTo(outStream) } }
            } catch (e: IOException) {
                target.discard()
                return FileResult.Failure(e.toFileError(ErrorContext(sourceNode.name)))
            }
            val committed = target.toNode()
            if (committed is FileResult.Failure) return committed
            val deleteResult = delete(FileNodeId.saf(source.documentUri.toString()))
            return if (deleteResult is FileResult.Failure) deleteResult else committed
        }

        companion object {
            private val PROJECTION =
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_FLAGS,
                )
        }
    }

internal data class SafRef(val documentUri: Uri, val treeUri: Uri, val documentId: String)

/**
 * `internal`, not `private` — exposed so `SafUriMappingTest` can exercise this URI
 * encode/decode logic directly. It's pure `Uri`/`DocumentsContract` string manipulation
 * (no `ContentResolver` I/O), which Robolectric shadows reliably — unlike an actual
 * provider round-trip, this is genuinely testable without a fake `DocumentsProvider`.
 */
internal fun FileNodeId.toSafRef(): SafRef {
    require(prefix == FileNodeId.Prefix.SAF) { "SafBackend cannot handle $this" }
    val decoded = URLDecoder.decode(raw.removePrefix(FileNodeId.Prefix.SAF.scheme), "UTF-8")
    val documentUri = Uri.parse(decoded)
    val treeDocId = DocumentsContract.getTreeDocumentId(documentUri)
    val treeUri = DocumentsContract.buildTreeDocumentUri(documentUri.authority, treeDocId)
    val documentId = DocumentsContract.getDocumentId(documentUri)
    return SafRef(documentUri, treeUri, documentId)
}

internal fun safId(
    treeUri: Uri,
    documentId: String,
): FileNodeId {
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    return FileNodeId.saf(documentUri.toString())
}

private fun Cursor.toFileNode(
    treeUri: Uri,
    parentId: FileNodeId?,
): FileNode {
    val docId = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
    val id = safId(treeUri, docId)
    val name = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) ?: docId
    val mime = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
    val size = getLong(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
    val lastModified = getLong(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
    val flags = getInt(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS))
    val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR

    return FileNode(
        id = id,
        name = name,
        displayName = name,
        mimeType = if (isDirectory) null else mime,
        size = if (isDirectory) -1L else size,
        modifiedAt = lastModified,
        isDirectory = isDirectory,
        isHidden = name.startsWith("."),
        // Only known when this node came from a listChildren call — see class KDoc on
        // getNode. A single-document query has no COLUMN_PARENT_DOCUMENT_ID to read.
        parentId = parentId,
        // Best-effort default — real volume classification needs
        // com.devbehindyou.atomicfilemanager.data.volume.StorageVolumes cross-referenced against
        // this tree's authority/root, not done per-node here. SAF is most commonly (not
        // exclusively) used for non-primary volumes historically.
        storageType = StorageType.SD_CARD,
        access =
            AccessFlags(
                // Queryable at all implies at least readable.
                readable = true,
                writable = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0,
                deletable = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
                renamable = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0,
            ),
        childCount = null,
        extras = null,
    )
}

private class SafOutputTarget(
    private val resolver: ContentResolver,
    private val documentUri: Uri,
    private val name: String,
) : OutputTarget {
    private var discarded = false
    private var lastModified: Long = 0L

    override fun stream() =
        resolver.openOutputStream(documentUri, "wt")
            ?: error("SAF document has no writable stream: $documentUri")

    override fun setLastModified(epochMillis: Long) {
        lastModified = epochMillis
        // DocumentsContract has no portable "set last-modified" call — COLUMN_LAST_MODIFIED
        // is provider-reported, not client-settable, for most DocumentsProvider
        // implementations. Recorded but not actually appliable here; documented rather than
        // silently dropped.
    }

    override fun discard() {
        discarded = true
        runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }
    }

    override fun sync() {
        // No portable fsync-equivalent over a SAF ContentResolver stream.
    }

    override suspend fun toNode(): FileResult<FileNode> {
        if (discarded) return FileResult.Failure(FileError.OperationCancelled)
        val cursor =
            try {
                resolver.query(
                    documentUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        DocumentsContract.Document.COLUMN_FLAGS,
                    ),
                    null,
                    null,
                    null,
                )
            } catch (e: SecurityException) {
                return FileResult.Failure(FileError.AccessDenied(name))
            } ?: return FileResult.Failure(FileError.ProviderUnavailable(documentUri.authority))

        val treeDocId = DocumentsContract.getTreeDocumentId(documentUri)
        val treeUri = DocumentsContract.buildTreeDocumentUri(documentUri.authority, treeDocId)
        return cursor.use {
            if (it.moveToFirst()) {
                FileResult.Success(it.toFileNode(treeUri, parentId = null))
            } else {
                FileResult.Failure(FileError.IoFailure(name))
            }
        }
    }
}
