package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

/** Direct local children only: never recursively walk a folder or materialize a full listing. */
internal class FolderThumbnailReader(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val openDirectory: (Path) -> DirectoryStream<Path> = { Files.newDirectoryStream(it) },
) {
    suspend fun read(folder: FileNode): List<ThumbnailSource> =
        withContext(io) {
            currentCoroutineContext().ensureActive()
            if (!folder.isDirectory || folder.isHidden || !folder.access.readable ||
                folder.id.prefix != FileNodeId.Prefix.FILE
            ) {
                return@withContext emptyList()
            }
            val id = FileNodeId.parse(folder.id.raw) ?: return@withContext emptyList()
            try {
                val directory =
                    Paths.get(
                        id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme),
                    ).toAbsolutePath().normalize()
                if (!allowsMiniature(directory)) return@withContext emptyList()
                scan(directory)
            } catch (_: IOException) {
                emptyList()
            } catch (_: DirectoryIteratorException) {
                emptyList()
            } catch (_: IllegalArgumentException) {
                emptyList()
            } catch (_: SecurityException) {
                emptyList()
            }
        }

    private suspend fun allowsMiniature(directory: Path): Boolean {
        val ancestors = boundedThumbnailAncestors(directory) ?: return false
        // Check from the root so no descendant of a symlink is inspected before the link is rejected.
        for (ancestor in ancestors) {
            currentCoroutineContext().ensureActive()
            if (ancestor.fileName?.toString()?.startsWith('.') == true) return false
            val attributes = Files.readAttributes(ancestor, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (attributes.isSymbolicLink || !attributes.isDirectory || hasNoMediaMarker(ancestor)) return false
        }
        return true
    }

    private fun hasNoMediaMarker(directory: Path): Boolean =
        try {
            Files.readAttributes(directory.resolve(".nomedia"), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            true
        } catch (_: NoSuchFileException) {
            false
        }

    private suspend fun scan(directory: Path): List<ThumbnailSource> {
        val previews = ArrayList<ThumbnailSource>(MAX_PREVIEWS)
        openDirectory(directory).use { entries ->
            val iterator = entries.iterator()
            var examined = 0
            while (examined < MAX_EXAMINED && previews.size < MAX_PREVIEWS && iterator.hasNext()) {
                currentCoroutineContext().ensureActive()
                val child = iterator.next()
                examined++
                childCandidate(child)?.let(previews::add)
            }
        }
        return previews.toList()
    }

    private fun childCandidate(child: Path): ThumbnailSource? {
        if (child.fileName.toString().startsWith('.')) return null
        val attributes =
            try {
                Files.readAttributes(child, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            } catch (_: IOException) {
                return null
            }
        if (!attributes.isRegularFile || attributes.isSymbolicLink) return null
        return thumbnailSourceOf(childNode(child, attributes))
    }

    private fun childNode(
        child: Path,
        attributes: BasicFileAttributes,
    ) = FileNode(
        id = FileNodeId.file(child.toAbsolutePath().toString()),
        name = child.fileName.toString(), displayName = child.fileName.toString(), mimeType = null,
        size = attributes.size(), modifiedAt = attributes.lastModifiedTime().toMillis().coerceAtLeast(0),
        isDirectory = false, isHidden = false, parentId = null, storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL, childCount = null, extras = null,
    )

    companion object {
        const val MAX_EXAMINED = 64
        const val MAX_PREVIEWS = 4
        const val MAX_ANCESTORS = 128
    }
}

/** Pure bound check before any metadata access; input is absolute and normalized. */
internal fun boundedThumbnailAncestors(directory: Path): List<Path>? {
    val ancestors = ArrayList<Path>()
    var current: Path? = directory
    while (current != null && ancestors.size < FolderThumbnailReader.MAX_ANCESTORS) {
        ancestors.add(current)
        current = current.parent
    }
    return if (current == null) ancestors.asReversed() else null
}
