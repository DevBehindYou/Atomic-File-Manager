package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicFileType
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.data.preview.ThumbnailKind
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailKind
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import java.util.Locale

/** Decorative classification only: does not open files, request access or decide how to launch them. */
internal fun fileTypeFor(node: FileNode): AtomicFileType {
    if (node.isDirectory) return AtomicFileType.FOLDER
    val mime = node.mimeType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
    val extension = node.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val collectionType = if (node.id.prefix == FileNodeId.Prefix.MEDIA) mediaTypeFor(node) else null
    return typeFromMime(mime, extension)
        ?: collectionType
        ?: extensionTypes[extension]
        ?: AtomicFileType.GENERIC
}

private fun mediaTypeFor(node: FileNode): AtomicFileType? =
    when (thumbnailKind(node)) {
        ThumbnailKind.IMAGE -> AtomicFileType.IMAGE
        ThumbnailKind.VIDEO -> AtomicFileType.VIDEO
        null -> null
    }

/** One outlined icon family, with distinct shapes as well as type colours. */
fun iconFor(node: FileNode): ImageVector =
    when (fileTypeFor(node)) {
        AtomicFileType.FOLDER -> AtomicIcons.Files
        AtomicFileType.IMAGE -> AtomicIcons.Image
        AtomicFileType.VIDEO -> AtomicIcons.Video
        AtomicFileType.AUDIO -> AtomicIcons.AudioFile
        AtomicFileType.PDF -> AtomicIcons.Pdf
        AtomicFileType.APK -> AtomicIcons.Apk
        AtomicFileType.ARCHIVE -> AtomicIcons.Archive
        AtomicFileType.CODE -> AtomicIcons.Code
        AtomicFileType.TEXT -> AtomicIcons.TextFile
        AtomicFileType.DOCUMENT -> AtomicIcons.Document
        AtomicFileType.SPREADSHEET -> AtomicIcons.Spreadsheet
        AtomicFileType.PRESENTATION -> AtomicIcons.Presentation
        AtomicFileType.EBOOK -> AtomicIcons.Ebook
        AtomicFileType.FONT -> AtomicIcons.Font
        AtomicFileType.GENERIC -> AtomicIcons.UnknownFile
    }

private fun typeFromMime(
    mime: String,
    extension: String,
): AtomicFileType? =
    when {
        mime.startsWith("image/") -> AtomicFileType.IMAGE
        mime.startsWith("video/") -> AtomicFileType.VIDEO
        mime.startsWith("audio/") -> AtomicFileType.AUDIO
        mime.startsWith("font/") -> AtomicFileType.FONT
        mime in ebookMimes -> AtomicFileType.EBOOK
        mime == "application/pdf" -> AtomicFileType.PDF
        mime == "application/vnd.android.package-archive" -> AtomicFileType.APK
        mime in archiveMimes -> AtomicFileType.ARCHIVE
        mime in codeMimes -> AtomicFileType.CODE
        mime == "text/csv" || mime.contains("spreadsheet") || mime == "application/vnd.ms-excel" ->
            AtomicFileType.SPREADSHEET
        mime.contains("presentation") || mime == "application/vnd.ms-powerpoint" -> AtomicFileType.PRESENTATION
        mime.startsWith("text/") ->
            if (extensionTypes[extension] == AtomicFileType.CODE) AtomicFileType.CODE else AtomicFileType.TEXT
        mime.contains("wordprocessing") || mime.contains("opendocument.text") || mime in documentMimes ->
            AtomicFileType.DOCUMENT
        else -> null
    }

private val archiveMimes =
    setOf(
        "application/zip", "application/x-zip-compressed", "application/vnd.rar", "application/x-rar-compressed",
        "application/x-7z-compressed", "application/x-tar", "application/gzip", "application/x-gzip",
        "application/x-bzip2", "application/x-xz", "application/zstd", "application/x-iso9660-image",
    )
private val codeMimes =
    setOf(
        "application/json", "application/ld+json", "application/xml", "application/javascript", "application/sql",
        "application/x-sh", "application/x-httpd-php", "text/javascript", "text/x-python", "text/x-java-source",
        "text/x-kotlin", "text/x-c", "text/x-c++", "text/x-shellscript", "text/html", "text/css", "text/xml",
    )
private val documentMimes = setOf("application/msword", "application/rtf")
private val ebookMimes = setOf("application/epub+zip", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook")

private val extensionTypes =
    buildMap {
        fun add(
            type: AtomicFileType,
            extensions: String,
        ) {
            extensions.split(' ').forEach { put(it, type) }
        }
        add(AtomicFileType.IMAGE, "jpg jpeg png gif webp heic heif avif bmp svg tif tiff dng raw ico")
        add(AtomicFileType.VIDEO, "mp4 mkv webm avi mov 3gp m4v ts mpeg mpg wmv flv")
        add(AtomicFileType.AUDIO, "mp3 m4a aac flac wav ogg opus wma amr aiff mid midi")
        add(AtomicFileType.PDF, "pdf")
        add(AtomicFileType.APK, "apk apks xapk apkm")
        add(AtomicFileType.ARCHIVE, "zip rar 7z tar gz bz2 xz zst tgz tbz tbz2 txz iso")
        add(AtomicFileType.CODE, "kt kts java py js jsx ts tsx mjs cjs html htm css scss sass less")
        add(AtomicFileType.CODE, "json xml yaml yml sh bash zsh fish php rb go rs c cpp cc h hpp cs swift sql dart lua")
        add(AtomicFileType.TEXT, "txt md markdown log ini cfg conf properties toml rst")
        add(AtomicFileType.DOCUMENT, "doc docx odt rtf pages")
        add(AtomicFileType.EBOOK, "epub mobi azw azw3 fb2")
        add(AtomicFileType.FONT, "ttf otf woff woff2")
        add(AtomicFileType.SPREADSHEET, "xls xlsx ods csv tsv numbers")
        add(AtomicFileType.PRESENTATION, "ppt pptx odp key")
    }
