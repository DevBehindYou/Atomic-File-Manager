package com.devbehindyou.atomicfilemanager

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.LineEnding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextCodec
import com.devbehindyou.atomicfilemanager.domain.usecase.TextEncoding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFileEditor
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import com.devbehindyou.atomicfilemanager.ui.components.TextEditorSession
import com.devbehindyou.atomicfilemanager.ui.components.TextEditorSessionDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/** The actual dialog/session/FILE save service; only two uniquely owned private fixtures may change. */
@RunWith(AndroidJUnit4::class)
class EditorHistoryDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unicodeSelectionUndoRedoAndStagedSavePreserveFormatAndUnrelatedFile() =
        runBlocking<Unit> {
            val context = compose.activity.applicationContext
            val directory =
                Files.createTempDirectory(
                    context.cacheDir.toPath(),
                    "editor-history-",
                ).toFile().canonicalFile
            val source = File(directory, "unicode.txt")
            val sentinel = File(directory, "unrelated.bin")
            val original = "a🌍b\nsecond line"
            val inserted = "किरण🙂"
            val edited = "a${inserted}b\nsecond line"
            val format = TextFormat(TextEncoding.UTF8_BOM, LineEnding.CRLF)
            val originalBytes = TextCodec.encode(original, format)
            val sentinelBytes = byteArrayOf(13, 0, 87, -1, 42)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val shown = mutableStateOf(true)
            val saved = AtomicInteger()
            var session: TextEditorSession? = null
            var composed = false
            try {
                source.writeBytes(originalBytes)
                sentinel.writeBytes(sentinelBytes)
                val backend = FileSystemBackend(context)
                val node = (backend.getNode(FileNodeId.file(source.absolutePath)) as FileResult.Success).value
                // No global AppContainer, TrashManager, preferences or Recents are used by this fixture.
                val service = TextFileEditor({ backend })
                val editor = TextEditorSession(node, scope, Dispatchers.IO, service::open, service::save)
                session = editor
                compose.setContent {
                    AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                        if (shown.value) {
                            TextEditorSessionDialog(node, editor, onDismiss = { shown.value = false }, onSaved = {
                                saved.incrementAndGet()
                            })
                        }
                    }
                }
                composed = true
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithTag("text_editor_field").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("text_editor_field").assertTextEquals(original)
                compose.onNodeWithContentDescription(context.getString(R.string.editor_undo)).assertIsNotEnabled()
                compose.onNodeWithContentDescription(context.getString(R.string.editor_redo)).assertIsNotEnabled()
                compose.onNodeWithTag("text_editor_field").performTextInputSelection(TextRange(1, 3))
                compose.onNodeWithTag("text_editor_field").performTextInput(inserted)
                compose.onNodeWithTag("text_editor_field").assertTextEquals(edited)
                compose.onNodeWithTag("text_editor_save").assertIsEnabled()
                assertArrayEquals("An unsaved edit must not write the source", originalBytes, source.readBytes())

                compose.onNodeWithContentDescription(context.getString(R.string.editor_undo)).performClick()
                compose.onNodeWithTag("text_editor_field").assertTextEquals(original)
                compose.runOnIdle { assertEquals(TextRange(1, 3), editor.state.value.value.selection) }
                compose.onNodeWithTag("text_editor_save").assertIsNotEnabled()
                compose.onNodeWithContentDescription(context.getString(R.string.editor_redo)).performClick()
                compose.onNodeWithTag("text_editor_field").assertTextEquals(edited)
                compose.runOnIdle { assertEquals(TextRange(1 + inserted.length), editor.state.value.value.selection) }
                compose.onNodeWithTag("text_editor_save").performClick()
                compose.waitUntil(15_000) { saved.get() == 1 && !editor.state.value.saving }
                compose.onNodeWithTag("text_editor_save").assertIsNotEnabled()
                assertArrayEquals("Saved BOM and CRLF", TextCodec.encode(edited, format), source.readBytes())
                compose.runOnIdle { assertEquals(format, editor.state.value.format) }

                compose.onNodeWithContentDescription(context.getString(R.string.editor_undo)).performClick()
                compose.onNodeWithTag("text_editor_field").assertTextEquals(original)
                compose.onNodeWithTag("text_editor_save").assertIsEnabled()
                assertArrayEquals(
                    "Undo after Save must remain memory-only",
                    TextCodec.encode(edited, format),
                    source.readBytes(),
                )
                compose.onNodeWithContentDescription(context.getString(R.string.editor_redo)).performClick()
                compose.onNodeWithTag("text_editor_save").assertIsNotEnabled()
                compose.runOnIdle { assertFalse(editor.state.value.dirty) }
                assertEquals(1, saved.get())
                assertArrayEquals("Unrelated private file unchanged", sentinelBytes, sentinel.readBytes())
                assertEquals(setOf(source.name, sentinel.name), directory.listFiles().orEmpty().map { it.name }.toSet())
            } finally {
                // Never remove a fixture from under an accepted save that has not completed.
                var saveSettled = false
                try {
                    session?.let { editor -> compose.waitUntil(15_000) { !editor.state.value.saving } }
                    saveSettled = true
                } finally {
                    try {
                        if (composed) {
                            compose.runOnIdle { shown.value = false }
                            compose.waitForIdle()
                        }
                    } finally {
                        try {
                            compose.runOnIdle { session?.close(force = true) }
                        } finally {
                            scope.cancel()
                            // If a save times out, retain its private files rather than racing publication.
                            if (saveSettled) cleanOwnedFixtures(directory, source, sentinel, sentinelBytes)
                        }
                    }
                }
            }
        }

    private fun cleanOwnedFixtures(
        directory: File,
        source: File,
        sentinel: File,
        sentinelBytes: ByteArray,
    ) {
        try {
            if (sentinel.exists()) assertArrayEquals(sentinelBytes, sentinel.readBytes())
        } finally {
            val failures = mutableListOf<String>()
            for (owned in listOf(source, sentinel)) {
                if (!owned.delete() && owned.exists()) failures += "Owned editor fixture cleanup failed"
            }
            // An unexpected staged file remains for diagnosis; do not recursively delete it.
            if (!directory.delete() && directory.exists()) failures += "Owned editor directory was not empty"
            assertTrue(failures.joinToString(), failures.isEmpty())
        }
    }
}
