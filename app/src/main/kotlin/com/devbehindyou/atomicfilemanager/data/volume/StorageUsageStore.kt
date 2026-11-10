package com.devbehindyou.atomicfilemanager.data.volume

import android.content.Context
import com.devbehindyou.atomicfilemanager.domain.model.CategoryUsage
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageSnapshot
import java.util.Base64
import java.util.zip.CRC32

/** One remembered result, with the storage it describes ([inputKey]) and the plan it was walked under. */
data class StoredStorageUsageResult(
    val inputKey: String,
    val planKey: String,
    val snapshot: StorageUsageSnapshot,
)

/**
 * What a later process may show about file types: the last complete result, the last observed one and the
 * storage whose last walk started but never published a result. Aggregate totals only, never file names.
 */
data class StoredStorageUsage(
    val lastGood: StoredStorageUsageResult? = null,
    val observed: StoredStorageUsageResult? = null,
    val interruptedInputKey: String? = null,
)

/** Durable copy of [StorageUsageRepository]'s evidence. Called on its IO dispatcher only. */
interface StorageUsageStore {
    fun read(): StoredStorageUsage?

    fun write(value: StoredStorageUsage): Boolean
}

/** Private app preferences; backups are disabled for the app, so nothing leaves the device. */
class SharedPreferencesStorageUsageStore(context: Context) : StorageUsageStore {
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences("atomic_storage_usage", Context.MODE_PRIVATE)
    }

    override fun read(): StoredStorageUsage? = prefs.getString(KEY, null)?.let(StorageUsageCodec::decode)

    // Commit reports whether the evidence reached disk before the scan continues.
    override fun write(value: StoredStorageUsage): Boolean =
        prefs.edit().putString(KEY, StorageUsageCodec.encode(value)).commit()

    private companion object {
        const val KEY = "file_types_v1"
    }
}

/** Versioned line codec. Anything unknown, truncated or invalid decodes to null and is simply not shown. */
internal object StorageUsageCodec {
    private const val VERSION = "v1"
    private const val MAX_TEXT = 16 * 1024
    private const val SNAPSHOT_FIELDS = 11

    fun encode(value: StoredStorageUsage): String {
        val body =
            listOf(
                VERSION,
                value.lastGood?.let(::encodeResult).orEmpty(),
                value.observed?.let(::encodeResult).orEmpty(),
                value.interruptedInputKey?.let(::encodeText).orEmpty(),
            ).joinToString("\n")
        return "$body\n${checksum(body)}"
    }

    fun decode(encoded: String): StoredStorageUsage? {
        if (encoded.length > MAX_TEXT) return null
        // The trailing checksum rejects truncated or altered records, which could otherwise still parse.
        val body = encoded.substringBeforeLast('\n', "")
        if (encoded.substringAfterLast('\n') != checksum(body)) return null
        val lines = body.split('\n')
        if (lines.size != 4 || lines[0] != VERSION) return null
        return try {
            StoredStorageUsage(
                lastGood = lines[1].takeIf(String::isNotEmpty)?.let { decodeResult(it) ?: return null },
                observed = lines[2].takeIf(String::isNotEmpty)?.let { decodeResult(it) ?: return null },
                interruptedInputKey = lines[3].takeIf(String::isNotEmpty)?.let { decodeText(it) ?: return null },
            )
        } catch (_: IllegalArgumentException) {
            // Model invariants (negative sizes, free above total) reject tampered or corrupt values.
            null
        }
    }

    private fun checksum(body: String): String =
        CRC32().apply { update(body.toByteArray(Charsets.UTF_8)) }.value.toString(16)

    private fun encodeResult(value: StoredStorageUsageResult): String =
        listOf(encodeText(value.inputKey), encodeText(value.planKey), encodeSnapshot(value.snapshot)).joinToString("|")

    private fun decodeResult(encoded: String): StoredStorageUsageResult? {
        val parts = encoded.split('|')
        if (parts.size != 3) return null
        return StoredStorageUsageResult(
            decodeText(parts[0]) ?: return null,
            decodeText(parts[1]) ?: return null,
            decodeSnapshot(parts[2]) ?: return null,
        )
    }

    private fun encodeSnapshot(value: StorageUsageSnapshot): String {
        val capacity = value.capacity
        val numbers =
            listOf(
                capacity.totalBytes, capacity.freeBytes, capacity.mountedVolumes, capacity.unknownVolumes,
                value.scannedFiles, value.scannedDirectories, value.unreadable, value.skippedLinks,
                if (value.limitReached) 1 else 0, if (value.complete) 1 else 0, value.scannedAtMillis,
            ).joinToString(",")
        val categories =
            value.categories.entries.joinToString(";") { (category, usage) ->
                "${category.name}:${usage.bytes}:${usage.files}"
            }
        return "$numbers/$categories"
    }

    private fun decodeSnapshot(encoded: String): StorageUsageSnapshot? {
        val (numberText, categoryText) = encoded.split('/').takeIf { it.size == 2 } ?: return null
        val numbers = numberText.split(',').map { it.toLongOrNull() ?: return null }
        if (numbers.size != SNAPSHOT_FIELDS || numbers[8] !in 0..1 || numbers[9] !in 0..1) return null
        val categories = HashMap<StorageUsageCategory, CategoryUsage>()
        if (categoryText.isNotEmpty()) {
            for (entry in categoryText.split(';')) {
                val fields = entry.split(':').takeIf { it.size == 3 } ?: return null
                val category = StorageUsageCategory.entries.firstOrNull { it.name == fields[0] } ?: return null
                val usage =
                    CategoryUsage(fields[1].toLongOrNull() ?: return null, fields[2].toLongOrNull() ?: return null)
                if (categories.put(category, usage) != null) return null
            }
        }
        return StorageUsageSnapshot(
            StorageCapacity(numbers[0], numbers[1], int(numbers[2]) ?: return null, int(numbers[3]) ?: return null),
            categories,
            numbers[4],
            int(numbers[5]) ?: return null,
            int(numbers[6]) ?: return null,
            int(numbers[7]) ?: return null,
            numbers[8] == 1L,
            numbers[9] == 1L,
            numbers[10],
        )
    }

    private fun int(value: Long): Int? = value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private fun encodeText(value: String): String =
        Base64.getEncoder().encodeToString(
            value.toByteArray(Charsets.UTF_8),
        )

    private fun decodeText(encoded: String): String? =
        try {
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
}
