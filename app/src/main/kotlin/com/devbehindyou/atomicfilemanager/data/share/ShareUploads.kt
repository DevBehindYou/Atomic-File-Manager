package com.devbehindyou.atomicfilemanager.data.share

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives a complete upload before exclusively creating its final name. CREATE_NEW refuses
 * existing files, directories and symlinks, including another upload's destination.
 * Publication is a copy, not an atomic rename: other filesystem users can see it in progress.
 * ShareServer hides these names until publication finishes. Process death can leave a partial
 * final file; startup must not blindly delete it without a persisted ownership record.
 */
internal class ShareUploads(private val checkActive: () -> Unit) {
    private val publishing = ConcurrentHashMap.newKeySet<File>()

    fun isPublishing(file: File): Boolean = file.canonicalFile in publishing

    fun receive(
        input: InputStream,
        directory: File,
        name: String,
        length: Long,
        onBytes: () -> Unit,
    ) {
        val resolvedDirectory = directory.canonicalFile
        val part = File(resolvedDirectory, ".atomic-upload-${UUID.randomUUID()}.part")
        val target = File(resolvedDirectory, name)
        // Opening with CREATE_NEW establishes ownership without truncating a predictable path.
        val stage = Files.newOutputStream(part.toPath(), WRITE, CREATE_NEW)
        try {
            stage.use { copyExactly(input, it, length, onBytes) }
            checkActive()
            // A second upload must not remove the first upload's visibility guard.
            if (!publishing.add(target)) throw FileAlreadyExistsException(target.path)
            try {
                publish(part, target, length)
            } finally {
                publishing.remove(target)
            }
        } finally {
            Files.deleteIfExists(part.toPath())
        }
    }

    private fun publish(
        part: File,
        target: File,
        length: Long,
    ) {
        // Never delete on failure to open: that name belongs to someone else.
        val output = Files.newOutputStream(target.toPath(), WRITE, CREATE_NEW)
        try {
            output.use { sink ->
                part.inputStream().use { copyExactly(it, sink, length) {} }
                checkActive()
            }
        } catch (error: Exception) {
            Files.deleteIfExists(target.toPath())
            throw error
        }
    }

    private fun copyExactly(
        input: InputStream,
        sink: OutputStream,
        length: Long,
        onBytes: () -> Unit,
    ) {
        val buffer = ByteArray(BUFFER)
        var left = length
        while (left > 0) {
            checkActive()
            val count = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            if (count < 0) throw IOException("Upload cut short")
            sink.write(buffer, 0, count)
            left -= count
            onBytes()
        }
        checkActive()
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
