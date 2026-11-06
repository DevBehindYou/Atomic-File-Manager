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
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.DefaultAppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.ReadFileContentUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Actual viewer and bounded text reader; no engine seam or network/asset fetching. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
class MarkdownPreparationRecoveryComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val visible = mutableStateOf(true)
    private val source =
        FileNode(
            FileNodeId.file("/markdown-consumer/first.md"), "first.md", "first.md", "text/markdown",
            20, 0, false, false, null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )
    private val shown = mutableStateOf(source)
    private val selectedContainer = mutableStateOf<AppContainer?>(null)

    @After
    fun disposeTheOwnedViewer() {
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
    }

    @Test
    fun aFailureDoesNotObscureTheReplacementDocumentsContent() {
        val replacement = source.copy(id = FileNodeId.file("/markdown-consumer/second.md"))
        show(
            container { id ->
                if (id == source.id) {
                    FileResult.Failure(FileError.ProviderUnavailable("fixture"))
                } else {
                    text("# Replacement document")
                }
            },
        )
        awaitText(FAILURE)
        compose.runOnIdle { shown.value = replacement }
        awaitText("Replacement document")
        compose.onNodeWithText(FAILURE).assertDoesNotExist()
    }

    @Test
    fun aRecoverableFailureRequiresAnExplicitRetryAndCanRecoverWithoutChangingIdentity() {
        val available = AtomicBoolean()
        val reads = AtomicInteger()
        show(
            container {
                reads.incrementAndGet()
                if (available.get()) {
                    text("# Recovered document")
                } else {
                    FileResult.Failure(FileError.ProviderUnavailable("fixture"))
                }
            },
        )
        awaitText(FAILURE)
        compose.runOnIdle {
            assertEquals("Failure must not start an automatic retry loop", 1, reads.get())
            available.set(true)
        }
        compose.onNodeWithContentDescription(app.getString(R.string.action_retry)).performClick()
        awaitText("Recovered document")
        assertEquals(2, reads.get())
        compose.onNodeWithText(FAILURE).assertDoesNotExist()
    }

    @Test
    fun aKnownRevisionReloadsAndClosesBothOriginalAndReplacementStreams() {
        val revised = AtomicBoolean()
        val reads = AtomicInteger()
        val closes = AtomicInteger()
        show(
            container {
                reads.incrementAndGet()
                text(if (revised.get()) "# Revised document" else "# Original document", closes)
            },
        )
        awaitText("Original document")
        compose.runOnIdle {
            revised.set(true)
            shown.value = source.copy(modifiedAt = 1, size = 21)
        }
        awaitText("Revised document")
        compose.onNodeWithText("Original document").assertDoesNotExist()
        assertEquals(2, reads.get())
        assertEquals(2, closes.get())
    }

    @Test
    fun replacingTheReaderReloadsTheCurrentSource() {
        val firstReads = AtomicInteger()
        val secondReads = AtomicInteger()
        show(
            container {
                firstReads.incrementAndGet()
                text("# First reader")
            },
        )
        awaitText("First reader")
        compose.runOnIdle {
            selectedContainer.value =
                container {
                    secondReads.incrementAndGet()
                    text("# Second reader")
                }
        }
        awaitText("Second reader")
        compose.onNodeWithText("First reader").assertDoesNotExist()
        assertEquals(1, firstReads.get())
        assertEquals(1, secondReads.get())
    }

    @Test
    fun changingSourceCancelsPendingAcquisitionAndShowsOnlyItsReplacement() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val replacement = source.copy(id = FileNodeId.file("/markdown-consumer/replacement.md"))
        show(
            container { id ->
                if (id == source.id) {
                    entered.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                } else {
                    text("# Latest document")
                }
            },
        )
        awaitCondition { entered.isCompleted }
        compose.runOnIdle { shown.value = replacement }
        awaitText("Latest document")
        awaitCondition { cancelled.isCompleted }
        compose.onNodeWithText(FAILURE).assertDoesNotExist()
    }

    @Test
    fun closingTheViewerCancelsItsPendingAcquisition() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        show(
            container {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            },
        )
        awaitCondition { entered.isCompleted }
        compose.runOnIdle { visible.value = false }
        awaitCondition { cancelled.isCompleted }
    }

    @Test
    fun aBoundedPreviewExplicitlyDisclosesTruncationWithoutReadingTheWholeDocument() {
        val closes = AtomicInteger()
        show(container { text("\n".repeat(5_001), closes) })
        awaitText("Preview limited to the first 5,000 lines or 1 MiB. The full document may contain more content.")
        assertEquals(1, closes.get())
    }

    private fun show(container: AppContainer) {
        selectedContainer.value = container
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (visible.value) MarkdownPreviewContent(shown.value, checkNotNull(selectedContainer.value))
            }
        }
    }

    private fun container(open: suspend (FileNodeId) -> FileResult<InputStreamProvider>): AppContainer {
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> = open(id)
            }
        return object : AppContainer by DefaultAppContainer(app) {
            override val readFileContentUseCase = ReadFileContentUseCase { backend }
        }
    }

    private fun text(
        content: String,
        closes: AtomicInteger = AtomicInteger(),
    ): FileResult<InputStreamProvider> =
        FileResult.Success(
            InputStreamProvider {
                object : ByteArrayInputStream(content.toByteArray()) {
                    override fun close() {
                        closes.incrementAndGet()
                        super.close()
                    }
                }
            },
        )

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

    private companion object {
        const val FAILURE = "Failed to load Markdown document"
    }
}
