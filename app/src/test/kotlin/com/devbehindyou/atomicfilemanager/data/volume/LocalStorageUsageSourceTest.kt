package com.devbehindyou.atomicfilemanager.data.volume

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class LocalStorageUsageSourceTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `real directory metadata includes hidden entries in bounded batches`() = runTest {
        repeat(260) { Files.createFile(temporary.resolve("file-$it.bin")) }
        Files.write(temporary.resolve(".hidden.pdf"), byteArrayOf(1, 2, 3))
        Files.createDirectory(temporary.resolve("Child"))
        val source = source()
        val results = source.list(temporary.toString()).toList()
        assertFalse(results.any { it is FileResult.Failure })
        val batches = results.filterIsInstance<FileResult.Success<List<StorageUsageEntry>>>().map { it.value }
        assertTrue(batches.all { it.size <= 128 })
        val entries = batches.flatten()
        assertEquals(262, entries.size)
        assertEquals(262, entries.map { it.path }.toSet().size)
        assertEquals(3L, entries.single { it.name == ".hidden.pdf" }.size)
        assertTrue(entries.single { it.name == "Child" }.directory)
        assertFalse(entries.any { it.link })
    }

    @Test
    fun `empty directory emits explicit empty success and missing directory emits failure`() = runTest {
        val source = source()
        assertEquals(listOf(FileResult.Success(emptyList<StorageUsageEntry>())), source.list(temporary.toString()).toList())
        val missing = source.list(temporary.resolve("missing").toString()).toList()
        assertTrue(missing.single() is FileResult.Failure)
    }

    @Test
    fun `symlink entries are identified and cannot be traversed as directories`() = runTest {
        val root = Files.createDirectory(temporary.resolve("volume"))
        val outside = Files.createDirectory(temporary.resolve("outside"))
        Files.write(outside.resolve("private.txt"), byteArrayOf(7))
        val link = root.resolve("linked")
        val supported = try {
            Files.createSymbolicLink(link, outside)
            true
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
        assumeTrue(supported, "Host does not permit creating test symlinks")
        val source = source()
        val entries = source.list(root.toString()).toList()
            .filterIsInstance<FileResult.Success<List<StorageUsageEntry>>>().flatMap { it.value }
        assertEquals(1, entries.size)
        assertTrue(entries.single().link)
        assertEquals("linked", entries.single().name)
        assertTrue(source.list(link.toString()).toList().single() is FileResult.Failure)
    }

    @Test
    fun `repeated UUIDs and corroborated canonical aliases contribute capacity once`() = runTest {
        val nested = Files.createDirectory(temporary.resolve("nested"))
        val root = volume("primary", temporary)
        val sameId = volume("primary", nested)
        val sameCanonical = volume("alias", temporary.resolve("nested/.."))
        val plan = source().plan(listOf(root, sameId, sameCanonical))
        assertEquals(1, plan.capacity.mountedVolumes)
        assertEquals(1_000L, plan.capacity.totalBytes)
        assertEquals(250L, plan.capacity.freeBytes)
        assertEquals(listOf(temporary.toFile().canonicalPath), plan.roots)
    }

    @Test
    fun `distinct storage UUIDs with identical capacity still contribute separately`() = runTest {
        val sd = Files.createDirectory(temporary.resolve("sd"))
        val usb = Files.createDirectory(temporary.resolve("usb"))
        val plan = source().plan(listOf(volume("SD-UUID", sd), volume("USB-UUID", usb)))
        assertEquals(2, plan.capacity.mountedVolumes)
        assertEquals(2_000L, plan.capacity.totalBytes)
        assertEquals(500L, plan.capacity.freeBytes)
        assertEquals(setOf(sd.toFile().canonicalPath, usb.toFile().canonicalPath), plan.roots.toSet())
    }

    @Test
    fun `nested readable roots enumerate once while mounted denied and unknown volumes remain reported`() = runTest {
        val nested = Files.createDirectory(temporary.resolve("nested"))
        val denied = volume("denied", null).copy(totalBytes = 0, freeBytes = 0, requiresGrant = true)
        val unmounted = volume("removed", null).copy(isMounted = false)
        val plan = source().plan(listOf(volume("primary", temporary), volume("linked", nested), denied, unmounted))
        assertEquals(3, plan.capacity.mountedVolumes)
        assertEquals(1, plan.capacity.unknownVolumes)
        assertEquals(1, plan.unavailableRoots)
        assertEquals(listOf(temporary.toFile().canonicalPath), plan.roots)
    }

    @Test
    fun `unrepresentable physical capacity remains unknown rather than fabricating zero used space`() = runTest {
        val first = Files.createDirectory(temporary.resolve("first"))
        val second = Files.createDirectory(temporary.resolve("second"))
        val source = LocalStorageUsageSource(primaryPath = { first.toString() }, space = { Long.MAX_VALUE to 10L })
        val plan = source.plan(listOf(volume("first", first), volume("second", second)))
        assertEquals(2, plan.capacity.mountedVolumes)
        assertEquals(1, plan.capacity.unknownVolumes)
        assertEquals(Long.MAX_VALUE, plan.capacity.totalBytes)
        assertEquals(10L, plan.capacity.freeBytes)
        assertEquals(Long.MAX_VALUE - 10L, plan.capacity.usedBytes)
    }

    private fun source() = LocalStorageUsageSource(primaryPath = { temporary.toString() }, space = { 1_000L to 250L })

    private fun volume(id: String, root: Path?) = StorageVolumeInfo(
        id = id,
        label = id,
        type = if (id == "primary") StorageType.INTERNAL_SHARED else StorageType.SD_CARD,
        totalBytes = 1_000,
        freeBytes = 250,
        isRemovable = id != "primary",
        isMounted = true,
        rootNodeId = root?.let { FileNodeId.file(it.toString()) },
        requiresGrant = root == null,
    )
}
