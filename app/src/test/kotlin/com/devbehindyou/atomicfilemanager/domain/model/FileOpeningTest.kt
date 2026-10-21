package com.devbehindyou.atomicfilemanager.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FileOpeningTest {
    private val builtIn = FileOpenTarget(FileOpenMode.BUILT_IN)
    private val ask = FileOpenTarget(FileOpenMode.ASK)
    private val external = FileOpenTarget(FileOpenMode.EXTERNAL, "reader.app/reader.app.View", "Reader")

    @Test
    fun `extension MIME family then default precedence is explicit`() {
        val node = node("Report.PDF", " Application/PDF; version=1.7 ")
        val extension = keyFor(node, FileOpenRuleScope.EXTENSION)!!
        val mime = keyFor(node, FileOpenRuleScope.MIME)!!
        val family = keyFor(node, FileOpenRuleScope.FAMILY)!!
        val policy = FileOpeningPreferences(FileOpenMode.ASK)
            .withRule(family, builtIn).withRule(mime, external).withRule(extension, ask)
        assertEquals(ask, policy.targetFor(node))
        assertEquals(external, policy.withoutRule(extension).targetFor(node))
        assertEquals(builtIn, policy.withoutRule(extension).withoutRule(mime).targetFor(node))
        assertEquals(ask, FileOpeningPreferences(FileOpenMode.ASK).targetFor(node))
    }

    @Test
    fun `known MIME controls family unless the user explicitly overrides an extension`() {
        val misleading = node("movie.txt", "video/mp4")
        assertEquals(FileOpenFamily.VIDEO, fileOpenFamily(misleading))
        val policy = FileOpeningPreferences().withRule(FileOpenRuleKey(FileOpenRuleScope.EXTENSION, "txt"), external)
        assertEquals(external, policy.targetFor(misleading))
        assertEquals(FileOpenFamily.PDF, fileOpenFamily(node("book.PDF", "application/octet-stream")))
    }

    @Test
    fun `directories never inherit external file associations`() {
        val directory = node("folder.pdf", "application/pdf").copy(isDirectory = true)
        assertEquals(builtIn, FileOpeningPreferences(FileOpenMode.EXTERNAL).targetFor(directory))
        FileOpenRuleScope.entries.forEach { assertNull(keyFor(directory, it)) }
    }

    @Test
    fun `invalid or ambiguous keys are not silently normalized into another rule`() {
        assertEquals(FileOpenRuleKey(FileOpenRuleScope.EXTENSION, "pdf"), parseFileOpenRuleKey(FileOpenRuleScope.EXTENSION, " .PDF "))
        assertNull(parseFileOpenRuleKey(FileOpenRuleScope.EXTENSION, "tar.gz"))
        assertNull(parseFileOpenRuleKey(FileOpenRuleScope.EXTENSION, "../pdf"))
        assertNull(parseFileOpenRuleKey(FileOpenRuleScope.MIME, "image/*"))
        assertNull(parseFileOpenRuleKey(FileOpenRuleScope.MIME, "application/octet-stream"))
        assertNull(keyFor(node("file", "application/" + "x".repeat(200)), FileOpenRuleScope.MIME))
        assertNull(fileExtension(".nomedia"))
        assertNull(fileExtension("report."))
    }

    @Test
    fun `resetting a rule preserves unrelated associations`() {
        val pdf = FileOpenRuleKey(FileOpenRuleScope.EXTENSION, "pdf")
        val text = FileOpenRuleKey(FileOpenRuleScope.MIME, "text/plain")
        val policy = FileOpeningPreferences().withRule(pdf, external).withRule(text, ask)
        assertEquals(mapOf(text to ask), policy.withoutRule(pdf).rules)
        assertEquals(mapOf(pdf to external, text to ask), policy.rules)
    }

    private fun node(name: String, mime: String?) = FileNode(
        FileNodeId.file("/fixture/$name"), name, name, mime, 1, 0, false, false, null,
        StorageType.INTERNAL_SHARED, AccessFlags.FULL, null, null,
    )
}
