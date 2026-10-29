package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GalleryTest {
    private fun node(
        name: String,
        mime: String? = null,
        directory: Boolean = false,
    ) = FileNode(
        id = FileNodeId.file("/storage/emulated/0/DCIM/$name"),
        name = name,
        displayName = name,
        mimeType = mime,
        size = 1,
        modifiedAt = 0,
        isDirectory = directory,
        isHidden = false,
        parentId = null,
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL,
        childCount = null,
        extras = null,
    )

    @Test
    fun `GIF routing preserves normalized metadata and never animates folders`() {
        assertTrue(Gallery.isGif(node("animation.GIF", mime = "text/plain")))
        assertTrue(Gallery.isGif(node("opaque", mime = "IMAGE/GIF; charset=binary")))
        assertFalse(Gallery.isGif(node("still.png", mime = "image/png")))
        assertFalse(Gallery.isGif(node("opaque", mime = "image/*")))
        assertFalse(Gallery.isGif(node("folder.gif", directory = true)))
    }

    @Test
    fun `images are known by mime type or extension, never folders`() {
        assertTrue(Gallery.isImage(node("IMG_1.JPG")))
        assertTrue(Gallery.isImage(node("scan", mime = "image/png")))
        assertTrue(Gallery.isImage(node("photo.heic")))
        assertFalse(Gallery.isImage(node("notes.txt")))
        assertFalse(Gallery.isImage(node("album.jpg", directory = true)))
    }

    @Test
    fun `the gallery keeps listing order and skips non-images`() {
        val a = node("a.jpg")
        val b = node("b.png")
        val listing = listOf(node("Trip", directory = true), a, node("readme.txt"), b)

        val (images, index) = Gallery.of(listing, b)

        assertEquals(listOf(a, b), images)
        assertEquals(1, index)
    }

    @Test
    fun `an image missing from the listing opens on its own`() {
        val stray = node("elsewhere.jpg")

        assertEquals(listOf(stray) to 0, Gallery.of(listOf(node("a.jpg")), stray))
    }

    @Test
    fun `panning stops at the zoomed image's edge`() {
        assertEquals(0f, Gallery.clampPan(40f, scale = 1f, extent = 1000f))
        assertEquals(500f, Gallery.clampPan(900f, scale = 2f, extent = 1000f))
        assertEquals(-500f, Gallery.clampPan(-900f, scale = 2f, extent = 1000f))
        assertEquals(120f, Gallery.clampPan(120f, scale = 2f, extent = 1000f))
        assertEquals("3 of 12", Gallery.position(2, 12))
    }
}
