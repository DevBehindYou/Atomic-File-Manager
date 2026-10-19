package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import com.devbehindyou.atomicfilemanager.domain.usecase.LineEnding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextEncoding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextEditorSessionTest {
    @Test
    fun `duplicate loads never replace a draft or its selection and format`() =
        runTest {
            var opens = 0
            val session =
                TextEditorSession(editorTestNode(), this, StandardTestDispatcher(testScheduler), {
                    opens++
                    FileResult.Success(EditableText("original", EDITOR_TEST_FORMAT))
                }, { node, _, _ -> FileResult.Success(node) })
            session.load()
            session.load()
            testScheduler.advanceUntilIdle()
            val draft = TextFieldValue("changed", TextRange(2, 5))
            session.edit(draft)
            session.load()
            testScheduler.advanceUntilIdle()
            assertEquals(1, opens)
            assertEquals(draft, session.state.value.value)
            assertEquals(EDITOR_TEST_FORMAT, session.state.value.format)
            assertTrue(session.state.value.dirty)
            session.close()
        }

    @Test
    fun `one accepted save retains replacement identity and acknowledgement until claimed`() =
        runTest {
            val completed = CompletableDeferred<FileResult<FileNode>>()
            val replacement = editorTestNode("file:/opaque/replacement")
            var saves = 0
            val session =
                TextEditorSession(editorTestNode(), this, StandardTestDispatcher(testScheduler), {
                    FileResult.Success(EditableText("old", EDITOR_TEST_FORMAT))
                }, { original, text, format ->
                    saves++
                    assertEquals(editorTestNode().id, original.id)
                    assertEquals("draft", text)
                    assertEquals(EDITOR_TEST_FORMAT, format)
                    completed.await()
                })
            session.load()
            testScheduler.advanceUntilIdle()
            session.edit(TextFieldValue("draft", TextRange(3)))
            assertTrue(session.save())
            assertFalse(session.save())
            assertFalse(session.close())
            session.edit(TextFieldValue("not admitted"))
            assertEquals("draft", session.state.value.value.text)
            testScheduler.runCurrent()
            completed.complete(FileResult.Success(replacement))
            testScheduler.advanceUntilIdle()
            val saved = session.state.value
            assertEquals(1, saves)
            assertFalse(saved.dirty)
            assertFalse(saved.saving)
            assertEquals(TextRange(3), saved.value.selection)
            assertEquals(replacement, saved.current)
            val revision = checkNotNull(saved.acknowledgement).revision
            assertNull(session.acknowledgeSave(revision + 1))
            assertEquals(replacement, session.acknowledgeSave(revision))
            assertNull(session.acknowledgeSave(revision))
            session.close()
        }

    @Test
    fun `typed save failure retains draft and original identity then retry targets the saved replacement`() =
        runTest {
            var attempts = 0
            val targets = mutableListOf<FileNodeId>()
            val replacement = editorTestNode("file:/replacement")
            val failure = FileError.AccessDenied("note.txt")
            val session =
                TextEditorSession(editorTestNode(), this, StandardTestDispatcher(testScheduler), {
                    FileResult.Success(EditableText("old", EDITOR_TEST_FORMAT))
                }, { node, _, _ ->
                    targets.add(node.id)
                    attempts++
                    if (attempts == 1) FileResult.Failure(failure) else FileResult.Success(replacement)
                })
            session.load()
            testScheduler.advanceUntilIdle()
            session.edit(TextFieldValue("first"))
            session.save()
            testScheduler.advanceUntilIdle()
            assertEquals(failure, session.state.value.saveError)
            assertEquals(editorTestNode(), session.state.value.current)
            assertEquals("first", session.state.value.value.text)
            assertTrue(session.state.value.dirty)
            session.save()
            testScheduler.advanceUntilIdle()
            assertFalse(session.state.value.dirty)
            session.edit(TextFieldValue("second"))
            session.save()
            testScheduler.advanceUntilIdle()
            assertEquals(listOf(editorTestNode().id, editorTestNode().id, replacement.id), targets)
            session.close()
        }

    @Test
    fun `close releases both large strings and an ignored-cancellation late read cannot republish`() =
        runTest {
            val result = CompletableDeferred<FileResult<EditableText>>()
            val session =
                TextEditorSession(editorTestNode(), this, StandardTestDispatcher(testScheduler), {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { result.await() }
                }, { node, _, _ -> FileResult.Success(node) })
            session.load()
            testScheduler.runCurrent()
            session.close(force = true)
            result.complete(FileResult.Success(EditableText("large draft".repeat(10_000), EDITOR_TEST_FORMAT)))
            testScheduler.advanceUntilIdle()
            assertEquals("", session.state.value.value.text)
            assertEquals("", session.state.value.savedText)
            assertNull(session.state.value.current)
            assertNull(session.state.value.format)
            assertNull(session.state.value.acknowledgement)
        }

    @Test
    fun `accepted save finishes after owner cancellation without publishing an acknowledgement`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val owner = CoroutineScope(SupervisorJob() + dispatcher)
            val completion = CompletableDeferred<Unit>()
            var committed = false
            val session =
                TextEditorSession(editorTestNode(), owner, dispatcher, {
                    FileResult.Success(EditableText("old", EDITOR_TEST_FORMAT))
                }, { node, _, _ ->
                    completion.await()
                    committed = true
                    FileResult.Success(node)
                })
            session.load()
            testScheduler.advanceUntilIdle()
            session.edit(TextFieldValue("new"))
            session.save()
            testScheduler.runCurrent()
            owner.cancel()
            session.close(force = true)
            completion.complete(Unit)
            testScheduler.advanceUntilIdle()
            assertTrue(committed)
            assertNull(session.state.value.acknowledgement)
            assertEquals("", session.state.value.value.text)
        }

    @Test
    fun `unexpected save exception is unconfirmed and never claims original unchanged or successful`() =
        runTest {
            val session =
                TextEditorSession(editorTestNode(), this, StandardTestDispatcher(testScheduler), {
                    FileResult.Success(EditableText("old", EDITOR_TEST_FORMAT))
                }, { _, _, _ -> throw IllegalStateException("backend broke contract") })
            session.load()
            testScheduler.advanceUntilIdle()
            session.edit(TextFieldValue("new"))
            session.save()
            testScheduler.advanceUntilIdle()
            assertTrue(session.state.value.saveUnconfirmed)
            assertFalse(session.state.value.saving)
            assertTrue(session.state.value.dirty)
            assertNull(session.state.value.acknowledgement)
            session.close()
        }

    @Test
    fun `failed open can retry but never writes the document`() =
        runTest {
            var attempts = 0
            val session =
                TextEditorSession(editorTestNode(), this, StandardTestDispatcher(testScheduler), {
                    attempts++
                    if (attempts == 1) {
                        FileResult.Failure(FileError.IoFailure("read"))
                    } else {
                        FileResult.Success(EditableText("recovered", EDITOR_TEST_FORMAT))
                    }
                }, { _, _, _ -> error("no write expected") })
            session.load()
            testScheduler.advanceUntilIdle()
            assertTrue(session.state.value.openError is FileError.IoFailure)
            assertFalse(session.save())
            session.load()
            testScheduler.advanceUntilIdle()
            assertEquals("recovered", session.state.value.value.text)
            assertNull(session.state.value.openError)
            assertFalse(session.state.value.dirty)
            session.close()
        }
}

internal val EDITOR_TEST_FORMAT = TextFormat(TextEncoding.UTF8_BOM, LineEnding.CRLF)

internal fun editorTestNode(raw: String = "file:/original"): FileNode =
    FileNode(
        id = FileNodeId(raw), name = "note.txt", displayName = "note.txt", mimeType = "text/plain", size = 3,
        modifiedAt = 0, isDirectory = false, isHidden = false, parentId = FileNodeId("file:/parent"),
        storageType = StorageType.INTERNAL_SHARED, access = AccessFlags.FULL, childCount = null, extras = null,
    )
