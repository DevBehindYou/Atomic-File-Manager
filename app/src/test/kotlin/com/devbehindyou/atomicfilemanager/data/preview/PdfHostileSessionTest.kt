package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/** C021: large, mixed-size, malformed, encrypted and source-loss sessions over the owned-copy loader. */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfHostileSessionTest {
    @TempDir
    lateinit var directory: File

    private val id = FileNodeId("file:/hostile.pdf")
    private val password = FileError.UnsupportedFormat(PDF_PASSWORD_PROTECTED)

    @Test
    fun `a password protected document is reported distinctly from a corrupt one`() =
        runTest {
            val outcomes =
                listOf<() -> Exception>(
                    { SecurityException("password required or incorrect password") },
                    { IOException("file not in PDF format or corrupted") },
                ).map { failure ->
                    var sourceClosed = false
                    val loader =
                        PdfDocumentLoader<String>(
                            { directory },
                            { FileResult.Success(InputStreamProvider { tracked { sourceClosed = true } }) },
                            { throw failure() },
                            StandardTestDispatcher(testScheduler),
                        )
                    val result = loader.withDocument(id) { it.pageCount }
                    assertTrue(sourceClosed)
                    assertTrue(directory.listFiles()!!.isEmpty())
                    (result as FileResult.Failure).error
                }
            assertEquals(password, outcomes[0])
            assertEquals(FileError.IoFailure("PDF preview unavailable"), outcomes[1])
            assertNotEquals(outcomes[0], outcomes[1])
        }

    @Test
    fun `a provider security failure while staging is never reported as a password`() =
        runTest {
            for (throwOnRead in listOf(false, true)) {
                var rendererOpened = false
                val loader =
                    PdfDocumentLoader(
                        { directory },
                        {
                            FileResult.Success(
                                InputStreamProvider {
                                    if (!throwOnRead) throw SecurityException("grant revoked")
                                    object : ByteArrayInputStream(byteArrayOf(1)) {
                                        override fun read(
                                            bytes: ByteArray,
                                            offset: Int,
                                            length: Int,
                                        ): Int = throw SecurityException("grant revoked")
                                    }
                                },
                            )
                        },
                        {
                            rendererOpened = true
                            FakeRasterizer(3)
                        },
                        StandardTestDispatcher(testScheduler),
                    )
                val error = (loader.withDocument(id) { it.pageCount } as FileResult.Failure).error
                assertNotEquals(password, error)
                assertFalse(rendererOpened)
                assertTrue(directory.listFiles()!!.isEmpty())
            }
        }

    @Test
    fun `a thousand page mixed size session renders serially inside the raster budget and closes once`() =
        runTest {
            val rasterizer = FakeRasterizer(1000)
            val loader =
                PdfDocumentLoader(
                    { directory },
                    { source(ByteArray(4096)) },
                    { rasterizer },
                    StandardTestDispatcher(testScheduler),
                )
            val result =
                loader.withDocument(id) { session ->
                    assertEquals(1000, session.pageCount)
                    val pages = (0 until 1000).map { index -> async { session.renderPage(index) } }.awaitAll()
                    assertTrue(pages.all { it is FileResult.Success })
                    for (outside in listOf(-1, 1000, Int.MAX_VALUE)) {
                        assertTrue(session.renderPage(outside) is FileResult.Failure)
                    }
                    assertTrue(session.renderPage(0, maxDimension = 0) is FileResult.Failure)
                }
            assertTrue(result is FileResult.Success)
            assertEquals(1000, rasterizer.renders)
            assertEquals(1, rasterizer.peakInFlight)
            assertTrue(rasterizer.peakPixels <= 4_000_000)
            assertTrue(rasterizer.maxEdge <= 1600)
            assertEquals(1, rasterizer.closes)
            assertTrue(directory.listFiles()!!.isEmpty())
        }

    @Test
    fun `a malformed page fails only itself and the large session keeps rendering`() =
        runTest {
            val rasterizer = FakeRasterizer(1000, broken = setOf(500))
            val loader =
                PdfDocumentLoader(
                    { directory },
                    { source(ByteArray(16)) },
                    { rasterizer },
                    StandardTestDispatcher(testScheduler),
                )
            loader.withDocument(id) { session ->
                assertTrue(session.renderPage(499) is FileResult.Success)
                assertEquals(
                    FileError.IoFailure("PDF page unavailable"),
                    (session.renderPage(500) as FileResult.Failure).error,
                )
                assertTrue(session.renderPage(501) is FileResult.Success)
                assertTrue(session.renderPage(999) is FileResult.Success)
            }
            assertEquals(1, rasterizer.closes)
            assertTrue(directory.listFiles()!!.isEmpty())
        }

    @Test
    fun `source loss keeps the open copy and a later session reports the loss`() =
        runTest {
            val original = ByteArray(10_000) { (it % 251).toByte() }
            val source = File(directory, "source.pdf").apply { writeBytes(original) }
            val cache = File(directory, "cache").apply { check(mkdir()) }
            val loader =
                PdfDocumentLoader(
                    { cache },
                    {
                        if (source.exists()) {
                            FileResult.Success(InputStreamProvider { FileInputStream(source) })
                        } else {
                            FileResult.Failure(FileError.FileNotFound(source.name))
                        }
                    },
                    { staged -> FakeRasterizer(3, staged = staged, expected = original) },
                    StandardTestDispatcher(testScheduler),
                )
            val first =
                loader.withDocument(id) { session ->
                    assertTrue(source.delete())
                    (0 until 3).map { session.renderPage(it) }
                }
            assertTrue((first as FileResult.Success).value.all { it is FileResult.Success })
            assertTrue(cache.listFiles()!!.isEmpty())
            assertEquals(FileError.FileNotFound(source.name), (loader.withDocument(id) { } as FileResult.Failure).error)
            source.writeBytes(byteArrayOf(9, 9))
            val replaced = loader.withDocument(id) { it.pageCount }
            assertTrue(replaced is FileResult.Failure, "The replacement no longer matches the fixture bytes")
            assertTrue(cache.listFiles()!!.isEmpty())
        }

    @Test
    fun `repeated completed failed and cancelled sessions release every renderer and copy`() =
        runTest {
            val opened = mutableListOf<FakeRasterizer>()
            val loader =
                PdfDocumentLoader(
                    { directory },
                    { source(ByteArray(64)) },
                    { FakeRasterizer(if (opened.size % 3 == 1) 0 else 1000).also(opened::add) },
                    StandardTestDispatcher(testScheduler),
                )
            repeat(150) { cycle ->
                when (cycle % 3) {
                    0 -> assertTrue(loader.withDocument(id) { it.renderPage(cycle) } is FileResult.Success)
                    1 ->
                        assertEquals(
                            FileError.UnsupportedFormat("application/pdf"),
                            (loader.withDocument(id) { } as FileResult.Failure).error,
                        )
                    else -> {
                        val open = launch { loader.withDocument(id) { awaitCancellation() } }
                        runCurrent()
                        assertEquals(1, directory.listFiles()!!.size)
                        open.cancel()
                        advanceUntilIdle()
                    }
                }
                assertTrue(directory.listFiles()!!.isEmpty())
            }
            assertEquals(150, opened.size)
            assertTrue(opened.all { it.closes == 1 })
        }

    private fun source(bytes: ByteArray) = FileResult.Success(InputStreamProvider { ByteArrayInputStream(bytes) })

    private fun tracked(onClose: () -> Unit) =
        object : ByteArrayInputStream(byteArrayOf(37, 80, 68, 70)) {
            override fun close() {
                onClose()
                super.close()
            }
        }

    /** Mixed page boxes: tiny, ordinary, landscape, poster, extreme strips and a huge scanned-like raster. */
    private class FakeRasterizer(
        override val pageCount: Int,
        private val broken: Set<Int> = emptySet(),
        private val staged: File? = null,
        private val expected: ByteArray? = null,
    ) : PdfRasterizer<String> {
        private val boxes =
            listOf(1 to 1, 72 to 72, 595 to 842, 842 to 595, 14_400 to 14_400, 14_400 to 1, 1 to 14_400, 7016 to 9921)
        private var inFlight = 0
        var peakInFlight = 0
        var renders = 0
        var peakPixels = 0L
        var maxEdge = 0
        var closes = 0

        init {
            if (staged != null && expected != null && !staged.readBytes().contentEquals(expected)) {
                throw IOException("file not in PDF format or corrupted")
            }
        }

        override fun render(
            index: Int,
            sizeFor: (Int, Int) -> PdfPageSize,
        ): String {
            check(closes == 0) { "Rendered after close" }
            inFlight++
            peakInFlight = maxOf(peakInFlight, inFlight)
            try {
                check(index !in broken) { "malformed page" }
                if (staged != null) assertArrayEquals(expected, staged.readBytes())
                val (width, height) = boxes[index % boxes.size]
                val size = sizeFor(width, height)
                renders++
                peakPixels = maxOf(peakPixels, size.width.toLong() * size.height)
                maxEdge = maxOf(maxEdge, size.width, size.height)
                return "page$index:${size.width}x${size.height}"
            } finally {
                inFlight--
            }
        }

        override fun close() {
            closes++
        }
    }
}
