package com.devbehindyou.atomicfilemanager.data.repository

import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeState
import org.json.JSONException
import org.json.JSONObject

/** A saved video state plus the revision it belongs to and when it was written (for eviction). */
internal data class VideoResumeEntry(
    val modifiedAt: Long,
    val size: Long,
    val state: VideoResumeState,
    val writtenAt: Long,
)

/** Versioned, size-capped JSON; anything malformed or out of range decodes to null and is dropped. */
internal object VideoResumeCodec {
    private const val VERSION = 1
    private const val MAX_CHARACTERS = 256

    /** Longer than any plausible local or remote preview (one week). */
    const val MAX_POSITION_MS = 7L * 24 * 60 * 60 * 1000
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 4f

    fun encode(entry: VideoResumeEntry): String =
        JSONObject().apply {
            put("v", VERSION)
            put("m", entry.modifiedAt)
            put("s", entry.size)
            put("p", entry.state.positionMs)
            put("w", entry.state.playWhenReady)
            put("r", entry.state.speed.toDouble())
            put("t", entry.writtenAt)
        }.toString()

    fun decode(raw: String?): VideoResumeEntry? {
        if (raw == null || raw.length > MAX_CHARACTERS) return null
        return try {
            val json = JSONObject(raw)
            if (json.optInt("v") != VERSION) return null
            val modifiedAt = json.getLong("m")
            val size = json.getLong("s")
            val position = json.getLong("p")
            val speed = json.getDouble("r").toFloat()
            val state = VideoResumeState(position, json.getBoolean("w"), speed)
            if (modifiedAt <= 0L || size < 0L || !isValid(state)) return null
            VideoResumeEntry(modifiedAt, size, state, json.optLong("t", 0L))
        } catch (_: JSONException) {
            null
        }
    }

    fun isValid(state: VideoResumeState): Boolean =
        state.positionMs in 0..MAX_POSITION_MS && state.speed.isFinite() && state.speed in MIN_SPEED..MAX_SPEED
}
