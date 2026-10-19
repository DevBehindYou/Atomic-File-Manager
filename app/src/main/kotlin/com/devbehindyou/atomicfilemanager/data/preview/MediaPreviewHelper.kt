package com.devbehindyou.atomicfilemanager.data.preview

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.devbehindyou.atomicfilemanager.data.backend.toMediaRef
import com.devbehindyou.atomicfilemanager.data.backend.toSafRef
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

data class MediaMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val artwork: Bitmap?,
    val mimeType: String?,
    val tags: AudioTags = AudioTags(),
)

data class PreparedMedia(
    val filePath: String,
    val metadata: MediaMetadata,
    val cleanup: () -> Unit,
    val playbackUri: Uri = Uri.fromFile(File(filePath)),
)

class MediaPreviewHelper(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxCacheBytes: Long = 512L * 1024 * 1024,
    private val metadataReader: ((Uri) -> MediaMetadata)? = null,
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    /** Owns staging even if preparation finishes just as its UI is cancelled. */
    suspend fun <T> withMedia(
        id: FileNodeId,
        fileName: String,
        block: suspend (PreparedMedia) -> T,
    ): FileResult<T> {
        var owned: PreparedMedia? = null
        try {
            return when (val result = prepareMedia(id, fileName)) {
                is FileResult.Failure -> result
                is FileResult.Success -> {
                    owned = result.value
                    coroutineContext.ensureActive()
                    FileResult.Success(block(result.value))
                }
            }
        } finally {
            withContext(NonCancellable + io) { owned?.cleanup?.invoke() }
        }
    }

    suspend fun prepareMedia(
        id: FileNodeId,
        fileName: String,
    ): FileResult<PreparedMedia> {
        var staged: File? = null
        try {
            return withContext(io) {
                val direct = directMediaUri(id)
                val uri =
                    direct ?: copyToCache(id, fileName).also { staged = it }?.let(Uri::fromFile)
                        ?: return@withContext FileResult.Failure(FileError.IoFailure("Could not open media"))
                val metadata = metadataReader?.invoke(uri) ?: readMetadata(uri)
                coroutineContext.ensureActive()
                FileResult.Success(
                    PreparedMedia(
                        filePath = staged?.absolutePath ?: uri.path.orEmpty(),
                        metadata = metadata,
                        cleanup = {
                            staged?.delete()
                            Unit
                        },
                        playbackUri = uri,
                    ),
                )
            }.also { if (it is FileResult.Failure) withContext(NonCancellable + io) { staged?.delete() } }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + io) { staged?.delete() }
            throw cancelled
        } catch (_: Exception) {
            withContext(NonCancellable + io) { staged?.delete() }
            return FileResult.Failure(FileError.IoFailure("Could not prepare media"))
        }
    }

    private fun readMetadata(uri: Uri): MediaMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return extractMetadata(retriever)
        } finally {
            retriever.release()
        }
    }

    private fun directMediaUri(id: FileNodeId): Uri? =
        when (id.prefix) {
            FileNodeId.Prefix.FILE ->
                File(
                    id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme),
                ).takeIf { it.isFile && it.canRead() }?.let(Uri::fromFile)
            FileNodeId.Prefix.SAF -> id.toSafRef().documentUri
            FileNodeId.Prefix.MEDIA -> {
                val ref = id.toMediaRef()
                require(!ref.isCollectionRoot)
                ContentUris.withAppendedId(ref.collectionUri, ref.rowId)
            }
            else -> null
        }

    suspend fun extractVideoThumbnail(
        id: FileNodeId,
        fileName: String,
    ): FileResult<Bitmap> =
        when (
            val prepared =
                withMedia(id, fileName) { media ->
                    withContext(io) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, media.playbackUri)
                            retriever.getScaledFrameAtTime(
                                1_000_000,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                512,
                                512,
                            )
                        } finally {
                            retriever.release()
                        }
                    }
                }
        ) {
            is FileResult.Failure -> prepared
            is FileResult.Success ->
                prepared.value?.let { FileResult.Success(it) }
                    ?: FileResult.Failure(FileError.IoFailure("Could not extract video frame"))
        }

    private fun extractMetadata(retriever: MediaMetadataRetriever): MediaMetadata {
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = durationStr?.toLongOrNull() ?: 0L
        val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val width = widthStr?.toIntOrNull() ?: 0
        val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val height = heightStr?.toIntOrNull() ?: 0
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

        val artBytes = retriever.embeddedPicture
        val artwork =
            if (artBytes != null && artBytes.isNotEmpty() && artBytes.size <= MAX_ART_BYTES) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, bounds)
                val options =
                    BitmapFactory.Options().apply {
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_ART_DIMENSION) sample *= 2
                        inSampleSize = sample
                    }
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
            } else {
                null
            }

        return MediaMetadata(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            width = width,
            height = height,
            artwork = artwork,
            mimeType = mime,
            tags = extractTags(retriever),
        )
    }

    private fun extractTags(retriever: MediaMetadataRetriever): AudioTags {
        fun text(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }

        fun number(key: Int) = text(key)?.toIntOrNull()?.takeIf { it > 0 }
        val newer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return AudioTags(
            albumArtist = text(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
            year = text(MediaMetadataRetriever.METADATA_KEY_YEAR),
            genre = text(MediaMetadataRetriever.METADATA_KEY_GENRE),
            track = text(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
            disc = text(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
            composer = text(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
            bitrateBps = number(MediaMetadataRetriever.METADATA_KEY_BITRATE),
            sampleRateHz = if (newer) number(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE) else null,
            bitsPerSample = if (newer) number(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE) else null,
        )
    }

    private suspend fun copyToCache(
        id: FileNodeId,
        fileName: String,
    ): File? {
        val backend = backendSelector(id)
        val inRes = backend.openInput(id)
        if (inRes !is FileResult.Success) return null

        val extension =
            fileName.substringAfterLast('.', "tmp").takeIf {
                it.length <= 12 && it.all(Char::isLetterOrDigit)
            } ?: "tmp"
        val cacheFile = File.createTempFile("media-preview-", ".$extension", context.cacheDir)
        var completed = false
        return try {
            inRes.value.stream().use { input ->
                FileOutputStream(cacheFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        copied += count
                        if (copied > maxCacheBytes) throw IOException("Media cache limit exceeded")
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            coroutineContext.ensureActive()
            completed = true
            cacheFile
        } catch (e: IOException) {
            null
        } finally {
            if (!completed) cacheFile.delete()
        }
    }

    private companion object {
        const val MAX_ART_BYTES = 4 * 1024 * 1024
        const val MAX_ART_DIMENSION = 512
    }
}
