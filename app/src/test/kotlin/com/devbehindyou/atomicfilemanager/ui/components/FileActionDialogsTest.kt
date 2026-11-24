package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FileActionDialogsTest {
    @Test
    fun `usable names pass`() {
        assertNull(fileNameError("Tickets 2026"))
        assertNull(fileNameError(".nomedia"))
        assertNull(fileNameError("  padded  "))
    }

    @Test
    fun `bad names explain how to fix them`() {
        assertEquals("Enter a name.", fileNameError("   "))
        assertEquals("A name can't contain \"/\". Try \"Tickets 2026\".", fileNameError("Tickets/2026"))
        assertEquals("\"..\" is reserved. Choose another name.", fileNameError(".."))
        assertEquals("That name is too long. Shorten it.", fileNameError("a".repeat(256)))
    }

    private fun node(
        size: Long,
        directory: Boolean = false,
    ) = FileNode(
        id = FileNodeId.file("/storage/emulated/0/Download/Boarding-pass-BLR.pdf"),
        name = "Boarding-pass-BLR.pdf",
        displayName = "Boarding-pass-BLR.pdf",
        mimeType = "application/pdf",
        size = size,
        modifiedAt = 0L,
        isDirectory = directory,
        isHidden = false,
        parentId = null,
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.READ_ONLY,
        childCount = if (directory) 3 else null,
        extras = null,
    )

    @Test
    fun `file facts give exact bytes, mono path and access`() {
        val facts = fileFacts(node(219_136)).associate { it.label to it.value }

        assertEquals("/storage/emulated/0/Download/Boarding-pass-BLR.pdf", facts["Location"])
        assertEquals("214.0 KB (219,136 bytes)", facts["Size"])
        assertEquals("Read", facts["Access"])
        assertEquals("Unknown", facts["Modified"])
    }

    @Test
    fun `unknown size and folders are described honestly`() {
        assertEquals("Unknown", fileFacts(node(-1)).first { it.label == "Size" }.value)
        val folder = fileFacts(node(-1, directory = true)).associate { it.label to it.value }
        assertEquals("3", folder["Items"])
        assertEquals("Not calculated", folder["Size"])
    }

    @Test
    fun `provider identifiers stay intact and do not masquerade as paths`() {
        val provider = FileNodeId.saf("content://documents/tree/p%3A/document/p%3Asecret")
        val linked = node(-1).copy(id = provider, mimeType = null, access = AccessFlags.NONE)
        val facts = fileFacts(linked).associate { it.label to it.value }

        assertEquals(provider.raw, displayPath(linked))
        assertEquals("Unavailable", facts["Location"])
        assertEquals("Unknown", facts["Type"])
        assertEquals("None", facts["Access"])
    }

    @Test
    fun `details distinguish actual folder data from unavailable values`() {
        val details = node(0, directory = true).copy(childCount = null, access = AccessFlags.FULL)
        val facts = fileFacts(details).associate { it.label to it.value }

        assertEquals("Folder", facts["Type"])
        assertEquals("0 B (0 bytes)", facts["Size"])
        assertEquals("Not counted", facts["Items"])
        assertEquals("Read · Write · Rename · Delete", facts["Access"])
    }

    @Test
    fun `removable drives add the FAT name rules`() {
        assertNull(fileNameError("10:30 meeting"))
        assertEquals(
            "USB drives and SD cards can't store \":\" in a name.",
            fileNameError("10:30 meeting", removable = true),
        )
        assertNull(fileNameError("Report", removable = true))
    }
}
