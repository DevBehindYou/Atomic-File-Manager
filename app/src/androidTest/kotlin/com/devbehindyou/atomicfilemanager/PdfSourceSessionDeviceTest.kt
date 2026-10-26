package com.devbehindyou.atomicfilemanager

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.data.preview.PdfPreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.components.preview.PdfPreviewContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** Actual native raster/Compose ownership, generated private PDFs only. */
@RunWith(AndroidJUnit4::class)
class PdfSourceSessionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sameIdentityRevisionReplacesTheDocumentAndResetsItsPage() =
        withFixture { fixture ->
            fixture.show()
            fixture.awaitPage(count = 3, page = 1)
            compose.onNodeWithContentDescription(fixture.context.getString(R.string.pdf_next)).performClick()
            fixture.awaitPage(count = 3, page = 2)
            fixture.expectedSource = pdf(pages = 1, color = Color.BLUE)
            fixture.source.writeBytes(fixture.expectedSource)
            compose.runOnIdle {
                fixture.shown.value =
                    fixture.original.copy(
                        size = fixture.expectedSource.size.toLong(),
                        modifiedAt = fixture.original.modifiedAt + 1,
                    )
            }
            fixture.awaitPage(count = 1, page = 1)
            fixture.assertCentreColor(blue = true)
            assertEquals(1, fixture.cacheA.listFiles().orEmpty().size)
        }

    @Test
    fun helperReplacementClosesTheOldNativeDocumentAndOwnsTheNewCopy() =
        withFixture { fixture ->
            fixture.show()
            fixture.awaitPage(count = 3, page = 1)
            fixture.assertCentreColor(blue = false)
            assertEquals(1, fixture.cacheA.listFiles().orEmpty().size)
            compose.runOnIdle { fixture.owner.value = fixture.container(fixture.cacheB) }
            compose.waitUntil(10_000) {
                fixture.cacheA.listFiles().orEmpty().isEmpty() && fixture.cacheB.listFiles().orEmpty().size == 1
            }
            fixture.awaitPage(count = 3, page = 1)
            fixture.assertCentreColor(blue = false)
        }

    private fun withFixture(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            block(fixture)
            assertArrayEquals(fixture.expectedSource, fixture.source.readBytes())
            assertArrayEquals(fixture.sentinelBytes, fixture.sentinel.readBytes())
        } finally {
            compose.runOnIdle { fixture.visible.value = false }
            compose.waitUntil(10_000) {
                fixture.cacheA.listFiles().orEmpty().isEmpty() && fixture.cacheB.listFiles().orEmpty().isEmpty()
            }
            assertArrayEquals(fixture.expectedSource, fixture.source.readBytes())
            assertArrayEquals(fixture.sentinelBytes, fixture.sentinel.readBytes())
            for (owned in listOf(fixture.source, fixture.sentinel, fixture.cacheA, fixture.cacheB, fixture.directory)) {
                assertTrue(
                    "Only captured fixtures and empty directories may be removed",
                    owned.delete() || !owned.exists(),
                )
            }
        }
    }

    private inner class Fixture {
        val context: Context = compose.activity.applicationContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "pdf-session-device-").toFile()
        val cacheA = File(directory, "cache-a").apply { check(mkdir()) }
        val cacheB = File(directory, "cache-b").apply { check(mkdir()) }
        val sentinelBytes = byteArrayOf(4, 7, 12, 55)
        val sentinel = File(directory, "unrelated.bin").apply { writeBytes(sentinelBytes) }
        var expectedSource = pdf(pages = 3, color = Color.RED)
        val source = File(directory, "document.pdf").apply { writeBytes(expectedSource) }
        val backend = FileSystemBackend(context)
        val original: FileNode =
            runBlocking {
                (backend.getNode(FileNodeId.file(source.absolutePath)) as FileResult.Success).value
            }
        val shown = mutableStateOf(original)
        val visible = mutableStateOf(true)
        val owner = mutableStateOf(container(cacheA))

        fun container(cache: File): AppContainer {
            val privateContext =
                object : ContextWrapper(context) {
                    override fun getCacheDir() = cache
                }
            return object : AppContainer by DefaultAppContainer(context) {
                override val pdfPreviewHelper = PdfPreviewHelper(privateContext) { backend }
            }
        }

        fun show() {
            compose.setContent {
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    if (visible.value) PdfPreviewContent(shown.value, owner.value)
                }
            }
        }

        fun awaitPage(
            count: Int,
            page: Int,
        ) {
            val label = context.getString(R.string.pdf_page_label, page, count)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithTag("pdf_page").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(label).assertIsDisplayed()
        }

        fun assertCentreColor(blue: Boolean) {
            val pixels = compose.onNodeWithTag("pdf_page").captureToImage().toPixelMap()
            val color = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue(
                "Native PDF pixels must belong to the current document",
                if (blue) {
                    color.blue > 0.8f && color.red < 0.2f
                } else {
                    color.red > 0.8f && color.blue < 0.2f
                },
            )
        }
    }

    private fun pdf(
        pages: Int,
        color: Int,
    ): ByteArray {
        val document = PdfDocument()
        return try {
            repeat(pages) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(200, 280, index + 1).create())
                page.canvas.drawColor(color)
                document.finishPage(page)
            }
            ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }
}
