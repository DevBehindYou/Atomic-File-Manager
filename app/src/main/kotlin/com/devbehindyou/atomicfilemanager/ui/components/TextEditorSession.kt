package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class EditorSaveAcknowledgement(val revision: Long, val node: FileNode)

internal data class TextEditorSessionState(
    val current: FileNode? = null,
    val value: TextFieldValue = TextFieldValue(),
    val savedText: String = "",
    val format: TextFormat? = null,
    val loading: Boolean = true,
    val openError: FileError? = null,
    val saving: Boolean = false,
    val saveError: FileError? = null,
    val saveUnconfirmed: Boolean = false,
    val acknowledgement: EditorSaveAcknowledgement? = null,
) {
    val dirty: Boolean get() = format != null && value.text != savedText
}

/** Activity-owned memory only: no draft file, SavedStateHandle, Bundle, or Activity callback. */
internal class TextEditorSession(
    node: FileNode,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val open: suspend (FileNode) -> FileResult<EditableText>,
    private val saveDocument: suspend (FileNode, String, TextFormat) -> FileResult<FileNode>,
) {
    private val mutableState = MutableStateFlow(TextEditorSessionState(current = node))
    val state = mutableState.asStateFlow()
    private var closed = false
    private var generation = 0L
    private var revision = 0L
    private var loadJob: Job? = null

    fun load() {
        if (closed || loadJob?.isActive == true || state.value.format != null) return
        val current = state.value.current ?: return
        val request = ++generation
        mutableState.value = state.value.copy(loading = true, openError = null)
        loadJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val result =
                    withContext(io) {
                        try {
                            open(current)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            FileResult.Failure(FileError.IoFailure(current.name))
                        }
                    }
                currentCoroutineContext().ensureActive()
                if (closed || request != generation) return@launch
                mutableState.value =
                    when (result) {
                        is FileResult.Failure -> state.value.copy(loading = false, openError = result.error)
                        is FileResult.Success ->
                            state.value.copy(
                                loading = false,
                                value = TextFieldValue(result.value.text),
                                savedText = result.value.text,
                                format = result.value.format,
                                openError = null,
                            )
                    }
            }
    }

    fun edit(value: TextFieldValue) {
        if (!closed && !state.value.loading && !state.value.saving && state.value.format != null) {
            mutableState.value = state.value.copy(value = value, saveError = null, saveUnconfirmed = false)
        }
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
                val result =
                    withContext(NonCancellable + io) {
                        try {
                            saveDocument(current, snapshot.value.text, format)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                currentCoroutineContext().ensureActive()
                if (closed || accepted != revision) return@launch
                mutableState.value =
                    when (result) {
                        is FileResult.Success ->
                            state.value.copy(
                                current = result.value,
                                savedText = snapshot.value.text,
                                saving = false,
                                acknowledgement = EditorSaveAcknowledgement(accepted, result.value),
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

    /** Claim before invoking the current composition's callback; never retains that callback. */
    fun acknowledgeSave(expected: Long): FileNode? {
        val acknowledgement = state.value.acknowledgement ?: return null
        if (closed || acknowledgement.revision != expected) return null
        mutableState.value = state.value.copy(acknowledgement = null)
        return acknowledgement.node
    }

    fun close(force: Boolean = false): Boolean {
        if (!force && state.value.saving) return false
        closed = true
        generation++
        revision++
        loadJob?.cancel()
        // Release both draft and original text immediately, including after hidden-route disposal.
        mutableState.value = TextEditorSessionState(loading = false)
        return true
    }
}

internal class TextEditorSessionsViewModel(
    private val io: CoroutineDispatcher,
    private val open: suspend (FileNode) -> FileResult<EditableText>,
    private val save: suspend (FileNode, String, TextFormat) -> FileResult<FileNode>,
) : ViewModel() {
    private val sessions = mutableMapOf<FileNodeId, TextEditorSession>()

    fun session(node: FileNode): TextEditorSession =
        sessions[node.id] ?: sessions.values.firstOrNull { it.state.value.current?.id == node.id }
            ?: TextEditorSession(node, viewModelScope, io, open, save).also { sessions[node.id] = it }

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
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(TextEditorSessionsViewModel::class.java))
                    return TextEditorSessionsViewModel(io, open, save) as T
                }
            }
    }
}
