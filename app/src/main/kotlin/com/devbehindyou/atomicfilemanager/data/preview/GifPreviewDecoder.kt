package com.devbehindyou.atomicfilemanager.data.preview

import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/** Native API28 decoder; encoded bytes stay immutable for the drawable's whole lifetime. */
internal class GifPreviewDecoder(
    private val backendSelector: (FileNodeId) -> StorageBackend,
    private val io: CoroutineDispatcher,
) {
    suspend fun decode(id: FileNodeId): FileResult<Drawable> =
        withContext(io) {
            try {
                val input = backendSelector(id).openInput(id)
                if (input is FileResult.Failure) return@withContext input
                val bytes =
                    (input as FileResult.Success).value.stream().use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = stream.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                            if (count == -1) break
                            if (count == 0) throw IOException("GIF stream made no progress")
                            output.write(buffer, 0, count)
                            if (output.size() > MAX_BYTES) return@withContext tooLarge(id)
                        }
                        output.toByteArray()
                    }
                if (bytes.size < 6 || bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) !in HEADERS) {
                    return@withContext FileResult.Failure(FileError.UnsupportedFormat("GIF"))
                }
                currentCoroutineContext().ensureActive()
                val drawable =
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                        val width = info.size.width
                        val height = info.size.height
                        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) throw GifBudgetExceeded()
                        val ratio = minOf(1.0, MAX_DIMENSION.toDouble() / maxOf(width, height))
                        decoder.setTargetSize(
                            (width * ratio).toInt().coerceAtLeast(1),
                            (height * ratio).toInt().coerceAtLeast(1),
                        )
                        decoder.setOnPartialImageListener { false }
                    }
                currentCoroutineContext().ensureActive()
                FileResult.Success(drawable)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: GifBudgetExceeded) {
                tooLarge(id)
            } catch (_: ImageDecoder.DecodeException) {
                FileResult.Failure(FileError.UnsupportedFormat("GIF"))
            } catch (_: OutOfMemoryError) {
                FileResult.Failure(FileError.OutOfMemory)
            } catch (_: Exception) {
                FileResult.Failure(FileError.IoFailure(id.raw))
            }
        }

    private fun tooLarge(id: FileNodeId) = FileResult.Failure(FileError.FileTooLarge(id.raw, MAX_BYTES.toLong()))

    private class GifBudgetExceeded : IOException()

    private companion object {
        const val MAX_BYTES = 16 * 1024 * 1024
        const val MAX_PIXELS = 16L * 1024 * 1024
        const val MAX_DIMENSION = 2048
        val HEADERS = setOf("GIF87a", "GIF89a")
    }
}
