package com.devbehindyou.atomicfilemanager.data.backend.network

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.OutputTarget
import kotlinx.coroutines.CancellationException
import java.io.OutputStream
import java.net.HttpURLConnection

/** A create-only PUT. Cleanup must never DELETE a rejected collision or a changed resource. */
internal class WebDavOutputTarget(
    private val connection: HttpURLConnection,
    private val name: String,
    private val node: (Long) -> FileNode,
    private val deleteCreated: (String) -> Unit,
) : OutputTarget {
    private val raw = connection.outputStream
    private var closed = false
    private var discarded = false
    private var bytes = 0L
    private var result: FileResult<FileNode>? = null
    private var createdTag: String? = null
    private val output =
        object : OutputStream() {
            override fun write(value: Int) {
                check(!closed)
                raw.write(value)
                bytes++
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                check(!closed)
                raw.write(buffer, offset, length)
                bytes += length
            }

            override fun flush() {
                if (!closed) raw.flush()
            }

            override fun close() {
                if (!closed) {
                    closed = true
                    raw.close()
                }
            }
        }

    override fun stream(): OutputStream = output

    override fun setLastModified(epochMillis: Long) = Unit

    override fun sync() = output.flush()

    override suspend fun toNode(): FileResult<FileNode> =
        if (discarded) FileResult.Failure(FileError.OperationCancelled) else finish()

    private fun finish(): FileResult<FileNode> {
        result?.let { return it }
        val finished =
            try {
                output.close()
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_CREATED) {
                    // Weak validators cannot safely identify the bytes being deleted.
                    createdTag = connection.getHeaderField("ETag")?.takeIf { it.startsWith('"') && it.endsWith('"') }
                }
                when {
                    code == HttpURLConnection.HTTP_PRECON_FAILED -> FileResult.Failure(FileError.FileAlreadyExists(name))
                    code in 200..299 -> FileResult.Success(node(bytes))
                    else -> FileResult.Failure(FileError.AccessDenied("PUT rejected ($code)"))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                FileResult.Failure(FileError.IoFailure(name))
            } finally {
                connection.disconnect()
            }
        result = finished
        return finished
    }

    override fun discard() {
        if (discarded) return
        discarded = true
        finish()
        // Without a returned strong ETag, leave the staged resource for recovery. An
        // unconditional DELETE could erase a concurrent replacement, including after 412.
        createdTag?.let { tag -> runCatching { deleteCreated(tag) } }
    }
}
