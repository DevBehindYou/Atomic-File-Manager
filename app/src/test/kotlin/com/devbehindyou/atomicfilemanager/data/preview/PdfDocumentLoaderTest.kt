package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PdfDocumentLoaderTest {
    @TempDir
    lateinit var directory: File

    private val id = FileNodeId("file:/document.pdf")

    @Test
    fun `page count and repeated pages use one owned copy and close before deletion`() =
        runTest {
            var opens = 0
            var owned: File? = null
            var closed = false
            var escaped: PdfDocumentSession<String>? = null
            val loader =
                PdfDocumentLoader(
                    { directory },
                    {
                        opens++
                        FileResult.Success(InputStreamProvider { ByteArrayInputStream(byteArrayOf(1, 2)) })
                    },
                    { file ->
                        owned = file
                        renderer(onClose = {
                            assertTrue(file.exists())
                            closed = true
                        })
                    },
                    StandardTestDispatcher(testScheduler),
                )
            val result =
                loader.withDocument(id) { session ->
                    escaped = session
                    assertEquals(3, session.pageCount)
                    assertTrue(owned!!.exists())
                    assertEquals(FileResult.Success("page0:1600x800"), session.renderPage(0))
                    assertEquals(FileResult.Success("page2:1600x800"), session.renderPage(2))
                }
            assertTrue(result is FileResult.Success)
            assertEquals(1, opens)
            assertTrue(closed)
            assertFalse(owned!!.exists())
            assertTrue(escaped!!.renderPage(0) is FileResult.Failure)
        }

    @Test
    fun `exact cap succeeds but overflow removes its copy before opening the renderer`() =
        runTest {
            for (size in listOf(4, 5)) {
                var opened = false
                val loader =
                    PdfDocumentLoader(
                        { directory },
                        { FileResult.Success(InputStreamProvider { ByteArrayInputStream(ByteArray(size)) }) },
                        {
                            opened = true
                            renderer()
                        },
                        StandardTestDispatcher(testScheduler),
                        byteLimit = 4,
                    )
                val result = loader.withDocument(id) { it.pageCount }
                if (size == 4) {
                    assertEquals(FileResult.Success(3), result)
                } else {
                    assertTrue((result as FileResult.Failure).error is FileError.FileTooLarge)
                }
                assertEquals(size == 4, opened)
                assertTrue(directory.listFiles()!!.isEmpty())
            }
        }

    @Test
    fun `read failure and renderer construction failure both delete owned partial copies`() =
        runTest {
            for (failRead in listOf(false, true)) {
                val loader =
                    PdfDocumentLoader<String>(
                        { directory },
                        {
                            FileResult.Success(
                                InputStreamProvider {
                                    if (failRead) throw IOException("unreadable")
                                    ByteArrayInputStream(byteArrayOf(1))
                                },
                            )
                        },
                        { throw IOException("renderer unavailable") },
                        StandardTestDispatcher(testScheduler),
                    )
                assertTrue(loader.withDocument(id) { it.pageCount } is FileResult.Failure)
                assertTrue(directory.listFiles()!!.isEmpty())
            }
        }

    @Test
    fun `cancellation during resource acquisition cannot lose ownership at handoff`() =
        runTest {
            var closed = false
            var enteredCallback = false
            val owner = Job()
            val loader =
                PdfDocumentLoader(
                    { directory },
                    { FileResult.Success(InputStreamProvider { ByteArrayInputStream(byteArrayOf(1)) }) },
                    {
                        owner.cancel()
                        renderer(onClose = { closed = true })
                    },
                    StandardTestDispatcher(testScheduler),
                )
            val work = launch(owner) { loader.withDocument(id) { enteredCallback = true } }
            advanceUntilIdle()
            work.join()
            assertFalse(enteredCallback)
            assertTrue(closed)
            assertTrue(directory.listFiles()!!.isEmpty())
        }

    @Test
    fun `closing the preview cancels the document lifetime and closes its renderer`() =
        runTest {
            var closed = false
            val loader =
                PdfDocumentLoader(
                    { directory },
                    { FileResult.Success(InputStreamProvider { ByteArrayInputStream(byteArrayOf(1)) }) },
                    { renderer(onClose = { closed = true }) },
                    StandardTestDispatcher(testScheduler),
                )
            val work = launch { loader.withDocument(id) { awaitCancellation() } }
            runCurrent()
            assertEquals(1, directory.listFiles()!!.size)
            work.cancel()
            advanceUntilIdle()
            assertTrue(closed)
            assertTrue(directory.listFiles()!!.isEmpty())
        }

    @Test
    fun `simultaneous sessions own distinct copies even for the same document`() =
        runTest {
            val files = mutableListOf<File>()
            val loader =
                PdfDocumentLoader(
                    { directory },
                    { FileResult.Success(InputStreamProvider { ByteArrayInputStream(byteArrayOf(1)) }) },
                    {
                        files += it
                        renderer()
                    },
                    StandardTestDispatcher(testScheduler),
                )
            val first = launch { loader.withDocument(id) { awaitCancellation() } }
            val second = launch { loader.withDocument(id) { awaitCancellation() } }
            runCurrent()
            assertEquals(2, files.distinct().size)
            first.cancel()
            advanceUntilIdle()
            assertFalse(files[0].exists())
            assertTrue(files[1].exists())
            second.cancel()
            advanceUntilIdle()
            assertTrue(directory.listFiles()!!.isEmpty())
        }

    @Test
    fun `cancellation after a copy read closes the source and removes the partial file`() =
        runTest {
            val owner = Job()
            var sourceClosed = false
            var rendererOpened = false
            val loader =
                PdfDocumentLoader(
                    { directory },
                    {
                        FileResult.Success(
                            InputStreamProvider {
                                object : ByteArrayInputStream(ByteArray(100_000)) {
                                    override fun read(
                                        bytes: ByteArray,
                                        offset: Int,
                                        length: Int,
                                    ): Int {
                                        val count = super.read(bytes, offset, length)
                                        owner.cancel()
                                        return count
                                    }

                                    override fun close() {
                                        sourceClosed = true
                                        super.close()
                                    }
                                }
                            },
                        )
                    },
                    {
                        rendererOpened = true
                        renderer()
                    },
                    StandardTestDispatcher(testScheduler),
                )
            launch(owner) { loader.withDocument(id) { } }
            advanceUntilIdle()
            assertTrue(sourceClosed)
            assertFalse(rendererOpened)
            assertTrue(directory.listFiles()!!.isEmpty())
        }

    @Test
    fun `renderer count and close failures do not leak the owned file or escape as ordinary exceptions`() =
        runTest {
            for (failCount in listOf(false, true)) {
                var closed = false
                val loader =
                    PdfDocumentLoader(
                        { directory },
                        { FileResult.Success(InputStreamProvider { ByteArrayInputStream(byteArrayOf(1)) }) },
                        {
                            object : PdfRasterizer<String> {
                                override val pageCount: Int get() = if (failCount) throw IOException("count") else 3

                                override fun render(
                                    index: Int,
                                    sizeFor: (Int, Int) -> PdfPageSize,
                                ) = "page"

                                override fun close() {
                                    closed = true
                                    if (!failCount) throw IOException("close")
                                }
                            }
                        },
                        StandardTestDispatcher(testScheduler),
                    )
                assertTrue(loader.withDocument(id) { it.pageCount } is FileResult.Failure)
                assertTrue(closed)
                assertTrue(directory.listFiles()!!.isEmpty())
            }
        }

    private fun renderer(onClose: () -> Unit = {}) =
        object : PdfRasterizer<String> {
            override val pageCount = 3

            override fun render(
                index: Int,
                sizeFor: (Int, Int) -> PdfPageSize,
            ): String {
                val size = sizeFor(4000, 2000)
                return "page$index:${size.width}x${size.height}"
            }

            override fun close() = onClose()
        }
}
