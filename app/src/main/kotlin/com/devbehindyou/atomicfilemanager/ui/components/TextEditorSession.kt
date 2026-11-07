package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.DraftLookup
import com.devbehindyou.atomicfilemanager.domain.repository.DraftWrite
import com.devbehindyou.atomicfilemanager.domain.repository.EditorDraft
import com.devbehindyou.atomicfilemanager.domain.repository.EditorDraftStore
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import com.devbehindyou.atomicfilemanager.domain.usecase.TextDrafts
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class EditorSaveAcknowledgement(val revision: Long, val node: FileNode)

/** A draft an earlier session kept; the editor shows the source until the user picks Recover or Discard. */
internal data class EditorDraftOffer(val savedAt: Long, val sourceChanged: Boolean)

internal enum class EditorDraftNotice { DAMAGED_REMOVED, TOO_LARGE, WRITE_FAILED }

/** Crash-recovery draft wiring for [TextEditorSession]; absent means memory-only editing. */
internal class TextEditorDrafts(
    val store: EditorDraftStore,
    val allows: (FileNode) -> Boolean = { true },
    val clock: () -> Long = System::currentTimeMillis,
    val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    companion object {
        /** Typing pauses this long before a draft is written: one write per pause, not per keystroke. */
        const val DEFAULT_DEBOUNCE_MILLIS = 1_000L
    }
}

internal data class TextEditorSessionState(
    val current: FileNode? = null,
    val value: TextFieldValue = TextFieldValue(),
    val savedText: String = "",
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val format: TextFormat? = null,
    val loading: Boolean = true,
    val openError: FileError? = null,
    val saving: Boolean = false,
    val saveError: FileError? = null,
    val saveUnconfirmed: Boolean = false,
    val acknowledgement: EditorSaveAcknowledgement? = null,
    val draftOffer: EditorDraftOffer? = null,
    /** The text came from a draft whose source has changed since; Save would replace the current file. */
    val recoveredOverChangedSource: Boolean = false,
    val draftNotice: EditorDraftNotice? = null,
) {
    val dirty: Boolean get() = format != null && value.text != savedText
}

/**
 * Activity-owned editing state: no SavedStateHandle, Bundle, or Activity callback. With [drafts],
 * unsaved text is also kept in that app-private store after a typing pause (C026), so it can be
 * offered back after process loss; recovering never writes the source, only Save does.
 */
internal class TextEditorSession(
    node: FileNode,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val open: suspend (FileNode) -> FileResult<EditableText>,
    private val saveDocument: suspend (FileNode, String, TextFormat) -> FileResult<FileNode>,
    private val drafts: TextEditorDrafts? = null,
) {
    private val mutableState = MutableStateFlow(TextEditorSessionState(current = node))
    val state = mutableState.asStateFlow()
    private val history = TextEditorHistory()
    private var closed = false
    private var generation = 0L
    private var revision = 0L
    private var loadJob: Job? = null

    // Draft bookkeeping; only touched on the session's (main) thread.
    private val draftLock = Mutex()
    private var draftJob: Job? = null
    private var draftsOn = false
    private var draftId: FileNodeId? = null
    private var pendingDraft: EditorDraft? = null

    /** Revision the current text derives from: the loaded source, or an older one after recovering over a change. */
    private var draftRevision: String? = null

    fun load() {
        if (closed || loadJob?.isActive == true || state.value.format != null) return
        val current = state.value.current ?: return
        val request = ++generation
        mutableState.value = state.value.copy(loading = true, openError = null)
        loadJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val draftable = drafts?.takeIf { it.allows(current) }
                val loaded =
                    withContext(io) {
                        try {
                            when (val result = open(current)) {
                                is FileResult.Failure -> Loaded(result, null, null)
                                is FileResult.Success -> {
                                    val text = result.value
                                    val source = draftable?.let { TextDrafts.sourceRevision(text.text, text.format) }
                                    Loaded(result, source, draftable?.let { lookUpDraft(it.store, current.id) })
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Loaded(FileResult.Failure(FileError.IoFailure(current.name)), null, null)
                        }
                    }
                currentCoroutineContext().ensureActive()
                if (closed || request != generation) return@launch
                mutableState.value =
                    when (val result = loaded.result) {
                        is FileResult.Failure -> state.value.copy(loading = false, openError = result.error)
                        is FileResult.Success -> {
                            draftsOn = draftable != null
                            draftId = current.id
                            draftRevision = loaded.revision
                            offered(loaded.draft, result.value, loaded.revision)
                        }
                    }
            }
    }

    private class Loaded(val result: FileResult<EditableText>, val revision: String?, val draft: DraftLookup?)

    private fun lookUpDraft(
        store: EditorDraftStore,
        id: FileNodeId,
    ): DraftLookup =
        try {
            store.read(id)
        } catch (_: Exception) {
            DraftLookup.None
        }

    private fun offered(
        lookup: DraftLookup?,
        loaded: EditableText,
        revision: String?,
    ): TextEditorSessionState {
        val base =
            state.value.copy(
                loading = false,
                value = TextFieldValue(loaded.text),
                savedText = loaded.text,
                format = loaded.format,
                openError = null,
            )
        return when (lookup) {
            null, DraftLookup.None -> base
            DraftLookup.Damaged -> base.copy(draftNotice = EditorDraftNotice.DAMAGED_REMOVED)
            is DraftLookup.Found -> {
                val draft = lookup.draft
                if (draft.text == loaded.text) {
                    // Nothing unsaved any more (for example saved elsewhere); drop it rather than ask.
                    persist(DraftCommand.Discard(checkNotNull(draftId)))
                    base
                } else {
                    pendingDraft = draft
                    base.copy(
                        draftOffer = EditorDraftOffer(draft.savedAt, sourceChanged = draft.sourceRevision != revision),
                    )
                }
            }
        }
    }

    /** Loads the kept draft into the editor as one undoable step; the source file is not touched. */
    fun recoverDraft() {
        val snapshot = state.value
        val offer = snapshot.draftOffer ?: return
        val draft = pendingDraft ?: return
        if (closed || snapshot.loading || snapshot.saving || snapshot.format == null) return
        pendingDraft = null
        history.record(snapshot.value)
        draftRevision = draft.sourceRevision
        mutableState.value =
            snapshot.copy(
                value = TextFieldValue(draft.text),
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                draftOffer = null,
                recoveredOverChangedSource = offer.sourceChanged,
            )
    }

    /** Removes the kept draft and keeps editing the source as loaded. */
    fun discardDraft() {
        if (closed || state.value.draftOffer == null) return
        pendingDraft = null
        mutableState.value = state.value.copy(draftOffer = null)
        draftId?.let { persist(DraftCommand.Discard(it)) }
    }

    fun edit(value: TextFieldValue) {
        if (!canEdit()) return
        val previous = state.value.value
        if (previous.text != value.text) history.record(previous)
        publishEdit(value)
    }

    fun undo() {
        if (canEdit()) history.undo(state.value.value)?.let(::publishEdit)
    }

    fun redo() {
        if (canEdit()) history.redo(state.value.value)?.let(::publishEdit)
    }

    private fun canEdit(): Boolean =
        !closed && !state.value.loading && !state.value.saving && state.value.format != null &&
            state.value.draftOffer == null

    private fun publishEdit(value: TextFieldValue) {
        val changed = value.text != state.value.value.text
        mutableState.value =
            state.value.copy(
                value = value,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                saveError = null,
                saveUnconfirmed = false,
            )
        if (changed) scheduleDraft()
    }

    private sealed interface DraftCommand {
        data class Write(val id: FileNodeId, val draft: EditorDraft) : DraftCommand

        data class Discard(val id: FileNodeId) : DraftCommand
    }

    /** Debounced: each text change restarts the wait, so rapid typing produces one write per pause. */
    private fun scheduleDraft() {
        val config = drafts ?: return
        if (!draftsOn) return
        draftJob?.cancel()
        draftJob =
            scope.launch {
                delay(config.debounceMillis)
                draftCommand()?.let { run(it) }
            }
    }

    /** What the store should hold for the current text: nothing when it equals the saved text. */
    private fun draftCommand(): DraftCommand? {
        val snapshot = state.value
        val id = draftId ?: return null
        val format = snapshot.format ?: return null
        val revision = draftRevision ?: return null
        val config = drafts ?: return null
        return if (snapshot.value.text == snapshot.savedText) {
            DraftCommand.Discard(id)
        } else {
            DraftCommand.Write(id, EditorDraft(snapshot.value.text, format, revision, config.clock()))
        }
    }

    private fun persist(command: DraftCommand) {
        if (drafts == null) return
        scope.launch { run(command) }
    }

    /** Store calls run one at a time in submission order, and a started call finishes even if cancelled. */
    private suspend fun run(command: DraftCommand) {
        val store = drafts?.store ?: return
        val outcome =
            draftLock.withLock {
                withContext(NonCancellable + io) {
                    try {
                        when (command) {
                            is DraftCommand.Write -> store.write(command.id, command.draft)
                            is DraftCommand.Discard -> {
                                store.discard(command.id)
                                null
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        DraftWrite.Failed
                    }
                }
            }
        if (closed || outcome == null) return
        val notice =
            when (outcome) {
                DraftWrite.Written -> null
                DraftWrite.TooLarge -> EditorDraftNotice.TOO_LARGE
                DraftWrite.Failed -> EditorDraftNotice.WRITE_FAILED
            }
        if (state.value.draftNotice != notice) mutableState.value = state.value.copy(draftNotice = notice)
    }

    /** Admission is synchronous, so a second click cannot enqueue another save. */
    fun save(): Boolean {
        val snapshot = state.value
        val format = snapshot.format ?: return false
        val current = snapshot.current ?: return false
        if (closed || !snapshot.dirty || snapshot.saving) return false
        val accepted = ++revision
        mutableState.value = snapshot.copy(saving = true, saveError = null, saveUnconfirmed = false)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Once accepted, finish the finite staged save even if the Activity finishes.
                var savedRevision: String? = null
                val drafting = draftsOn
                val result =
                    withContext(NonCancellable + io) {
                        try {
                            saveDocument(current, snapshot.value.text, format).also {
                                if (drafting && it is FileResult.Success) {
                                    savedRevision = TextDrafts.sourceRevision(snapshot.value.text, format)
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                currentCoroutineContext().ensureActive()
                if (closed || accepted != revision) return@launch
                if (result is FileResult.Success) savedDraftBaseline(result.value.id, savedRevision)
                mutableState.value =
                    when (result) {
                        is FileResult.Success ->
                            state.value.copy(
                                current = result.value,
                                savedText = snapshot.value.text,
                                saving = false,
                                acknowledgement = EditorSaveAcknowledgement(accepted, result.value),
                                recoveredOverChangedSource = false,
                            )
                        is FileResult.Failure -> state.value.copy(saving = false, saveError = result.error)
                        null -> state.value.copy(saving = false, saveUnconfirmed = true)
                    }
            } finally {
                if (!closed && scope.isActive && accepted == revision && state.value.saving) {
                    mutableState.value = state.value.copy(saving = false, saveUnconfirmed = true)
                }
            }
        }
        return true
    }

    /** The saved text is the new baseline: its draft is obsolete, and later drafts follow the saved identity. */
    private fun savedDraftBaseline(
        saved: FileNodeId,
        revision: String?,
    ) {
        if (!draftsOn) return
        draftJob?.cancel()
        draftId?.let { persist(DraftCommand.Discard(it)) }
        if (saved != draftId) persist(DraftCommand.Discard(saved))
        draftId = saved
        draftRevision = revision
    }

    /** Claim before invoking the current composition's callback; never retains that callback. */
    fun acknowledgeSave(expected: Long): FileNode? {
        val acknowledgement = state.value.acknowledgement ?: return null
        if (closed || acknowledgement.revision != expected) return null
        mutableState.value = state.value.copy(acknowledgement = null)
        return acknowledgement.node
    }

    /**
     * A user close (clean, or "Discard changes" confirmed) removes the draft unless an offer is still
     * unanswered. A forced release (route disposal, owner cleared) keeps it and writes the latest text
     * now if the owner scope still runs; after process loss the last debounced write stands.
     */
    private fun closeDrafts(force: Boolean) {
        if (closed || !draftsOn) return
        draftJob?.cancel()
        val id = draftId ?: return
        when {
            state.value.draftOffer != null -> Unit
            !force -> persist(DraftCommand.Discard(id))
            else -> draftCommand()?.let(::persist)
        }
    }

    fun close(force: Boolean = false): Boolean {
        if (!force && state.value.saving) return false
        closeDrafts(force)
        closed = true
        generation++
        revision++
        loadJob?.cancel()
        history.clear()
        pendingDraft = null
        // Release text, original and history immediately, including after hidden-route disposal.
        mutableState.value = TextEditorSessionState(loading = false)
        return true
    }
}

internal class TextEditorSessionsViewModel(
    private val io: CoroutineDispatcher,
    private val open: suspend (FileNode) -> FileResult<EditableText>,
    private val save: suspend (FileNode, String, TextFormat) -> FileResult<FileNode>,
    private val drafts: TextEditorDrafts? = null,
) : ViewModel() {
    private val sessions = mutableMapOf<FileNodeId, TextEditorSession>()

    fun session(node: FileNode): TextEditorSession =
        sessions[node.id] ?: sessions.values.firstOrNull { it.state.value.current?.id == node.id }
            ?: TextEditorSession(node, viewModelScope, io, open, save, drafts).also { sessions[node.id] = it }

    fun release(
        session: TextEditorSession,
        force: Boolean = false,
    ): Boolean {
        if (!session.close(force)) return false
        sessions.entries.removeAll { it.value === session }
        return true
    }

    override fun onCleared() {
        sessions.values.forEach { it.close(force = true) }
        sessions.clear()
    }

    companion object {
        fun factory(
            io: CoroutineDispatcher,
            open: suspend (FileNode) -> FileResult<EditableText>,
            save: suspend (FileNode, String, TextFormat) -> FileResult<FileNode>,
            drafts: TextEditorDrafts? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(TextEditorSessionsViewModel::class.java))
                    return TextEditorSessionsViewModel(io, open, save, drafts) as T
                }
            }
    }
}
