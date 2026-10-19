package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextEditorSessionDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun savingBlocksCloseAndAStoppedOwnerCannotConsumeTheCurrentOwnersSavedReplacementCallback() {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val completion = CompletableDeferred<FileResult<FileNode>>()
        val replacement = editorTestNode("file:/saved-replacement")
        val session =
            TextEditorSession(editorTestNode(), scope, dispatcher, {
                FileResult.Success(EditableText("old", EDITOR_TEST_FORMAT))
            }, { _, _, _ -> completion.await() })
        val originalOwner = EditorTestLifecycleOwner()
        var owner by mutableStateOf(originalOwner)
        var newOwnerCallback by mutableStateOf(false)
        val oldCalls = mutableListOf<FileNode>()
        val newCalls = mutableListOf<FileNode>()
        var dismisses = 0
        try {
            compose.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    val callback = if (newOwnerCallback) newCalls else oldCalls
                    TextEditorSessionDialog(
                        editorTestNode(),
                        session,
                        onDismiss = { dismisses++ },
                        onSaved = { callback.add(it) },
                    )
                }
            }
            compose.runOnIdle { session.edit(TextFieldValue("draft")) }
            compose.onNodeWithTag("text_editor_save").performClick()
            compose.onNodeWithTag("text_editor_save").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Close editor").assertIsNotEnabled()
            compose.runOnIdle {
                originalOwner.registry.currentState = Lifecycle.State.CREATED
                completion.complete(FileResult.Success(replacement))
            }
            compose.waitForIdle()
            assertTrue(oldCalls.isEmpty())
            compose.runOnIdle {
                newOwnerCallback = true
                owner = EditorTestLifecycleOwner()
            }
            compose.waitForIdle()
            assertTrue(oldCalls.isEmpty())
            assertEquals(listOf(replacement), newCalls)
            assertEquals(0, dismisses)
            compose.onNodeWithContentDescription("Close editor").performClick()
            assertEquals(1, dismisses)
        } finally {
            session.close(force = true)
            scope.cancel()
        }
    }
}

private class EditorTestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}
