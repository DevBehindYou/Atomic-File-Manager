package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.app.Application
import android.content.ContextWrapper
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.DefaultAppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.preview.MediaMetadata
import com.devbehindyou.atomicfilemanager.data.preview.MediaPreviewHelper
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Preparation and ownership consumers; player creation does not prove decoded video playback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
class VideoPreparationRecoveryComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val render = mutableStateOf(true)
    private val ownedCache by lazy {
        Files.createTempDirectory(app.cacheDir.toPath(), "video-preparation-consumer-").toFile()
    }
    private val source =
        FileNode(
            FileNodeId.ftp("fixture", "/clip.mp4"), "clip.mp4", "clip.mp4", "video/mp4", 8, 0, false, false,
            null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )

    @After
    fun disposeThePreviewAndRemoveOnlyItsOwnedCacheDirectory() {
        compose.runOnIdle { render.value = false }
        awaitCondition { stagedFiles().isEmpty() }
        assertTrue("The uniquely owned fixture directory must be empty", ownedCache.delete())
    }

    @Test
    fun unavailableSourceWaitsForExplicitRetryThenOwnsAndReleasesItsStagedCopy() {
        val available = AtomicBoolean()
        val opens = AtomicInteger()
        val metadataReads = AtomicInteger()
        // These bytes exercise staging only. Metadata is supplied through the existing helper seam.
        val payload = ByteArray(8) { (it + 1).toByte() }
        val container =
            container(metadataReads) {
                opens.incrementAndGet()
                if (available.get()) {
                    FileResult.Success(InputStreamProvider { ByteArrayInputStream(payload) })
                } else {
                    FileResult.Failure(FileError.ProviderUnavailable("fixture"))
                }
            }
        show(container)
        awaitCondition { opens.get() == 1 && stagedFiles().isEmpty() }
        val retry = retryAfterPreparationFailure()
        compose.runOnIdle {
            assertEquals("Failed preparation must not retry automatically", 1, opens.get())
            available.set(true)
        }
        retry.performClick()
        awaitCondition { metadataReads.get() == 1 && stagedFiles().singleOrNull()?.length() == payload.size.toLong() }
        assertArrayEquals(payload, stagedFiles().single().readBytes())
        assertEquals(2, opens.get())
        retry.assertDoesNotExist()
        compose.runOnIdle { render.value = false }
        awaitCondition { stagedFiles().isEmpty() }
    }

    @Test
    fun repeatedPreparationFailuresEachRequireAnotherExplicitRetry() {
        val opens = AtomicInteger()
        val metadataReads = AtomicInteger()
        val container =
            container(metadataReads) {
                opens.incrementAndGet()
                FileResult.Failure(FileError.ProviderUnavailable("fixture"))
            }
        show(container)
        awaitCondition { opens.get() == 1 && stagedFiles().isEmpty() }
        retryAfterPreparationFailure().performClick()
        awaitCondition { opens.get() == 2 && stagedFiles().isEmpty() }
        retryAfterPreparationFailure().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("A repeated failure must remain idle", 2, opens.get())
            assertEquals("Failed acquisition must never reach metadata or player preparation", 0, metadataReads.get())
        }
    }

    @Test
    fun disposalCancelsPendingRemotePreparationAndRemovesItsPartialStagingFile() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val metadataReads = AtomicInteger()
        val container =
            container(metadataReads) {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        show(container)
        awaitCondition { entered.isCompleted }
        assertEquals("A pending copy owns exactly one staging file", 1, stagedFiles().size)
        compose.runOnIdle { render.value = false }
        awaitCondition { cancelled.isCompleted && stagedFiles().isEmpty() }
        assertEquals(0, metadataReads.get())
    }

    private fun retryAfterPreparationFailure() =
        compose.onNodeWithContentDescription(app.getString(R.string.action_retry)).also {
            awaitCondition {
                compose.onAllNodesWithContentDescription(app.getString(R.string.action_retry))
                    .fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Failed to load video file").fetchSemanticsNodes().isNotEmpty()
            }
            it.assertIsDisplayed()
        }

    private fun show(container: AppContainer) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (render.value) VideoPreviewContent(source, container)
            }
        }
    }

    private fun container(
        metadataReads: AtomicInteger,
        open: suspend (FileNodeId) -> FileResult<InputStreamProvider>,
    ): AppContainer {
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> = open(id)
            }
        val context =
            object : ContextWrapper(app) {
                override fun getCacheDir() = ownedCache
            }
        return object : AppContainer by DefaultAppContainer(app) {
            override val mediaPreviewHelper =
                MediaPreviewHelper(context, metadataReader = {
                    metadataReads.incrementAndGet()
                    MediaMetadata(null, null, null, 0, 0, 0, null, null)
                }) { backend }
        }
    }

    private fun stagedFiles(): List<File> = ownedCache.listFiles().orEmpty().toList()

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
