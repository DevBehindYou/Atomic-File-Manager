package com.devbehindyou.atomicfilemanager.benchmark

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.Closeable

internal data class BenchmarkProviderIdentity(
    val owner: String?,
    val path: String?,
    val name: String?,
    val mime: String?,
)

internal data class BenchmarkOwnedRow(
    val uri: Uri,
    val relativePath: String,
    val name: String,
    val mime: String,
    val identity: BenchmarkProviderIdentity? = null,
)

/** Never updates a trusted identity after insertion; user/provider mutations fail closed. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class BenchmarkOwnedMedia(
    private val context: Context,
    private val journal: BenchmarkFixtureJournal,
) : Closeable {
    private val resolver get() = context.contentResolver
    private val owned = mutableListOf<BenchmarkOwnedRow>()
    val rows get() = owned.toList()

    fun insert(folder: String, name: String, mime: String): BenchmarkOwnedRow {
        val relative = "${Environment.DIRECTORY_DOWNLOADS}/$folder/"
        journal.intent(relative, name, mime)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "MediaStore refused benchmark fixture creation"
        }
        var row = BenchmarkOwnedRow(uri, relative, name, mime)
        owned += row
        // Persist the provider-returned URI even if querying its identity subsequently fails.
        journal.record("inserted", row)
        row = row.copy(identity = checkNotNull(identity(uri)) { "Inserted fixture disappeared" })
        owned[owned.lastIndex] = row
        journal.record("captured", row)
        validate(row)
        return row
    }

    fun publish(row: BenchmarkOwnedRow) {
        validate(row)
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        check(resolver.update(row.uri, values, null, null) == 1) { "Could not publish a benchmark-owned fixture" }
        validate(row)
        journal.record("published", row)
    }

    fun validateAll() = owned.forEach(::validate)

    private fun validate(row: BenchmarkOwnedRow) {
        val captured = checkNotNull(row.identity) { "Fixture identity was not captured" }
        check(
            captured == BenchmarkProviderIdentity(context.packageName, row.relativePath, row.name, row.mime),
        ) { "Provider changed the requested fixture name/path/owner/MIME; refusing measurement" }
        check(identity(row.uri) == captured) { "Fixture identity changed before measurement" }
    }

    private fun identity(uri: Uri): BenchmarkProviderIdentity? {
        val segments = uri.pathSegments
        check(
            uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY && segments.size == 3 &&
                segments[0] in listOf(MediaStore.VOLUME_EXTERNAL, MediaStore.VOLUME_EXTERNAL_PRIMARY) &&
                segments[1] == "downloads" && segments[2].toLongOrNull()?.let { it > 0 } == true,
        ) { "Unexpected fixture provider URI; preserving its bytes" }
        val projection = arrayOf(
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns._ID,
        )
        return resolver.query(uri, projection, null, null, null).use { cursor ->
            checkNotNull(cursor) { "Cannot verify fixture ownership; preserving its bytes" }
            if (!cursor.moveToFirst()) return@use null
            check(cursor.getLong(4).toString() == segments[2]) { "Provider row ID did not match its recorded URI" }
            BenchmarkProviderIdentity(
                cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
            )
                .also { check(!cursor.moveToNext()) { "Fixture URI resolved to multiple rows" } }
        }
    }

    override fun close() {
        var firstFailure: Exception? = null
        val unresolved = mutableListOf<BenchmarkOwnedRow>()
        owned.asReversed().forEach { row ->
            try {
                deleteOwned(row)
                journal.record("deleted", row)
            } catch (failure: Exception) {
                unresolved += row
                if (firstFailure == null) firstFailure = failure else firstFailure?.addSuppressed(failure)
            }
        }
        owned.clear()
        owned.addAll(unresolved.asReversed())
        firstFailure?.let { throw it }
        journal.complete()
    }

    private fun deleteOwned(row: BenchmarkOwnedRow) {
        val current = identity(row.uri) ?: return
        val captured = checkNotNull(row.identity) { "No frozen fixture identity; preserving its bytes" }
        check(canDelete(context.packageName, row.relativePath, captured, current)) {
            "Fixture identity changed; preserving its bytes. Recovery journal: ${journal.file.name}"
        }
        val condition = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND " +
            "${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val expected = arrayOf(
            checkNotNull(captured.name), row.relativePath, context.packageName, checkNotNull(captured.mime),
        )
        check(resolver.delete(row.uri, condition, expected) == 1) { "Could not remove an unchanged owned fixture" }
    }

    companion object {
        internal fun canDelete(
            owner: String,
            path: String,
            captured: BenchmarkProviderIdentity,
            current: BenchmarkProviderIdentity,
        ): Boolean = captured.owner == owner && captured.path == path && captured.name != null &&
            captured.mime != null && current == captured
    }
}
