package com.devbehindyou.atomicfilemanager.data.preview

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException

/** A distinct Coil model prevents its general file decoders from opening a video. */
internal data class ProviderVideoThumbnail(val source: Any, val pixels: Int)

/** Decorative frames come from the provider; never create a native retriever while scrolling. */
internal class ProviderVideoThumbnailFetcher(
    private val context: Context,
    private val data: ProviderVideoThumbnail,
) : Fetcher {
    override suspend fun fetch(): ImageFetchResult {
        val bitmap = ProviderVideoThumbnailReader(context).read(data.source, data.pixels)
        return ImageFetchResult(bitmap.toDrawable(context.resources).asImage(), true, DataSource.DISK)
    }

    class Factory : Fetcher.Factory<ProviderVideoThumbnail> {
        override fun create(
            data: ProviderVideoThumbnail,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ProviderVideoThumbnailFetcher(options.context.applicationContext, data)
    }
}

internal class ProviderVideoThumbnailReader(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val load: (Any, Int, CancellationSignal) -> Bitmap = AndroidVideoThumbnails(context)::load,
) {
    suspend fun read(
        source: Any,
        pixels: Int,
    ): Bitmap {
        require(pixels > 0)
        val edge = pixels.coerceAtMost(MAX_EDGE)
        var owned: Bitmap? = null
        return try {
            requests.withPermit {
                coroutineScope {
                    val signal = CancellationSignal()
                    val request =
                        async(io) {
                            currentCoroutineContext().ensureActive()
                            val original = load(source, edge, signal).also { owned = it }
                            currentCoroutineContext().ensureActive()
                            val largest = maxOf(original.width, original.height)
                            if (largest > edge) {
                                val scale = edge.toDouble() / largest
                                val scaled =
                                    Bitmap.createScaledBitmap(
                                        original,
                                        (original.width * scale).toInt().coerceAtLeast(1),
                                        (original.height * scale).toInt().coerceAtLeast(1),
                                        true,
                                    )
                                owned = scaled
                                if (scaled !== original) original.recycle()
                            }
                            currentCoroutineContext().ensureActive()
                            checkNotNull(owned)
                        }
                    try {
                        request.await()
                    } finally {
                        // Runs in the caller's context while the IO child may still be
                        // waiting in Binder. The scope joins it before releasing the permit.
                        signal.cancel()
                    }
                }
            }.also { owned = null }
        } finally {
            owned?.recycle()
        }
    }

    companion object {
        const val MAX_EDGE = 512
        private val requests = Semaphore(2)
    }
}

private class AndroidVideoThumbnails(private val context: Context) {
    private val resolver = context.contentResolver

    fun load(
        source: Any,
        pixels: Int,
        signal: CancellationSignal,
    ): Bitmap {
        val uri =
            when (source) {
                is File -> indexedVideo(source, signal)
                is Uri -> source.takeIf { it.scheme == "content" }
                else -> null
            } ?: throw IOException("No provider thumbnail is available")
        signal.throwIfCanceled()
        val bitmap =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    resolver.loadThumbnail(
                        uri,
                        Size(pixels, pixels),
                        signal,
                    )
                DocumentsContract.isDocumentUri(context, uri) ->
                    DocumentsContract.getDocumentThumbnail(resolver, uri, Point(pixels, pixels), signal)
                else -> legacyVideo(uri, pixels, signal)
            }
        return bitmap ?: throw IOException("Provider did not supply a video thumbnail")
    }

    private fun indexedVideo(
        file: File,
        signal: CancellationSignal,
    ): Uri? {
        // DATA is used only to match an already authorized local path, never to
        // discover paths or write MediaStore. It also works on the API 27/28 fallback.
        @Suppress("DEPRECATION")
        val pathColumn = MediaStore.MediaColumns.DATA
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        return resolver.query(
            collection,
            arrayOf(MediaStore.Video.Media._ID),
            "$pathColumn = ?",
            arrayOf(file.canonicalPath),
            null,
            signal,
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyVideo(
        uri: Uri,
        pixels: Int,
        signal: CancellationSignal,
    ): Bitmap? {
        if (uri.authority != MediaStore.AUTHORITY || "video" !in uri.pathSegments) {
            throw IOException("This provider has no legacy video thumbnail")
        }
        // Query the provider's image cache on API 27/28. Do not call the deprecated
        // getThumbnail helper: it can fall back to an in-process native retriever.
        val videoId = ContentUris.parseId(uri).toString()
        val collection = MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI
        val requested =
            collection.buildUpon().appendQueryParameter("blocking", "1")
                .appendQueryParameter("orig_id", videoId).build()
        val thumbnail =
            resolver.query(
                requested,
                arrayOf(MediaStore.Video.Thumbnails._ID),
                "${MediaStore.Video.Thumbnails.VIDEO_ID} = ? AND ${MediaStore.Video.Thumbnails.KIND} = ?",
                arrayOf(videoId, MediaStore.Video.Thumbnails.MINI_KIND.toString()),
                null,
                signal,
            )?.use { cursor ->
                if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
            } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(thumbnail, bounds, signal)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = imagePreviewSampleSize(maxOf(bounds.outWidth, bounds.outHeight), pixels)
            }
        signal.throwIfCanceled()
        return decode(thumbnail, options, signal)
    }

    private fun decode(
        uri: Uri,
        options: BitmapFactory.Options,
        signal: CancellationSignal,
    ): Bitmap? =
        resolver.openAssetFileDescriptor(uri, "r", signal)?.use { descriptor ->
            descriptor.createInputStream().use { BitmapFactory.decodeStream(it, null, options) }
        }
}
