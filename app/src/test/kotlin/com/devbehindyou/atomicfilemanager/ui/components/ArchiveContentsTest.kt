package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.usecase.ArchiveEntryInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveContentsTest {
    @Test
    fun listsImmediateChildrenIncludingImplicitAndEmptyFolders() =
        runBlocking {
            val entries =
                listOf(
                    entry("notes.txt"),
                    entry("trip/photos/one.jpg"),
                    entry("trip/photos/two.jpg"),
                    entry("trip/summary.txt"),
                    entry("empty/", directory = true),
                )

            assertEquals(listOf("trip", "empty", "notes.txt"), archiveFolderRows(entries, "").map { it.name })
            assertEquals(listOf("photos", "summary.txt"), archiveFolderRows(entries, "trip").map { it.name })
            assertEquals(listOf("one.jpg", "two.jpg"), archiveFolderRows(entries, "trip/photos").map { it.name })
            assertEquals(emptyList<ArchiveBrowserRow>(), archiveFolderRows(entries, "empty"))
        }

    @Test
    fun displayGroupingPreservesEachOriginalFilePathAndDistinguishesAFileFromAFolder() =
        runBlocking {
            val entries = listOf(entry("trip//notes.txt"), entry("trip/notes.txt"), entry("trip"))
            assertEquals(listOf("folder:trip", "file:trip"), archiveFolderRows(entries, "").map { it.key })
            val rows = archiveFolderRows(entries, "trip")
            assertEquals(listOf("file:trip//notes.txt", "file:trip/notes.txt"), rows.map { it.key })
            assertEquals(entries.take(2), rows.map { it.file })
        }

    private fun entry(path: String, directory: Boolean = false): ArchiveEntryInfo =
        ArchiveEntryInfo(path.trimEnd('/').substringAfterLast('/'), path, directory, 20, 10, 0)
}
