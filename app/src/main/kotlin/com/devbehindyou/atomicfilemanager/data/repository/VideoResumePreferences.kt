package com.devbehindyou.atomicfilemanager.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeMemory
import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeState
import com.devbehindyou.atomicfilemanager.domain.repository.VideoRevision
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Video resume points in a small private SharedPreferences file, following [FolderSortPreferences]:
 * one entry per source, keyed by a SHA-256 of the source id so no path or account name is stored,
 * holding the revision it was saved for. Capped at [maxEntries]; past that the least recently
 * written entries are evicted.
 *
 * The file is loaded by [read] on [io]. Once loaded, [save] and [forget] only edit the
 * framework's in-memory map and `apply()` it, which writes to disk asynchronously, so they are
 * safe on the main thread. Every instance in a process shares the framework's one map for this
 * file, so a recreated viewer never reads behind its predecessor's last save, and nothing here
 * is process-wide state of its own.
 */
class VideoResumePreferences(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = MAX_ENTRIES,
) : VideoResumeMemory {
    @Volatile private var loaded: SharedPreferences? = null

    /** Only for a write that arrives before any read has loaded the file. */
    private val lateLoad by lazy { CoroutineScope(SupervisorJob() + io) }

    private suspend fun prefs(): SharedPreferences =
        loaded ?: withContext(io) {
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).also {
                it.contains(FILE_NAME) // Blocks until the file is loaded, here on IO.
                loaded = it
            }
        }

    override suspend fun read(revision: VideoRevision): VideoResumeState? {
        val prefs = prefs()
        val key = keyOf(revision.source)
        val raw = prefs.getString(key, null) ?: return null
        val entry = VideoResumeCodec.decode(raw)
        if (entry == null || entry.modifiedAt != revision.modifiedAt || entry.size != revision.size) {
            // Corrupt, or saved for a file that has since been replaced: never resume into it.
            synchronized(this) { if (prefs.getString(key, null) == raw) prefs.edit().remove(key).apply() }
            return null
        }
        return entry.state
    }

    override fun save(
        revision: VideoRevision,
        state: VideoResumeState,
    ) {
        if (!VideoResumeCodec.isValid(state)) return forget(revision)
        val value = VideoResumeCodec.encode(VideoResumeEntry(revision.modifiedAt, revision.size, state, clock()))
        edit { prefs ->
            val key = keyOf(revision.source)
            val editor = prefs.edit()
            if (!prefs.contains(key)) evictForOneMore(prefs, editor)
            editor.putString(key, value).apply()
        }
    }

    override fun forget(revision: VideoRevision) = edit { it.edit().remove(keyOf(revision.source)).apply() }

    private fun edit(change: (SharedPreferences) -> Unit) {
        val prefs = loaded
        if (prefs != null) {
            synchronized(this) { change(prefs) }
        } else {
            lateLoad.launch {
                val late = prefs()
                synchronized(this@VideoResumePreferences) { change(late) }
            }
        }
    }

    private fun evictForOneMore(
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor,
    ) {
        val all = prefs.all
        val excess = all.size - maxEntries + 1
        if (excess <= 0) return
        all.entries
            .sortedBy { (_, value) -> VideoResumeCodec.decode(value as? String)?.writtenAt ?: Long.MIN_VALUE }
            .take(excess)
            .forEach { editor.remove(it.key) }
    }

    companion object {
        const val FILE_NAME = "atomic_video_resume"
        const val MAX_ENTRIES = 200

        internal fun keyOf(source: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
