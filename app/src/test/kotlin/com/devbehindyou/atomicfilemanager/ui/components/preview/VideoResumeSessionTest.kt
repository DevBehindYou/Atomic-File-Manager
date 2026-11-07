package com.devbehindyou.atomicfilemanager.ui.components.preview

import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeMemory
import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeState
import com.devbehindyou.atomicfilemanager.domain.repository.VideoRevision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoResumeSessionTest {
    private val revision = VideoRevision("file:/sdcard/Movies/a.mp4", 1_700_000_000_000, 9_000_000)
    private val memory = Recorder()
    private var clock = 0L

    private fun session(
        saved: VideoResumeState? = null,
        durationMs: Long = 600_000,
    ) = VideoResumeSession(revision, memory, saved, durationMs) { clock }

    private fun at(
        positionMs: Long,
        playing: Boolean = true,
        speed: Float = 1f,
        ended: Boolean = false,
    ) = VideoPlaybackSnapshot(positionMs, playing, speed, ended)

    @Test
    fun `restoring and closing at the restored place writes nothing`() {
        val saved = VideoResumeState(90_000, playWhenReady = false)
        val session = session(saved)
        assertEquals(saved, session.start)
        session.close(at(90_400, playing = false))
        assertEquals(emptyList<Any>(), memory.calls)
    }

    @Test
    fun `position changes inside the tolerance are not written and larger ones are`() {
        val session = session(VideoResumeState(10_000, true))
        session.checkpoint(at(10_999))
        assertEquals(0, memory.calls.size)
        session.checkpoint(at(11_000))
        assertEquals(listOf<Any>(VideoResumeState(11_000, true)), memory.calls)
    }

    @Test
    fun `pause and speed are written exactly even inside the position tolerance`() {
        val session = session(VideoResumeState(10_000, true))
        session.checkpoint(at(10_200, playing = false))
        session.checkpoint(at(10_200, playing = false, speed = 2f))
        session.checkpoint(at(10_200, playing = false, speed = 2f))
        assertEquals(
            listOf<Any>(VideoResumeState(10_200, false), VideoResumeState(10_200, false, 2f)),
            memory.calls,
        )
    }

    @Test
    fun `continuous playback writes at most once per progress interval`() {
        val session = session()
        // Ten minutes of 100 ms ticks, far more often than the preview ever calls it.
        for (tick in 1..6_000) {
            clock = tick * 100L
            session.progress(at(clock))
        }
        val bound = (6_000 * 100L) / VideoResumeSession.PROGRESS_INTERVAL_MS
        assertTrue(memory.calls.size <= bound, "${memory.calls.size} writes for ten minutes")
        assertTrue(memory.calls.size >= bound - 1, "progress must still be recorded")
    }

    @Test
    fun `finishing forgets the source once and later checkpoints stay quiet`() {
        val session = session(VideoResumeState(30_000, true))
        session.checkpoint(at(600_000, ended = true))
        session.checkpoint(at(600_000, ended = true))
        session.close(at(599_500))
        assertEquals(listOf<Any>("forget"), memory.calls)
    }

    @Test
    fun `an unreachable saved point is never applied and is forgotten`() {
        val session = session(VideoResumeState(599_000, false), durationMs = 600_000)
        assertNull(session.start)
        assertEquals(listOf<Any>("forget"), memory.calls)
        session.close(at(0))
        assertEquals(1, memory.calls.size)
    }

    @Test
    fun `an unknown duration still applies the saved point`() {
        assertEquals(
            VideoResumeState(5_000_000, true),
            session(VideoResumeState(5_000_000, true), durationMs = 0).start,
        )
    }

    @Test
    fun `the first second at normal speed is not remembered`() {
        val session = session()
        session.checkpoint(at(900, playing = false))
        session.close(at(900, playing = false))
        assertEquals(emptyList<Any>(), memory.calls)
        session(VideoResumeState(4_000, true)).close(at(500))
        assertEquals(listOf<Any>("forget"), memory.calls)
    }

    @Test
    fun `nothing is written after close`() {
        val session = session()
        session.close(at(20_000))
        session.checkpoint(at(40_000, playing = false))
        clock = 60_000
        session.progress(at(60_000))
        assertEquals(listOf<Any>(VideoResumeState(20_000, true)), memory.calls)
    }

    private class Recorder : VideoResumeMemory {
        val calls = mutableListOf<Any>()

        override suspend fun read(revision: VideoRevision): VideoResumeState? = error("sessions never read")

        override fun save(
            revision: VideoRevision,
            state: VideoResumeState,
        ) {
            calls += state
        }

        override fun forget(revision: VideoRevision) {
            calls += "forget"
        }
    }
}
