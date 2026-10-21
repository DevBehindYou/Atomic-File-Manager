package com.devbehindyou.atomicfilemanager.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class StorageUsageTest {
    @ParameterizedTest
    @CsvSource(
        "photo.HEIC,IMAGES", "clip.mkv,VIDEOS", "recording.opus,AUDIO", ".private.PDF,PDFS",
        "sheet.xlsx,DOCUMENTS", "slides.pptx,DOCUMENTS", ".backup.tar.gz,ARCHIVES", "app.APK,APKS",
        "config.TOML,TEXT", "source.kts,TEXT", "book.epub,EBOOKS", "font.woff2,FONTS", "unknown.blob,OTHER",
    )
    fun `named and hidden files belong to one exclusive family`(name: String, expected: StorageUsageCategory) {
        assertEquals(expected, storageUsageCategory(name))
    }

    @ParameterizedTest
    @CsvSource(
        "image/jpeg,IMAGES", "video/mp4,VIDEOS", "audio/flac,AUDIO", "application/pdf,PDFS",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document,DOCUMENTS",
        "application/epub+zip,EBOOKS", "font/woff2,FONTS", "application/json,TEXT",
        "application/vnd.android.package-archive,APKS", "application/x-7z-compressed,ARCHIVES",
        "application/octet-stream,OTHER", "application/x-unfamiliar,OTHER",
    )
    fun `extensionless files use MIME metadata without being read`(mime: String, expected: StorageUsageCategory) {
        assertEquals(expected, storageUsageCategory("export", mime))
    }

    @Test
    fun `known names preserve collection precedence and MIME normalization ignores case and parameters`() {
        assertEquals(StorageUsageCategory.APKS, storageUsageCategory("installer.apk", "application/zip"))
        assertEquals(StorageUsageCategory.PDFS, storageUsageCategory(".hidden.pdf", "text/plain"))
        assertEquals(StorageUsageCategory.PDFS, storageUsageCategory("report", " Application/PDF; version=1.7 "))
        assertEquals(StorageUsageCategory.OTHER, storageUsageCategory(".nomedia", null))
    }

    @Test
    fun `PDF text and ebooks never contribute again to the documents umbrella`() {
        val categories = listOf("manual.pdf", "notes.txt", "book.epub", "report.docx")
            .groupingBy { storageUsageCategory(it) }.eachCount()
        assertEquals(
            mapOf(StorageUsageCategory.PDFS to 1, StorageUsageCategory.TEXT to 1,
                StorageUsageCategory.EBOOKS to 1, StorageUsageCategory.DOCUMENTS to 1),
            categories,
        )
    }

    @Test
    fun `usage additions saturate instead of overflowing and ignore negative raw metadata`() {
        assertEquals(Long.MAX_VALUE, storageUsageAdd(Long.MAX_VALUE - 1, 100))
        assertEquals(0L, storageUsageAdd(-5, -1))
        assertEquals(3L, storageUsageAdd(-5, 3))
        val snapshot = snapshot(mapOf(
            StorageUsageCategory.IMAGES to CategoryUsage(Long.MAX_VALUE - 1, 1),
            StorageUsageCategory.OTHER to CategoryUsage(10, 1),
        ))
        assertEquals(Long.MAX_VALUE, snapshot.readableBytes)
    }

    @Test
    fun `overflowing logical totals keep meaningful fractions and cannot claim a physical remainder`() {
        val result = snapshot(mapOf(
            StorageUsageCategory.IMAGES to CategoryUsage(Long.MAX_VALUE, 1),
            StorageUsageCategory.OTHER to CategoryUsage(Long.MAX_VALUE, 1),
        )).copy(capacity = StorageCapacity(Long.MAX_VALUE, 0, 1))
        assertEquals(0.5f, result.fraction(StorageUsageCategory.IMAGES))
        assertNull(result.unattributedUsedEstimate)
    }

    @Test
    fun `physical usage and category fractions keep their different denominators`() {
        val result = snapshot(mapOf(
            StorageUsageCategory.IMAGES to CategoryUsage(20, 2),
            StorageUsageCategory.OTHER to CategoryUsage(10, 1),
        ))
        assertEquals(80L, result.capacity.usedBytes)
        assertEquals(0.8f, result.capacity.fraction)
        assertEquals(30L, result.readableBytes)
        assertEquals(50L, result.unattributedUsedEstimate)
        assertEquals(2f / 3f, result.fraction(StorageUsageCategory.IMAGES))
        assertEquals(0f, result.fraction(StorageUsageCategory.PDFS))
        assertEquals(0f, snapshot(emptyMap()).fraction(StorageUsageCategory.IMAGES))
    }

    @Test
    fun `unknown physical capacity and logical sizes larger than physical usage have no remainder claim`() {
        val large = snapshot(mapOf(StorageUsageCategory.OTHER to CategoryUsage(90, 1)))
        assertNull(large.unattributedUsedEstimate)
        assertNull(large.copy(capacity = StorageCapacity(100, 20, 2, 1)).unattributedUsedEstimate)
        assertEquals(0L, StorageCapacity(0, 0, 1, 1).usedBytes)
        assertEquals(0f, StorageCapacity(0, 0, 1, 1).fraction)
        assertThrows(IllegalArgumentException::class.java) { StorageCapacity(100, 101, 1) }
        assertThrows(IllegalArgumentException::class.java) { CategoryUsage(-1, 0) }
    }

    private fun snapshot(categories: Map<StorageUsageCategory, CategoryUsage>) = StorageUsageSnapshot(
        capacity = StorageCapacity(100, 20, 1),
        categories = categories,
        scannedFiles = 3,
        scannedDirectories = 1,
        unreadable = 0,
        skippedLinks = 0,
        limitReached = false,
        complete = true,
        scannedAtMillis = 1_000,
    )
}
