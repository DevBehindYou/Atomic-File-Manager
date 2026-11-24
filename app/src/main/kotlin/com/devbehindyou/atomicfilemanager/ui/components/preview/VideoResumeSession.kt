package com.devbehindyou.atomicfilemanager.ui.components.preview

import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeMemory
import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeState
import com.devbehindyou.atomicfilemanager.domain.repository.VideoRevision
import kotlin.math.abs

/** A player's observable state at one moment. */
internal data class VideoPlaybackSnapshot(
    val positionMs: Long,
    val playWhenReady: Boolean,
    val speed: Float,
    val ended: Boolean,
)

/**
 * Owns the resume point of one player for one exact [revision]. It decides once, when the
 * player is created, where to start (a process recovery or a reopened preview); later
 * recompositions reuse the same session and never seek again. Writes are bounded: explicit
 * pause/speed/stop/close checkpoints only write a real change, and playback progress writes
 * at most once per [PROGRESS_INTERVAL_MS] of [now] time.
 */
internal class VideoResumeSession(
    private val revision: VideoRevision,
    private val memory: VideoResumeMemory,
    saved: VideoResumeState?,
    private val durationMs: Long,
    private val now: () -> Long,
) {
    /** Where the new player starts; null starts at zero with the default autoplay policy. */
    val start: VideoResumeState? = saved?.takeIf { !nearEnd(it.positionMs) }

    /** The state this session knows to be in [memory] for [revision]. */
    private var recorded: VideoResumeState? = start
    private var lastWriteAt = now()
    private var closed = false

    init {
        // A position the file can no longer reach (finished, or past its known duration).
        if (saved != null && start == null) memory.forget(revision)
    }

    /** An explicit moment (pause, speed, STOP, end): write any real change now. */
    fun checkpoint(snapshot: VideoPlaybackSnapshot) {
        if (!closed) record(snapshot)
    }

    /** Periodic playback progress: rate limited so continuous playback cannot write per tick. */
    fun progress(snapshot: VideoPlaybackSnapshot) {
        if (!closed && now() - lastWriteAt >= PROGRESS_INTERVAL_MS) record(snapshot)
    }

    /** The player is going away; nothing is written after this. */
    fun close(snapshot: VideoPlaybackSnapshot) {
        checkpoint(snapshot)
        closed = true
    }

    private fun record(snapshot: VideoPlaybackSnapshot) {
        val state = VideoResumeState(snapshot.positionMs.coerceAtLeast(0L), snapshot.playWhenReady, snapshot.speed)
        if (snapshot.ended || nearEnd(state.positionMs) || !isIntentional(state)) {
            if (recorded != null) {
                memory.forget(revision)
                recorded = null
                lastWriteAt = now()
            }
            return
        }
        val previous = recorded
        if (previous != null &&
            previous.playWhenReady == state.playWhenReady &&
            previous.speed == state.speed &&
            abs(previous.positionMs - state.positionMs) < POSITION_TOLERANCE_MS
        ) {
            return
        }
        memory.save(revision, state)
        recorded = state
        lastWriteAt = now()
    }

    private fun nearEnd(positionMs: Long) = durationMs > 0L && positionMs >= durationMs - END_MARGIN_MS

    /** The first second at normal speed is not worth restoring: such a file opens as before. */
    private fun isIntentional(state: VideoResumeState) = state.positionMs >= MIN_RESUME_MS || state.speed != 1f

    companion object {
        const val MIN_RESUME_MS = 1_000L
        const val END_MARGIN_MS = 2_000L
        const val POSITION_TOLERANCE_MS = 1_000L
        const val PROGRESS_INTERVAL_MS = 15_000L
    }
}
