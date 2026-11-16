package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.RecentItem
import com.devbehindyou.atomicfilemanager.domain.model.RecentSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShortcutTextTest {
    @Test
    fun `a missing favourite says so, an unchecked one waits`() {
        assertEquals("Missing · tap to remove", ShortcutText.favouriteMeta(node = null, known = true))
        assertEquals("Checking…", ShortcutText.favouriteMeta(node = null, known = false))
    }

    @Test
    fun `recent meta says where it came from`() {
        val opened = RecentItem(FileNodeId.file("/a.pdf"), "a.pdf", null, 2_048, 0, RecentSource.OPENED)
        assertTrue(ShortcutText.recentMeta(opened).startsWith("2.0 KB · Opened "))
        val media = opened.copy(size = -1, source = RecentSource.MEDIA)
        assertTrue(ShortcutText.recentMeta(media).startsWith("Modified "))
    }
}
