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
                val directory = Paths.get(id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme))
                // A dot folder, symlink or explicit media-hiding marker must not leak a miniature.
                if (directory.any { it.toString().startsWith('.') } || Files.isSymbolicLink(directory) ||
                    !Files.isDirectory(directory, NOFOLLOW_LINKS) ||
                    Files.exists(directory.resolve(".nomedia"), NOFOLLOW_LINKS)
                ) {
                    return@withContext emptyList()
                }
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

    private suspend fun scan(directory: Path): List<ThumbnailSource> {
        val previews = ArrayList<ThumbnailSource>(MAX_PREVIEWS)
        openDirectory(directory).use { entries ->
            val iterator = entries.iterator()
            var examined = 0
            while (examined < MAX_EXAMINED && previews.size < MAX_PREVIEWS && iterator.hasNext()) {
                currentCoroutineContext().ensureActive()
                val child = iterator.next()
                examined++
                if (child.fileName.toString().startsWith('.')) continue
                val attributes =
                    try {
                        Files.readAttributes(child, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                    } catch (_: IOException) {
                        continue
                    }
                if (!attributes.isRegularFile || attributes.isSymbolicLink) continue
                val node = childNode(child, attributes)
                thumbnailSourceOf(node)?.let(previews::add)
            }
        }
        return previews.toList()
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
    }
}
