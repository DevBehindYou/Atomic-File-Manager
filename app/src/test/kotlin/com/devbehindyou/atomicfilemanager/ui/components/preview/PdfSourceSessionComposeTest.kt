package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.app.Application
import android.content.ContextWrapper
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.DefaultAppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.preview.PdfPreviewHelper
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
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/** Actual PDF helper acquisition/failure ownership; successful native rendering is tested on device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
class PdfSourceSessionComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val render = mutableStateOf(true)
    private val node =
        FileNode(
            FileNodeId.ftp("fixture", "/document.pdf"), "document.pdf", "document.pdf", "application/pdf",
            64, 0, false, false, null, StorageType.VIRTUAL, AccessFlags.READ_ONLY, null, null,
        )
    private val shown = mutableStateOf(node)
    private val owner = mutableStateOf<AppContainer?>(null)
    private val cache by lazy { Files.createTempDirectory(app.cacheDir.toPath(), "pdf-source-host-").toFile() }

    @After
    fun disposeAndRemoveOnlyTheEmptyOwnedCacheDirectory() {
        compose.runOnIdle { render.value = false }
        awaitCondition { cache.listFiles().orEmpty().isEmpty() }
        assertTrue("Owned PDF cache directory must be empty", cache.delete())
    }

    @Test
    fun modifiedRevisionAtTheSameIdentityStartsAnotherOwnedAcquisition() = revisedSource(sizeOnly = false)

    @Test
    fun sizeRevisionAtTheSameIdentityStartsAnotherOwnedAcquisition() = revisedSource(sizeOnly = true)

    @Test
    fun replacingTheHelperAtTheSameIdentityCannotKeepTheOldFailureSession() {
        val first = AtomicInteger()
        val second = AtomicInteger()
        owner.value =
            container {
                first.incrementAndGet()
                unavailable()
            }
        show()
        awaitFailure { first.get() == 1 }
        compose.runOnIdle {
            owner.value =
                container {
                    second.incrementAndGet()
                    unavailable()
                }
        }
        awaitFailure { second.get() == 1 }
        assertEquals(1, first.get())
        assertEquals(1, second.get())
    }

    @Test
    fun repeatedOpenFailuresRemainIdleUntilEachExplicitRetry() {
        val opens = AtomicInteger()
        owner.value =
            container {
                opens.incrementAndGet()
                unavailable()
            }
        show()
        awaitFailure { opens.get() == 1 }
        retry().performClick()
        awaitFailure { opens.get() == 2 }
        retry().assertIsDisplayed()
        compose.runOnIdle { assertEquals("No automatic retry loop", 2, opens.get()) }
    }

    @Test
    fun closingCancelsBlockedAcquisitionAndRemovesItsOwnedPartialCopy() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        owner.value =
            container {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        show()
        awaitCondition { entered.isCompleted && cache.listFiles().orEmpty().size == 1 }
        compose.runOnIdle { render.value = false }
        awaitCondition { cancelled.isCompleted && cache.listFiles().orEmpty().isEmpty() }
        compose.onNodeWithText(app.getString(R.string.pdf_open_error)).assertDoesNotExist()
    }

    private fun revisedSource(sizeOnly: Boolean) {
        val opens = AtomicInteger()
        owner.value =
            container {
                opens.incrementAndGet()
                unavailable()
            }
        show()
        awaitFailure { opens.get() == 1 }
        compose.runOnIdle {
            shown.value = if (sizeOnly) node.copy(size = node.size + 1) else node.copy(modifiedAt = node.modifiedAt + 1)
        }
        awaitFailure { opens.get() == 2 }
        compose.runOnIdle { assertEquals(2, opens.get()) }
    }

    private fun show() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (render.value) PdfPreviewContent(shown.value, checkNotNull(owner.value))
            }
        }
    }

    private fun container(open: suspend () -> FileResult<InputStreamProvider>): AppContainer {
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                    check(id == node.id)
                    return open()
                }
            }
        val context =
            object : ContextWrapper(app) {
                override fun getCacheDir() = cache
            }
        return object : AppContainer by DefaultAppContainer(app) {
            override val pdfPreviewHelper = PdfPreviewHelper(context) { backend }
        }
    }

    private fun unavailable(): FileResult<InputStreamProvider> =
        FileResult.Failure(
            FileError.ProviderUnavailable("fixture"),
        )

    private fun retry() = compose.onNodeWithContentDescription(app.getString(R.string.pdf_retry))

    private fun awaitFailure(entered: () -> Boolean) {
        awaitCondition {
            entered() && cache.listFiles().orEmpty().isEmpty() &&
                compose.onAllNodesWithContentDescription(app.getString(R.string.pdf_retry))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        retry().assertIsDisplayed()
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
