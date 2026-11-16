package com.devbehindyou.atomicfilemanager.data.repository

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.repository.DraftLookup
import com.devbehindyou.atomicfilemanager.domain.repository.DraftWrite
import com.devbehindyou.atomicfilemanager.domain.repository.EditorDraft
import com.devbehindyou.atomicfilemanager.domain.repository.EditorDraftStore
import com.devbehindyou.atomicfilemanager.domain.usecase.LineEnding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextEncoding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFileEditor
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Points in [TextDraftStore.write] where tests inject a failure or a simulated process loss. */
enum class DraftWritePhase {
    /** Every byte is in the temporary file, which is not yet synced. */
    TEMP_WRITTEN,

    /** The temporary file is synced; it has not taken the draft name. */
    TEMP_SYNCED,

    /** The draft name now holds the new bytes; the LRU stamp and eviction have not run. */
    PUBLISHED,
}

/**
 * Crash-recovery drafts in an app-private directory (the app supplies `noBackupFilesDir`, which
 * Auto Backup never includes; the manifest also sets `allowBackup="false"`). Nothing is written
 * next to the source file or to shared storage.
 *
 * Each draft is one self-describing file named by a SHA-256 of the node id, so the path never
 * appears in a file name. A write goes to a unique temporary file, is synced, and only then
 * renames over the previous draft, so a reader in a later process sees the old complete draft or
 * the new complete draft, never a mix. The body is followed by a SHA-256 of everything before it;
 * a truncated or altered draft is reported once as [DraftLookup.Damaged] and removed. Temporary
 * files a dead process left behind are removed on this instance's first use.
 *
 * Bounded: a draft body over [maxDraftBytes] (default: the editor's own 2 MiB load limit) is not
 * kept, and after each publish the least recently written drafts beyond [maxDrafts] or
 * [maxTotalBytes] are evicted (never the one just written).
 */
class TextDraftStore(
    /** Resolved on the first store call (on the I/O dispatcher), never at construction. */
    locate: () -> File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxDrafts: Int = MAX_DRAFTS,
    private val maxTotalBytes: Long = MAX_TOTAL_BYTES,
    private val maxDraftBytes: Long = TextFileEditor.MAX_EDIT_BYTES,
    private val newToken: () -> String = { UUID.randomUUID().toString() },
    private val fault: (DraftWritePhase, File) -> Unit = { _, _ -> },
) : EditorDraftStore {
    private val directory: File by lazy(locate)
    private val lock = Any()
    private var swept = false

    override fun read(id: FileNodeId): DraftLookup =
        synchronized(lock) {
            prepare()
            val file = draftFile(id)
            if (!file.isFile) return DraftLookup.None
            val draft =
                try {
                    decode(file, keyOf(id))
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            if (draft == null) {
                file.delete()
                DraftLookup.Damaged
            } else {
                DraftLookup.Found(draft)
            }
        }

    override fun write(
        id: FileNodeId,
        draft: EditorDraft,
    ): DraftWrite =
        synchronized(lock) {
            prepare()
            val key = keyOf(id)
            val body = draft.text.toByteArray(Charsets.UTF_8)
            if (body.size > maxDraftBytes) {
                draftFile(id).delete()
                return DraftWrite.TooLarge
            }
            val payload = encode(key, draft, body)
            val target = draftFile(id)
            val temp = File(directory, "$key.${newToken()}$TEMP_SUFFIX")
            try {
                if (!directory.isDirectory && !directory.mkdirs()) return DraftWrite.Failed
                FileOutputStream(temp).use { out ->
                    out.write(payload)
                    out.flush()
                    fault(DraftWritePhase.TEMP_WRITTEN, temp)
                    out.fd.sync()
                }
                fault(DraftWritePhase.TEMP_SYNCED, temp)
                if (!temp.renameTo(target)) return DraftWrite.Failed
                try {
                    fault(DraftWritePhase.PUBLISHED, target)
                    target.setLastModified(clock())
                    evict(keep = target)
                } catch (_: IOException) {
                    // The new draft is already complete under its name; the next write re-runs eviction.
                }
                DraftWrite.Written
            } catch (_: IOException) {
                DraftWrite.Failed
            } finally {
                temp.delete()
            }
        }

    override fun discard(id: FileNodeId) {
        synchronized(lock) {
            prepare()
            draftFile(id).delete()
        }
    }

    /** Draft files currently published (for tests and diagnostics); temporaries are excluded. */
    fun publishedCount(): Int = synchronized(lock) { drafts().size }

    private fun prepare() {
        if (swept) return
        swept = true
        directory.listFiles()?.filter { it.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
    }

    private fun drafts(): List<File> =
        directory.listFiles()?.filter {
            it.isFile && it.name.endsWith(DRAFT_SUFFIX)
        }.orEmpty()

    private fun evict(keep: File) {
        var total = 0L
        var count = 0
        drafts()
            .sortedWith(
                compareByDescending<File> { it == keep }.thenByDescending { it.lastModified() }.thenBy { it.name },
            )
            .forEach { file ->
                val size = file.length()
                if (file != keep && (count >= maxDrafts || total + size > maxTotalBytes)) {
                    file.delete()
                } else {
                    count++
                    total += size
                }
            }
    }

    private fun draftFile(id: FileNodeId): File = File(directory, keyOf(id) + DRAFT_SUFFIX)

    private fun encode(
        key: String,
        draft: EditorDraft,
        body: ByteArray,
    ): ByteArray {
        val bytes = ByteArrayOutputStream(body.size + HEADER_SLACK)
        DataOutputStream(bytes).use { out ->
            out.write(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(key)
            out.writeUTF(draft.format.encoding.name)
            out.writeUTF(draft.format.lineEnding.name)
            out.writeUTF(draft.sourceRevision)
            out.writeLong(draft.savedAt)
            out.writeInt(body.size)
            out.write(body)
        }
        val content = bytes.toByteArray()
        return content + sha256(content)
    }

    private fun decode(
        file: File,
        key: String,
    ): EditorDraft? {
        val length = file.length()
        if (length < MAGIC.size + DIGEST_BYTES || length > maxDraftBytes + HEADER_SLACK) return null
        val all = ByteArray(length.toInt())
        DataInputStream(file.inputStream()).use { it.readFully(all) }
        val content = all.copyOfRange(0, all.size - DIGEST_BYTES)
        val stored = all.copyOfRange(all.size - DIGEST_BYTES, all.size)
        if (!MessageDigest.isEqual(sha256(content), stored)) return null
        return DataInputStream(ByteArrayInputStream(content)).use { input ->
            val magic = ByteArray(MAGIC.size).also(input::readFully)
            if (!magic.contentEquals(MAGIC) || input.readInt() != VERSION || input.readUTF() != key) return null
            val encoding = TextEncoding.valueOf(input.readUTF())
            val lineEnding = LineEnding.valueOf(input.readUTF())
            val revision = input.readUTF()
            val savedAt = input.readLong()
            val size = input.readInt()
            if (size < 0 || size > maxDraftBytes || size != input.available()) return null
            val body = ByteArray(size).also(input::readFully)
            EditorDraft(String(body, Charsets.UTF_8), TextFormat(encoding, lineEnding), revision, savedAt)
        }
    }

    companion object {
        const val DIRECTORY_NAME = "editor-drafts"
        const val MAX_DRAFTS = 16
        const val MAX_TOTAL_BYTES = 8L * 1024 * 1024
        const val DRAFT_SUFFIX = ".draft"
        const val TEMP_SUFFIX = ".tmp"
        private const val VERSION = 1
        private const val DIGEST_BYTES = 32
        private const val HEADER_SLACK = 4 * 1024
        private val MAGIC = "AFMDRAFT".toByteArray(Charsets.US_ASCII)

        fun keyOf(id: FileNodeId): String =
            sha256(
                id.raw.toByteArray(Charsets.UTF_8),
            ).joinToString("") { "%02x".format(it) }

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
