package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class DocxPreviewHelperTest {
    @Test
    fun realZipPreservesHeadingsEmphasisListsTableOrderAndEscapedText() {
        val xml =
            body(
                """<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                <w:r><w:rPr><w:b/><w:i/></w:rPr><w:t>Title &amp; &#65;</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr/></w:pPr><w:r><w:t>Item</w:t><w:tab/><w:t>one</w:t><w:br/></w:r></w:p>
                <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Cell A</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>Cell B</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
                <w:p><w:del><w:r><w:delText>Removed</w:delText></w:r></w:del><w:r><w:t>Current</w:t></w:r></w:p>""",
            )
        val document = parse(zip("word/document.xml" to xml))
        assertEquals(
            listOf("Title & A", "Item\tone\n", "Cell A", "Cell B", "Current"),
            document.paragraphs.map { it.text },
        )
        assertEquals(1, document.paragraphs.first().headingLevel)
        assertTrue(document.paragraphs.first().runs.single().bold)
        assertTrue(document.paragraphs.first().runs.single().italic)
        assertTrue(document.paragraphs[1].listItem)
        assertTrue(document.paragraphs[2].inTable)
        assertFalse(document.paragraphs.last().inTable)
    }

    @Test
    fun explicitFalseRunPropertiesAndStrictWordNamespaceAreSupported() {
        val xml =
            body("<w:p><w:r><w:rPr><w:b w:val=\"false\"/><w:i w:val=\"0\"/></w:rPr><w:t>Plain</w:t></w:r></w:p>")
                .replace(WORD_NS, "http://purl.oclc.org/ooxml/wordprocessingml/main")
        val run = parse(zip("word/document.xml" to xml)).paragraphs.single().runs.single()
        assertFalse(run.bold)
        assertFalse(run.italic)
    }

    @Test
    fun rejectsMissingDocumentWrongNamespaceMalformedXmlAndDtdEntities() {
        val invalid =
            listOf(
                zip("other.xml" to body("")),
                zip("word/document.xml" to "<document><body/></document>"),
                zip(
                    "word/document.xml" to
                        body("").replace("<w:body>", "<w:extra><w:body>")
                            .replace("</w:body>", "</w:body></w:extra>"),
                ),
                zip("word/document.xml" to body("<w:p>")),
                zip(
                    "word/document.xml" to
                        "<!DOCTYPE w:document [<!ENTITY xxe SYSTEM 'file:///unreadable-secret'>]>" +
                        body("<w:p><w:r><w:t>&xxe;</w:t></w:r></w:p>"),
                ),
                zip(
                    "word/document.xml" to
                        "<!DOCTYPE w:document [<!ENTITY x 'expanded'>]>" +
                        body("<w:p><w:r><w:t>&x;</w:t></w:r></w:p>"),
                ),
            )
        invalid.forEach { bytes -> assertThrows(Exception::class.java) { parse(bytes) } }
    }

    @Test
    fun allUnknownPartsAreDrainedThroughExpandedAndEntryBoundsEvenAfterTheDocument() {
        val bytes = zip("word/document.xml" to body(""), "word/media/large.bin" to "x".repeat(20_000))
        assertThrows(Exception::class.java) { parse(bytes, DocxPreviewLimits(expandedBytes = 1024)) }
        assertThrows(Exception::class.java) { parse(bytes, DocxPreviewLimits(entries = 1)) }
    }

    @Test
    fun drawingSubtreesAreSkippedWithoutRejectingBodyParagraphsOrExecutingObjects() {
        val xml =
            body(
                """<w:p><w:r><w:t>Before</w:t><w:drawing><w:p><w:r><w:t>Text box</w:t></w:r></w:p></w:drawing></w:r>
                <w:r><w:object><w:p/></w:object><w:t>After</w:t></w:r></w:p>""",
            )
        assertEquals("BeforeAfter", parse(zip("word/document.xml" to xml)).paragraphs.single().text)
    }

    @Test
    fun compressedXmlTextTokenDepthAndParagraphLimitsFailClosed() {
        val xml = body("<w:p><w:r><w:t>Content</w:t></w:r></w:p><w:p/>")
        val bytes = zip("word/document.xml" to xml)
        listOf(
            DocxPreviewLimits(compressedBytes = 10),
            DocxPreviewLimits(documentBytes = 20),
            DocxPreviewLimits(characters = 2),
            DocxPreviewLimits(tokens = 3),
            DocxPreviewLimits(depth = 2),
            DocxPreviewLimits(paragraphs = 1),
        ).forEach { limits -> assertThrows(Exception::class.java) { parse(bytes, limits) } }
    }

    @Test
    fun cancelledParsingChecksBeforeReadingAndClosesTheOwnedArchiveOnMidStreamCancellation() {
        val job = Job()
        job.cancel()
        assertThrows(CancellationException::class.java) {
            readDocx(ByteArrayInputStream(zip("word/document.xml" to body(""))), job)
        }
        val activeJob = Job()
        val input =
            object : ByteArrayInputStream(zip("word/document.xml" to body(""))) {
                var closed = false

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    activeJob.cancel()
                    return super.read(buffer, offset, length)
                }

                override fun close() {
                    closed = true
                    super.close()
                }
            }
        assertThrows(CancellationException::class.java) { readDocx(input, activeJob) }
        assertTrue(input.closed)
    }

    @Test
    fun helperKeepsBackendFailureAndMapsStreamFailureWhileClosingInput() =
        runTest {
            val original = FileResult.Failure(FileError.IoFailure("Backend unavailable"))
            val failedBackend = backend { original }
            assertEquals(original, DocxPreviewHelper({ failedBackend }, StandardTestDispatcher(testScheduler)).read(ID))
            var closed = false
            val broken =
                object : ByteArrayInputStream(byteArrayOf()) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = throw IOException("broken")

                    override fun read(): Int = throw IOException("broken")

                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            val helper =
                DocxPreviewHelper({
                    backend { FileResult.Success(InputStreamProvider { broken }) }
                }, StandardTestDispatcher(testScheduler))
            assertTrue(helper.read(ID) is FileResult.Failure)
            assertTrue(closed)
        }

    @Test
    fun backendConstructionOpenAndEveryReadUseInjectedWorkerAndCancellationIsNotMapped() {
        val caller = Thread.currentThread()
        var closed = false
        val input =
            object : ByteArrayInputStream(zip("word/document.xml" to body(""))) {
                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    check(Thread.currentThread() !== caller)
                    return super.read(buffer, offset, length)
                }

                override fun close() {
                    check(Thread.currentThread() !== caller)
                    closed = true
                    super.close()
                }
            }
        Executors.newSingleThreadExecutor().use { executor ->
            executor.asCoroutineDispatcher().use { io ->
                val helper =
                    DocxPreviewHelper({
                        check(Thread.currentThread() !== caller)
                        backend {
                            check(Thread.currentThread() !== caller)
                            FileResult.Success(InputStreamProvider { input })
                        }
                    }, io)
                assertTrue(runBlocking { helper.read(ID) } is FileResult.Success)
                assertTrue(closed)
                val cancelled = DocxPreviewHelper({ backend { throw CancellationException("cancel") } }, io)
                assertThrows(CancellationException::class.java) { runBlocking { cancelled.read(ID) } }
            }
        }
    }

    private fun backend(open: () -> FileResult<InputStreamProvider>): StorageBackend =
        object : StorageBackend by InMemoryBackend() {
            override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> = open()
        }

    private fun parse(
        bytes: ByteArray,
        limits: DocxPreviewLimits = DocxPreviewLimits(),
    ): DocxDocument = readDocx(ByteArrayInputStream(bytes), EmptyCoroutineContext, limits)

    private fun body(content: String): String =
        "<w:document xmlns:w=\"$WORD_NS\"><w:body>$content</w:body></w:document>"

    private fun zip(vararg parts: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { archive ->
            parts.forEach { (name, value) ->
                archive.putNextEntry(ZipEntry(name))
                archive.write(value.toByteArray(Charsets.UTF_8))
                archive.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private companion object {
        const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val ID = FileNodeId("file:/document.docx")
    }
}
