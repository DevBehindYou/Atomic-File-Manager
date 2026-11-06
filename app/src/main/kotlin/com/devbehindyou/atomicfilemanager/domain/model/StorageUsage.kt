package com.devbehindyou.atomicfilemanager.domain.model

import java.util.Locale

/** Exclusive metadata families: unlike collection filters, Documents does not include PDFs or text. */
enum class StorageUsageCategory(val label: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    PDFS("PDFs"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    APKS("APKs"),
    TEXT("Text and code"),
    EBOOKS("E-books"),
    FONTS("Fonts"),
    OTHER("Other files"),
}

/** Name/MIME only; hidden entries are included and nothing is opened or decoded. */
fun storageUsageCategory(
    name: String,
    mimeType: String? = null,
): StorageUsageCategory {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    usageExtensions[extension]?.let { return it }
    val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
    return when {
        mime == "application/vnd.android.package-archive" -> StorageUsageCategory.APKS
        mime.startsWith("image/") -> StorageUsageCategory.IMAGES
        mime.startsWith("video/") -> StorageUsageCategory.VIDEOS
        mime.startsWith("audio/") -> StorageUsageCategory.AUDIO
        mime.startsWith("font/") || mime in usageFontMimes -> StorageUsageCategory.FONTS
        mime == "application/pdf" -> StorageUsageCategory.PDFS
        mime in usageEbookMimes -> StorageUsageCategory.EBOOKS
        mime.startsWith("text/") || mime in usageCodeMimes || mime.endsWith("+json") || mime.endsWith("+xml") ->
            StorageUsageCategory.TEXT
        mime in usageArchiveMimes -> StorageUsageCategory.ARCHIVES
        mime.contains("officedocument") || mime.contains("opendocument") || mime in usageOfficeMimes ->
            StorageUsageCategory.DOCUMENTS
        else -> StorageUsageCategory.OTHER
    }
}

data class CategoryUsage(val bytes: Long = 0, val files: Long = 0) {
    init {
        require(bytes >= 0 && files >= 0)
    }
}

/** Filesystem capacity of deduplicated mounted volumes; unknown volumes contribute no invented bytes. */
data class StorageCapacity(
    val totalBytes: Long,
    val freeBytes: Long,
    val mountedVolumes: Int,
    val unknownVolumes: Int = 0,
) {
    init {
        require(totalBytes >= 0 && freeBytes in 0..totalBytes)
        require(mountedVolumes >= 0 && unknownVolumes in 0..mountedVolumes)
    }

    val usedBytes: Long get() = totalBytes - freeBytes
    val fraction: Float get() = if (totalBytes == 0L) 0f else (usedBytes.toDouble() / totalBytes).toFloat()
}

/** Complete describes this bounded readable-shared-file scan, never all system/app/private data. */
data class StorageUsageSnapshot(
    val capacity: StorageCapacity,
    val categories: Map<StorageUsageCategory, CategoryUsage>,
    val scannedFiles: Long,
    val scannedDirectories: Int,
    val unreadable: Int,
    val skippedLinks: Int,
    val limitReached: Boolean,
    val complete: Boolean,
    val scannedAtMillis: Long,
) {
    init {
        require(scannedFiles >= 0 && scannedDirectories >= 0 && unreadable >= 0 && skippedLinks >= 0)
    }

    val readableBytes: Long get() = categories.values.fold(0L) { sum, usage -> storageUsageAdd(sum, usage.bytes) }

    /** An estimate only: sparse files/hard links can make logical file sizes exceed physical used bytes. */
    val unattributedUsedEstimate: Long?
        get() {
            if (capacity.unknownVolumes != 0) return null
            var remaining = capacity.usedBytes
            for (usage in categories.values) {
                if (usage.bytes > remaining) return null
                remaining -= usage.bytes
            }
            return remaining
        }

    fun fraction(category: StorageUsageCategory): Float {
        val total = categories.values.sumOf { it.bytes.toDouble() }
        return if (total == 0.0) 0f else ((categories[category]?.bytes ?: 0L).toDouble() / total).toFloat()
    }
}

/** Nonnegative saturating aggregation keeps malformed/very large metadata from wrapping usage totals. */
fun storageUsageAdd(
    a: Long,
    b: Long,
): Long {
    val left = a.coerceAtLeast(0L)
    val right = b.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}

private val usageExtensions =
    buildMap {
        fun add(
            category: StorageUsageCategory,
            words: String,
        ) {
            words.split(' ').forEach { put(it, category) }
        }
        add(StorageUsageCategory.IMAGES, "jpg jpeg png gif webp heic heif avif bmp svg tif tiff dng raw ico")
        add(StorageUsageCategory.VIDEOS, "mp4 mkv webm avi mov 3gp m4v ts mpeg mpg wmv flv")
        add(StorageUsageCategory.AUDIO, "mp3 m4a aac flac wav ogg opus wma amr aiff mid midi")
        add(StorageUsageCategory.PDFS, "pdf")
        add(StorageUsageCategory.DOCUMENTS, "doc docx xls xlsx ppt pptx odt ods odp rtf pages numbers key")
        add(StorageUsageCategory.ARCHIVES, "zip rar 7z tar gz bz2 xz zst tgz tbz tbz2 txz iso")
        add(StorageUsageCategory.APKS, "apk apks xapk apkm")
        add(StorageUsageCategory.TEXT, "txt md markdown csv tsv json xml log html htm css js jsx tsx kt kts java py sh")
        add(
            StorageUsageCategory.TEXT,
            "yaml yml ini cfg conf properties toml rst go rs c cpp h hpp sql rb php swift dart lua",
        )
        add(StorageUsageCategory.EBOOKS, "epub mobi azw azw3 fb2")
        add(StorageUsageCategory.FONTS, "ttf otf woff woff2")
    }
private val usageArchiveMimes =
    setOf(
        "application/zip", "application/x-zip-compressed", "application/vnd.rar", "application/x-rar-compressed",
        "application/x-7z-compressed", "application/x-tar", "application/gzip", "application/x-gzip",
        "application/x-bzip2", "application/x-xz", "application/zstd", "application/x-iso9660-image",
    )
private val usageOfficeMimes =
    setOf(
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint",
        "application/rtf",
    )
private val usageCodeMimes =
    setOf(
        "application/json",
        "application/xml",
        "application/javascript",
        "application/sql",
        "application/x-sh",
    )
private val usageEbookMimes =
    setOf(
        "application/epub+zip",
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.ebook",
    )
private val usageFontMimes = setOf("application/font-sfnt", "application/font-woff", "application/vnd.ms-fontobject")
