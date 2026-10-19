package com.devbehindyou.atomicfilemanager.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HiddenFolderPathsTest {
    @Test
    fun `volume root of internal storage and of an SD card`() {
        assertEquals("/storage/emulated/0", volumeRootPathOf("/storage/emulated/0/Download/a.txt"))
        assertEquals("/storage/F929-FA97", volumeRootPathOf("/storage/F929-FA97/DCIM/a.jpg"))
    }

    @Test
    fun `paths outside a known storage volume have no root`() {
        assertNull(volumeRootPathOf("/data/data/app/files/a.txt"))
        assertNull(volumeRootPathOf("/storage/emulated"))
        assertNull(volumeRootPathOf("/storage/self/primary/a.txt"))
    }

    @Test
    fun `a taken name gets a numbered suffix before the extension`(
        @TempDir dir: File,
    ) {
        assertEquals(File(dir, "photo.jpg"), uniqueChild(dir, "photo.jpg"))
        File(dir, "photo.jpg").writeText("x")
        assertEquals(File(dir, "photo (1).jpg"), uniqueChild(dir, "photo.jpg"))
        File(dir, "photo (1).jpg").writeText("x")
        assertEquals(File(dir, "photo (2).jpg"), uniqueChild(dir, "photo.jpg"))
        File(dir, "notes").writeText("x")
        assertEquals(File(dir, "notes (1)"), uniqueChild(dir, "notes"))
    }
}
