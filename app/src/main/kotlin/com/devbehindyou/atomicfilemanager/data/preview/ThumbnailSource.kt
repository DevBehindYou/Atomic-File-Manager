package com.devbehindyou.atomicfilemanager.data.preview

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import coil3.request.ImageRequest
import coil3.size.Precision
import com.devbehindyou.atomicfilemanager.data.backend.toMediaRef
import com.devbehindyou.atomicfilemanager.domain.model.FileCollection
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import java.io.File
import java.net.URLDecoder

enum class ThumbnailKind { IMAGE, VIDEO }

/** A Coil model resolved in the data layer; UI code never decodes a backend-qualified id. */
data class ThumbnailSource(val data: Any, val cacheKey: String, val kind: ThumbnailKind)

fun thumbnailKind(node: FileNode): ThumbnailKind? {
    if (node.isDirectory) return null
    val mime = node.mimeType.orEmpty().lowercase().substringBefore(';').trim()
    return when {
        mime.startsWith("image/") -> ThumbnailKind.IMAGE
        mime.startsWith("video/") -> ThumbnailKind.VIDEO
        !genericThumbnailMime(mime) -> null
        else ->
            mediaCollectionKind(node.id) ?: when {
                FileCollection.IMAGES.matches(node) -> ThumbnailKind.IMAGE
                FileCollection.VIDEOS.matches(node) -> ThumbnailKind.VIDEO
                else -> null
            }
    }
}

private fun mediaCollectionKind(id: FileNodeId): ThumbnailKind? {
    if (id.prefix != FileNodeId.Prefix.MEDIA || FileNodeId.parse(id.raw) == null) return null
    val ref = id.toMediaRef()
    if (ref.isCollectionRoot) return null
    return when (ref.collection) {
        "images" -> ThumbnailKind.IMAGE
        "video" -> ThumbnailKind.VIDEO
        else -> null
    }
}

fun genericThumbnailMime(mime: String?): Boolean =
    mime.isNullOrBlank() || mime.lowercase().substringBefore(';').trim() in GENERIC_MIMES

/** No filesystem/provider access: resolution is safe while constructing a request. */
fun thumbnailSourceOf(node: FileNode): ThumbnailSource? {
    if (node.isHidden || node.name.startsWith('.') || !node.access.readable) return null
    val kind = thumbnailKind(node) ?: return null
    val id = FileNodeId.parse(node.id.raw) ?: return null
    val data =
        try {
            when (id.prefix) {
                FileNodeId.Prefix.FILE -> File(id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme))
                FileNodeId.Prefix.SAF -> {
                    val uri = Uri.parse(URLDecoder.decode(id.raw.removePrefix(FileNodeId.Prefix.SAF.scheme), "UTF-8"))
                    uri.takeIf { it.scheme == "content" && !it.authority.isNullOrBlank() }
                }
                FileNodeId.Prefix.MEDIA -> {
                    val ref = id.toMediaRef()
                    if (ref.isCollectionRoot) null else ContentUris.withAppendedId(ref.collectionUri, ref.rowId)
                }
                else -> null
            }
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
    return ThumbnailSource(data, thumbnailCacheKey(node), kind)
}

fun thumbnailCacheKey(node: FileNode): String =
    "thumbnail:${node.id.raw.length}:${node.id.raw}:${node.modifiedAt}:${node.size}"

/** Retained for callers that need a local path; the UI does not parse ids. */
fun localThumbnailPathOf(node: FileNode): String? =
    if (thumbnailSourceOf(node)?.data is File) node.id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme) else null

fun buildThumbnailRequest(
    context: Context,
    source: ThumbnailSource,
    pixels: Int,
): ImageRequest =
    ImageRequest.Builder(context)
        .data(
            if (source.kind == ThumbnailKind.VIDEO) {
                ProviderVideoThumbnail(
                    source.data,
                    pixels.coerceAtLeast(1),
                )
            } else {
                source.data
            },
        )
        .size(pixels.coerceAtLeast(1)).precision(Precision.EXACT)
        .memoryCacheKey("${source.cacheKey}:${source.kind}:${pixels.coerceAtLeast(1)}").diskCacheKey(source.cacheKey)
        .build()

private val GENERIC_MIMES =
    setOf("application/octet-stream", "binary/octet-stream", "application/unknown", "application/x-unknown")
