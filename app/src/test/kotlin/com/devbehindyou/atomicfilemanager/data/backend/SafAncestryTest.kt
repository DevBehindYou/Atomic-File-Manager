package com.devbehindyou.atomicfilemanager.data.backend

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/** Controlled ContentResolver provider coverage; this does not certify OEM provider behaviour. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 29])
class SafAncestryTest {
    private lateinit var provider: AncestryProvider
    private lateinit var backend: SafBackend

    @Before
    fun setUp() {
        provider = AncestryProvider()
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        backend = SafBackend(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun sameDocumentThroughDifferentTreeGrantsIsSelfWithoutProviderCall() =
        runBlocking {
            assertEquals(FileResult.Success(true), backend.isSameOrDescendant(id("a"), id("a", tree = "other-grant")))
            assertTrue(provider.queries.isEmpty())
            assertEquals(0, provider.ancestryCalls)
        }

    @Test
    fun sameOpaqueDocumentIdInAnotherAuthorityIsUnrelated() =
        runBlocking {
            assertEquals(FileResult.Success(false), backend.isSameOrDescendant(id("a"), id("a", authority = "other")))
            assertTrue(provider.queries.isEmpty())
        }

    @Test
    fun opaqueGrandchildReachedThroughDifferentGrantIsDetected() =
        runBlocking {
            provider.children["a"] = listOf(directory("z"))
            provider.children["z"] = listOf(directory("b"))
            assertEquals(FileResult.Success(true), backend.isSameOrDescendant(id("a"), id("b", tree = "b")))
            assertEquals(listOf("a", "z"), provider.queries)
            assertTrue(provider.cursors.all { it.isClosed })
        }

    @Test
    fun unsupportedAncestryApiFallsBackToEnumeration() =
        runBlocking {
            provider.unsupported = true
            provider.children["a"] = listOf(directory("z"))
            assertEquals(FileResult.Success(true), backend.isSameOrDescendant(id("a"), id("z")))
            assertEquals(listOf("a"), provider.queries)
        }

    @Test
    @Config(sdk = [29])
    fun positiveProviderAnswerAvoidsEnumeration() =
        runBlocking {
            provider.isChild = true
            assertEquals(FileResult.Success(true), backend.isSameOrDescendant(id("a"), id("z", tree = "other-grant")))
            assertEquals(1, provider.ancestryCalls)
            assertTrue(provider.queries.isEmpty())
        }

    @Test
    @Config(sdk = [29])
    fun cancellationDuringPositiveProviderReplyPropagates() =
        runBlocking {
            provider.isChild = true
            val operation = Job()
            provider.onAncestry = { operation.cancel() }
            try {
                withContext(currentCoroutineContext() + operation) {
                    backend.isSameOrDescendant(id("a"), id("z"))
                }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertEquals(1, provider.ancestryCalls)
                assertTrue(provider.queries.isEmpty())
            }
        }

    @Test
    fun cyclesAndFilesAreNotRequeriedAndPathLikeIdsAreNotGuessed() =
        runBlocking {
            provider.children["a"] = listOf(directory("z"), "file" to "text/plain")
            provider.children["z"] = listOf(directory("a"), directory("z"))
            assertEquals(FileResult.Success(false), backend.isSameOrDescendant(id("a"), id("a/unrelated")))
            assertEquals(listOf("a", "z"), provider.queries)
            assertTrue(provider.cursors.all { it.isClosed })
        }

    @Test
    fun nullCursorIsFailure() =
        runBlocking {
            provider.unavailable = true
            val result = backend.isSameOrDescendant(id("a"), id("z"))
            assertTrue(result is FileResult.Failure && result.error is FileError.ProviderUnavailable)
        }

    @Test
    fun deniedNestedDirectoryIsFailure() =
        runBlocking {
            provider.children["a"] = listOf(directory("z"))
            provider.deniedId = "z"
            assertEquals(FileResult.Failure(FileError.AccessDenied(null)), backend.isSameOrDescendant(id("a"), id("b")))
            assertTrue(provider.cursors.all { it.isClosed })
        }

    @Test
    fun crashedCursorIsFailureAndCloses() =
        runBlocking {
            provider.crashOnMove = true
            provider.children["a"] = listOf(directory("z"))
            val result = backend.isSameOrDescendant(id("a"), id("b"))
            assertTrue(result is FileResult.Failure && result.error is FileError.ProviderUnavailable)
            assertTrue(provider.cursors.all { it.isClosed })
        }

    @Test
    fun loadingOrErrorExtrasCannotProveDestinationSafe() =
        runBlocking {
            for (extra in listOf(DocumentsContract.EXTRA_LOADING, DocumentsContract.EXTRA_ERROR)) {
                provider.incompleteExtra = extra
                val result = backend.isSameOrDescendant(id("a"), id("b"))
                assertTrue(result is FileResult.Failure && result.error is FileError.ProviderUnavailable)
            }
            assertTrue(provider.cursors.all { it.isClosed })
        }

    @Test
    fun cancellationWhileReadingRowsPropagatesAndClosesCursor() =
        runBlocking {
            provider.children["a"] = listOf(directory("z"))
            val operation = Job()
            provider.onMove = { operation.cancel() }
            try {
                withContext(currentCoroutineContext() + operation) {
                    backend.isSameOrDescendant(id("a"), id("b"))
                }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertTrue(provider.cursors.all { it.isClosed })
            }
        }

    private fun directory(documentId: String): Pair<String, String> = documentId to DocumentsContract.Document.MIME_TYPE_DIR

    private fun id(
        documentId: String,
        tree: String = "root",
        authority: String = AUTHORITY,
    ): FileNodeId = safId(DocumentsContract.buildTreeDocumentUri(authority, tree), documentId)

    class AncestryProvider : ContentProvider() {
        val children = mutableMapOf<String, List<Pair<String, String>>>()
        val queries = mutableListOf<String>()
        val cursors = mutableListOf<Cursor>()
        var ancestryCalls = 0
        var unsupported = false
        var isChild = false
        var unavailable = false
        var deniedId: String? = null
        var crashOnMove = false
        var incompleteExtra: String? = null
        var onMove: (() -> Unit)? = null
        var onAncestry: (() -> Unit)? = null

        override fun onCreate() = true

        override fun call(
            method: String,
            arg: String?,
            extras: Bundle?,
        ): Bundle {
            assertEquals("android:isChildDocument", method)
            ancestryCalls++
            if (unsupported) throw UnsupportedOperationException()
            onAncestry?.invoke()
            return Bundle().apply { putBoolean("result", isChild) }
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            val parent = DocumentsContract.getDocumentId(uri)
            queries.add(parent)
            if (parent == deniedId) throw SecurityException()
            if (unavailable) return null
            val columns = requireNotNull(projection)
            val cursor =
                object : MatrixCursor(columns) {
                    override fun getExtras(): Bundle =
                        Bundle().apply {
                            incompleteExtra?.let { putBoolean(it, true) }
                        }

                    override fun onMove(
                        oldPosition: Int,
                        newPosition: Int,
                    ): Boolean {
                        check(!crashOnMove) { "Provider disconnected" }
                        onMove?.invoke()
                        return super.onMove(oldPosition, newPosition)
                    }
                }
            for ((documentId, mime) in children[parent].orEmpty()) {
                cursor.addRow(
                    columns.map { column ->
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
                            DocumentsContract.Document.COLUMN_MIME_TYPE -> mime
                            else -> error("Unexpected projection")
                        }
                    }.toTypedArray(),
                )
            }
            cursors.add(cursor)
            return cursor
        }

        override fun getType(uri: Uri): String = DocumentsContract.Document.MIME_TYPE_DIR

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri = error("Ancestry checks must not mutate")

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = error("Ancestry checks must not mutate")

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = error("Ancestry checks must not mutate")
    }

    companion object {
        private const val AUTHORITY = "refract.ancestry.documents"
    }
}
