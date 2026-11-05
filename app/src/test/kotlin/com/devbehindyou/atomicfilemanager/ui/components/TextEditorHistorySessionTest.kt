package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextEditorHistorySessionTest {
    @Test
    fun `selection-only updates keep redo and preserve the selection immediately before an edit`() =
        runTest {
            val session =
                TextEditorSession(editorTestNode(), this, StandardTestDispatcher(testScheduler), {
                    FileResult.Success(EditableText("original", EDITOR_TEST_FORMAT))
                }, { _, _, _ -> error("History must not save") })
            session.load()
            testScheduler.advanceUntilIdle()
            session.edit(TextFieldValue("original", TextRange(2, 5)))
            assertFalse(session.state.value.canUndo)
            session.edit(TextFieldValue("draft", TextRange(5)))
            session.undo()
            assertEquals(TextRange(2, 5), session.state.value.value.selection)
            assertFalse(session.state.value.dirty)
            session.edit(session.state.value.value.copy(selection = TextRange(1)))
            assertTrue(session.state.value.canRedo)
            session.redo()
            assertEquals("draft", session.state.value.value.text)
            session.undo()
            assertEquals(TextRange(1), session.state.value.value.selection)
            session.close()
        }

    @Test
    fun `history cannot edit during load save or after close and tracks saved baseline`() =
        runTest {
            val opened = CompletableDeferred<FileResult<EditableText>>()
            val saved = CompletableDeferred<FileResult<FileNode>>()
            val replacement = editorTestNode("file:/replacement")
            var saves = 0
            val session =
                TextEditorSession(editorTestNode(), this, StandardTestDispatcher(testScheduler), {
                    opened.await()
                }, { _, _, _ ->
                    saves++
                    saved.await()
                })
            session.load()
            testScheduler.runCurrent()
            session.undo()
            session.redo()
            session.edit(TextFieldValue("not admitted"))
            assertEquals("", session.state.value.value.text)
            opened.complete(FileResult.Success(EditableText("original", EDITOR_TEST_FORMAT)))
            testScheduler.advanceUntilIdle()
            session.edit(TextFieldValue("saved draft"))
            assertTrue(session.save())
            session.undo()
            session.redo()
            assertEquals("saved draft", session.state.value.value.text)
            testScheduler.runCurrent()
            saved.complete(FileResult.Success(replacement))
            testScheduler.advanceUntilIdle()
            session.undo()
            assertTrue(session.state.value.dirty)
            assertEquals(replacement, session.state.value.current)
            session.redo()
            assertFalse(session.state.value.dirty)
            assertEquals(1, saves)
            session.close()
            session.undo()
            session.redo()
            assertEquals("", session.state.value.value.text)
            assertFalse(session.state.value.canUndo)
            assertFalse(session.state.value.canRedo)
        }

    @Test
    fun `failed save keeps history and new edits retire redo without changing format or identity`() =
        runTest {
            val original = editorTestNode()
            val session =
                TextEditorSession(original, this, StandardTestDispatcher(testScheduler), {
                    FileResult.Success(EditableText("original", EDITOR_TEST_FORMAT))
                }, { _, _, _ -> FileResult.Failure(FileError.DiskFull(required = 20, available = 0)) })
            session.load()
            testScheduler.advanceUntilIdle()
            session.edit(TextFieldValue("draft"))
            session.save()
            testScheduler.advanceUntilIdle()
            session.undo()
            assertTrue(session.state.value.canRedo)
            session.edit(TextFieldValue("branch"))
            assertFalse(session.state.value.canRedo)
            assertEquals(original, session.state.value.current)
            assertEquals(EDITOR_TEST_FORMAT, session.state.value.format)
            assertEquals("original", session.state.value.savedText)
            session.close()
        }
}
