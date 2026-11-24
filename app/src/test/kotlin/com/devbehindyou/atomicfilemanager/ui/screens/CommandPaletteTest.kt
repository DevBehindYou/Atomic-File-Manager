package com.devbehindyou.atomicfilemanager.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandPaletteTest {
    @Test
    fun `blank query lists every command in order`() {
        assertEquals(PaletteCommand.entries, CommandPalette.match("  "))
    }

    @Test
    fun `title prefix ranks first`() {
        assertEquals(PaletteCommand.TRASH, CommandPalette.match("tra").first())
        assertEquals(PaletteCommand.STORAGE, CommandPalette.match("stor").first())
        assertEquals(PaletteCommand.ANALYSIS, CommandPalette.match("storage an").first())
    }

    @Test
    fun `keywords find commands by other words`() {
        assertEquals(listOf(PaletteCommand.THEME), CommandPalette.match("dark"))
        assertEquals(listOf(PaletteCommand.NEW_FOLDER), CommandPalette.match("mkdir"))
        assertEquals(PaletteCommand.ARCHIVES, CommandPalette.match("zip").first())
        assertTrue(PaletteCommand.TRASH in CommandPalette.match("recycle bin"))
    }

    @Test
    fun `title words outrank keywords`() {
        // No title has a word starting "copy"; Operations lists it as a keyword.
        val copy = CommandPalette.match("copy")
        assertEquals(listOf(PaletteCommand.OPERATIONS), copy)
        // "files" starts the Files title and is a word of "APK files" and "Private files".
        val files = CommandPalette.match("files")
        assertEquals(PaletteCommand.FILES, files.first())
        assertTrue(PaletteCommand.PRIVATE in files && PaletteCommand.APKS in files)
    }

    @Test
    fun `every word must match and case does not matter`() {
        assertTrue(CommandPalette.match("trash theme").isEmpty())
        assertTrue(CommandPalette.match("xyz").isEmpty())
        assertEquals(CommandPalette.match("TRASH"), CommandPalette.match("trash"))
    }
}
