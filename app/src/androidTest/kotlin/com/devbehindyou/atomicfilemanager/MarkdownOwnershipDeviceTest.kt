package com.devbehindyou.atomicfilemanager

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.ReadFileContentUseCase
import com.devbehindyou.atomicfilemanager.ui.components.preview.MarkdownPreviewContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Actual reader and native viewer; only captured, uniquely owned cache fixtures may change. */
@RunWith(AndroidJUnit4::class)
class MarkdownOwnershipDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun retryRevisionAndRawDisclosurePreserveOwnedSourceAndSentinel() =
        runBlocking<Unit> {
            val context = compose.activity.applicationContext
            val directory = Files.createTempDirectory(context.cacheDir.toPath(), "markdown-ownership-").toFile()
            val source = File(directory, "preview.md")
            val sentinel = File(directory, "sentinel.bin")
            val sentinelBytes = byteArrayOf(7, 0, -1, 42)
            val unavailable = AtomicBoolean(true)
            val reads = AtomicInteger()
            val shown = mutableStateOf(true)
            var mounted = false
            try {
                val original = "# Native original".toByteArray()
                source.writeBytes(original)
                sentinel.writeBytes(sentinelBytes)
                val backend = FileSystemBackend(context)
                val initial = (backend.getNode(FileNodeId.file(source.absolutePath)) as FileResult.Success).value
                val selected = mutableStateOf(initial)
                val controlled =
                    object : StorageBackend by backend {
                        override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                            reads.incrementAndGet()
                            return if (unavailable.get()) {
                                FileResult.Failure(FileError.ProviderUnavailable("owned fixture"))
                            } else {
                                backend.openInput(id)
                            }
                        }
                    }
                val container =
                    object : AppContainer by DefaultAppContainer(context) {
                        override val readFileContentUseCase = ReadFileContentUseCase { controlled }
                    }
                compose.setContent {
                    AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                        if (shown.value) MarkdownPreviewContent(selected.value, container)
                    }
                }
                mounted = true
                awaitText(context.getString(R.string.markdown_preview_failed))
                unavailable.set(false)
                compose.waitForIdle()
                assertEquals("No automatic retry", 1, reads.get())
                compose.onNodeWithContentDescription(context.getString(R.string.action_retry)).performClick()
                awaitText("Native original")
                assertEquals(2, reads.get())
                assertArrayEquals(original, source.readBytes())
                compose.onNodeWithText("Raw").performClick()
                awaitText("# Native original")

                val revised = "# Native revised".toByteArray()
                source.writeBytes(revised)
                compose.runOnIdle {
                    selected.value = initial.copy(modifiedAt = initial.modifiedAt + 1, size = revised.size.toLong())
                }
                awaitText("Native revised")
                compose.onNodeWithText("# Native original").assertDoesNotExist()
                assertEquals(3, reads.get())
                assertArrayEquals(revised, source.readBytes())

                val bounded = "\n".repeat(5_001).toByteArray()
                source.writeBytes(bounded)
                compose.runOnIdle {
                    selected.value = initial.copy(modifiedAt = initial.modifiedAt + 2, size = bounded.size.toLong())
                }
                val disclosure = context.getString(R.string.markdown_preview_truncated)
                awaitText(disclosure)
                compose.onNodeWithText("Raw").performClick()
                compose.onNodeWithText(disclosure).assertIsDisplayed()
                assertEquals(4, reads.get())
                assertArrayEquals("The viewer must not rewrite its source", bounded, source.readBytes())
                assertArrayEquals("Unrelated private file", sentinelBytes, sentinel.readBytes())
                assertEquals(setOf(source.name, sentinel.name), directory.listFiles().orEmpty().map { it.name }.toSet())
            } finally {
                if (mounted) {
                    compose.runOnIdle { shown.value = false }
                    compose.waitForIdle()
                }
                if (source.exists()) assertTrue(source.delete())
                if (sentinel.exists()) assertTrue(sentinel.delete())
                assertTrue("Unexpected fixture contents must be retained", directory.delete())
            }
        }

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
