package com.devbehindyou.atomicfilemanager.data.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ShareUploadsTest {
    @TempDir
    lateinit var root: File

    @Test
    fun `concurrent uploads have independent staging and only one owns the final name`() {
        val uploads = ShareUploads {}
        val staged = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                listOf("first", "second").map { text ->
                    pool.submit<Boolean> {
                        try {
                            uploads.receive(text.byteInputStream(), root, "same.txt", text.length.toLong()) {
                                staged.await(5, TimeUnit.SECONDS)
                            }
                            true
                        } catch (_: FileAlreadyExistsException) {
                            false
                        }
                    }
                }.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it })
            assertEquals(if (results.first()) "first" else "second", File(root, "same.txt").readText())
            assertEquals(listOf("same.txt"), root.list()!!.toList())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `predictable old staging symlinks and existing final files are never changed`() {
        val victim = File(root, "victim").apply { writeText("keep") }
        val oldPart = File(root, ".new.txt.atomic-part")
        Files.createSymbolicLink(oldPart.toPath(), victim.toPath())
        val uploads = ShareUploads {}
        uploads.receive("data".byteInputStream(), root, "new.txt", 4) {}
        assertEquals("keep", victim.readText())
        assertTrue(Files.isSymbolicLink(oldPart.toPath()))
        assertEquals("data", File(root, "new.txt").readText())
        assertThrows(FileAlreadyExistsException::class.java) {
            uploads.receive("bad".byteInputStream(), root, "victim", 3) {}
        }
        assertEquals("keep", victim.readText())
    }

    @Test
    fun `an existing final symlink is refused even when its destination is absent`() {
        val absent = File(root, "absent")
        Files.createSymbolicLink(File(root, "new.txt").toPath(), absent.toPath())
        assertThrows(FileAlreadyExistsException::class.java) {
            ShareUploads {}.receive("bad".byteInputStream(), root, "new.txt", 3) {}
        }
        assertFalse(absent.exists())
    }

    @Test
    fun `short receive removes only its own stage`() {
        val oldPart = File(root, ".new.txt.atomic-part").apply { writeText("keep") }
        assertThrows(IOException::class.java) {
            ShareUploads {}.receive("short".byteInputStream(), root, "new.txt", 100) {}
        }
        assertEquals(listOf(oldPart.name), root.list()!!.toList())
        assertEquals("keep", oldPart.readText())
    }

    @Test
    fun `publication is guarded and stopping it removes the owned partial target`() {
        val target = File(root, "new.txt")
        val publishing = CountDownLatch(1)
        val stop = CountDownLatch(1)
        val uploads =
            ShareUploads {
                if (target.exists()) {
                    publishing.countDown()
                    check(stop.await(5, TimeUnit.SECONDS))
                    throw IOException("Stopped")
                }
            }
        val pool = Executors.newSingleThreadExecutor()
        try {
            val result =
                pool.submit {
                    assertThrows(IOException::class.java) {
                        uploads.receive("data".byteInputStream(), root, target.name, 4) {}
                    }
                }
            assertTrue(publishing.await(5, TimeUnit.SECONDS))
            assertTrue(uploads.isPublishing(target))
            stop.countDown()
            result.get(10, TimeUnit.SECONDS)
            assertFalse(target.exists())
            assertFalse(uploads.isPublishing(target))
            assertTrue(root.list()!!.isEmpty())
        } finally {
            stop.countDown()
            pool.shutdownNow()
        }
    }
}
