package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextDraftsTest {
    private val utf8Lf = TextFormat(TextEncoding.UTF8, LineEnding.LF)

    @Test
    fun `source revision follows text and format, not identity`() {
        val base = TextDrafts.sourceRevision("hello", utf8Lf)
        assertEquals(base, TextDrafts.sourceRevision("hello", utf8Lf))
        assertNotEquals(base, TextDrafts.sourceRevision("hello!", utf8Lf))
        assertNotEquals(base, TextDrafts.sourceRevision("hello", utf8Lf.copy(lineEnding = LineEnding.CRLF)))
        assertNotEquals(base, TextDrafts.sourceRevision("hello", utf8Lf.copy(encoding = TextEncoding.LATIN1)))
        assertEquals(64, base.length)
    }

    @Test
    fun `hidden, hide-from-gallery and private vault files are never drafted`() {
        assertTrue(TextDrafts.allows(node("file:/storage/emulated/0/Documents/notes.txt"), DEFAULT))
        assertTrue(TextDrafts.allows(node("sftp://host/home/me/notes.txt"), DEFAULT))
        assertFalse(TextDrafts.allows(node("file:/storage/emulated/0/Documents/.env", hidden = true), DEFAULT))
        assertFalse(TextDrafts.allows(node("file:/storage/emulated/0/Documents/.AtomicHidden/notes.txt"), DEFAULT))
        assertFalse(TextDrafts.allows(node("file:/storage/emulated/0/Atomic File Manager/Hidden/notes.txt"), DEFAULT))
        assertFalse(TextDrafts.allows(node("file:/storage/emulated/0/Custom/Vault/notes.txt"), "/Custom/Vault/"))
        assertFalse(
            TextDrafts.allows(
                node("file:/data/user/0/com.devbehindyou.atomicfilemanager/files/private_storage/a.txt"),
                DEFAULT,
            ),
        )
        assertFalse(
            TextDrafts.allows(
                node(SAF_TREE + "primary%253A.AtomicHidden%2Fa.txt"),
                DEFAULT,
            ),
        )
        assertTrue(TextDrafts.allows(node("file:/storage/emulated/0/notes.txt"), "  "))
    }

    private fun node(
        raw: String,
        hidden: Boolean = false,
    ) = FileNode(
        id = FileNodeId(raw), name = raw.substringAfterLast('/'), displayName = raw.substringAfterLast('/'),
        mimeType = "text/plain", size = 1, modifiedAt = 0, isDirectory = false, isHidden = hidden,
        parentId = null, storageType = StorageType.INTERNAL_SHARED, access = AccessFlags.FULL,
        childCount = null, extras = null,
    )

    private companion object {
        const val DEFAULT = "Atomic File Manager/Hidden"
        const val SAF_TREE = "saf:content%3A%2F%2Fcom.android.externalstorage.documents%2Ftree%2F"
    }
}
