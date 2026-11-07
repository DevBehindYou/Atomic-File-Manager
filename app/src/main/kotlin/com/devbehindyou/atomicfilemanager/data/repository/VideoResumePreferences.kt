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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Video resume points in a small private SharedPreferences file, following [FolderSortPreferences]:
 * one entry per source, keyed by a SHA-256 of the source id so no path or account name is stored,
 * holding the revision it was saved for. Capped at [maxEntries]; past that the least recently
 * written entries are evicted. Writes are queued off the caller's thread; a queued value is
 * visible to [read] at once, so a recreated viewer never reads behind its predecessor's last save.
 */
class VideoResumePreferences(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = MAX_ENTRIES,
) : VideoResumeMemory {
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }
    private val access = Mutex()
    private val writes = CoroutineScope(SupervisorJob() + io)

    /** Hashed key to encoded value ([REMOVED] for a removal) not yet handed to SharedPreferences. */
    private val pending = HashMap<String, String>()

    override suspend fun read(revision: VideoRevision): VideoResumeState? {
        val key = keyOf(revision.source)
        val raw =
            synchronized(pending) { pending[key] }
                ?: access.withLock { withContext(io) { prefs.getString(key, null) } }
        if (raw == null || raw == REMOVED) return null
        val entry = VideoResumeCodec.decode(raw)
        if (entry == null || entry.modifiedAt != revision.modifiedAt || entry.size != revision.size) {
            // Corrupt, or saved for a file that has since been replaced: never resume into it.
            removeIfStill(key, raw)
            return null
        }
        return entry.state
    }

    override fun save(
        revision: VideoRevision,
        state: VideoResumeState,
    ) {
        if (!VideoResumeCodec.isValid(state)) return forget(revision)
        val entry = VideoResumeEntry(revision.modifiedAt, revision.size, state, clock())
        enqueue(keyOf(revision.source), VideoResumeCodec.encode(entry))
    }

    override fun forget(revision: VideoRevision) = enqueue(keyOf(revision.source), REMOVED)

    private fun enqueue(
        key: String,
        value: String,
    ) {
        synchronized(pending) { pending[key] = value }
        writes.launch { flush(key) }
    }

    /** A save queued after [stale] was read belongs to the current revision and wins. */
    private fun removeIfStill(
        key: String,
        stale: String,
    ) {
        val replaced =
            synchronized(pending) {
                val queued = pending[key]
                (queued == null || queued == stale).also { if (it) pending[key] = REMOVED }
            }
        if (replaced) writes.launch { flush(key) }
    }

    /** Several queued values for one key collapse into a single SharedPreferences edit. */
    private suspend fun flush(key: String) =
        access.withLock {
            withContext(io) {
                val value = synchronized(pending) { pending[key] } ?: return@withContext
                val editor = prefs.edit()
                if (value == REMOVED) {
                    editor.remove(key)
                } else {
                    if (!prefs.contains(key)) evictForOneMore(editor)
                    editor.putString(key, value)
                }
                editor.apply()
                synchronized(pending) { if (pending[key] === value) pending.remove(key) }
            }
        }

    private fun evictForOneMore(editor: SharedPreferences.Editor) {
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
        private const val REMOVED = ""

        private var shared: VideoResumePreferences? = null
        private var sharedOwner: Context? = null

        /**
         * The one store for this process until the manual AppContainer exposes it, so every
         * viewer shares a single write queue. A new Application (tests) gets a new store.
         */
        fun shared(context: Context): VideoResumePreferences {
            val application = context.applicationContext
            return synchronized(this) {
                shared?.takeIf { sharedOwner === application }
                    ?: VideoResumePreferences(application).also {
                        shared = it
                        sharedOwner = application
                    }
            }
        }

        internal fun keyOf(source: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
