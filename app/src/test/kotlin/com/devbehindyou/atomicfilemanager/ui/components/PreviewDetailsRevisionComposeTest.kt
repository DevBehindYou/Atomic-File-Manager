package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.preview.ApkInfoReader
import com.devbehindyou.atomicfilemanager.data.preview.ExifMetadataReader
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/** File-info details belong to one source revision; a replacement never shows the previous file's facts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewDetailsRevisionComposeTest {
    @get:Rule val compose = createComposeRule()

    @get:Rule val folder = TemporaryFolder()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun replacedPhotoNeverShowsThePreviousCameraWhileItsOwnMetadataLoads() {
        val first = photo("first.jpg", "FirstCam")
        val second = photo("second.jpg", "SecondCam")
        val gate = Gate()
        val reader = ExifMetadataReader(gate)
        val shown = mutableStateOf(first)
        show { ImageMetadataSection(node(shown.value, "image/jpeg"), shown.value, reader) }
        compose.onNodeWithText("FirstCam", substring = true).assertIsDisplayed()

        gate.hold()
        compose.runOnIdle { shown.value = second }
        compose.waitForIdle()

        compose.onAllNodesWithText("FirstCam", substring = true).assertCountEquals(0)

        gate.release()
        compose.waitForIdle()
        compose.onNodeWithText("SecondCam", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("FirstCam", substring = true).assertCountEquals(0)
    }

    @Test
    fun rewrittenPhotoAtTheSamePathReadsItsMetadataAgain() {
        val file = photo("same.jpg", "BeforeCam")
        val reader = ExifMetadataReader(Gate())
        val revision = mutableStateOf(node(file, "image/jpeg"))
        show { ImageMetadataSection(revision.value, file, reader) }
        compose.onNodeWithText("BeforeCam", substring = true).assertIsDisplayed()

        photo("same.jpg", "AfterCam")
        compose.runOnIdle { revision.value = revision.value.copy(modifiedAt = revision.value.modifiedAt + 1) }
        compose.waitForIdle()

        compose.onNodeWithText("AfterCam", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("BeforeCam", substring = true).assertCountEquals(0)
    }

    @Test
    fun replacedApkStartsFromReadingInsteadOfThePreviousVerdict() {
        val first = folder.newFile("first.apk").apply { writeText("not an app") }
        val second = folder.newFile("second.apk").apply { writeText("still not an app") }
        val gate = Gate()
        val reader = ApkInfoReader(app, gate)
        val shown = mutableStateOf(first)
        show { ApkSummary(node(shown.value, "application/vnd.android.package-archive"), reader) }
        compose.onNodeWithText(CANT_READ, substring = true).assertIsDisplayed()

        gate.hold()
        compose.runOnIdle { shown.value = second }
        compose.waitForIdle()

        compose.onNodeWithText("Reading app details", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText(CANT_READ, substring = true).assertCountEquals(0)

        gate.release()
        compose.waitForIdle()
        compose.onNodeWithText(CANT_READ, substring = true).assertIsDisplayed()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) { content() }
        }
        compose.waitForIdle()
    }

    private fun photo(
        name: String,
        camera: String,
    ): File {
        val file = File(folder.root, name)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_MAKE, camera)
            saveAttributes()
        }
        return file
    }

    private fun node(
        file: File,
        mime: String,
    ): FileNode =
        FileNode(
            FileNodeId.file(file.absolutePath), file.name, file.name, mime, file.length(), 1_000, false, false,
            null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )

    /** Runs IO inline on the test thread, or holds it until [release] to observe the loading frame. */
    private class Gate : CoroutineDispatcher() {
        private val held = ConcurrentLinkedQueue<Runnable>()

        @Volatile private var open = true

        fun hold() {
            open = false
        }

        fun release() {
            open = true
            while (true) held.poll()?.run() ?: break
        }

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            if (open) block.run() else held += block
        }
    }

    private companion object {
        const val CANT_READ = "can't be read as an app"
    }
}
