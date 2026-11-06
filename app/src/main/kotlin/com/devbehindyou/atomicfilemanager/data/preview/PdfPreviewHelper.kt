package com.devbehindyou.atomicfilemanager.data.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import java.io.File

class PdfPreviewHelper(context: Context, backendSelector: (FileNodeId) -> StorageBackend) {
    private val documents =
        PdfDocumentLoader(
            cacheDirectory = { context.cacheDir },
            openInput = { backendSelector(it).openInput(it) },
            openRenderer = { AndroidPdfRasterizer(it) },
        )

    /** The document and private cache copy exist only for this structured callback. */
    suspend fun <T> withDocument(
        id: FileNodeId,
        block: suspend (PdfDocumentSession<Bitmap>) -> T,
    ): FileResult<T> = documents.withDocument(id, block)

    /** Compatibility for isolated callers; the reader uses one withDocument session instead. */
    suspend fun getPageCount(id: FileNodeId): FileResult<Int> = withDocument(id) { it.pageCount }

    suspend fun renderPage(
        id: FileNodeId,
        pageIndex: Int,
        maxDimension: Int = 1600,
    ): FileResult<Bitmap> =
        when (val document = withDocument(id) { it.renderPage(pageIndex, maxDimension) }) {
            is FileResult.Success -> document.value
            is FileResult.Failure -> document
        }
}

private class AndroidPdfRasterizer(file: File) : PdfRasterizer<Bitmap> {
    private val renderer: PdfRenderer

    init {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            renderer = PdfRenderer(descriptor)
        } catch (failure: Exception) {
            descriptor.close()
            throw failure
        }
    }

    override val pageCount: Int get() = renderer.pageCount

    override fun render(
        index: Int,
        sizeFor: (Int, Int) -> PdfPageSize,
    ): Bitmap =
        renderer.openPage(index).use { page ->
            val size = sizeFor(page.width, page.height)
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (failure: Exception) {
                bitmap.recycle()
                throw failure
            }
        }

    override fun close() = renderer.close()
}
