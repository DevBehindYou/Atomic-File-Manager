package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.devbehindyou.atomicfilemanager.data.repository.TextDraftStore
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.DraftLookup
import com.devbehindyou.atomicfilemanager.domain.repository.DraftWrite
import com.devbehindyou.atomicfilemanager.domain.repository.EditorDraft
import com.devbehindyou.atomicfilemanager.domain.repository.EditorDraftStore
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import com.devbehindyou.atomicfilemanager.domain.usecase.TextCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest

/**
 * C026: unsaved text survives process loss as an app-private draft. "Process loss" here is a new
 * [TextDraftStore] and a new session over the same directory while the old session is abandoned
 * without close, which is what a killed process leaves; physical process kills are not exercised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextEditorDraftSessionTest {
    @TempDir lateinit var root: File

    private val draftDir get() = File(root, "no_backup/editor-drafts")
    private val source get() = File(root, "shared/note.txt")
    private val node get() = editorTestNode(FileNodeId.file(source.absolutePath).raw)

    private fun writeSource(text: String) {
        source.parentFile.mkdirs()
        source.writeBytes(TextCodec.encode(text, EDITOR_TEST_FORMAT))
    }

    private fun sourceHash(): String =
        MessageDigest.getInstance(
            "SHA-256",
        ).digest(source.readBytes()).joinToString("") {
            "%02x".format(it)
        }

    private class Counting(private val inner: EditorDraftStore) : EditorDraftStore {
        var writes = 0
        var reads = 0
        var discards = 0

        override fun read(id: FileNodeId): DraftLookup = inner.read(id).also { reads++ }

        override fun write(
            id: FileNodeId,
            draft: EditorDraft,
        ): DraftWrite = inner.write(id, draft).also { writes++ }

        override fun discard(id: FileNodeId) = inner.discard(id).also { discards++ }
    }

    /** One "process": its own store instance and scope over the shared directories. */
    private inner class Process(
        test: TestScope,
        allows: (FileNode) -> Boolean = { true },
        maxDraftBytes: Long = 2L * 1024 * 1024,
    ) {
        val io = StandardTestDispatcher(test.testScheduler)
        val scope = CoroutineScope(SupervisorJob() + io)
        val store =
            Counting(
                TextDraftStore({ draftDir }, clock = { test.testScheduler.currentTime }, maxDraftBytes = maxDraftBytes),
            )
        var saves = 0
        val session =
            TextEditorSession(
                node,
                scope,
                io,
                { FileResult.Success(checkNotNull(TextCodec.decode(source.readBytes()))) },
                { current, text, format ->
                    saves++
                    source.writeBytes(TextCodec.encode(text, format))
                    FileResult.Success(current)
                },
                TextEditorDrafts(store, allows, clock = { test.testScheduler.currentTime }, debounceMillis = DEBOUNCE),
            )

        /** Process death: no close, no flush; the scope simply stops. */
        fun die() = scope.cancel()
    }

    @Test
    fun `unsaved text is offered after process loss and recovery never touches the source`() =
        runTest {
            writeSource("original")
            val first = Process(this)
            first.session.load()
            testScheduler.advanceUntilIdle()
            first.session.edit(TextFieldValue("original plus unsaved work"))
            testScheduler.advanceTimeBy(DEBOUNCE + 1)
            testScheduler.runCurrent()
            assertEquals(1, first.store.writes)
            val hashBefore = sourceHash()
            first.die()

            val second = Process(this)
            second.session.load()
            testScheduler.advanceUntilIdle()
            val offered = second.session.state.value
            assertEquals(EditorDraftOffer(savedAt = DEBOUNCE, sourceChanged = false), offered.draftOffer)
            assertEquals("original", offered.value.text)
            assertFalse(offered.dirty)
            // Editing is held until the user answers the offer.
            second.session.edit(TextFieldValue("typed over the offer"))
            assertEquals("original", second.session.state.value.value.text)

            second.session.recoverDraft()
            val recovered = second.session.state.value
            assertEquals("original plus unsaved work", recovered.value.text)
            assertTrue(recovered.dirty)
            assertNull(recovered.draftOffer)
            assertFalse(recovered.recoveredOverChangedSource)
            assertEquals(hashBefore, sourceHash())
            testScheduler.advanceUntilIdle()
            assertEquals(0, second.store.writes)
            assertEquals(hashBefore, sourceHash())

            second.session.undo()
            assertEquals("original", second.session.state.value.value.text)
            second.session.redo()
            assertTrue(second.session.save())
            testScheduler.advanceUntilIdle()
            assertEquals("original plus unsaved work", checkNotNull(TextCodec.decode(source.readBytes())).text)
            assertEquals(1, second.saves)
            assertEquals(DraftLookup.None, TextDraftStore({ draftDir }).read(node.id))
            second.session.close()
            second.die()
        }

    @Test
    fun `a draft over a changed source is offered as changed and can be recovered marked or discarded`() =
        runTest {
            writeSource("original")
            val first = Process(this)
            first.session.load()
            testScheduler.advanceUntilIdle()
            first.session.edit(TextFieldValue("my edit"))
            testScheduler.advanceUntilIdle()
            first.die()
            writeSource("changed elsewhere")
            val changedHash = sourceHash()

            val second = Process(this)
            second.session.load()
            testScheduler.advanceUntilIdle()
            assertEquals(true, second.session.state.value.draftOffer?.sourceChanged)
            assertEquals("changed elsewhere", second.session.state.value.value.text)
            second.session.recoverDraft()
            assertEquals("my edit", second.session.state.value.value.text)
            assertTrue(second.session.state.value.recoveredOverChangedSource)
            assertFalse(second.session.save())
            testScheduler.runCurrent()
            assertEquals(0, second.saves)
            assertEquals(changedHash, sourceHash())
            // A crash after recovering keeps the draft tied to the revision it came from.
            second.session.edit(TextFieldValue("my edit, continued"))
            testScheduler.advanceUntilIdle()
            second.die()

            val third = Process(this)
            third.session.load()
            testScheduler.advanceUntilIdle()
            assertEquals(true, third.session.state.value.draftOffer?.sourceChanged)
            third.session.discardDraft()
            testScheduler.advanceUntilIdle()
            assertNull(third.session.state.value.draftOffer)
            assertEquals("changed elsewhere", third.session.state.value.value.text)
            assertEquals(DraftLookup.None, TextDraftStore({ draftDir }).read(node.id))
            assertEquals(changedHash, sourceHash())
            third.session.close()
            third.die()
        }

    @Test
    fun `rapid typing writes once per pause and returning to the saved text removes the draft`() =
        runTest {
            writeSource("a")
            val process = Process(this)
            process.session.load()
            testScheduler.advanceUntilIdle()
            var text = "a"
            repeat(200) {
                text += "b"
                process.session.edit(TextFieldValue(text))
                testScheduler.advanceTimeBy(DEBOUNCE / 10)
                testScheduler.runCurrent()
            }
            assertEquals(0, process.store.writes)
            testScheduler.advanceUntilIdle()
            assertEquals(1, process.store.writes)
            // Selection-only changes are not text changes and write nothing.
            process.session.edit(TextFieldValue(text, TextRange(1)))
            testScheduler.advanceUntilIdle()
            assertEquals(1, process.store.writes)
            repeat(50) {
                text += "c"
                process.session.edit(TextFieldValue(text))
            }
            testScheduler.advanceUntilIdle()
            assertEquals(2, process.store.writes)
            val draftText = (TextDraftStore({ draftDir }).read(node.id) as DraftLookup.Found).draft.text
            assertEquals(text, draftText)

            process.session.edit(TextFieldValue("a"))
            testScheduler.advanceUntilIdle()
            assertEquals(2, process.store.writes)
            assertEquals(DraftLookup.None, TextDraftStore({ draftDir }).read(node.id))
            process.session.close()
            process.die()
        }

    @Test
    fun `a damaged draft is removed with a notice and the source opens normally`() =
        runTest {
            writeSource("original")
            val first = Process(this)
            first.session.load()
            testScheduler.advanceUntilIdle()
            first.session.edit(TextFieldValue("unsaved"))
            testScheduler.advanceUntilIdle()
            first.die()
            val draftFile = File(draftDir, TextDraftStore.keyOf(node.id) + TextDraftStore.DRAFT_SUFFIX)
            draftFile.writeBytes(draftFile.readBytes().copyOf(draftFile.length().toInt() / 2))

            val second = Process(this)
            second.session.load()
            testScheduler.advanceUntilIdle()
            val state = second.session.state.value
            assertNull(state.draftOffer)
            assertEquals(EditorDraftNotice.DAMAGED_REMOVED, state.draftNotice)
            assertEquals("original", state.value.text)
            assertFalse(draftFile.exists())
            second.session.close()
            second.die()
        }

    @Test
    fun `closing keeps or removes the draft by intent`() =
        runTest {
            writeSource("original")
            // Confirmed discard (a user close while dirty) removes the draft.
            val user = Process(this)
            user.session.load()
            testScheduler.advanceUntilIdle()
            user.session.edit(TextFieldValue("unsaved"))
            testScheduler.advanceUntilIdle()
            assertTrue(user.session.close())
            testScheduler.advanceUntilIdle()
            assertEquals(DraftLookup.None, TextDraftStore({ draftDir }).read(node.id))
            user.die()

            // A forced release keeps the draft and flushes text typed inside the debounce window.
            val forced = Process(this)
            forced.session.load()
            testScheduler.advanceUntilIdle()
            forced.session.edit(TextFieldValue("first"))
            testScheduler.advanceUntilIdle()
            forced.session.edit(TextFieldValue("first and latest"))
            assertTrue(forced.session.close(force = true))
            testScheduler.advanceUntilIdle()
            assertEquals(
                "first and latest",
                (TextDraftStore({ draftDir }).read(node.id) as DraftLookup.Found).draft.text,
            )
            forced.die()

            // Closing without answering an offer keeps the draft for next time.
            val unanswered = Process(this)
            unanswered.session.load()
            testScheduler.advanceUntilIdle()
            assertNotNull(unanswered.session.state.value.draftOffer)
            assertTrue(unanswered.session.close())
            testScheduler.advanceUntilIdle()
            assertTrue(TextDraftStore({ draftDir }).read(node.id) is DraftLookup.Found)
            unanswered.die()
            assertEquals("original", checkNotNull(TextCodec.decode(source.readBytes())).text)
        }

    @Test
    fun `policy-denied files are never drafted and oversized text is reported`() =
        runTest {
            writeSource("original")
            val denied = Process(this, allows = { false })
            denied.session.load()
            testScheduler.advanceUntilIdle()
            denied.session.edit(TextFieldValue("secret unsaved"))
            denied.session.close(force = true)
            testScheduler.advanceUntilIdle()
            assertEquals(0, denied.store.writes + denied.store.reads + denied.store.discards)
            assertFalse(draftDir.exists() && draftDir.list().orEmpty().isNotEmpty())
            denied.die()

            val bounded = Process(this, maxDraftBytes = 8)
            bounded.session.load()
            testScheduler.advanceUntilIdle()
            bounded.session.edit(TextFieldValue("far more than eight bytes"))
            testScheduler.advanceUntilIdle()
            assertEquals(EditorDraftNotice.TOO_LARGE, bounded.session.state.value.draftNotice)
            assertEquals(DraftLookup.None, TextDraftStore({ draftDir }).read(node.id))
            bounded.session.close(force = true)
            bounded.die()
        }

    @Test
    fun `without draft wiring the session stays memory-only`() =
        runTest {
            writeSource("original")
            val io = StandardTestDispatcher(testScheduler)
            val session =
                TextEditorSession(node, this, io, {
                    FileResult.Success(EditableText("original", EDITOR_TEST_FORMAT))
                }, { current, _, _ -> FileResult.Success(current) })
            session.load()
            testScheduler.advanceUntilIdle()
            session.edit(TextFieldValue("unsaved"))
            testScheduler.advanceUntilIdle()
            session.close(force = true)
            assertFalse(draftDir.exists())
        }

    private companion object {
        const val DEBOUNCE = 1_000L
    }
}
