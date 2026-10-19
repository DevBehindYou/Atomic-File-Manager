package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreviewTypeTest {
    @Test
    fun `Word packages route to DOCX before text and archive matching`() {
        assertEquals(PreviewType.DOCX, resolvePreviewType(node("REPORT.DOCX", "application/octet-stream")))
        assertEquals(PreviewType.DOCX, resolvePreviewType(node("report", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
    }

    @Test
    fun `unsupported Office containers never become UTF8 text`() {
        for ((name, mime) in listOf(
            "book.xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "slides.pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "legacy.doc" to "application/msword", "macro.docm" to "application/vnd.ms-word.document.macroEnabled.12",
            "document.odt" to "application/octet-stream",
        )) assertEquals(PreviewType.GENERIC, resolvePreviewType(node(name, mime)))
    }

    @Test
    fun `structured text and source code remain readable`() {
        for ((name, mime) in listOf("data" to "application/problem+json", "data" to "application/xml", "config.toml" to null, "app.tsx" to null)) {
            assertEquals(PreviewType.TEXT, resolvePreviewType(node(name, mime)))
        }
        assertEquals(PreviewType.MARKDOWN, resolvePreviewType(node("readme.markdown", "text/plain")))
    }

    @Test
    fun `media PDF and archives preserve their viewers`() {
        for ((name, type) in listOf("image.avif" to PreviewType.IMAGE, "clip.m4v" to PreviewType.VIDEO, "track.flac" to PreviewType.AUDIO, "read.pdf" to PreviewType.PDF, "data.7z" to PreviewType.ARCHIVE)) {
            assertEquals(type, resolvePreviewType(node(name, null)))
        }
        assertEquals(PreviewType.GENERIC, resolvePreviewType(node("folder.pdf", null).copy(isDirectory = true)))
    }

    private fun node(name: String, mime: String?) = FileNode(
        FileNodeId.file("/fixture/$name"), name, name, mime, 1, 0, false, false, null,
        StorageType.INTERNAL_SHARED, AccessFlags.FULL, null, null,
    )
}
