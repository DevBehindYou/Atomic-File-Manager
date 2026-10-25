package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual controls share document-owned history and preserve save admission. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TextEditorUndoRedoComposeTest {
    @get:Rule val compose = createComposeRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var session: TextEditorSession? = null

    @After
    fun releaseOnlyTheMemoryOwnedSession() {
        session?.close(force = true)
        scope.cancel()
    }

    @Test
    fun explicitControlsRestoreUnicodeTextSelectionAndRedoCaretWithoutSaving() {
        val original = "a🌍b"
        val replacement = "किरण🙂"
        val editor = show(original) { _, _, _ -> error("Undo/redo must never save") }
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()
        compose.onNodeWithTag("text_editor_field").performTextInputSelection(TextRange(1, 3))
        compose.onNodeWithTag("text_editor_field").performTextInput(replacement)
        compose.onNodeWithTag("text_editor_field").assertTextEquals("a${replacement}b")
        compose.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        compose.onNodeWithTag("text_editor_field").assertTextEquals(original)
        assertEquals(TextRange(1, 3), editor.state.value.value.selection)
        assertFalse(editor.state.value.dirty)
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        compose.onNodeWithTag("text_editor_field").assertTextEquals("a${replacement}b")
        assertEquals(TextRange(1 + replacement.length), editor.state.value.value.selection)
        assertTrue(editor.state.value.dirty)
        compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()
    }

    @Test
    fun historyControlsAndHardwareUndoCannotChangeAcceptedSaveThenTrackSavedBaseline() {
        val completion = CompletableDeferred<FileResult<FileNode>>()
        val replacementNode = editorTestNode("file:/history-saved-replacement")
        var saveCalls = 0
        val editor =
            show("original") { _, text, format ->
                saveCalls++
                assertEquals("saved 🙂", text)
                assertEquals(EDITOR_TEST_FORMAT, format)
                completion.await()
            }
        try {
            compose.onNodeWithTag("text_editor_field").performTextReplacement("saved 🙂")
            compose.onNodeWithContentDescription("Undo").assertIsEnabled()
            compose.onNodeWithTag("text_editor_save").performClick()
            compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()
            compose.onNodeWithTag("text_editor_field").performClick().performKeyInput {
                keyDown(Key.CtrlLeft)
                keyDown(Key.Z)
                keyUp(Key.Z)
                keyUp(Key.CtrlLeft)
            }
            assertEquals("saved 🙂", editor.state.value.value.text)
            assertEquals(1, saveCalls)
            compose.runOnIdle { completion.complete(FileResult.Success(replacementNode)) }
            compose.waitForIdle()
            assertEquals(replacementNode, editor.state.value.current)
            assertFalse(editor.state.value.dirty)
            compose.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
            compose.onNodeWithTag("text_editor_field").assertTextEquals("original")
            assertTrue(editor.state.value.dirty)
            assertEquals(replacementNode, editor.state.value.current)
            compose.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
            compose.onNodeWithTag("text_editor_field").assertTextEquals("saved 🙂")
            assertFalse(editor.state.value.dirty)
            assertEquals(1, saveCalls)
        } finally {
            completion.complete(FileResult.Success(replacementNode))
        }
    }

    @Test
    fun existingHardwareUndoAndRedoRemainAWorkingControl() {
        val editor = show("original") { _, _, _ -> error("Keyboard history must never save") }
        compose.onNodeWithTag("text_editor_field").performTextReplacement("keyboard draft")
        compose.onNodeWithTag("text_editor_field").performClick().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.Z)
            keyUp(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithTag("text_editor_field").assertTextEquals("original")
        assertFalse(editor.state.value.dirty)
        compose.onNodeWithTag("text_editor_field").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            keyDown(Key.Z)
            keyUp(Key.Z)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithTag("text_editor_field").assertTextEquals("keyboard draft")
        assertTrue(editor.state.value.dirty)
    }

    private fun show(
        original: String,
        save: suspend (FileNode, String, TextFormat) -> FileResult<FileNode>,
    ): TextEditorSession {
        val editor =
            TextEditorSession(editorTestNode(), scope, dispatcher, {
                FileResult.Success(EditableText(original, EDITOR_TEST_FORMAT))
            }, save)
        session = editor
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                TextEditorSessionDialog(editorTestNode(), editor, onDismiss = {}, onSaved = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("text_editor_field").assertTextEquals(original)
        return editor
    }
}
