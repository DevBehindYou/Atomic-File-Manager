package com.devbehindyou.atomicfilemanager.domain.repository

import com.devbehindyou.atomicfilemanager.domain.model.FileNode

/**
 * One exact source revision: the backend-qualified id (which already names the provider and
 * account) plus the listed modification time and size. A replacement file at the same id is a
 * different revision and must never inherit the old position.
 */
data class VideoRevision(
    val source: String,
    val modifiedAt: Long,
    val size: Long,
) {
    companion object {
        /** Null when the listing cannot tell revisions apart, so nothing is remembered for it. */
        fun of(node: FileNode): VideoRevision? =
            if (node.isDirectory || node.modifiedAt <= 0L || node.size < 0L) {
                null
            } else {
                VideoRevision(node.id.raw, node.modifiedAt, node.size)
            }
    }
}

/** What the viewer intentionally left behind: position, play/pause and speed. Never media bytes. */
data class VideoResumeState(
    val positionMs: Long,
    val playWhenReady: Boolean,
    val speed: Float = 1f,
)

/** Bounded per-source resume memory; at most one revision per source is kept. */
interface VideoResumeMemory {
    /** The saved state for exactly [revision]; a stale revision of the same source is cleared. */
    suspend fun read(revision: VideoRevision): VideoResumeState?

    /** Records [state] without blocking the caller; later reads see it immediately. */
    fun save(
        revision: VideoRevision,
        state: VideoResumeState,
    )

    /** Forgets the source of [revision] (finished or no longer intentional). */
    fun forget(revision: VideoRevision)
}
