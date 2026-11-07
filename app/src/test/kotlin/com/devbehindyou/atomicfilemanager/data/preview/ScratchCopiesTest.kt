package com.devbehindyou.atomicfilemanager.data.preview

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScratchCopiesTest {
    private val directory: File = Files.createTempDirectory("scratch-owner").toFile()
    private val outside: File = Files.createTempDirectory("scratch-outside").toFile()

    @After
    fun removeOnlyThisTestsDirectories() {
        directory.deleteRecursively()
        outside.deleteRecursively()
    }

    @Test
    fun aCopyMadeByThisProcessSurvivesLaterRecoveryRequests() {
        val orphan = orphan("atomic-pdf-old.pdf")
        val live = ScratchCopies.create(directory, ScratchCopies.PDF_PREFIX, ".pdf")
        assertFalse(orphan.exists())
        // Startup recovery that arrives after the first copy must not treat it as an orphan.
        assertEquals(0, ScratchCopies.recoverOnce(directory))
        val second = ScratchCopies.create(directory, ScratchCopies.MEDIA_PREFIX, ".mp4")
        assertTrue(live.isFile)
        assertTrue(second.isFile)
    }

    @Test
    fun startupRecoveryCountsOrphansOnceAndLeavesLaterCopies() {
        orphan("media-preview-1.mp4")
        orphan("atomic-7z-2.7z")
        assertEquals(2, ScratchCopies.recoverOnce(directory))
        val live = ScratchCopies.create(directory, ScratchCopies.ARCHIVE_PREFIX, ".7z")
        assertEquals(0, ScratchCopies.recoverOnce(directory))
        assertTrue(live.isFile)
    }

    @Test
    fun linksThatShareAPrefixAreNotFollowedOrRemoved() {
        val target = File(outside, "user-video.mp4").apply { writeBytes(byteArrayOf(5)) }
        val link = File(directory, "media-preview-link.mp4").toPath()
        Files.createSymbolicLink(link, target.toPath())
        assertEquals(0, ScratchCopies.recoverOnce(directory))
        assertTrue(Files.isSymbolicLink(link))
        assertEquals(5, target.readBytes().single().toInt())
    }

    @Test
    fun aCacheThatIsNotADirectoryRecoversNothingAndCreationStillFails() {
        val notDirectory = File(outside, "cache-file").apply { writeBytes(byteArrayOf(1)) }
        assertEquals(0, ScratchCopies.recoverOnce(notDirectory))
        assertThrows(java.io.IOException::class.java) {
            ScratchCopies.create(notDirectory, ScratchCopies.MEDIA_PREFIX, ".tmp")
        }
        assertTrue(notDirectory.isFile)
    }

    @Test
    fun onlyScratchPrefixesCanBeCreated() {
        assertThrows(IllegalArgumentException::class.java) {
            ScratchCopies.create(directory, "open-", ".bin")
        }
    }

    @Test
    fun copiesCreatedWhileStartupRecoveryRunsAllSurvive() {
        repeat(ORPHANS) { orphan("media-preview-old-$it.mp4") }
        val pool = Executors.newFixedThreadPool(WORKERS + 1)
        try {
            val start = CountDownLatch(1)
            val recovery =
                pool.submit<Int> {
                    start.await()
                    ScratchCopies.recoverOnce(directory)
                }
            val creations =
                (0 until WORKERS).map { index ->
                    pool.submit<File> {
                        start.await()
                        ScratchCopies.create(directory, ScratchCopies.PDF_PREFIX, ".$index.pdf")
                    }
                }
            start.countDown()
            val created = creations.map { it.get(10, TimeUnit.SECONDS) }
            val removed = recovery.get(10, TimeUnit.SECONDS)
            assertTrue("Every copy made in this process survives", created.all(File::isFile))
            assertTrue("Exactly one caller recovered the orphans", removed == 0 || removed == ORPHANS)
            assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith("media-preview-old-") })
        } finally {
            pool.shutdownNow()
        }
    }

    private fun orphan(name: String) = File(directory, name).apply { writeBytes(ByteArray(8)) }

    private companion object {
        const val ORPHANS = 32
        const val WORKERS = 8
    }
}
