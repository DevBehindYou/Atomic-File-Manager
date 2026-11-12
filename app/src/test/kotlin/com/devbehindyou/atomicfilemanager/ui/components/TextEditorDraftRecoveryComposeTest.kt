package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.DraftLookup
import com.devbehindyou.atomicfilemanager.domain.repository.DraftWrite
import com.devbehindyou.atomicfilemanager.domain.repository.EditorDraft
import com.devbehindyou.atomicfilemanager.domain.repository.EditorDraftStore
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import com.devbehindyou.atomicfilemanager.domain.usecase.TextDrafts
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
class TextEditorDraftRecoveryComposeTest {
    @get:Rule val compose = createComposeRule()

    private class MemoryStore(initial: EditorDraft?) : EditorDraftStore {
        val drafts = mutableMapOf<FileNodeId, EditorDraft>()
        var writes = 0

        init {
            initial?.let { drafts[editorTestNode().id] = it }
        }

        override fun read(id: FileNodeId): DraftLookup = drafts[id]?.let { DraftLookup.Found(it) } ?: DraftLookup.None

        override fun write(
            id: FileNodeId,
            draft: EditorDraft,
        ): DraftWrite {
            writes++
            drafts[id] = draft
            return DraftWrite.Written
        }

        override fun discard(id: FileNodeId) {
            drafts.remove(id)
        }
    }

    private fun withOfferedDraft(
        draftRevisionOf: String,
        block: (TextEditorSession, MemoryStore) -> Unit,
    ) {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val store =
            MemoryStore(
                EditorDraft(
                    "kept edits",
                    EDITOR_TEST_FORMAT,
                    TextDrafts.sourceRevision(draftRevisionOf, EDITOR_TEST_FORMAT),
                    1L,
                ),
            )
        val session =
            TextEditorSession(
                editorTestNode(),
                scope,
                dispatcher,
                { FileResult.Success(EditableText("on disk", EDITOR_TEST_FORMAT)) },
                { node, _, _ -> FileResult.Success(node) },
                TextEditorDrafts(store),
            )
        try {
            compose.setContent { TextEditorSessionDialog(editorTestNode(), session, onDismiss = {}, onSaved = {}) }
            block(session, store)
        } finally {
            session.close(force = true)
            scope.cancel()
        }
    }

    @Test
    fun aKeptDraftIsOfferedWithoutBeingAppliedAndRecoverLoadsItIntoTheEditor() =
        withOfferedDraft(draftRevisionOf = "on disk") { session, store ->
            compose.onNodeWithTag("text_editor_draft_offer").assertIsDisplayed()
            compose.onNodeWithText(
                "Unsaved edits from an earlier session were kept. The file itself is unchanged.",
            ).assertIsDisplayed()
            compose.onNodeWithTag("text_editor_save").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
            assertEquals("on disk", session.state.value.value.text)
            compose.onNodeWithTag("text_editor_draft_recover").performClick()
            compose.waitForIdle()
            assertEquals("kept edits", session.state.value.value.text)
            assertTrue(session.state.value.dirty)
            compose.onNodeWithTag("text_editor_draft_offer").assertDoesNotExist()
            assertEquals(1, store.drafts.size)
            assertEquals(0, store.writes)
        }

    @Test
    fun aDraftOverAChangedSourceSaysSoAndDiscardRemovesIt() =
        withOfferedDraft(draftRevisionOf = "an older version") { session, store ->
            compose
                .onNodeWithText(
                    "Unsaved edits were kept, but the file has changed since. " +
                        "Recovering puts them in the editor; saving would replace the current file.",
                ).assertIsDisplayed()
            compose.onNodeWithTag("text_editor_draft_discard").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("text_editor_draft_offer").assertDoesNotExist()
            assertEquals("on disk", session.state.value.value.text)
            assertTrue(store.drafts.isEmpty())
        }
}
