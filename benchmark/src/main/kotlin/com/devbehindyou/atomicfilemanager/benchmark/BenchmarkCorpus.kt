package com.devbehindyou.atomicfilemanager.benchmark

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.File
import java.util.UUID

/** Pure fixture specification; unknown sizes fail before shared-storage writes. */
internal class FixturePlan(val entries: Int) {
    init {
        require(entries in SIZES) { "Only the reviewed 100/1000/10000-entry corpora are supported" }
    }

    val imageCount = minOf(entries / 10, 100)

    fun name(index: Int): String {
        require(index in 0 until entries)
        val extension = if (index < imageCount) "png" else listOf("txt", "json", "md")[index % 3]
        return "bench_${index.toString().padStart(5, '0')}.$extension"
    }

    companion object {
        val SIZES = listOf(100, 1_000, 10_000)
    }
}

/**
 * Owns only inserted MediaStore identities. Cleanup checks owner/path/name and deletes one
 * known URI at a time; no directory scan or recursive deletion can touch existing user files.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class BenchmarkCorpus private constructor(
    private val context: Context,
    private val plan: FixturePlan,
) : Closeable {
    private data class OwnedRow(val uri: Uri, val relativePath: String, val name: String)

    private val resolver get() = context.contentResolver
    private val owned = mutableListOf<OwnedRow>()
    private val runId = UUID.randomUUID().toString()
    val folderName = "AtomicBenchmark-${plan.entries}-$runId"
    val resetName = "AtomicBenchmark-reset-$runId"
    val imageCount get() = plan.imageCount
    lateinit var folderPath: String
        private set
    lateinit var resetPath: String
        private set

    private fun generate() {
        for (index in 0 until plan.entries) {
            val row = insert(folderName, plan.name(index), if (index < imageCount) "image/png" else "text/plain")
            resolver.openOutputStream(row.uri, "w").use { output ->
                checkNotNull(output) { "Could not write a benchmark-owned fixture" }
                if (index < imageCount) {
                    val image = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                    try {
                        image.eraseColor(Color.rgb(index % 255, 80, 180))
                        check(image.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode fixture PNG" }
                    } finally {
                        image.recycle()
                    }
                } else {
                    output.write("Atomic benchmark fixture $index\n".toByteArray(Charsets.UTF_8))
                }
            }
            publish(row)
            if (index == 0) folderPath = directoryOf(row)
        }
        val reset = insert(resetName, "reset.txt", "text/plain")
        resolver.openOutputStream(reset.uri, "w").use { output ->
            checkNotNull(output).write("Atomic benchmark navigation reset\n".toByteArray(Charsets.UTF_8))
        }
        publish(reset)
        resetPath = directoryOf(reset)
    }

    private fun insert(folder: String, name: String, mime: String): OwnedRow {
        val relative = "${Environment.DIRECTORY_DOWNLOADS}/$folder/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "MediaStore refused benchmark fixture creation"
        }
        return OwnedRow(uri, relative, name).also { owned += it }
    }

    private fun publish(row: OwnedRow) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        check(resolver.update(row.uri, values, null, null) == 1) { "Could not publish a benchmark-owned fixture" }
    }

    private fun directoryOf(row: OwnedRow): String {
        // DATA is read for an existing app-owned row, never used to create/update media. It bridges
        // the fixture into Atomic's existing local-folder shortcut without a hardcoded volume path.
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        return resolver.query(row.uri, projection, null, null, null).use { cursor ->
            check(cursor != null && cursor.moveToFirst()) { "Fixture path unavailable" }
            val file = File(checkNotNull(cursor.getString(0)))
            val expectedFolder = row.relativePath.trimEnd('/').substringAfterLast('/')
            check(file.name == row.name && file.parentFile?.name == expectedFolder) {
                "Provider placed the fixture outside its requested folder"
            }
            checkNotNull(file.parentFile).absolutePath
        }
    }

    override fun close() {
        var firstFailure: Exception? = null
        owned.asReversed().forEach { row ->
            try {
                deleteOwned(row)
            } catch (failure: Exception) {
                if (firstFailure == null) firstFailure = failure else firstFailure?.addSuppressed(failure)
            }
        }
        owned.clear()
        firstFailure?.let { throw it }
    }

    private fun deleteOwned(row: OwnedRow) {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
        )
        val exists = resolver.query(row.uri, projection, null, null, null).use { cursor ->
            checkNotNull(cursor) { "Cannot verify fixture ownership; preserving its bytes" }
            if (!cursor.moveToFirst()) return@use false
            check(ownsRow(context.packageName, row.relativePath, row.name, cursor.getString(2), cursor.getString(1), cursor.getString(0))) {
                "Fixture identity changed; preserving its bytes"
            }
            true
        }
        if (exists) {
            val condition = "${projection[0]} = ? AND ${projection[1]} = ? AND ${projection[2]} = ?"
            val expected = arrayOf(row.name, row.relativePath, context.packageName)
            check(resolver.delete(row.uri, condition, expected) == 1) { "Could not remove an unchanged owned fixture" }
        }
    }

    companion object {
        fun create(context: Context, entries: Int): BenchmarkCorpus {
            val corpus = BenchmarkCorpus(context, FixturePlan(entries))
            try {
                corpus.generate()
                return corpus
            } catch (failure: Throwable) {
                try {
                    corpus.close()
                } catch (cleanup: Exception) {
                    failure.addSuppressed(cleanup)
                }
                throw failure
            }
        }

        internal fun ownsRow(
            owner: String,
            path: String,
            name: String,
            actualOwner: String?,
            actualPath: String?,
            actualName: String?,
        ): Boolean = owner == actualOwner && path == actualPath && name == actualName
    }
}
