package com.devbehindyou.atomicfilemanager

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.system.Os
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.data.preview.PDF_PASSWORD_PROTECTED
import com.devbehindyou.atomicfilemanager.data.preview.PdfDocumentSession
import com.devbehindyou.atomicfilemanager.data.preview.PdfPreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.components.preview.PdfPreviewContent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import kotlin.math.abs

/**
 * C021: native PdfRenderer sessions over generated 1000-page, mixed-size, scanned-like, malformed and
 * encrypted documents. Every fixture lives in a private directory; the staged copy, its descriptor and
 * the bitmap budget are asserted for each session.
 */
@RunWith(AndroidJUnit4::class)
class PdfHostileSessionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = compose.activity.applicationContext
    private val directory by lazy {
        Files.createTempDirectory(context.cacheDir.toPath(), "pdf-hostile-device-").toFile()
    }
    private val cache by lazy { File(directory, "cache").apply { check(mkdir()) } }
    private val source by lazy { File(directory, "document.pdf") }
    private val backend by lazy { FileSystemBackend(context) }
    private val helper by lazy {
        PdfPreviewHelper(
            object : ContextWrapper(context) {
                override fun getCacheDir() = cache
            },
        ) { backend }
    }

    @After
    fun removeOnlyOwnedFixtures() {
        assertTrue("Every staged copy must be removed", cache.listFiles().orEmpty().isEmpty())
        assertEquals("Every staged descriptor must be closed", 0, stagedDescriptors())
        assertEquals("No descriptor may remain on an owned fixture", emptyList<String>(), ownedTargets())
        for (owned in listOf(source, cache, directory)) assertTrue(owned.delete() || !owned.exists())
    }

    @Test
    fun thousandPageDocumentRendersBoundedPagesAndClosesItsDescriptor() =
        runBlocking {
            source.outputStream().use { output -> generate(List(1000) { 200 to 280 }, output) }
            var escaped: PdfDocumentSession<Bitmap>? = null
            var peakBytes = 0
            var openDescriptors = 0
            val order = listOf(0, 999, 500, 1, 998) + (0 until 1000 step 37)
            val result =
                helper.withDocument(id()) { session ->
                    escaped = session
                    openDescriptors = stagedDescriptors()
                    assertEquals(1000, session.pageCount)
                    assertEquals(1, cache.listFiles().orEmpty().size)
                    for (index in order) {
                        val bitmap = (session.renderPage(index) as FileResult.Success).value
                        try {
                            assertBounded(bitmap)
                            peakBytes = maxOf(peakBytes, bitmap.allocationByteCount)
                            assertPageColour(bitmap, index)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    for (outside in listOf(-1, 1000)) assertTrue(session.renderPage(outside) is FileResult.Failure)
                }
            assertTrue(result is FileResult.Success)
            assertTrue("The open session must hold its staged descriptor", openDescriptors >= 1)
            assertTrue("Peak page bitmap stays inside 4 MP ARGB", peakBytes <= 16_000_000)
            assertEquals(0, stagedDescriptors())
            assertTrue(escaped!!.renderPage(0) is FileResult.Failure)
        }

    @Test
    fun mixedPageBoxesStayInsideTheRasterBudgetAndKeepTheirShape() =
        runBlocking {
            val boxes =
                listOf(1 to 1, 10 to 10, 72 to 72, 595 to 842, 842 to 595, 14_400 to 14_400, 14_400 to 20, 20 to 14_400)
            source.outputStream().use { output -> generate(boxes, output) }
            val result =
                helper.withDocument(id()) { session ->
                    assertEquals(boxes.size, session.pageCount)
                    boxes.forEachIndexed { index, (width, height) ->
                        val bitmap = (session.renderPage(index) as FileResult.Success).value
                        try {
                            assertBounded(bitmap)
                            if (bitmap.width >= 50 && bitmap.height >= 50) {
                                val expected = width.toDouble() / height
                                val actual = bitmap.width.toDouble() / bitmap.height
                                assertTrue("Page $index aspect $actual vs $expected", abs(actual / expected - 1) < 0.05)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            assertTrue(result is FileResult.Success)
        }

    @Test
    fun scannedLikeLargeRasterPageStagesWhollyAndRendersWithinBudget() =
        runBlocking {
            source.outputStream().use { output -> scanned(output) }
            assertTrue("Fixture must be large", source.length() > 4L * 1024 * 1024)
            val result =
                helper.withDocument(id()) { session ->
                    assertEquals(source.length(), cache.listFiles().orEmpty().single().length())
                    val bitmap = (session.renderPage(0) as FileResult.Success).value
                    try {
                        assertBounded(bitmap)
                        assertTrue("The embedded raster must be drawn", samples(bitmap).any { it != Color.WHITE })
                    } finally {
                        bitmap.recycle()
                    }
                }
            assertTrue(result is FileResult.Success)
        }

    @Test
    fun malformedDocumentsFailClearlyAndReleaseEverything() =
        runBlocking {
            val valid = ByteArrayOutputStream().also { generate(List(3) { 200 to 280 }, it) }.toByteArray()
            val deterministic =
                listOf(
                    ByteArray(0),
                    ByteArray(4096) { (it * 31 + 7).toByte() },
                    "%PDF-1.4\n%%EOF\n".toByteArray(),
                    HostilePdfFixtures.zeroPages(),
                )
            for (bytes in deterministic) {
                source.writeBytes(bytes)
                val result = helper.withDocument(id()) { it.pageCount }
                assertTrue("Malformed input of ${bytes.size} bytes must fail", result is FileResult.Failure)
                assertNotEquals(
                    FileError.UnsupportedFormat(PDF_PASSWORD_PROTECTED),
                    (result as FileResult.Failure).error,
                )
                assertTrue(cache.listFiles().orEmpty().isEmpty())
                assertEquals(0, stagedDescriptors())
            }
            // PDFium may repair a truncated body; either a clear failure or bounded pages is acceptable.
            for (fraction in listOf(0.3, 0.6, 0.9)) {
                source.writeBytes(valid.copyOf((valid.size * fraction).toInt()))
                val result =
                    helper.withDocument(id()) { session ->
                        for (index in 0 until session.pageCount) {
                            (session.renderPage(index) as? FileResult.Success)?.value?.let {
                                assertBounded(it)
                                it.recycle()
                            }
                        }
                    }
                if (result is FileResult.Failure) {
                    assertNotEquals(FileError.UnsupportedFormat(PDF_PASSWORD_PROTECTED), result.error)
                }
                assertTrue(cache.listFiles().orEmpty().isEmpty())
                assertEquals(0, stagedDescriptors())
            }
        }

    @Test
    fun passwordProtectedDocumentIsReportedDistinctlyFromCorruption() =
        runBlocking {
            source.writeBytes(HostilePdfFixtures.plain())
            val plain =
                helper.withDocument(id()) { session ->
                    val bitmap = (session.renderPage(0) as FileResult.Success).value
                    try {
                        val centre = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                        assertTrue(isBlue(centre))
                    } finally {
                        bitmap.recycle()
                    }
                    session.pageCount
                }
            assertEquals("The fixture writer produces a readable twin", FileResult.Success(1), plain)
            source.writeBytes(HostilePdfFixtures.encrypted())
            val encrypted = helper.withDocument(id()) { it.pageCount }
            assertEquals(FileResult.Failure(FileError.UnsupportedFormat(PDF_PASSWORD_PROTECTED)), encrypted)
            assertEquals(0, stagedDescriptors())
            source.writeBytes(ByteArray(2048) { (it * 13 + 1).toByte() })
            val corrupt = (helper.withDocument(id()) { it.pageCount } as FileResult.Failure).error
            assertNotEquals((encrypted as FileResult.Failure).error, corrupt)
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun thousandPageReaderJumpsStepsAndShowsOnePageAtATime() {
        source.outputStream().use { output -> generate(List(1000) { 200 to 280 }, output) }
        val reader = Reader()
        reader.awaitPage(1, 1000)
        reader.jump(1000)
        reader.awaitPage(1000, 1000)
        compose.onNode(button(R.string.pdf_next)).assertIsNotEnabled()
        compose.onNode(button(R.string.pdf_previous)).performClick()
        reader.awaitPage(999, 1000)
        reader.jump(500)
        reader.awaitPage(500, 1000)
        repeat(20) { compose.onNode(button(R.string.pdf_next)).performClick() }
        reader.awaitPage(520, 1000)
        assertEquals(
            "Exactly one page is held on screen",
            1,
            compose.onAllNodesWithTag("pdf_page").fetchSemanticsNodes().size,
        )
        assertTrue("The displayed reader holds its staged descriptor", stagedDescriptors() >= 1)
        reader.close()
    }

    @Test
    fun sourceLossKeepsTheOpenCopyThenPasswordAndRecoveryAreReported() {
        source.outputStream().use { output -> generate(List(3) { 200 to 280 }, output) }
        val reader = Reader()
        reader.awaitPage(1, 3)
        assertTrue(source.delete())
        compose.onNode(button(R.string.pdf_next)).performClick()
        reader.awaitPage(2, 3)
        reader.revise()
        reader.awaitText(R.string.pdf_open_error)
        compose.waitUntil(10_000) { cache.listFiles().orEmpty().isEmpty() && stagedDescriptors() == 0 }
        source.writeBytes(HostilePdfFixtures.encrypted())
        compose.onNode(button(R.string.pdf_retry)).performClick()
        reader.awaitText(R.string.pdf_password_protected)
        assertTrue(
            "Retrying cannot unlock a password",
            compose.onAllNodes(button(R.string.pdf_retry)).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(cache.listFiles().orEmpty().isEmpty())
        assertEquals(0, stagedDescriptors())
        source.writeBytes(HostilePdfFixtures.plain())
        reader.revise()
        reader.awaitPage(1, 1)
        reader.close()
    }

    @Test
    fun repeatedDisposalReleasesEveryNativeDocument() {
        source.outputStream().use { output -> generate(List(3) { 200 to 280 }, output) }
        val reader = Reader()
        reader.awaitPage(1, 3)
        reader.close()
        // Total process descriptors are not ours to bound: API 34 HWUI keeps a pool of
        // anon_inode:sync_file frame fences that grows to a plateau (C021 evidence). Assert by target.
        repeat(12) { lifetime ->
            compose.runOnIdle { reader.visible.value = true }
            reader.awaitPage(1, 3)
            assertEquals(1, cache.listFiles().orEmpty().size)
            assertTrue("Lifetime $lifetime holds its staged copy", stagedDescriptors() >= 1)
            reader.close()
            assertEquals("Lifetime $lifetime left descriptors on owned files", emptyList<String>(), ownedTargets())
        }
    }

    private inner class Reader {
        val original: FileNode = runBlocking { (backend.getNode(id()) as FileResult.Success).value }
        val shown = mutableStateOf(original)
        val visible = mutableStateOf(true)
        private val container =
            object : AppContainer by DefaultAppContainer(compose.activity.application) {
                override val pdfPreviewHelper = helper
            }

        init {
            compose.setContent {
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    if (visible.value) PdfPreviewContent(shown.value, container)
                }
            }
        }

        fun awaitPage(
            page: Int,
            count: Int,
        ) {
            val label = context.getString(R.string.pdf_page_label, page, count)
            compose.waitUntil(20_000) {
                compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithTag("pdf_page").fetchSemanticsNodes().size == 1
            }
        }

        fun awaitText(text: Int) {
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText(context.getString(text)).fetchSemanticsNodes().isNotEmpty()
            }
        }

        fun jump(page: Int) {
            compose.onNode(hasSetTextAction()).performTextReplacement(page.toString())
            compose.onNode(button(R.string.pdf_go)).performClick()
        }

        fun revise() {
            compose.runOnIdle { shown.value = shown.value.copy(modifiedAt = shown.value.modifiedAt + 1) }
        }

        fun close() {
            compose.runOnIdle { visible.value = false }
            compose.waitUntil(10_000) { cache.listFiles().orEmpty().isEmpty() && stagedDescriptors() == 0 }
        }
    }

    private fun button(label: Int): SemanticsMatcher {
        val text = context.getString(label)
        return hasClickAction() and (hasContentDescription(text) or hasText(text))
    }

    private fun id() = FileNodeId.file(source.absolutePath)

    private fun assertBounded(bitmap: Bitmap) {
        assertTrue("Width ${bitmap.width}", bitmap.width in 1..1600)
        assertTrue("Height ${bitmap.height}", bitmap.height in 1..1600)
        assertTrue(bitmap.width.toLong() * bitmap.height <= 4_000_000)
    }

    private fun assertPageColour(
        bitmap: Bitmap,
        index: Int,
    ) {
        val centre = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val expectedBlue = index % 2 == 1
        assertTrue(
            "Page $index must be its own pixels",
            if (expectedBlue) isBlue(centre) else Color.red(centre) > 200 && Color.blue(centre) < 50,
        )
    }

    private fun samples(bitmap: Bitmap): List<Int> {
        val pixels = mutableListOf<Int>()
        for (x in 1..9) {
            for (y in 1..9) pixels += bitmap.getPixel(bitmap.width * x / 10, bitmap.height * y / 10)
        }
        return pixels
    }

    private fun isBlue(colour: Int) = Color.blue(colour) > 200 && Color.red(colour) < 50

    /** Descriptors whose target is a staged copy in this test's private cache (deleted or not). */
    private fun stagedDescriptors(): Int {
        val marker = "/${directory.name}/cache/"
        return File("/proc/self/fd").listFiles().orEmpty().count { link ->
            runCatching { Os.readlink(link.absolutePath) }.getOrNull()?.contains(marker) == true
        }
    }

    /** Descriptor targets on any file this test owns: the source and every staged copy. */
    private fun ownedTargets(): List<String> {
        val marker = "/${directory.name}/"
        return File("/proc/self/fd").listFiles().orEmpty().mapNotNull { link ->
            runCatching { Os.readlink(link.absolutePath) }.getOrNull()?.takeIf { it.contains(marker) }
        }
    }

    private fun generate(
        boxes: List<Pair<Int, Int>>,
        output: OutputStream,
    ) {
        val document = PdfDocument()
        try {
            val paint = Paint().apply { textSize = 18f }
            boxes.forEachIndexed { index, (width, height) ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                page.canvas.drawColor(if (index % 2 == 1) Color.BLUE else Color.RED)
                page.canvas.drawText("${index + 1}", 4f, 20f, paint)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    /** An A4 page carrying a 2000x2800 incompressible raster, like a 240 dpi scan. */
    private fun scanned(output: OutputStream) {
        val raster = Bitmap.createBitmap(2000, 2800, Bitmap.Config.RGB_565)
        val document = PdfDocument()
        try {
            var seed = 0x2545F491
            val row = IntArray(raster.width)
            for (y in 0 until raster.height) {
                for (x in row.indices) {
                    seed = seed * 1_103_515_245 + 12_345
                    val grey = 96 + ((seed ushr 16) and 0x7F)
                    row[x] = Color.rgb(grey, grey, grey)
                }
                raster.setPixels(row, 0, raster.width, 0, y, raster.width, 1)
            }
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            page.canvas.drawBitmap(raster, null, Rect(0, 0, 595, 842), null)
            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
            raster.recycle()
        }
    }
}
