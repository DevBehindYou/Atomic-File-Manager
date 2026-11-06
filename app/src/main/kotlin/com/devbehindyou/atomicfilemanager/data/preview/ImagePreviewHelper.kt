package com.devbehindyou.atomicfilemanager.data.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

class ImagePreviewHelper(
    private val backendSelector: (FileNodeId) -> StorageBackend,
    private val io: CoroutineDispatcher,
) {
    constructor(backendSelector: (FileNodeId) -> StorageBackend) : this(backendSelector, Dispatchers.IO)

    suspend fun decodeGif(id: FileNodeId): FileResult<android.graphics.drawable.Drawable> =
        GifPreviewDecoder(backendSelector, io).decode(id)

    suspend fun decodeImage(
        id: FileNodeId,
        maxDimension: Int = 2048,
    ): FileResult<Bitmap> {
        var owned: Bitmap? = null
        return try {
            withContext(io) {
                if (maxDimension <= 0) {
                    return@withContext FileResult.Failure(
                        FileError.InvalidDestination("Image preview size"),
                    )
                }
                try {
                    currentCoroutineContext().ensureActive()
                    val backend = backendSelector(id)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    when (val input = backend.openInput(id)) {
                        is FileResult.Failure -> return@withContext input
                        is FileResult.Success -> {
                            val guarded = ImagePreviewInput(input.value.stream(), currentCoroutineContext())
                            try {
                                BufferedInputStream(
                                    guarded,
                                    STREAM_BUFFER,
                                ).use { BitmapFactory.decodeStream(it, null, options) }
                            } finally {
                                guarded.rethrowCancellation()
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    if (options.outWidth <= 0 || options.outHeight <= 0) {
                        return@withContext FileResult.Failure(FileError.UnsupportedFormat("image"))
                    }
                    val orientation = readOrientation(backend, id)
                    currentCoroutineContext().ensureActive()
                    val decodeOptions =
                        BitmapFactory.Options().apply {
                            val largestDimension = maxOf(options.outWidth, options.outHeight)
                            inSampleSize = imagePreviewSampleSize(largestDimension, maxDimension)
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                    when (val input = backend.openInput(id)) {
                        is FileResult.Failure -> return@withContext input
                        is FileResult.Success -> {
                            val guarded = ImagePreviewInput(input.value.stream(), currentCoroutineContext())
                            try {
                                BufferedInputStream(guarded, STREAM_BUFFER).use {
                                    owned = BitmapFactory.decodeStream(it, null, decodeOptions)
                                }
                            } finally {
                                guarded.rethrowCancellation()
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    var bitmap = owned ?: return@withContext FileResult.Failure(FileError.IoFailure("Image decoding"))
                    val largest = maxOf(bitmap.width, bitmap.height)
                    if (largest > maxDimension) {
                        val scale = maxDimension.toDouble() / largest
                        val scaled =
                            Bitmap.createScaledBitmap(
                                bitmap,
                                (bitmap.width * scale).toInt().coerceAtLeast(1),
                                (bitmap.height * scale).toInt().coerceAtLeast(1),
                                true,
                            )
                        if (scaled !== bitmap) bitmap.recycle()
                        owned = scaled
                        bitmap = scaled
                    }
                    currentCoroutineContext().ensureActive()
                    val oriented =
                        Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            exifOrientationMatrix(orientation),
                            true,
                        )
                    if (oriented !== bitmap) bitmap.recycle()
                    owned = oriented
                    currentCoroutineContext().ensureActive()
                    FileResult.Success(oriented)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    FileResult.Failure(FileError.IoFailure(id.raw))
                }
            }.also { if (it is FileResult.Success) owned = null }
        } finally {
            owned?.recycle()
        }
    }

    /** Optional metadata must not break formats accepted by BitmapFactory, including BMP. */
    private suspend fun readOrientation(
        backend: StorageBackend,
        id: FileNodeId,
    ): Int {
        return try {
            when (val input = backend.openInput(id)) {
                is FileResult.Failure -> ExifInterface.ORIENTATION_NORMAL
                is FileResult.Success -> {
                    val guarded = ImagePreviewInput(input.value.stream(), currentCoroutineContext(), MAX_METADATA_BYTES)
                    val orientation =
                        try {
                            BufferedInputStream(guarded, STREAM_BUFFER).use {
                                ExifInterface(
                                    it,
                                ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                            }
                        } finally {
                            // The library can swallow malformed-input IO errors, including a wrapped cancellation.
                            guarded.rethrowCancellation()
                        }
                    currentCoroutineContext().ensureActive()
                    if (guarded.limitExceeded) ExifInterface.ORIENTATION_NORMAL else orientation
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private companion object {
        const val STREAM_BUFFER = 64 * 1024
        const val MAX_METADATA_BYTES = 8L * 1024 * 1024
    }
}

/** Matrices use origin-independent linear transforms; Bitmap.createBitmap normalizes translated output bounds. */
internal fun exifOrientationMatrix(orientation: Int): Matrix =
    Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> setValues(floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f))
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }

internal fun imagePreviewSampleSize(
    largest: Int,
    maximum: Int,
): Int {
    require(largest > 0 && maximum > 0)
    var sample = 1
    while ((largest.toLong() + sample - 1) / sample > maximum && sample < (1 shl 30)) sample *= 2
    return sample
}

/** Enforces cancellation and a consumed-byte budget for optional metadata, including skips and rereads. */
internal class ImagePreviewInput(
    input: InputStream,
    private val context: CoroutineContext,
    private val limit: Long = Long.MAX_VALUE,
) : FilterInputStream(input) {
    private var consumed = 0L
    private var cancelled: CancellationException? = null
    var limitExceeded: Boolean = false
        private set

    override fun read(): Int =
        checked {
            `in`.read().also { if (it != -1) record(1) }
        }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        checked {
            val allowed = minOf(length.toLong(), remainingProbe()).toInt()
            `in`.read(buffer, offset, allowed).also { if (it > 0) record(it.toLong()) }
        }

    override fun skip(count: Long): Long =
        checked {
            if (count <= 0) 0L else `in`.skip(minOf(count, remainingProbe())).also { record(it) }
        }

    override fun available(): Int =
        checked {
            minOf(`in`.available().toLong(), (limit - consumed).coerceAtLeast(0)).toInt()
        }

    fun rethrowCancellation() {
        cancelled?.let { throw it }
    }

    private fun remainingProbe(): Long = if (consumed < limit) limit - consumed else 1L

    private inline fun <T> checked(block: () -> T): T =
        try {
            context.ensureActive()
            if (consumed >= limit || limitExceeded) {
                limitExceeded = true
                throw IOException("Image metadata exceeds preview budget")
            }
            block()
        } catch (failure: CancellationException) {
            cancelled = failure
            throw failure
        }

    private fun record(count: Long) {
        consumed += count
        if (consumed > limit) {
            limitExceeded = true
            throw IOException("Image metadata exceeds preview budget")
        }
    }
}
