package com.devbehindyou.atomicfilemanager.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStackTest {
    @Test
    fun `push and pop follow last in first out`() {
        val stack =
            RouteStack()
                .push(AtomicRoute.Analysis("file:/storage/emulated/0"))
                .push(AtomicRoute.Category("IMAGES"))

        assertEquals(AtomicRoute.Category("IMAGES"), stack.top)
        assertEquals(AtomicRoute.Analysis("file:/storage/emulated/0"), stack.pop().top)
        assertTrue(stack.pop().pop().isEmpty)
    }

    @Test
    fun `pushing the route on top again does nothing`() {
        val stack = RouteStack().push(AtomicRoute.PrivateFiles).push(AtomicRoute.PrivateFiles)

        assertEquals(1, stack.entries.size)
    }

    @Test
    fun `popping an empty stack stays empty`() {
        assertTrue(RouteStack().pop().isEmpty)
    }

    @Test
    fun `replace top swaps only the visible screen`() {
        val stack =
            RouteStack()
                .push(AtomicRoute.Analysis("file:/a"))
                .push(AtomicRoute.Category("PDFS"))
                .replaceTop(AtomicRoute.Category("TEXT"))

        assertEquals(listOf(AtomicRoute.Analysis("file:/a"), AtomicRoute.Category("TEXT")), stack.entries)
    }

    @Test
    fun `encode and decode round trip, including colons in paths`() {
        val stack =
            RouteStack()
                .push(AtomicRoute.Analysis("saf:content://tree/primary:Download"))
                .push(AtomicRoute.Category("VIDEOS"))
                .push(AtomicRoute.RecentlyOpened)
                .push(AtomicRoute.PrivateFiles)
                .push(AtomicRoute.Operations)
                .push(AtomicRoute.PinnedLocations)
                .push(AtomicRoute.Apps)
                .push(AtomicRoute.FolderCompare("file:/storage/emulated/0/DCIM", "sftp:nas:/home/me/Photos"))
                .push(AtomicRoute.WifiShare)
                .push(AtomicRoute.StorageTool(StorageToolPage.CONNECTIONS))

        assertEquals(stack, RouteStack.decode(stack.encode()))
    }

    @Test
    fun `unknown or malformed entries are dropped`() {
        val decoded = RouteStack.decode(listOf("private", "bogus:1", "category:", "analysis", "compare:file:/a"))

        assertEquals(listOf<AtomicRoute>(AtomicRoute.PrivateFiles), decoded.entries)
        assertNull(AtomicRoute.decode(""))
    }
}
