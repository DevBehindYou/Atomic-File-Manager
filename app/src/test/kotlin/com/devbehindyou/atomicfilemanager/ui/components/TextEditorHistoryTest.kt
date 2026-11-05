package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextEditorHistoryTest {
    @Test
    fun `undo and redo restore Unicode selection without reviving an IME composition`() {
        val history = TextEditorHistory()
        val selected = TextFieldValue("a🌍b", TextRange(1, 3), TextRange(1, 3))
        val edited = TextFieldValue("aकिरण🙂b", TextRange(7))
        history.record(selected)
        val previous = checkNotNull(history.undo(edited))
        assertEquals(selected.copy(composition = null), previous)
        assertNull(previous.composition)
        assertFalse(history.canUndo)
        assertEquals(edited, history.redo(previous))
        assertFalse(history.canRedo)
    }

    @Test
    fun `twenty entry cap covers both directions without skipping nearest edits`() {
        val history = TextEditorHistory()
        repeat(25) { history.record(TextFieldValue("value $it")) }
        var current = TextFieldValue("value 25")
        repeat(20) { index ->
            current = checkNotNull(history.undo(current))
            assertEquals("value ${24 - index}", current.text)
            assertEquals(20, history.retainedEntries)
        }
        assertFalse(history.canUndo)
        repeat(20) { index ->
            current = checkNotNull(history.redo(current))
            assertEquals("value ${6 + index}", current.text)
            assertEquals(20, history.retainedEntries)
        }
        assertFalse(history.canRedo)
    }

    @Test
    fun `UTF16 payload budget counts surrogate pairs and both history directions`() {
        val history = TextEditorHistory(maxTextBytes = 6)
        history.record(TextFieldValue("a"))
        history.record(TextFieldValue("🌍"))
        assertEquals(6L, history.retainedTextBytes)
        val selected = checkNotNull(history.undo(TextFieldValue("b")))
        assertEquals("🌍", selected.text)
        assertEquals(4L, history.retainedTextBytes)
        assertEquals("b", history.redo(selected)?.text)
        assertEquals(6L, history.retainedTextBytes)
        history.record(TextFieldValue("cc"))
        assertEquals(4L, history.retainedTextBytes)
        assertEquals(1, history.retainedEntries)
    }

    @Test
    fun `new edit clears redo and keeps only reachable predecessors`() {
        val history = TextEditorHistory()
        history.record(TextFieldValue("first"))
        history.record(TextFieldValue("second"))
        val second = checkNotNull(history.undo(TextFieldValue("third")))
        history.record(second)
        assertFalse(history.canRedo)
        assertEquals("second", history.undo(TextFieldValue("branch"))?.text)
        assertEquals("first", history.undo(second)?.text)
    }

    @Test
    fun `unretainable predecessor is a barrier instead of undoing past an omitted edit`() {
        val history = TextEditorHistory(maxTextBytes = 6)
        history.record(TextFieldValue("a"))
        history.record(TextFieldValue("long"))
        assertFalse(history.canUndo)
        assertEquals(0L, history.retainedTextBytes)
        history.record(TextFieldValue("b"))
        assertEquals("b", history.undo(TextFieldValue("huge"))?.text)
        assertFalse(history.canRedo)
    }

    @Test
    fun `clear releases both directions and all retained payload`() {
        val history = TextEditorHistory()
        history.record(TextFieldValue("first"))
        history.record(TextFieldValue("second"))
        history.undo(TextFieldValue("third"))
        assertTrue(history.canUndo)
        assertTrue(history.canRedo)
        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(0, history.retainedEntries)
        assertEquals(0L, history.retainedTextBytes)
    }
}
