package com.devbehindyou.atomicfilemanager.data.backend

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/** Exercises ContentResolver/cursor plumbing with a controlled provider, not an OEM provider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProviderListingIntegrationTest {
    private lateinit var provider: ListingProvider
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = ListingProvider()
        ShadowContentResolver.registerProviderInternal("refract.test.documents", provider)
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    private fun backends(): List<Pair<StorageBackend, FileNodeId>> {
        val tree = DocumentsContract.buildTreeDocumentUri("refract.test.documents", "root")
        return listOf(
            SafBackend(context) to FileNodeId.saf(DocumentsContract.buildDocumentUriUsingTree(tree, "root").toString()),
            MediaStoreBackend(context) to MediaStoreBackend.rootId("external", "images"),
        )
    }

    @Test
    fun bothProvidersStreamAll451RowsAndCloseCursor() =
        runBlocking {
            for ((backend, root) in backends()) {
                val chunks = backend.listChildren(root).toList()
                assertEquals(listOf(200, 200, 51), chunks.map { (it as FileResult.Success).value.size })
                assertTrue(provider.lastCursor!!.isClosed)
            }
        }

    @Test
    fun revokedPermissionIsFailureNotEmptyDirectory() =
        runBlocking {
            provider.deny = true
            for ((backend, root) in backends()) {
                val result = backend.listChildren(root).toList().single()
                assertTrue(result is FileResult.Failure && result.error is FileError.AccessDenied)
            }
        }

    @Test
    fun unavailableProviderIsFailureNotEmptyDirectory() =
        runBlocking {
            provider.unavailable = true
            for ((backend, root) in backends()) {
                val result = backend.listChildren(root).toList().single()
                assertTrue(result is FileResult.Failure && result.error is FileError.ProviderUnavailable)
            }
        }

    @Test
    fun consumerCancellationClosesCursorWithoutConvertingToError() =
        runBlocking {
            for ((backend, root) in backends()) {
                assertEquals(1, backend.listChildren(root).take(1).toList().size)
                assertTrue(provider.lastCursor!!.isClosed)
            }
        }

    @Test
    fun providerFailureAfterFirstChunkIsVisible() =
        runBlocking {
            provider.crashAfter = 220
            for ((backend, root) in backends()) {
                val chunks = backend.listChildren(root).toList()
                assertTrue(chunks.first() is FileResult.Success)
                assertTrue(chunks.last() is FileResult.Failure)
                assertTrue(provider.lastCursor!!.isClosed)
            }
        }

    class ListingProvider : ContentProvider() {
        var deny = false
        var unavailable = false
        var crashAfter = Int.MAX_VALUE
        var lastCursor: Cursor? = null

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            if (deny) throw SecurityException("Revoked grant")
            if (unavailable) return null
            val columns = requireNotNull(projection)
            val cursor =
                object : MatrixCursor(columns) {
                    override fun onMove(
                        oldPosition: Int,
                        newPosition: Int,
                    ): Boolean {
                        check(newPosition < crashAfter) { "Provider disconnected" }
                        return super.onMove(oldPosition, newPosition)
                    }
                }
            repeat(451) { index ->
                cursor.addRow(
                    columns.map<String, Any?> { column ->
                        when (column) {
                            "document_id" -> "root/file$index"
                            "_id" -> index.toLong()
                            "_display_name" -> "file$index.jpg"
                            "mime_type" -> "image/jpeg"
                            "_size" -> 1024L
                            "last_modified", "date_modified" -> 1000L
                            "flags" -> 0
                            else -> null
                        }
                    }.toTypedArray(),
                )
            }
            lastCursor = cursor
            return cursor
        }

        override fun getType(uri: Uri): String = "image/jpeg"

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}
