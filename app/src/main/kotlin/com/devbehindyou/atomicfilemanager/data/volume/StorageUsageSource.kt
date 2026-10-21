package com.devbehindyou.atomicfilemanager.data.volume

import android.os.StatFs
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

data class StorageUsageEntry(
    val path: String,
    val name: String,
    val size: Long,
    val directory: Boolean,
    val link: Boolean,
)

data class StorageUsagePlan(
    val key: String,
    val roots: List<String>,
    val capacity: StorageCapacity,
    val unavailableRoots: Int,
)

interface StorageUsageSource {
    suspend fun plan(volumes: List<StorageVolumeInfo>): StorageUsagePlan

    /** Explicit empty success is evidence of an empty directory; silence is not. */
    fun list(directory: String): Flow<FileResult<List<StorageUsageEntry>>>
}

/** Metadata only: no file streams, hashing, thumbnail decoding, MediaStore query or root access. */
class LocalStorageUsageSource(
    private val volumesNow: (List<StorageVolumeInfo>) -> List<StorageVolumeInfo> = { it },
    private val primaryPath: () -> String,
    private val space: (String) -> Pair<Long, Long>? = { path ->
        runCatching { StatFs(path).let { it.totalBytes to it.availableBytes } }.getOrNull()
    },
) : StorageUsageSource {
    override suspend fun plan(volumes: List<StorageVolumeInfo>): StorageUsagePlan {
        val mounted = volumesNow(volumes).filter { it.isMounted }
        val ids = HashSet<String>()
        val paths = HashSet<String>()
        val roots = mutableListOf<String>()
        val keys = mutableListOf<String>()
        var total = 0L
        var free = 0L
        var unknown = 0
        var unavailable = 0
        var counted = 0
        for (volume in mounted) {
            currentCoroutineContext().ensureActive()
            val root =
                volume.rootNodeId?.takeIf { it.prefix == FileNodeId.Prefix.FILE }
                    ?.raw?.removePrefix(FileNodeId.Prefix.FILE.scheme)
                    ?.let { runCatching { File(it).canonicalPath }.getOrNull() }
            // UUID identity and a corroborated canonical alias deduplicate volumes. FUSE st_dev
            // alone is deliberately insufficient: distinct SD/USB UUIDs must never be collapsed.
            if (volume.id in ids || root != null && root in paths) continue
            ids += volume.id
            if (root != null) paths += root
            counted++
            val probe = root ?: if (volume.type == StorageType.INTERNAL_SHARED) primaryPath() else null
            val measured = probe?.let(space) ?: (volume.totalBytes to volume.freeBytes)
            if (
                measured.first > 0 && measured.second in 0..measured.first &&
                Long.MAX_VALUE - total >= measured.first
            ) {
                total += measured.first
                free += measured.second
            } else {
                unknown++
            }
            if (root == null || volume.requiresGrant) unavailable++ else roots += root
            // Capacity uncertainty does not change the identity of a previous logical breakdown.
            keys += "${volume.id}|${root.orEmpty()}|${volume.requiresGrant}"
        }
        // Nested roots are scanned once, even if the caller has linked the same folder twice.
        val distinctRoots =
            roots.sortedBy { it.length }.fold(mutableListOf<String>()) { result, root ->
                if (result.none { isUnderStorageRoot(root, it) }) result += root
                result
            }
        return StorageUsagePlan(
            keys.sorted().joinToString("\n"),
            distinctRoots,
            StorageCapacity(total, free.coerceAtMost(total), counted, unknown),
            unavailable,
        )
    }

    override fun list(directory: String): Flow<FileResult<List<StorageUsageEntry>>> =
        flow {
            val batch = ArrayList<StorageUsageEntry>(128)
            val folder = File(directory).toPath()
            val attributes = Files.readAttributes(folder, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (attributes.isSymbolicLink || !attributes.isDirectory) {
                emit(FileResult.Failure(FileError.AccessDenied(null)))
                return@flow
            }
            Files.newDirectoryStream(folder).use { entries ->
                for (path in entries) {
                    currentCoroutineContext().ensureActive()
                    val next =
                        try {
                            val child =
                                Files.readAttributes(
                                    path,
                                    BasicFileAttributes::class.java,
                                    LinkOption.NOFOLLOW_LINKS,
                                )
                            StorageUsageEntry(
                                path.toFile().canonicalPath,
                                path.fileName.toString(),
                                child.size().coerceAtLeast(0),
                                child.isDirectory,
                                child.isSymbolicLink || !child.isDirectory && !child.isRegularFile,
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    if (next == null) emit(FileResult.Failure(FileError.AccessDenied(null))) else batch += next
                    if (batch.size == 128) {
                        emit(FileResult.Success(batch.toList()))
                        batch.clear()
                    }
                }
            }
            // Emit an explicit success even for an empty directory.
            emit(FileResult.Success(batch.toList()))
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(FileResult.Failure(FileError.IoFailure(null)))
        }
}

internal fun isUnderStorageRoot(
    path: String,
    root: String,
): Boolean = path == root || path.startsWith(root.trimEnd(File.separatorChar) + File.separatorChar)
