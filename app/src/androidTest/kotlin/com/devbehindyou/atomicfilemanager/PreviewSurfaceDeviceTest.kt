package com.devbehindyou.atomicfilemanager

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.data.preview.FolderThumbnailReader
import com.devbehindyou.atomicfilemanager.data.preview.buildThumbnailRequest
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailSourceOf
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewPane
import com.devbehindyou.atomicfilemanager.ui.components.FileThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Generated app-private fixtures only; no shared-storage documents are changed. */
@RunWith(AndroidJUnit4::class)
class PreviewSurfaceDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nativeImageAndFolderMiniatureActuallyDisplayDecodedPixels() =
        runBlocking<Unit> {
            val context = compose.activity.applicationContext
            val directory =
                Files.createTempDirectory(
                    context.cacheDir.toPath(),
                    "thumbnail-surface-",
                ).toFile().canonicalFile
            val source = File(directory, "green.png")
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            try {
                source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                val backend = FileSystemBackend(context)
                val image = (backend.getNode(FileNodeId.file(source.absolutePath)) as FileResult.Success).value
                val folder = (backend.getNode(FileNodeId.file(directory.absolutePath)) as FileResult.Success).value
                assertTrue(
                    "Generated canonical directory rejected by folder preview: ${directory.absolutePath}",
                    FolderThumbnailReader().read(folder).isNotEmpty(),
                )
                val decoded =
                    SingletonImageLoader.get(context).execute(
                        buildThumbnailRequest(context, checkNotNull(thumbnailSourceOf(image)), 64),
                    )
                assertTrue("Generated PNG decode: ${(decoded as? ErrorResult)?.throwable}", decoded is SuccessResult)
                compose.setContent {
                    AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                        Column {
                            FileThumbnail(image)
                            FileThumbnail(folder)
                        }
                    }
                }
                compose.waitUntil(15_000) { greenPixel("file_thumbnail_${image.id.raw}", 2) }
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithTag("folder_miniature", useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty() &&
                        greenPixel("folder_miniature", 4)
                }
                compose.onNodeWithTag("file_type_icon_${folder.id.raw}", useUnmergedTree = true).assertIsDisplayed()
            } finally {
                bitmap.recycle()
                source.delete()
                directory.delete()
            }
        }

    @Test
    fun docxRoutesToDocumentReaderAndFindNavigatesItsText() =
        runBlocking<Unit> {
            val context = compose.activity.applicationContext
            val source = File.createTempFile("reader-surface-", ".docx", context.cacheDir)
            try {
                ZipOutputStream(source.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("word/document.xml"))
                    zip.write(
                        (
                            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                                "<w:body><w:p><w:r><w:t>First needle</w:t></w:r></w:p>" +
                                "<w:p><w:r><w:t>Second NEEDLE</w:t></w:r></w:p></w:body></w:document>"
                        ).toByteArray(),
                    )
                    zip.closeEntry()
                }
                val node =
                    (
                        FileSystemBackend(
                            context,
                        ).getNode(FileNodeId.file(source.absolutePath)) as FileResult.Success
                    ).value
                compose.setContent {
                    AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                        FilePreviewPane(node, onClose = {})
                    }
                }
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("docx_find").fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(
                    hasSetTextAction() and hasAnyAncestor(hasTestTag("docx_find")),
                ).performTextInput("needle")
                compose.onNodeWithText(context.getString(R.string.docx_match_count, 1, 2)).assertIsDisplayed()
                compose.onNodeWithTag("docx_next").performClick()
                compose.onNodeWithText(context.getString(R.string.docx_match_count, 2, 2)).assertIsDisplayed()
                compose.onNodeWithTag("preview_open_external").assertIsDisplayed()
            } finally {
                try {
                    withContext(Dispatchers.IO) {
                        val recents = (context as AtomicApp).container.recentsRepository
                        recents.remove(FileNodeId.file(source.absolutePath))
                        // Remove only this fixture family's private-cache shortcuts,
                        // including remnants from an earlier interrupted test run.
                        val prefixes =
                            setOf(context.cacheDir.absolutePath, context.cacheDir.canonicalPath)
                                .map { FileNodeId.file(File(it, "reader-surface-").path).raw }
                        recents.observeOpened(100).first().filter { item ->
                            item.name.endsWith(".docx") && prefixes.any { item.id.raw.startsWith(it) }
                        }.forEach { recents.remove(it.id) }
                    }
                } finally {
                    source.delete()
                }
            }
        }

    private fun greenPixel(
        tag: String,
        divisor: Int,
    ): Boolean {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        val color =
            pixels[
                (pixels.width / divisor).coerceAtMost(pixels.width - 1),
                (pixels.height / divisor).coerceAtMost(pixels.height - 1),
            ]
        return color.green > 0.8f && color.red < 0.2f && color.blue < 0.2f
    }
}
