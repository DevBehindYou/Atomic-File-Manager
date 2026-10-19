package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.UUID

enum class TextEncoding(val charset: Charset, val bom: ByteArray) {
    UTF8(Charsets.UTF_8, byteArrayOf()),
    UTF8_BOM(Charsets.UTF_8, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
    UTF16LE(Charsets.UTF_16LE, byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
    UTF16BE(Charsets.UTF_16BE, byteArrayOf(0xFE.toByte(), 0xFF.toByte())),

    /** Any byte sequence decodes, so text that isn't UTF-8 still opens and saves byte for byte. */
    LATIN1(Charsets.ISO_8859_1, byteArrayOf()),
}

enum class LineEnding(val chars: String) { LF("\n"), CRLF("\r\n"), CR("\r") }

/** How the file was stored, so a save writes it back the same way. */
data class TextFormat(
    val encoding: TextEncoding,
    val lineEnding: LineEnding,
)

/** Text with line breaks normalised to "\n" for editing, and the [format] to restore on save. */
data class EditableText(
    val text: String,
    val format: TextFormat,
)

/** Pure text rules for the editor (ALL_IN_ONE_PLAN.md 2.3); unit-tested. */
object TextCodec {
    private const val BINARY_SNIFF = 8 * 1024

    /** The text and how it was stored, or null when the bytes look binary (a NUL in the first 8 KB). */
    fun decode(bytes: ByteArray): EditableText? {
        val encoding = detectEncoding(bytes)
        val body = bytes.copyOfRange(encoding.bom.size, bytes.size)
        val utf16 = encoding == TextEncoding.UTF16LE || encoding == TextEncoding.UTF16BE
        if (!utf16 && body.take(BINARY_SNIFF).any { it == 0.toByte() }) return null
        val raw = String(body, encoding.charset)
        return EditableText(normalise(raw), TextFormat(encoding, detectLineEnding(raw)))
    }

    fun encode(
        text: String,
        format: TextFormat,
    ): ByteArray {
        val restored = if (format.lineEnding == LineEnding.LF) text else text.replace("\n", format.lineEnding.chars)
        return format.encoding.bom + restored.toByteArray(format.encoding.charset)
    }

    fun detectEncoding(bytes: ByteArray): TextEncoding =
        when {
            bytes.startsWith(TextEncoding.UTF8_BOM.bom) -> TextEncoding.UTF8_BOM
            bytes.startsWith(TextEncoding.UTF16LE.bom) -> TextEncoding.UTF16LE
            bytes.startsWith(TextEncoding.UTF16BE.bom) -> TextEncoding.UTF16BE
            isUtf8(bytes) -> TextEncoding.UTF8
            else -> TextEncoding.LATIN1
        }

    /** The most common break in [raw]; a file with none gets LF. */
    fun detectLineEnding(raw: String): LineEnding {
        val crlf = Regex("\r\n").findAll(raw).count()
        val lf = raw.count { it == '\n' } - crlf
        val cr = raw.count { it == '\r' } - crlf
        return when {
            crlf == 0 && lf == 0 && cr == 0 -> LineEnding.LF
            crlf >= lf && crlf >= cr -> LineEnding.CRLF
            cr > lf -> LineEnding.CR
            else -> LineEnding.LF
        }
    }

    fun normalise(raw: String): String = raw.replace("\r\n", "\n").replace('\r', '\n')

    /** Where [query] occurs in [text], non-overlapping, in order. */
    fun findAll(
        text: String,
        query: String,
        ignoreCase: Boolean = true,
    ): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val found = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val at = text.indexOf(query, from, ignoreCase)
            if (at < 0) break
            found += at until at + query.length
            from = at + query.length
        }
        return found
    }

    private fun isUtf8(bytes: ByteArray): Boolean =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        prefix.isNotEmpty() && size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}

/**
 * Opens and saves plain text (ALL_IN_ONE_PLAN.md 2.3). A save never edits the file in place: the
 * new text goes to a temporary file in the same folder, is synced and checked, and only then takes
 * the original's name. The previous version goes to the Trash when that storage has one (so the edit
 * can be undone by restoring it); otherwise it is set aside under a hidden name until the swap is done.
 */
class TextFileEditor(
    private val backendFor: (FileNodeId) -> StorageBackend,
    private val trashManager: TrashManager? = null,
    private val newToken: () -> String = { UUID.randomUUID().toString().take(TOKEN_LENGTH) },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun open(node: FileNode): FileResult<EditableText> =
        withContext(io) {
            if (node.size > MAX_EDIT_BYTES) {
                return@withContext FileResult.Failure(FileError.FileTooLarge(node.name, MAX_EDIT_BYTES))
            }
            try {
                currentCoroutineContext().ensureActive()
                when (val input = backendFor(node.id).openInput(node.id)) {
                    is FileResult.Failure -> input
                    is FileResult.Success -> {
                        val bytes = input.value.stream().use { readAtMost(it, MAX_EDIT_BYTES + 1) }
                        currentCoroutineContext().ensureActive()
                        when {
                            bytes.size > MAX_EDIT_BYTES ->
                                FileResult.Failure(FileError.FileTooLarge(node.name, MAX_EDIT_BYTES))
                            else ->
                                TextCodec.decode(bytes)?.let { FileResult.Success(it) }
                                    ?: FileResult.Failure(FileError.UnsupportedFormat(node.mimeType))
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                FileResult.Failure(FileError.AccessDenied(node.name))
            } catch (_: Exception) {
                FileResult.Failure(FileError.IoFailure(node.name))
            }
        }

    /** Writes [text] over [node] as described above and returns the saved file. */
    suspend fun save(
        node: FileNode,
        text: String,
        format: TextFormat,
    ): FileResult<FileNode> =
        withContext(io) {
            val parent = node.parentId ?: return@withContext FileResult.Failure(FileError.InvalidDestination(node.name))
            val backend = backendFor(node.id)
            val bytes = TextCodec.encode(text, format)
            val token = newToken()
            val temp =
                when (val written = writeTemp(backend, parent, ".atomic-save-$token", node.mimeType, bytes)) {
                    is FileResult.Success -> written.value
                    is FileResult.Failure -> return@withContext written
                }
            withContext(NonCancellable) { swap(backend, node, temp, ".atomic-prev-$token") }
        }

    private suspend fun writeTemp(
        backend: StorageBackend,
        parent: FileNodeId,
        name: String,
        mime: String?,
        bytes: ByteArray,
    ): FileResult<FileNode> {
        val target =
            when (val out = backend.openOutput(parent, name, mime)) {
                is FileResult.Success -> out.value
                is FileResult.Failure -> return out
            }
        try {
            target.stream().use { it.write(bytes) }
            target.sync()
        } catch (_: IOException) {
            target.discard()
            return FileResult.Failure(FileError.IoFailure(name))
        }
        val saved =
            when (val node = target.toNode()) {
                is FileResult.Success -> node.value
                is FileResult.Failure -> return node
            }
        if (saved.size >= 0 && saved.size != bytes.size.toLong()) {
            backend.delete(saved.id)
            return FileResult.Failure(FileError.IncompleteWrite(name, saved.size, bytes.size.toLong()))
        }
        return FileResult.Success(saved)
    }

    private suspend fun swap(
        backend: StorageBackend,
        original: FileNode,
        temp: FileNode,
        asideName: String,
    ): FileResult<FileNode> {
        val trash = trashManager?.takeIf { it.canTrash(original) }
        val trashed = trash?.trash(original, "edit-${temp.id.raw.hashCode()}")
        val aside: FileNodeId? =
            when {
                trashed is FileResult.Success -> null
                else ->
                    when (val moved = backend.rename(original.id, asideName)) {
                        is FileResult.Success -> moved.value.id
                        is FileResult.Failure -> {
                            backend.delete(temp.id)
                            return moved
                        }
                    }
            }
        return when (val renamed = backend.rename(temp.id, original.name)) {
            is FileResult.Success -> {
                aside?.let { backend.delete(it) }
                renamed
            }
            is FileResult.Failure -> {
                // Put the original back under its name; the new text is dropped, not half-applied.
                if (aside != null) backend.rename(aside, original.name)
                if (trashed is FileResult.Success) trash?.restore(trashed.value)
                backend.delete(temp.id)
                renamed
            }
        }
    }

    /** Reads up to [limit] bytes (InputStream.readNBytes needs API 33). */
    private suspend fun readAtMost(
        input: InputStream,
        limit: Long,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var ended = false
        while (!ended && out.size() < limit) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer, 0, minOf(BUFFER.toLong(), limit - out.size()).toInt())
            if (read < 0) {
                ended = true
            } else if (read == 0) {
                val single = input.read()
                if (single < 0) ended = true else out.write(single)
            } else {
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    companion object {
        /** Larger files open in the read-only viewer. */
        const val MAX_EDIT_BYTES = 2L * 1024 * 1024
        private const val TOKEN_LENGTH = 8
        private const val BUFFER = 64 * 1024
    }
}
