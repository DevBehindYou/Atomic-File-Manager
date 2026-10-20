package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowseFileFilterTest {
    @Test
    fun `category filters retain navigation folders and match collection metadata`() {
        val folder = node("Pictures", directory = true)
        val image = node("photo", mime = "image/jpeg")
        val video = node("holiday.mkv")
        val audio = node("recording.flac")
        val doc = node("letter.docx")
        val pdf = node("report.pdf")
        val archive = node("backup.7z")
        val pairs =
            listOf(
                BrowseFileFilter.IMAGES to image,
                BrowseFileFilter.VIDEOS to video,
                BrowseFileFilter.AUDIO to audio,
                BrowseFileFilter.DOCUMENTS to doc,
                BrowseFileFilter.DOCUMENTS to pdf,
                BrowseFileFilter.ARCHIVES to archive,
            )
        pairs.forEach { (filter, match) ->
            assertTrue(filter.matches(folder), "$filter must retain folders")
            assertTrue(filter.matches(match), "$filter must include ${match.name}")
            assertFalse(filter.matches(node("unknown.bin")), "$filter must exclude unrelated files")
        }
    }

    @Test
    fun `strict folder and file filters do not retain the opposite kind`() {
        val folder = node("Documents", directory = true)
        val file = node("notes.txt")
        assertTrue(BrowseFileFilter.ALL.matches(folder))
        assertTrue(BrowseFileFilter.ALL.matches(file))
        assertTrue(BrowseFileFilter.FOLDERS.matches(folder))
        assertFalse(BrowseFileFilter.FOLDERS.matches(file))
        assertTrue(BrowseFileFilter.FILES.matches(file))
        assertFalse(BrowseFileFilter.FILES.matches(folder))
    }

    private fun node(
        name: String,
        directory: Boolean = false,
        mime: String? = null,
    ) = FileNode(
        FileNodeId.file("/storage/example/$name"),
        name,
        name,
        mime,
        0,
        0,
        directory,
        false,
        null,
        StorageType.INTERNAL_SHARED,
        AccessFlags.READ_ONLY,
        null,
        null,
    )
}
