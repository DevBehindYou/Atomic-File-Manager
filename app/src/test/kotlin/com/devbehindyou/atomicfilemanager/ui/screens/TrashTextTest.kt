package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.TrashEntry
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId

class TrashTextTest {
    private val day = 24L * 60 * 60 * 1000

    private fun entry(
        size: Long = 2_048,
        deletedAt: Long = 0L,
    ) = TrashEntry(
        id = "t",
        name = "notes.txt",
        isDirectory = false,
        size = size,
        originalParent = FileNodeId.file("/storage/emulated/0/Download"),
        trashedId = FileNodeId.file("/storage/emulated/0/.Atomic File Manager/Trash/t/notes.txt"),
        holderId = FileNodeId.file("/storage/emulated/0/.Atomic File Manager/Trash/t"),
        volumeRoot = FileNodeId.file("/storage/emulated/0"),
        deletedAt = deletedAt,
        operationId = "op",
    )

    @Test
    fun `origin disambiguates volumes and retention includes deleted time and size`() {
        val deleted = 1_000 * day
        val stored = entry(deletedAt = deleted)
        val otherVolume = stored.copy(originalParent = FileNodeId.file("/storage/1234-5678/Download"))
        assertEquals("Originally /storage/emulated/0/Download", TrashText.origin(stored))
        assertEquals("Originally /storage/1234-5678/Download", TrashText.origin(otherVolume))
        val meta = TrashText.retention(stored, retentionDays = 30, now = deleted + 7 * day)
        assertTrue(meta.endsWith(" · 2.0 KB · 23 days left"), meta)
        assertTrue(meta.startsWith("Deleted ${FileUtils.formatDate(deleted, ZoneId.systemDefault())}"))
    }

    @Test
    fun `expired items never show negative days`() {
        assertTrue(TrashText.retention(entry(), retentionDays = 7, now = 100 * day).endsWith("0 days left"))
    }

    @Test
    fun `summaries and snackbars count items`() {
        assertEquals("Nothing here", TrashText.summary(emptyList()))
        assertEquals("2 items · 4.0 KB", TrashText.summary(listOf(entry(), entry())))
        assertEquals("Moved 1 item to Trash.", TrashText.moved(1, 0))
        assertEquals("Moved 3 items to Trash; 1 failed.", TrashText.moved(3, 1))
        assertEquals("Restored 2 items.", TrashText.restored(2, 0))
        assertEquals("Deletes “notes.txt” permanently. This can't be undone.", TrashText.foreverBody(listOf(entry())))
    }
}
