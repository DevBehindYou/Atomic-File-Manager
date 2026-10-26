package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.preview.DocxDocument
import com.devbehindyou.atomicfilemanager.data.preview.DocxPreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real bounded DOCX parser plus actual reader UI; no synthetic successful document model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
class DocxSourceSessionComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val node =
        FileNode(
            FileNodeId.ftp("fixture", "/document.docx"), "document.docx", "document.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 64, 0,
            false, false, null, StorageType.VIRTUAL, AccessFlags.READ_ONLY, null, null,
        )
    private val shown = mutableStateOf(node)
    private val visible = mutableStateOf(true)
    private val reader = mutableStateOf<suspend (FileNodeId) -> FileResult<DocxDocument>>({ error("Reader not set") })

    @After
    fun closeComposition() {
        compose.runOnIdle { visible.value = false }
    }

    @Test
    fun modifiedRevisionAtTheSameIdentityPublishesFreshParsedText() = revisedSource(sizeOnly = false)

    @Test
    fun sizeRevisionAtTheSameIdentityPublishesFreshParsedText() = revisedSource(sizeOnly = true)

    @Test
    fun replacingTheReaderAtTheSameIdentityCannotRetainOldParagraphs() {
        val first = AtomicInteger()
        val second = AtomicInteger()
        val closed = AtomicInteger()
        reader.value = helper {
            first.incrementAndGet()
            provider(docx("First helper text"), closed)
        }::read
        show()
        awaitText("First helper text")
        compose.runOnIdle {
            reader.value = helper {
                second.incrementAndGet()
                provider(docx("Second helper text"), closed)
            }::read
        }
        awaitText("Second helper text")
        compose.onNodeWithText("First helper text").assertDoesNotExist()
        assertEquals(1, first.get())
        assertEquals(1, second.get())
        assertEquals(2, closed.get())
    }

    @Test
    fun aPendingNewRevisionCannotDisplayThePreviousText() {
        val reads = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val closed = AtomicInteger()
        reader.value = helper {
            if (reads.incrementAndGet() == 1) {
                provider(docx("Previous revision text"), closed)
            } else {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }::read
        show()
        awaitText("Previous revision text")
        compose.runOnIdle { shown.value = node.copy(modifiedAt = 1) }
        awaitCondition { entered.isCompleted }
        compose.onNodeWithText("Previous revision text").assertDoesNotExist()
        compose.runOnIdle { visible.value = false }
        awaitCondition { cancelled.isCompleted }
        assertEquals(1, closed.get())
    }

    @Test
    fun anOpenFailureRetriesOnlyOnExplicitActionAndThenShowsParsedText() {
        val reads = AtomicInteger()
        val closed = AtomicInteger()
        reader.value = helper {
            if (reads.incrementAndGet() == 1) {
                FileResult.Failure(FileError.ProviderUnavailable("fixture"))
            } else {
                provider(docx("Recovered parsed text"), closed)
            }
        }::read
        show()
        awaitText(app.getString(R.string.docx_unavailable))
        compose.runOnIdle { assertEquals(1, reads.get()) }
        compose.onNodeWithContentDescription(app.getString(R.string.docx_retry)).performClick()
        awaitText("Recovered parsed text")
        compose.onNodeWithText(app.getString(R.string.docx_unavailable)).assertDoesNotExist()
        assertEquals(2, reads.get())
        assertEquals(1, closed.get())
    }

    @Test
    fun closingCancelsBlockedActualHelperAcquisition() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        reader.value = helper {
            entered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }::read
        show()
        awaitCondition { entered.isCompleted }
        compose.runOnIdle { visible.value = false }
        awaitCondition { cancelled.isCompleted }
        compose.onNodeWithText(app.getString(R.string.docx_unavailable)).assertDoesNotExist()
    }

    @Test
    fun switchingSourceCancelsTheOldPendingReadAndPublishesOnlyTheReplacement() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val closed = AtomicInteger()
        reader.value = helper { id ->
            if (id == node.id) {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            } else {
                provider(docx("Replacement source text"), closed)
            }
        }::read
        show()
        awaitCondition { entered.isCompleted }
        compose.runOnIdle { shown.value = node.copy(id = FileNodeId.ftp("fixture", "/replacement.docx")) }
        awaitText("Replacement source text")
        assertTrue(cancelled.isCompleted)
        assertEquals(1, closed.get())
        compose.onNodeWithText(app.getString(R.string.docx_unavailable)).assertDoesNotExist()
    }

    private fun revisedSource(sizeOnly: Boolean) {
        val bytes = AtomicReference(docx("Old revision text"))
        val opened = AtomicInteger()
        val closed = AtomicInteger()
        reader.value = helper {
            opened.incrementAndGet()
            provider(bytes.get(), closed)
        }::read
        show()
        awaitText("Old revision text")
        bytes.set(docx("New revision text"))
        compose.runOnIdle {
            shown.value = if (sizeOnly) node.copy(size = 65) else node.copy(modifiedAt = 1)
        }
        awaitText("New revision text")
        compose.onNodeWithText("Old revision text").assertDoesNotExist()
        assertEquals(2, opened.get())
        assertEquals(2, closed.get())
    }

    private fun show() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (visible.value) DocxPreviewContent(shown.value, reader.value)
            }
        }
    }

    private fun helper(open: suspend (FileNodeId) -> FileResult<InputStreamProvider>): DocxPreviewHelper {
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId) = open(id)
            }
        return DocxPreviewHelper({ backend })
    }

    private fun provider(
        bytes: ByteArray,
        closed: AtomicInteger,
    ): FileResult<InputStreamProvider> =
        FileResult.Success(
            InputStreamProvider {
                object : FilterInputStream(bytes.inputStream()) {
                    override fun close() {
                        super.close()
                        closed.incrementAndGet()
                    }
                }
            },
        )

    private fun docx(text: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(
                    (
                        "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                            "<w:body><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:body></w:document>"
                    ).toByteArray(),
                )
                zip.closeEntry()
            }
            output.toByteArray()
        }

    private fun awaitText(text: String) {
        awaitCondition { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
