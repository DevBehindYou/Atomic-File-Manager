package com.devbehindyou.atomicfilemanager.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
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

    fun mime(index: Int): String =
        when (name(index).substringAfterLast('.')) {
            "png" -> "image/png"
            "json" -> "application/json"
            "md" -> "text/markdown"
            else -> "text/plain"
        }

    companion object {
        val SIZES = listOf(100, 1_000, 10_000)

        /** Absence keeps the full suite; a supplied invalid value must never widen the workload. */
        fun selectedSizes(raw: String?, supplied: Boolean): List<Int> {
            if (!supplied) {
                require(raw == null) { "Unexpected corpus argument without a supplied key" }
                return SIZES
            }
            val selected = SIZES.filter { it.toString() == raw }
            require(selected.size == 1) { "fixtureEntries must be exactly 100, 1000 or 10000" }
            return selected
        }
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
    private val resolver get() = context.contentResolver
    private val runId = UUID.randomUUID().toString()
    private val journal = BenchmarkFixtureJournal(context, runId, plan.entries)
    private val media = BenchmarkOwnedMedia(context, journal)
    internal val providerRows get() = media.rows
    internal val recoveryFile get() = journal.file
    val folderName = "AtomicBenchmark-${plan.entries}-$runId"
    val resetName = "AtomicBenchmark-reset-$runId"
    val imageCount get() = plan.imageCount
    lateinit var folderPath: String
        private set
    lateinit var resetPath: String
        private set

    private fun generate() {
        for (index in 0 until plan.entries) {
            val row = media.insert(folderName, plan.name(index), plan.mime(index))
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
            media.publish(row)
            if (index == 0) folderPath = directoryOf(row)
        }
        val reset = media.insert(resetName, "reset.txt", "text/plain")
        resolver.openOutputStream(reset.uri, "w").use { output ->
            checkNotNull(output).write("Atomic benchmark navigation reset\n".toByteArray(Charsets.UTF_8))
        }
        media.publish(reset)
        resetPath = directoryOf(reset)
    }

    private fun directoryOf(row: BenchmarkOwnedRow): String {
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

    override fun close() = media.close()

    companion object {
        fun create(context: Context, entries: Int): BenchmarkCorpus {
            val corpus = BenchmarkCorpus(context, FixturePlan(entries))
            try {
                corpus.generate()
                corpus.media.validateAll()
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
