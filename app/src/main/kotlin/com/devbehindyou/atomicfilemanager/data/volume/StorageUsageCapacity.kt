package com.devbehindyou.atomicfilemanager.data.volume

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/** Capacity from the supplied volume snapshot only: no filesystem probe or file traversal. */
fun storageCapacity(volumes: List<StorageVolumeInfo>): StorageCapacity {
    val mounted = volumes.filter { it.isMounted }
    val parents = IntArray(mounted.size) { it }
    val identities = mutableMapOf<String, Int>()

    fun root(index: Int): Int {
        var current = index
        while (parents[current] != current) {
            parents[current] = parents[parents[current]]
            current = parents[current]
        }
        return current
    }

    mounted.forEachIndexed { index, volume ->
        val keys =
            buildList {
                if (volume.id.isNotBlank()) add("id:${volume.id}")
                capacityPath(volume)?.let { add("path:$it") }
            }
        for (key in keys) {
            val previous = identities.putIfAbsent(key, index)
            if (previous != null) parents[root(index)] = root(previous)
        }
    }
    val groups = mounted.indices.groupBy(::root)
    var total = 0L
    var free = 0L
    var unknown = 0
    for (indices in groups.values) {
        val known =
            indices.asSequence().map(mounted::get).firstOrNull {
                it.totalBytes > 0 && it.freeBytes in 0..it.totalBytes
            }
        if (known == null || Long.MAX_VALUE - total < known.totalBytes) {
            unknown++
        } else {
            total += known.totalBytes
            free += known.freeBytes
        }
    }
    return StorageCapacity(total, free, groups.size, unknown)
}

private fun capacityPath(volume: StorageVolumeInfo): String? {
    val node = volume.rootNodeId?.takeIf { it.prefix == FileNodeId.Prefix.FILE } ?: return null
    val raw = node.raw.removePrefix(FileNodeId.Prefix.FILE.scheme)
    val absolute =
        raw.startsWith('/') ||
            (raw.length >= 3 && raw[0].isLetter() && raw[1] == ':' && raw[2] in "/\\")
    if (!absolute) return null
    return try {
        val path = Paths.get(raw)
        // Without resolving links, parent traversal can refer to a different physical directory.
        if (path.any { it.toString() == ".." }) null else path.normalize().toString()
    } catch (_: InvalidPathException) {
        null
    }
}
