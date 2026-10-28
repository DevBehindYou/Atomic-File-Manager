package com.devbehindyou.atomicfilemanager

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.data.preview.PdfPreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native renderer coverage; execute on the API 28 and API 34 device matrix. */
@RunWith(AndroidJUnit4::class)
class PdfReaderDeviceTest {
    @Test
    fun nativeReaderCountsAndDrawsSeveralPagesThenRemovesItsPrivateCopy() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val source = File.createTempFile("pdf-fixture-", ".pdf", context.cacheDir)
            val document = PdfDocument()
            try {
                for (index in 0 until 3) {
                    val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, index + 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawText("Atomic PDF page ${index + 1}", 30f, 70f, Paint().apply { textSize = 24f })
                    document.finishPage(page)
                }
                source.outputStream().use(document::writeTo)
            } finally {
                document.close()
            }
            val before = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("atomic-pdf-") }.toSet()
            try {
                val backend = FileSystemBackend(context)
                val result =
                    PdfPreviewHelper(
                        context,
                    ) { backend }.withDocument(FileNodeId.file(source.absolutePath)) { session ->
                        assertEquals(3, session.pageCount)
                        for (index in listOf(0, 2, 1)) {
                            val rendered = session.renderPage(index)
                            assertTrue(rendered is FileResult.Success)
                            val bitmap = (rendered as FileResult.Success).value
                            assertTrue(bitmap.width in 1..1600)
                            assertTrue(bitmap.height in 1..1600)
                            assertTrue(bitmap.width.toLong() * bitmap.height <= 4_000_000)
                            bitmap.recycle()
                        }
                    }
                assertTrue(result is FileResult.Success)
                assertEquals(
                    before,
                    context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("atomic-pdf-") }.toSet(),
                )
            } finally {
                source.delete()
            }
        }
}
