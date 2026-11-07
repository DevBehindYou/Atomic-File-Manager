package com.devbehindyou.atomicfilemanager.data.preview

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Process-wide owner of private scratch copies in a cache directory.
 *
 * A media, PDF or 7z session deletes its own copy in `finally`, but process death skips that, and nothing in a
 * later process would own the copy. Each directory is recovered once per process, under a lock and before this
 * process creates its first copy there, so a copy still in use by this process can never be removed. Startup
 * may recover early; it then takes the same lock.
 *
 * Only top-level regular files with an owned prefix are touched. `external-preview/` copies (external readers
 * may open them later; they expire after a day) and `clean/` EXIF copies (shared to other apps) are not
 * scratch copies.
 */
internal object ScratchCopies {
    const val MEDIA_PREFIX = "media-preview-"
    const val PDF_PREFIX = "atomic-pdf-"
    const val ARCHIVE_PREFIX = "atomic-7z-"

    private val ownedPrefixes = listOf(MEDIA_PREFIX, PDF_PREFIX, ARCHIVE_PREFIX)
    private const val MAX_SCANNED_ENTRIES = 4096
    private val recovered = HashSet<String>()

    /** Creates an empty copy for [prefix] after any earlier process's copies in [directory] are removed. */
    fun create(
        directory: File,
        prefix: String,
        suffix: String,
    ): File {
        require(prefix in ownedPrefixes) { "Not a scratch-copy prefix: $prefix" }
        recoverOnce(directory)
        return File.createTempFile(prefix, suffix, directory)
    }

    /**
     * Removes copies left by an earlier process, once per [directory] in this process. Returns how many were
     * removed; 0 when this process already recovered the directory.
     */
    fun recoverOnce(directory: File): Int =
        synchronized(recovered) {
            if (!recovered.add(identity(directory))) return 0
            try {
                removeOrphans(directory)
            } catch (_: IOException) {
                // A missing or unreadable cache has nothing this process could recover; creation reports its own error.
                0
            } catch (_: SecurityException) {
                0
            }
        }

    private fun removeOrphans(directory: File): Int {
        if (!directory.isDirectory) return 0
        var removed = 0
        Files.newDirectoryStream(directory.toPath()).use { entries ->
            for (entry in entries.take(MAX_SCANNED_ENTRIES).filter(::isScratchCopy)) {
                try {
                    if (Files.deleteIfExists(entry)) removed++
                } catch (_: IOException) {
                    // One undeletable copy must not keep the others; it is retried by the next process.
                }
            }
        }
        return removed
    }

    /** A link or folder that shares a prefix was not made by File.createTempFile here. */
    private fun isScratchCopy(entry: Path): Boolean {
        val name = entry.fileName.toString()
        return ownedPrefixes.any(name::startsWith) && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
    }

    private fun identity(directory: File): String =
        try {
            directory.canonicalPath
        } catch (_: IOException) {
            directory.absolutePath
        }
}
