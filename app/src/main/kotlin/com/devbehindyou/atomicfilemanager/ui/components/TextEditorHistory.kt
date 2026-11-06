package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.text.input.TextFieldValue

/** Full in-memory snapshots only; text payload is counted conservatively even when strings are shared. */
internal class TextEditorHistory(
    private val maxEntries: Int = 20,
    private val maxTextBytes: Long = 16L * 1024 * 1024,
) {
    private val undo = ArrayDeque<TextFieldValue>()
    private val redo = ArrayDeque<TextFieldValue>()
    private var textBytes = 0L
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
    internal val retainedEntries: Int get() = undo.size + redo.size
    internal val retainedTextBytes: Long get() = textBytes

    init {
        require(maxEntries > 0 && maxTextBytes > 0)
    }

    fun record(previous: TextFieldValue) {
        redo.forEach { textBytes -= bytes(it) }
        redo.clear()
        push(undo, previous)
    }

    fun undo(current: TextFieldValue): TextFieldValue? = restore(undo, redo, current)

    fun redo(current: TextFieldValue): TextFieldValue? = restore(redo, undo, current)

    fun clear() {
        undo.clear()
        redo.clear()
        textBytes = 0
    }

    private fun restore(
        from: ArrayDeque<TextFieldValue>,
        to: ArrayDeque<TextFieldValue>,
        current: TextFieldValue,
    ): TextFieldValue? {
        if (from.isEmpty()) return null
        val restored = from.removeLast()
        textBytes -= bytes(restored)
        push(to, current)
        return restored
    }

    private fun push(
        to: ArrayDeque<TextFieldValue>,
        value: TextFieldValue,
    ) {
        // An unretainable immediate predecessor cannot be skipped in favour of an older state.
        if (bytes(value) > maxTextBytes) {
            to.forEach { textBytes -= bytes(it) }
            to.clear()
            return
        }
        val snapshot = value.copy(composition = null)
        to.addLast(snapshot)
        textBytes += bytes(snapshot)
        val other = if (to === undo) redo else undo
        while (retainedEntries > maxEntries || textBytes > maxTextBytes) {
            val discard = if (to.size > 1 || other.isEmpty()) to else other
            textBytes -= bytes(discard.removeFirst())
        }
    }

    private fun bytes(value: TextFieldValue): Long = value.text.length.toLong() * 2
}
