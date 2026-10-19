package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.usecase.ArchiveFormats
import java.util.Locale

internal enum class PreviewType { IMAGE, PDF, DOCX, TEXT, MARKDOWN, ARCHIVE, AUDIO, VIDEO, GENERIC }

/** Office packages are binary ZIP containers even though their MIME type contains "xml". */
internal fun resolvePreviewType(node: FileNode): PreviewType {
    val mime = node.mimeType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
    val extension = node.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        node.isDirectory -> PreviewType.GENERIC
        extension == "docx" || mime == DOCX_MIME -> PreviewType.DOCX
        extension in OFFICE_EXTENSIONS || mime.startsWith("application/vnd.openxmlformats-officedocument.") ||
            mime.startsWith("application/vnd.ms-") || mime == "application/msword" -> PreviewType.GENERIC
        Gallery.isImage(node) -> PreviewType.IMAGE
        mime.startsWith("audio/") || extension in AUDIO_EXTENSIONS -> PreviewType.AUDIO
        mime.startsWith("video/") || extension in VIDEO_EXTENSIONS -> PreviewType.VIDEO
        mime == "application/pdf" || extension == "pdf" -> PreviewType.PDF
        extension in setOf("md", "markdown") || mime == "text/markdown" -> PreviewType.MARKDOWN
        ArchiveFormats.kindOf(node.name) != null || mime in ARCHIVE_MIMES -> PreviewType.ARCHIVE
        mime.startsWith("text/") || mime == "application/json" || mime.endsWith("+json") ||
            mime == "application/xml" || mime.endsWith("+xml") || extension in TEXT_EXTENSIONS -> PreviewType.TEXT
        else -> PreviewType.GENERIC
    }
}

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private val OFFICE_EXTENSIONS =
    setOf("doc", "docm", "dotx", "dotm", "xls", "xlsx", "xlsm", "ppt", "pptx", "pptm", "odt", "ods", "odp")
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
private val ARCHIVE_MIMES = setOf("application/zip", "application/x-zip-compressed", "application/x-7z-compressed")
private val TEXT_EXTENSIONS =
    setOf(
        "txt", "json", "xml", "kt", "kts", "java", "log", "gradle", "sh", "bash", "py", "html", "htm", "css",
        "js", "jsx", "ts", "tsx", "csv", "yaml", "yml", "toml", "ini", "conf", "cfg", "c", "h", "cpp", "hpp",
        "rs", "go", "sql", "properties", "svg", "srt", "vtt",
    )
