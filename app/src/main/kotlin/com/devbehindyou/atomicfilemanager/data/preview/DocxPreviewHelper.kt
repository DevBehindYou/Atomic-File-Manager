package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext

data class DocxTextRun(val text: String, val bold: Boolean = false, val italic: Boolean = false)

data class DocxParagraph(
    val runs: List<DocxTextRun>,
    val headingLevel: Int = 0,
    val listItem: Boolean = false,
    val inTable: Boolean = false,
) {
    val text: String get() = runs.joinToString("") { it.text }
}

data class DocxDocument(val paragraphs: List<DocxParagraph>)

internal data class DocxPreviewLimits(
    val compressedBytes: Long = 16L * 1024 * 1024,
    val expandedBytes: Long = 32L * 1024 * 1024,
    val documentBytes: Long = 2L * 1024 * 1024,
    val entries: Int = 256,
    val characters: Int = 500_000,
    val paragraphs: Int = 10_000,
    val tokens: Int = 100_000,
    val depth: Int = 64,
)

/** Shown as the "format" of a Word file in the OLE container: legacy .doc, or a password-protected document. */
const val DOCX_LEGACY_OR_PROTECTED = "legacy or password-protected Word document"

/** Shown as the "format" of a file named or typed as DOCX that is not a ZIP package at all. */
const val DOCX_NOT_A_PACKAGE = "not a Word package"

/** Shown as the "format" of a DOCX that is larger or more complex than the preview reads. */
const val DOCX_OVER_PREVIEW_LIMITS = "over the DOCX preview limits"

/** A preview size or complexity bound was reached; the document itself may be fine. */
internal class DocxLimitExceeded(message: String) : IOException(message)

private inline fun withinLimit(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) throw DocxLimitExceeded(message())
}

/** Text-layout preview only. Never extracts ZIP entries or resolves relationships, images or external resources. */
class DocxPreviewHelper(
    private val backendSelector: (FileNodeId) -> StorageBackend,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun read(id: FileNodeId): FileResult<DocxDocument> =
        withContext(io) {
            try {
                when (val opened = backendSelector(id).openInput(id)) {
                    is FileResult.Failure -> opened
                    is FileResult.Success ->
                        opened.value.stream().use { raw ->
                            val input = BufferedInputStream(raw)
                            when (packageKind(input)) {
                                PackageKind.ZIP -> FileResult.Success(readDocx(input, coroutineContext))
                                PackageKind.OLE -> unsupported(DOCX_LEGACY_OR_PROTECTED)
                                PackageKind.OTHER -> unsupported(DOCX_NOT_A_PACKAGE)
                            }
                        }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: DocxLimitExceeded) {
                FileResult.Failure(FileError.UnsupportedFormat(DOCX_OVER_PREVIEW_LIMITS))
            } catch (failure: Exception) {
                FileResult.Failure(
                    FileError.IoFailure("DOCX text preview unavailable: ${failure.javaClass.simpleName}"),
                )
            }
        }
}

private enum class PackageKind { ZIP, OLE, OTHER }

private fun unsupported(kind: String): FileResult<DocxDocument> = FileResult.Failure(FileError.UnsupportedFormat(kind))

/** Reads the container signature without consuming it: ZIP for DOCX, OLE for .doc or protected Office files. */
private fun packageKind(input: BufferedInputStream): PackageKind {
    input.mark(OLE_SIGNATURE.size)
    val head = ByteArray(OLE_SIGNATURE.size)
    var filled = 0
    while (filled < head.size) {
        val read = input.read(head, filled, head.size - filled)
        if (read < 0) break
        filled += read
    }
    input.reset()
    return when {
        filled >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> PackageKind.ZIP
        filled == OLE_SIGNATURE.size && head.contentEquals(OLE_SIGNATURE) -> PackageKind.OLE
        else -> PackageKind.OTHER
    }
}

private val OLE_SIGNATURE =
    byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

internal fun readDocx(
    input: InputStream,
    context: CoroutineContext,
    limits: DocxPreviewLimits = DocxPreviewLimits(),
): DocxDocument {
    val compressed = PreviewBoundedInput(input, limits.compressedBytes, context)
    var expanded = 0L
    var entries = 0
    var document: DocxDocument? = null
    ZipInputStream(compressed).use { zip ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            context.ensureActive()
            val entry = zip.nextEntry ?: break
            withinLimit(++entries <= limits.entries) { "Too many ZIP entries" }
            val entryInput =
                PreviewBoundedInput(zip, limits.expandedBytes, context) { count ->
                    expanded += count
                    withinLimit(expanded <= limits.expandedBytes) { "Expanded document exceeds preview limit" }
                }
            if (entry.name == "word/document.xml") {
                require(document == null && !entry.isDirectory) { "Duplicate document part" }
                val documentInput = PreviewBoundedInput(entryInput, limits.documentBytes, context)
                document = parseDocument(documentInput, context, limits)
            }
            while (entryInput.read(buffer) != -1) context.ensureActive()
            zip.closeEntry()
        }
    }
    context.ensureActive()
    return requireNotNull(document) { "Missing DOCX document part" }
}

private class PreviewBoundedInput(
    input: InputStream,
    private val limit: Long,
    private val context: CoroutineContext,
    private val consumed: (Int) -> Unit = {},
) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        context.ensureActive()
        return super.read().also { if (it != -1) record(1) }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        context.ensureActive()
        return `in`.read(buffer, offset, length).also { if (it > 0) record(it) }
    }

    private fun record(bytes: Int) {
        count += bytes
        if (count > limit) throw DocxLimitExceeded("Document exceeds preview byte limit")
        consumed(bytes)
    }
}

private fun parseDocument(
    input: InputStream,
    context: CoroutineContext,
    limits: DocxPreviewLimits,
): DocxDocument {
    val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    parser.setInput(input, null)
    val output = mutableListOf<DocxParagraph>()
    var runs = mutableListOf<DocxTextRun>()
    var text = StringBuilder()
    var bold = false
    var italic = false
    var heading = 0
    var list = false
    var tableDepth = 0
    var paragraphDepth = 0
    var textDepth = 0
    var deletedDepth = 0
    var ignoredDepth = 0
    var bodyDepth = 0
    var characters = 0
    var tokens = 0
    var sawDocument = false
    var sawBody = false
    while (true) {
        context.ensureActive()
        val event = parser.nextToken()
        withinLimit(++tokens <= limits.tokens && parser.depth <= limits.depth) { "Document is too complex" }
        require(event != XmlPullParser.DOCDECL) { "DTD declarations are not supported" }
        if (event == XmlPullParser.END_DOCUMENT) break
        if (ignoredDepth > 0) {
            if (event == XmlPullParser.END_TAG && parser.depth == ignoredDepth) ignoredDepth = 0
        } else {
            val word = parser.namespace in WORD_NAMESPACES
            if (event == XmlPullParser.START_TAG && word) {
                when (parser.name) {
                    "document" -> {
                        require(parser.depth == 1)
                        sawDocument = true
                    }
                    "body" -> {
                        require(sawDocument && !sawBody && parser.depth == 2)
                        sawBody = true
                        bodyDepth = parser.depth
                    }
                    "drawing", "pict", "object" -> ignoredDepth = parser.depth
                    "tbl" -> tableDepth++
                    "del" -> deletedDepth++
                    "p" -> {
                        require(bodyDepth > 0 && paragraphDepth == 0) { "Invalid paragraph nesting" }
                        paragraphDepth = parser.depth
                        runs = mutableListOf()
                        heading = 0
                        list = false
                    }
                    "r" -> {
                        text = StringBuilder()
                        bold = false
                        italic = false
                    }
                    "b" -> bold = parser.wordValue() !in DISABLED_VALUES
                    "i" -> italic = parser.wordValue() !in DISABLED_VALUES
                    "pStyle" -> {
                        heading = parser.wordValue()?.removePrefix("Heading")?.toIntOrNull()?.coerceIn(0, 9) ?: 0
                    }
                    "numPr" -> list = true
                    "t" -> textDepth = parser.depth
                    "tab", "br", "cr" ->
                        if (paragraphDepth > 0 && deletedDepth == 0) {
                            text.append(if (parser.name == "tab") '\t' else '\n')
                            characters++
                        }
                }
            } else if (event == XmlPullParser.END_TAG && word) {
                when (parser.name) {
                    "body" -> bodyDepth = 0
                    "t" -> textDepth = 0
                    "r" -> if (text.isNotEmpty()) runs.add(DocxTextRun(text.toString(), bold, italic))
                    "p" -> {
                        withinLimit(output.size < limits.paragraphs) { "Too many paragraphs" }
                        output.add(DocxParagraph(runs.toList(), heading, list, tableDepth > 0))
                        paragraphDepth = 0
                    }
                    "tbl" -> tableDepth--
                    "del" -> deletedDepth--
                }
            } else if (textDepth > 0 && deletedDepth == 0 && event in TEXT_EVENTS) {
                require(
                    event != XmlPullParser.ENTITY_REF || parser.name in PREDEFINED_ENTITIES ||
                        parser.name.startsWith("#"),
                ) {
                    "Unsupported entity"
                }
                val value = parser.text.orEmpty()
                characters += value.length
                text.append(value)
            }
        }
        withinLimit(characters <= limits.characters) { "Document text exceeds preview limit" }
    }
    require(sawDocument && sawBody && paragraphDepth == 0) { "Invalid DOCX document" }
    return DocxDocument(output.toList())
}

private fun XmlPullParser.wordValue(): String? = WORD_NAMESPACES.firstNotNullOfOrNull { getAttributeValue(it, "val") }

private val WORD_NAMESPACES =
    setOf(
        "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
        "http://purl.oclc.org/ooxml/wordprocessingml/main",
    )
private val DISABLED_VALUES = setOf("0", "false", "off")
private val PREDEFINED_ENTITIES = setOf("amp", "lt", "gt", "quot", "apos")
private val TEXT_EVENTS = setOf(XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF)
