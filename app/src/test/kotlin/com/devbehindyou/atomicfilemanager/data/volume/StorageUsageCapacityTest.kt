package com.devbehindyou.atomicfilemanager.data.volume

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class StorageUsageCapacityTest {
    @Test
    fun `mounted internal and read only media contribute while removed media do not`() {
        val internal = volume("internal", 1_000, 300).copy(type = StorageType.INTERNAL_SHARED)
        // StorageVolumes maps both RW and RO mount states to this availability flag.
        val readOnly = volume("read-only", 2_000, 400)
        val removed = volume("removed", 5_000, 2_000).copy(isMounted = false)
        val actual = storageCapacity(listOf(internal, readOnly, removed))
        assertEquals(StorageCapacity(3_000, 700, 2), actual)
        assertEquals(2_300L, actual.usedBytes)
    }

    @Test
    fun `mounted unknown capacity remains visible and a denied root can retain known capacity`() {
        val unknown = volume("unknown", 0, 0).copy(rootNodeId = null, requiresGrant = true)
        val deniedKnown = volume("denied", 1_000, 200).copy(rootNodeId = null, requiresGrant = true)
        assertEquals(StorageCapacity(1_000, 200, 2, 1), storageCapacity(listOf(unknown, deniedKnown)))
        assertEquals(StorageCapacity(0, 0, 1, 1), storageCapacity(listOf(unknown)))
        assertEquals(StorageCapacity(0, 0, 0), storageCapacity(emptyList()))
    }

    @Test
    fun `repeated identities and normalized aliases contribute capacity once`() {
        val first = volume("first", 1_000, 200)
        val sameId = first.copy(rootNodeId = nodeId("elsewhere"))
        val alias = first.copy(id = "alias", rootNodeId = nodeId("first/./"))
        assertEquals(StorageCapacity(1_000, 200, 1), storageCapacity(listOf(first, sameId, alias)))
    }

    @Test
    fun `alias bridges deduplicate transitively regardless of row ordering`() {
        val first = volume("first", 1_000, 200)
        val second = volume("second", 1_000, 200)
        val bridge = second.copy(rootNodeId = first.rootNodeId)
        for (rows in listOf(listOf(first, second, bridge), listOf(bridge, second, first))) {
            assertEquals(StorageCapacity(1_000, 200, 1), storageCapacity(rows))
        }
    }

    @Test
    fun `distinct UUIDs are not collapsed for identical capacity or nested roots`() {
        val sd = volume("SD-UUID", 1_000, 200)
        val usb = volume("USB-UUID", 1_000, 200).copy(rootNodeId = nodeId("SD-UUID/nested"))
        assertEquals(StorageCapacity(2_000, 400, 2), storageCapacity(listOf(sd, usb)))
    }

    @Test
    fun `unknown duplicate metadata does not conceal an available physical snapshot`() {
        val unknown = volume("same", 0, 0).copy(rootNodeId = null)
        val known = volume("same", 1_000, 200)
        assertEquals(StorageCapacity(1_000, 200, 1), storageCapacity(listOf(unknown, known)))
    }

    @Test
    fun `malformed and unrepresentable capacity stays unknown without wrapped usage`() {
        val huge = volume("huge", Long.MAX_VALUE, 10)
        val extra = volume("extra", 1_000, 200)
        val malformed = volume("bad", 1_000, 1_001)
        val actual = storageCapacity(listOf(huge, extra, malformed))
        assertEquals(StorageCapacity(Long.MAX_VALUE, 10, 3, 2), actual)
        assertEquals(Long.MAX_VALUE - 10, actual.usedBytes)
    }

    @Test
    fun `parent traversal and missing identities do not invent corroborated aliases`() {
        val direct = volume("direct", 1_000, 200)
        val traversal = volume("traversal", 1_000, 200).copy(rootNodeId = nodeId("link/../direct"))
        val missingA = volume("", 0, 0).copy(rootNodeId = null)
        val missingB = missingA.copy()
        assertEquals(StorageCapacity(2_000, 400, 4, 2), storageCapacity(listOf(direct, traversal, missingA, missingB)))
    }

    private fun nodeId(path: String): FileNodeId = FileNodeId.file(File("capacity-snapshot", path).absolutePath)

    private fun volume(
        id: String,
        total: Long,
        free: Long,
    ) = StorageVolumeInfo(
        id = id,
        label = id,
        type = StorageType.SD_CARD,
        totalBytes = total,
        freeBytes = free,
        isRemovable = true,
        isMounted = true,
        rootNodeId = nodeId(id),
        requiresGrant = false,
    )
}
