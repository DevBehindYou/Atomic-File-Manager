package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TextEditorHistoryIntegrationComposeTest {
    @get:Rule val compose = createComposeRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val editor =
        TextEditorSession(editorTestNode(), scope, dispatcher, {
            FileResult.Success(EditableText("original", EDITOR_TEST_FORMAT))
        }, { _, _, _ -> error("History interaction must not save") })

    @After
    fun closeMemoryOnlySession() {
        editor.close(force = true)
        scope.cancel()
    }

    @Test
    fun documentShortcutsShareToolbarHistoryWhileFindKeepsItsOwnUndo() {
        show()
        compose.onNodeWithTag("text_editor_field").performTextReplacement("draft")
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithTag("text_editor_field").performClick().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.Y)
            keyUp(Key.Y)
            keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithTag("text_editor_field").assertTextEquals("draft")
        compose.onNodeWithText("FIND").performClick()
        compose.onNodeWithTag("find_query").performTextReplacement("query")
        compose.onNodeWithTag("find_query").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.Z)
            keyUp(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithTag("find_query").assertTextEquals("")
        assertEquals("draft", editor.state.value.value.text)
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithTag("text_editor_field").assertTextEquals("original")
        compose.onNodeWithTag("text_editor_field").performTextReplacement("new branch")
        compose.onNodeWithContentDescription("Redo").assertIsNotEnabled()
    }

    @Test
    fun remountingTheDialogRetainsSessionHistoryAndCloseReleasesIt() {
        val mounted = mutableStateOf(true)
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (mounted.value) {
                    TextEditorSessionDialog(editorTestNode(), editor, onDismiss = {}, onSaved = {})
                }
            }
        }
        compose.onNodeWithTag("text_editor_field").performTextReplacement("retained draft")
        compose.runOnIdle { mounted.value = false }
        compose.waitForIdle()
        compose.runOnIdle { mounted.value = true }
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithTag("text_editor_field").assertTextEquals("original")
        compose.onNodeWithContentDescription("Redo").performClick()
        compose.onNodeWithTag("text_editor_field").assertTextEquals("retained draft")
        compose.runOnIdle { editor.close() }
        assertEquals("", editor.state.value.value.text)
    }

    private fun show() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                TextEditorSessionDialog(editorTestNode(), editor, onDismiss = {}, onSaved = {})
            }
        }
        compose.onNodeWithTag("text_editor_field").assertTextEquals("original")
    }
}
