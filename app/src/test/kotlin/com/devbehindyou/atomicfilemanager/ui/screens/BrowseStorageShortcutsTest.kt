package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.components.BreadcrumbItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseStorageShortcutsTest {
    @Test
    fun filesystemDescendantsUseLongestRootAndRespectPathBoundaries() {
        val all = volume("all", FileNodeId.file("/storage"))
        val internal = volume("internal", FileNodeId.file("/storage/emulated/0"))
        val sd = volume("sd", FileNodeId.file("/storage/card"))
        val volumes = listOf(all, internal, sd)
        assertEquals(
            internal,
            currentStorageVolume(volumes, FileNodeId.file("/storage/emulated/0/Pictures"), emptyList()),
        )
        assertEquals(sd, currentStorageVolume(volumes, FileNodeId.file("/storage/card/Download"), emptyList()))
        assertEquals(all, currentStorageVolume(volumes, FileNodeId.file("/storage/card2"), emptyList()))
        assertNull(currentStorageVolume(listOf(sd), FileNodeId.file("/storage/card2/Download"), emptyList()))
    }

    @Test
    fun trailingRootSlashAndFilesystemRootStillMatchDescendants() {
        val root = volume("root", FileNodeId.file("/"))
        val sd = volume("sd", FileNodeId.file("/storage/card/"))
        assertEquals(sd, currentStorageVolume(listOf(root, sd), FileNodeId.file("/storage/card"), emptyList()))
        assertEquals(sd, currentStorageVolume(listOf(root, sd), FileNodeId.file("/storage/card/DCIM"), emptyList()))
        assertEquals(root, currentStorageVolume(listOf(root, sd), FileNodeId.file("/different"), emptyList()))
    }

    @Test
    fun opaqueRootUsesExactIdentityAndDoesNotAssumeProviderStringAncestry() {
        val root = FileNodeId.saf("content://provider/tree/root")
        val volume = volume("saf", root)
        assertEquals(volume, currentStorageVolume(listOf(volume), root, emptyList()))
        assertNull(
            currentStorageVolume(
                listOf(volume),
                FileNodeId.saf("content://provider/tree/root/child"),
                emptyList(),
            ),
        )
    }

    @Test
    fun deepestKnownBreadcrumbIdentifiesOpaqueStorageWithoutDecodingIds() {
        val outer = volume("outer", FileNodeId.saf("content://provider/tree/opaque%2Fouter"))
        val inner = volume("inner", FileNodeId.usb("opaque:root"))
        val child = FileNodeId.usb("unrelated:child")
        val crumbs =
            listOf(
                BreadcrumbItem("Outer", outer.rootNodeId!!.raw),
                BreadcrumbItem("Inner", inner.rootNodeId!!.raw),
                BreadcrumbItem("Child", child.raw),
            )
        assertEquals(inner, currentStorageVolume(listOf(outer, inner), child, crumbs))
        assertEquals(outer, currentStorageVolume(listOf(outer, inner), outer.rootNodeId, crumbs))
    }

    @Test
    fun unmountedAndMissingRootsCannotClaimTheCurrentStorage() {
        val root = FileNodeId.file("/storage/card")
        val unmounted = volume("removed", root).copy(isMounted = false)
        val needsGrant = volume("grant", null).copy(requiresGrant = true)
        assertNull(currentStorageVolume(listOf(unmounted, needsGrant), root, listOf(BreadcrumbItem("Card", root.raw))))
        assertNull(currentStorageVolume(listOf(needsGrant), null, emptyList()))
    }

    @Test
    fun filesystemRootDoesNotClaimOpaqueOrCaseDifferentLocations() {
        val volume = volume("sd", FileNodeId.file("/storage/card"))
        assertNull(currentStorageVolume(listOf(volume), FileNodeId.file("/storage/Card/Photos"), emptyList()))
        assertNull(currentStorageVolume(listOf(volume), FileNodeId.saf("content://provider/storage/card"), emptyList()))
    }

    private fun volume(
        id: String,
        root: FileNodeId?,
    ) = StorageVolumeInfo(id, id, StorageType.SD_CARD, 0, 0, true, true, root, false)
}
