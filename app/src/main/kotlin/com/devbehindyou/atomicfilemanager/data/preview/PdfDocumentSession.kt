package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.floor
import kotlin.math.sqrt

interface PdfDocumentSession<T> {
    val pageCount: Int

    suspend fun renderPage(
        index: Int,
        maxDimension: Int = 1600,
    ): FileResult<T>
}

internal data class PdfPageSize(val width: Int, val height: Int)

internal fun pdfPageSize(
    width: Int,
    height: Int,
    maxDimension: Int,
    maxPixels: Long = 4_000_000,
): PdfPageSize {
    require(width > 0 && height > 0 && maxDimension > 0 && maxPixels > 0)
    val edge = minOf(maxDimension, 2048).toDouble()
    val scale = minOf(edge / maxOf(width, height), sqrt(maxPixels.toDouble() / width / height), 2.5)
    var targetWidth = floor(width * scale).toInt().coerceAtLeast(1)
    var targetHeight = floor(height * scale).toInt().coerceAtLeast(1)
    if (targetWidth.toLong() * targetHeight > maxPixels) {
        if (targetWidth >= targetHeight) {
            targetWidth = (maxPixels / targetHeight).toInt().coerceAtLeast(1)
        } else {
            targetHeight = (maxPixels / targetWidth).toInt().coerceAtLeast(1)
        }
    }
    return PdfPageSize(targetWidth, targetHeight)
}

internal interface PdfRasterizer<T> : AutoCloseable {
    val pageCount: Int

    fun render(
        index: Int,
        sizeFor: (Int, Int) -> PdfPageSize,
    ): T
}

/** Owns acquisition before dispatcher handoff, so cancellation cannot strand a cache file. */
internal class PdfDocumentLoader<T>(
    private val cacheDirectory: () -> File,
    private val openInput: suspend (FileNodeId) -> FileResult<InputStreamProvider>,
    private val openRenderer: (File) -> PdfRasterizer<T>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val byteLimit: Long = 128L * 1024 * 1024,
) {
    suspend fun <R> withDocument(
        id: FileNodeId,
        block: suspend (PdfDocumentSession<T>) -> R,
    ): FileResult<R> {
        return try {
            FileResult.Success(useDocument(id, block))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PdfLoadFailure) {
            currentCoroutineContext().ensureActive()
            FileResult.Failure(failure.error)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            FileResult.Failure(FileError.IoFailure("PDF preview unavailable"))
        }
    }

    private suspend fun <R> useDocument(
        id: FileNodeId,
        block: suspend (PdfDocumentSession<T>) -> R,
    ): R {
        var cached: File? = null
        var session: OwnedPdfSession<T>? = null
        var rasterizer: PdfRasterizer<T>? = null
        try {
            require(byteLimit > 0)
            withContext(dispatcher) {
                val input =
                    when (val result = openInput(id)) {
                        is FileResult.Success -> result.value
                        is FileResult.Failure -> throw PdfLoadFailure(result.error)
                    }
                val file = File.createTempFile("atomic-pdf-", ".pdf", cacheDirectory()).also { cached = it }
                input.stream().use { source ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            if (read.toLong() > byteLimit - total) {
                                throw PdfLoadFailure(FileError.FileTooLarge("PDF preview", byteLimit))
                            }
                            output.write(buffer, 0, read)
                            total += read
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                rasterizer = openRenderer(file)
                session = OwnedPdfSession(checkNotNull(rasterizer), dispatcher)
                if (session!!.pageCount <= 0) throw PdfLoadFailure(FileError.UnsupportedFormat("application/pdf"))
            }
            currentCoroutineContext().ensureActive()
            return block(checkNotNull(session))
        } finally {
            withContext(NonCancellable + dispatcher) {
                try {
                    if (session != null) session?.close() else rasterizer?.close()
                } finally {
                    cached?.delete()
                }
            }
        }
    }
}

private class PdfLoadFailure(val error: FileError) : Exception()

private class OwnedPdfSession<T>(private val renderer: PdfRasterizer<T>, private val dispatcher: CoroutineDispatcher) :
    PdfDocumentSession<T> {
    private val mutex = Mutex()
    private var closed = false
    override val pageCount: Int = renderer.pageCount

    override suspend fun renderPage(
        index: Int,
        maxDimension: Int,
    ): FileResult<T> =
        withContext(dispatcher) {
            mutex.withLock {
                if (closed || index !in 0 until pageCount || maxDimension <= 0) {
                    return@withLock FileResult.Failure(FileError.IoFailure("PDF page unavailable"))
                }
                try {
                    currentCoroutineContext().ensureActive()
                    val rendered = renderer.render(index) { width, height -> pdfPageSize(width, height, maxDimension) }
                    currentCoroutineContext().ensureActive()
                    FileResult.Success(rendered)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    FileResult.Failure(FileError.IoFailure("PDF page unavailable"))
                }
            }
        }

    suspend fun close() {
        mutex.withLock {
            if (!closed) {
                closed = true
                renderer.close()
            }
        }
    }
}
