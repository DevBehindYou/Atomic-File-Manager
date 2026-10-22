package com.devbehindyou.atomicfilemanager.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowseTabsTest {
    private val home = "file:/storage/emulated/0"

    @Test
    fun `opening adds after the active tab, switches to it and stops at four`() {
        var tabs = BrowseTabsState.single(home)
        tabs = tabs.open("file:/storage/emulated/0/Download", "Download")
        assertEquals(listOf(0, 1), tabs.tabs.map { it.id })
        assertEquals(1, tabs.activeId)

        tabs = tabs.select(0).open("file:/storage/emulated/0/DCIM")
        assertEquals(listOf(0, 2, 1), tabs.tabs.map { it.id })
        tabs = tabs.open("file:/x")
        assertFalse(tabs.canOpenMore)
        assertSame(tabs, tabs.open("file:/y"))
    }

    @Test
    fun `closing moves to the left neighbour and never closes the last tab`() {
        val tabs = BrowseTabsState.single(home).open("file:/a").open("file:/b")
        val closed = tabs.close(2)
        assertEquals(1, closed.activeId)
        assertEquals(0, closed.close(1).activeId)
        val one = BrowseTabsState.single(home)
        assertSame(one, one.close(0))
        assertEquals(2, tabs.close(0).activeId)
    }

    @Test
    fun `an open from elsewhere reuses the active tab and bumps its request`() {
        val tabs = BrowseTabsState.single(home).openInActive("file:/storage/emulated/0/Music")
        assertEquals("file:/storage/emulated/0/Music", tabs.active.folderRaw)
        assertEquals(1, tabs.active.openRequest)
    }

    @Test
    fun `tabs survive encoding and bad saves fall back to one tab`() {
        val tabs = BrowseTabsState.single(home).open("file:/a b/c", "c").retitle(0, "Internal").openInActive("file:/d")
        assertEquals(tabs, BrowseTabsState.decode(tabs.encode(), home))
        assertEquals(BrowseTabsState.single(home), BrowseTabsState.decode(null, home))
        assertEquals(BrowseTabsState.single(home), BrowseTabsState.decode("garbage", home))
        assertEquals("Download", BrowseTabText.label(BrowseTab(1, "file:/storage/emulated/0/Download/")))
        assertEquals("Photos", BrowseTabText.label(BrowseTab(1, "file:/x", title = "Photos")))
    }

    @Test
    fun `closing the highest tab and reopening the same folder creates a fresh session`() {
        val opened = BrowseTabsState.single(home).open("file:/same")
        val closed = opened.active
        val reopened = opened.close(closed.id).open(closed.folderRaw).active
        assertEquals(closed.id, reopened.id)
        assertEquals(closed.folderRaw, reopened.folderRaw)
        assertTrue(closed.sessionId.isNotEmpty())
        assertNotEquals(closed.sessionId, reopened.sessionId)
        assertNotEquals(closed.stateKey, reopened.stateKey)
    }

    @Test
    fun `session identity survives selection retitling explicit root changes and recreation`() {
        val original = BrowseTabsState.single(home).open("file:/first").open("file:/second")
        val session = original.active.sessionId
        val changed = original.retitle(original.activeId, "New title").openInActive("file:/replacement")
        val restored = BrowseTabsState.decode(changed.encode(), home)
        assertEquals(changed, restored)
        assertEquals(session, restored.active.sessionId)
        assertEquals(changed.active.stateKey, restored.active.stateKey)
        assertEquals(original.tabs.first(), restored.select(0).active)
        assertEquals(original.tabs[1], restored.tabs[1])
    }

    @Test
    fun `legacy saved tabs retain their keys and gain sessions only when a new tab opens`() {
        val legacy = "1\u001E0\u001F2\u001FInternal\u001F$home\u001E1\u001F0\u001FPhotos\u001Ffile:/photos"
        val restored = BrowseTabsState.decode(legacy, home)
        assertEquals("", restored.tabs.first().stateKey)
        assertEquals("tab1:", restored.active.stateKey)
        assertTrue(restored.tabs.all { it.sessionId.isEmpty() })
        assertEquals(restored, BrowseTabsState.decode(restored.encode(), home))
        val fresh = restored.open("file:/photos")
        assertTrue(fresh.active.sessionId.isNotEmpty())
        assertEquals(restored.tabs, fresh.tabs.filterNot { it.id == fresh.activeId })
    }

    @Test
    fun `malformed optional sessions preserve usable legacy tabs and never become key syntax`() {
        val saved = "1\u001E0\u001F0\u001FInternal\u001F$home\u001Fbad-token" +
            "\u001E1\u001F0\u001FPhotos\u001Ffile:/photos\u001Ftab0:"
        val restored = BrowseTabsState.decode(saved, home)
        assertEquals(listOf(0, 1), restored.tabs.map { it.id })
        assertEquals(listOf("", "tab1:"), restored.tabs.map { it.stateKey })
        assertEquals("file:/photos", restored.active.folderRaw)
    }

    @Test
    fun `repeated close reopen cycles keep sibling sessions stable and never reuse opened sessions`() {
        var state = BrowseTabsState.single(home).open("file:/sibling")
        val sibling = state.active
        val sessions = mutableSetOf<String>()
        repeat(100) {
            state = BrowseTabsState.decode(state.open("file:/same").encode(), home)
            assertTrue(sessions.add(state.active.sessionId))
            state = state.close(state.activeId)
            assertEquals(sibling, state.active)
        }
        assertEquals(100, sessions.size)
    }
}
