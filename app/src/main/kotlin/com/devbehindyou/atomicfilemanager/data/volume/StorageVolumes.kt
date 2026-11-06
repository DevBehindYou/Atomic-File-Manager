package com.devbehindyou.atomicfilemanager.data.volume

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import java.io.File

/**
 * "Volume enumeration" (roadmap Phase 3 deliverable). Lists what storage volumes
 * physically exist on the device — internal, SD card, USB — using
 * `StorageManager.storageVolumes`, never by hardcoding `/storage/emulated/0` or scanning
 * `/storage/[wildcard]` (`ANDROID_STORAGE_RESEARCH.md`'s explicit rule).
 *
 * Resolves each mounted volume to its own root only when shared-storage access is
 * granted. Below API 30, app-specific directories are matched to their actual volume
 * through StorageManager instead of assuming the two lists have the same order.
 */
object StorageVolumes {
    fun enumerate(context: Context): List<StorageVolumeInfo> {
        val storageManager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        val volumes = storageManager.storageVolumes
        val legacyExternalDirs: Array<File?> =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) context.getExternalFilesDirs(null) else emptyArray()

        return volumes.mapIndexed { index, volume ->
            val directory = resolveDirectory(context, storageManager, volume, legacyExternalDirs)
            toStorageVolumeInfo(context, volume, index, directory)
        }
    }

    private fun resolveDirectory(
        context: Context,
        storageManager: StorageManager,
        volume: StorageVolume,
        legacyExternalDirs: Array<File?>,
    ): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching { volume.directory }.getOrNull()
        }
        val appSpecificDir =
            legacyExternalDirs.filterNotNull().firstOrNull {
                storageManager.getStorageVolume(it) == volume
            } ?: return null
        val suffix = "/Android/data/${context.packageName}/files"
        return if (appSpecificDir.path.endsWith(suffix)) {
            File(appSpecificDir.path.removeSuffix(suffix))
        } else {
            null
        }
    }

    private fun toStorageVolumeInfo(
        context: Context,
        volume: StorageVolume,
        index: Int,
        directory: File?,
    ): StorageVolumeInfo {
        val type =
            when {
                volume.isPrimary -> StorageType.INTERNAL_SHARED
                volume.isRemovable -> StorageType.SD_CARD // Can't reliably distinguish USB OTG
                // from an SD card at this API surface alone — ANDROID_STORAGE_RESEARCH.md §10
                // flags exactly this as an open, device-verification-only question.
                else -> StorageType.VIRTUAL
            }
        val space =
            directory?.takeIf { it.canRead() }?.let { dir ->
                runCatching { StatFs(dir.absolutePath) }
                    .map { it.totalBytes to it.availableBytes }
                    .getOrNull()
            } ?: if (volume.isPrimary) {
                runCatching {
                    val stat = StatFs(context.filesDir.absolutePath)
                    stat.totalBytes to stat.availableBytes
                }.getOrDefault(0L to 0L)
            } else {
                0L to 0L
            }

        return StorageVolumeInfo(
            id = volume.uuid ?: if (volume.isPrimary) "primary" else "volume-$index",
            label =
                runCatching { volume.getDescription(context) }
                    .getOrNull()
                    ?: if (volume.isPrimary) "Internal storage" else "Storage $index",
            type = type,
            totalBytes = space.first,
            freeBytes = space.second,
            isRemovable = volume.isRemovable,
            isMounted =
                volume.state == Environment.MEDIA_MOUNTED || volume.state == Environment.MEDIA_MOUNTED_READ_ONLY,
            rootNodeId =
                directory?.takeIf { SharedStorageAccess.isGranted(context) && it.canRead() }
                    ?.let { FileNodeId.file(it.absolutePath) },
            requiresGrant = !SharedStorageAccess.isGranted(context) || directory?.canRead() != true,
        )
    }

    fun getAppCacheSize(context: Context): Long {
        return runCatching {
            fun calculateDirSize(
                dir: File?,
                depth: Int = 0,
            ): Long {
                if (dir == null || depth > 4 || !dir.exists()) return 0L
                var size = 0L
                dir.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) calculateDirSize(file, depth + 1) else file.length()
                }
                return size
            }
            calculateDirSize(context.cacheDir)
        }.getOrDefault(0L)
    }
}
