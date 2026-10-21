package com.devbehindyou.atomicfilemanager.domain.model

import java.util.Locale

enum class FileOpenMode { BUILT_IN, ASK, EXTERNAL }

enum class FileOpenFamily { IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, ARCHIVE, CODE, TEXT, OTHER }

enum class FileOpenRuleScope { EXTENSION, MIME, FAMILY }

/** Component is an opaque Android adapter identifier; a null component requests the system chooser. */
data class FileOpenTarget(
    val mode: FileOpenMode,
    val component: String? = null,
    val appLabel: String? = null,
) {
    init {
        require(mode == FileOpenMode.EXTERNAL || component == null)
        require(component == null || component.length in 3..512)
    }
}

data class FileOpenRuleKey(val scope: FileOpenRuleScope, val value: String) {
    init {
        require(value.isNotBlank() && value.length <= 128)
    }
}

/** App-specific preferences only; never changes an operating-system default. */
data class FileOpeningPreferences(
    val defaultMode: FileOpenMode = FileOpenMode.BUILT_IN,
    val rules: Map<FileOpenRuleKey, FileOpenTarget> = emptyMap(),
) {
    init { require(rules.size <= MAX_FILE_OPENING_RULES) }

    fun targetFor(node: FileNode): FileOpenTarget =
        if (node.isDirectory) FileOpenTarget(FileOpenMode.BUILT_IN) else
            FileOpenRuleScope.entries.firstNotNullOfOrNull { scope -> keyFor(node, scope)?.let(rules::get) }
                ?: FileOpenTarget(defaultMode)

    fun withRule(key: FileOpenRuleKey, target: FileOpenTarget): FileOpeningPreferences =
        copy(rules = rules + (key to target))

    fun withoutRule(key: FileOpenRuleKey): FileOpeningPreferences = copy(rules = rules - key)
}

const val MAX_FILE_OPENING_RULES = 128

fun parseFileOpenRuleKey(scope: FileOpenRuleScope, raw: String): FileOpenRuleKey? {
    val value = raw.trim()
    val normalized = when (scope) {
        FileOpenRuleScope.EXTENSION -> value.removePrefix(".").lowercase(Locale.ROOT)
            .takeIf { it.length in 1..32 && it.all { c -> c.isLetterOrDigit() || c in "_+-" } }
        FileOpenRuleScope.MIME -> normalizedFileMime(value)
        FileOpenRuleScope.FAMILY -> FileOpenFamily.entries.firstOrNull { it.name == value }?.name
    }
    return normalized?.takeIf { it.length <= 128 }?.let { FileOpenRuleKey(scope, it) }
}

fun keyFor(node: FileNode, scope: FileOpenRuleScope): FileOpenRuleKey? {
    if (node.isDirectory) return null
    val value =
        when (scope) {
            FileOpenRuleScope.EXTENSION -> fileExtension(node.name)
            FileOpenRuleScope.MIME -> normalizedFileMime(node.mimeType)
            FileOpenRuleScope.FAMILY -> fileOpenFamily(node).name
        }
    return value?.let { FileOpenRuleKey(scope, it) }
}

fun fileExtension(name: String): String? {
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex) return null
    return name.substring(dot + 1).lowercase(Locale.ROOT)
        .takeIf { it.length <= 32 && it.all { c -> c.isLetterOrDigit() || c in "_+-" } }
}

fun normalizedFileMime(raw: String?): String? =
    raw?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        ?.takeIf { it.length <= 128 && it != "application/octet-stream" && MIME_PATTERN.matches(it) }

/** Classification for routing, independent of decorative icon colours and without file reads. */
fun fileOpenFamily(node: FileNode): FileOpenFamily {
    val mime = normalizedFileMime(node.mimeType).orEmpty()
    val extension = fileExtension(node.name)
    return when {
        mime.startsWith("image/") -> FileOpenFamily.IMAGE
        mime.startsWith("video/") -> FileOpenFamily.VIDEO
        mime.startsWith("audio/") -> FileOpenFamily.AUDIO
        mime == "application/pdf" -> FileOpenFamily.PDF
        mime.contains("wordprocessing") || mime.startsWith("application/vnd.ms-") || mime == "application/msword" ||
            mime.contains("spreadsheet") || mime.contains("presentation") || mime.startsWith("application/vnd.oasis.") ->
            FileOpenFamily.DOCUMENT
        mime in ARCHIVE_MIMES -> FileOpenFamily.ARCHIVE
        mime in CODE_MIMES || mime.endsWith("+json") || mime.endsWith("+xml") -> FileOpenFamily.CODE
        mime.startsWith("text/") -> if (extension in CODE_EXTENSIONS) FileOpenFamily.CODE else FileOpenFamily.TEXT
        mime.isNotEmpty() -> FileOpenFamily.OTHER
        extension in IMAGE_EXTENSIONS -> FileOpenFamily.IMAGE
        extension in VIDEO_EXTENSIONS -> FileOpenFamily.VIDEO
        extension in AUDIO_EXTENSIONS -> FileOpenFamily.AUDIO
        extension == "pdf" -> FileOpenFamily.PDF
        extension in DOCUMENT_EXTENSIONS -> FileOpenFamily.DOCUMENT
        extension in ARCHIVE_EXTENSIONS -> FileOpenFamily.ARCHIVE
        extension in CODE_EXTENSIONS -> FileOpenFamily.CODE
        extension in TEXT_EXTENSIONS -> FileOpenFamily.TEXT
        else -> FileOpenFamily.OTHER
    }
}

private val MIME_PATTERN = Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
private val DOCUMENT_EXTENSIONS = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf")
private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "tgz", "iso")
private val TEXT_EXTENSIONS = setOf("txt", "md", "markdown", "log", "ini", "conf", "cfg", "properties", "toml", "csv", "srt", "vtt")
private val CODE_EXTENSIONS =
    setOf("kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css", "json", "xml", "yaml", "yml", "sh", "bash", "c", "cpp", "h", "hpp", "rs", "go", "sql")
private val CODE_MIMES = setOf("application/json", "application/xml", "application/javascript", "application/sql", "application/x-sh")
private val ARCHIVE_MIMES = setOf("application/zip", "application/x-zip-compressed", "application/x-7z-compressed", "application/vnd.rar", "application/x-rar-compressed", "application/x-tar", "application/gzip")
