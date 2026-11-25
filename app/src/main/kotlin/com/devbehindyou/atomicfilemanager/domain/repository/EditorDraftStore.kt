package com.devbehindyou.atomicfilemanager.domain.repository

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat

/**
 * A crash-recovery copy of unsaved editor text. It is never a save: recovering one only puts the
 * text back in the editor, and the source file changes only through the editor's normal Save.
 */
data class EditorDraft(
    val text: String,
    /** How the source was stored when the edits began; a recovered draft saves with the format loaded then. */
    val format: TextFormat,
    /** [com.devbehindyou.atomicfilemanager.domain.usecase.TextDrafts.sourceRevision] of the text the edits started from. */
    val sourceRevision: String,
    /** Epoch millis of the write. */
    val savedAt: Long,
)

sealed interface DraftLookup {
    data object None : DraftLookup

    data class Found(val draft: EditorDraft) : DraftLookup

    /** A draft was present but unreadable (truncated, tampered or an unknown version); it has been removed. */
    data object Damaged : DraftLookup
}

enum class DraftWrite {
    Written,

    /** Over the store's per-draft bound; nothing was kept and any older draft for the file was removed. */
    TooLarge,

    /** Nothing new was published; an earlier complete draft, if any, is still readable. */
    Failed,
}

/**
 * App-private, bounded draft storage keyed by an opaque digest of [FileNodeId], never by the
 * file's path. Calls block on disk; run them on an I/O dispatcher.
 */
interface EditorDraftStore {
    fun read(id: FileNodeId): DraftLookup

    fun write(
        id: FileNodeId,
        draft: EditorDraft,
    ): DraftWrite

    fun discard(id: FileNodeId)
}
