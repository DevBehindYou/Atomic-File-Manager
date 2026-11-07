package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.data.archive.TempFileSpool
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

/**
 * A process that dies during a media, PDF or 7z preview cannot run its `finally` cleanup. The next process
 * owns those scratch copies and removes them before it makes its first one. Each test uses a fresh directory,
 * as a fresh process sees its cache. Only the real creators are exercised, so the controls also run on the
 * unchanged base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class ScratchCopyRecoveryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val directory: File = Files.createTempDirectory("previous-process-cache").toFile()

    @After
    fun removeOnlyThisTestsDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun mediaPreparationRemovesCopiesOrphanedByAnEarlierProcess() =
        runBlocking {
            val orphans = plantOrphans()
            val unrelated = plantUnrelated()
            val cache =
                object : ContextWrapper(context) {
                    override fun getCacheDir() = directory
                }
            val metadata = MediaMetadata(null, null, null, 0, 0, 0, null, null)
            val backend =
                object : StorageBackend by InMemoryBackend() {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                        FileResult.Success(InputStreamProvider { ByteArrayInputStream(byteArrayOf(1, 2, 3)) })
                }
            val helper = MediaPreviewHelper(cache, Dispatchers.IO, metadataReader = { metadata }) { backend }
            val result = helper.withMedia(FileNodeId.ftp("server", "/clip.mp4"), "clip.mp4") { "played" }
            assertEquals(FileResult.Success("played"), result)
            assertOrphansRemoved(orphans)
            assertUnrelatedKept(unrelated)
        }

    @Test
    fun pdfDocumentLoadRemovesCopiesOrphanedByAnEarlierProcess() =
        runBlocking {
            val orphans = plantOrphans()
            val unrelated = plantUnrelated()
            val loader =
                PdfDocumentLoader<Unit>(
                    { directory },
                    { FileResult.Success(InputStreamProvider { ByteArrayInputStream(byteArrayOf(1, 2)) }) },
                    { OnePageRasterizer },
                    Dispatchers.IO,
                )
            val result = loader.withDocument(FileNodeId.ftp("server", "/manual.pdf")) { it.pageCount }
            assertEquals(FileResult.Success(1), result)
            assertOrphansRemoved(orphans)
            assertUnrelatedKept(unrelated)
        }

    @Test
    fun archiveSpoolRemovesCopiesOrphanedByAnEarlierProcess() {
        val orphans = plantOrphans()
        val unrelated = plantUnrelated()
        TempFileSpool(directory).spool(ByteArrayInputStream(byteArrayOf(7, 7))).use { spooled ->
            assertEquals(2L, spooled.channel.size())
        }
        assertOrphansRemoved(orphans)
        assertUnrelatedKept(unrelated)
    }

    /** One file per prefix that only an in-process session ever owns. */
    private fun plantOrphans(): List<File> =
        listOf("media-preview-1.mp4", "media-preview-2.tmp", "atomic-pdf-3.pdf", "atomic-7z-4.7z").map { name ->
            File(directory, name).apply { writeBytes(ByteArray(16) { 42 }) }
        }

    /**
     * Copies another app may still read (`external-preview/`, `clean/`), caches with their own owners, user-like
     * names and a directory that merely shares a prefix must all survive.
     */
    private fun plantUnrelated(): List<File> {
        val external = File(directory, "external-preview").apply { mkdirs() }
        val clean = File(directory, "clean").apply { mkdirs() }
        val coil = File(directory, "image_cache").apply { mkdirs() }
        val prefixedDirectory = File(directory, "media-preview-folder").apply { mkdirs() }
        return listOf(
            File(external, "open-5.bin"),
            File(external, "media-preview-6.mp4"),
            File(clean, "IMG_0001.jpg"),
            File(coil, "journal"),
            File(prefixedDirectory, "inside.mp4"),
            File(directory, "notes.txt"),
            File(directory, "atomic-pdf"),
            File(directory, "my-media-preview-7.mp4"),
        ).onEach { it.writeBytes(byteArrayOf(9)) }
    }

    private fun assertOrphansRemoved(orphans: List<File>) {
        val survivors = orphans.filter { it.exists() }.map { it.name }
        assertTrue("Orphaned scratch copies must be removed, found $survivors", survivors.isEmpty())
        val leftover =
            directory.listFiles().orEmpty().filter { it.isFile && OWNED.any { prefix -> it.name.startsWith(prefix) } }
        assertTrue("The current copy must also be released, found $leftover", leftover.isEmpty())
    }

    private fun assertUnrelatedKept(unrelated: List<File>) {
        for (file in unrelated) {
            assertTrue("${file.relativeTo(directory)} must survive", file.isFile)
            assertEquals(9, file.readBytes().single().toInt())
        }
        assertFalse(File(directory, "media-preview-folder").isFile)
    }

    private object OnePageRasterizer : PdfRasterizer<Unit> {
        override val pageCount = 1

        override fun render(
            index: Int,
            sizeFor: (Int, Int) -> PdfPageSize,
        ) = Unit

        override fun close() = Unit
    }

    private companion object {
        val OWNED = listOf("media-preview-", "atomic-pdf-", "atomic-7z-")
    }
}
