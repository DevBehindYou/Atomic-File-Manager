package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.StorageBackendSelector
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.OwnedExportFileProviderShadow
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpeningPreferences
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.RecentItem
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.RecentsRepository
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Session MIME hints may drive rendering/handoff but must not replace authoritative Recent metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    qualifiers = "w411dp-h891dp",
    application = AtomicApp::class,
    shadows = [OwnedExportFileProviderShadow::class],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningInferredTypeRecentsComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: AtomicApp get() = ApplicationProvider.getApplicationContext()
    private val runId = UUID.randomUUID().toString()
    private val firstId = FileNodeId.ftp("opening-recents", "/$runId/original")
    private val nextId = FileNodeId.ftp("opening-recents", "/$runId/selected")
    private val original = node(firstId, "opaque-$runId.atomic_unknown", "application/octet-stream")
    private val second = node(nextId, "selected-$runId.png", "image/png")
    private val current = mutableStateOf(original)
    private val render = mutableStateOf(true)
    private val settings = SandboxSettings()
    private val recents = RecordingRecents()
    private val streams = ConcurrentLinkedQueue<TrackedInput>()
    private val closed = AtomicInteger()
    private val sourceFiles = linkedMapOf<FileNodeId, File>()
    private val payloads = linkedMapOf<FileNodeId, ByteArray>()
    private val containerField = AtomicApp::class.java.getDeclaredField("container").apply { isAccessible = true }
    private lateinit var originalContainer: AppContainer
    private lateinit var sentinel: File
    private lateinit var baselineExports: Set<File>
    private var shown = false

    @Before
    fun installOnlyOwnedImageSourcesAndRecordingRecents() {
        originalContainer = app.container
        payloads[firstId] = png(Color.GREEN)
        payloads[nextId] = png(Color.BLUE)
        payloads.forEach { (id, bytes) ->
            sourceFiles[id] =
                File.createTempFile("opening-recents-$runId-", ".png", app.cacheDir).apply {
                    writeBytes(bytes)
                }
        }
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override val type = BackendType.FILE

                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                    val source = checkNotNull(sourceFiles[id]) { "Only this run's source identities may be read" }
                    return FileResult.Success(
                        InputStreamProvider {
                            TrackedInput(source.inputStream()).also { streams += it }
                        },
                    )
                }
            }
        val controlled =
            object : AppContainer by originalContainer {
                override val settingsRepository: SettingsRepository = settings
                override val recentsRepository: RecentsRepository = recents
                override val storageBackendSelector = StorageBackendSelector(mapOf(BackendType.FTP to backend))
                override val imagePreviewHelper = ImagePreviewHelper { backend }
            }
        containerField.set(app, controlled)
        val directory = File(app.cacheDir, "external-preview")
        check(directory.mkdirs() || directory.isDirectory)
        sentinel = File.createTempFile("opening-recents-control-", ".bin", directory)
        sentinel.writeBytes(byteArrayOf(29, 31))
        baselineExports = exports()
        registerPngReader()
    }

    @After
    fun disposeAndRemoveOnlyTheCapturedFixtures() {
        try {
            if (shown) {
                compose.runOnIdle { render.value = false }
                compose.waitForIdle()
            }
            awaitCondition { streams.all { it.closed.get() } }
            val created = exports() - baselineExports
            assertTrue("Only this run's optional single handoff may create an export", created.size <= 1)
            created.forEach { check(it.delete()) }
            assertEquals(baselineExports, exports())
            assertArrayEquals(byteArrayOf(29, 31), sentinel.readBytes())
            sourceFiles.forEach { (id, source) -> assertArrayEquals(payloads.getValue(id), source.readBytes()) }
            assertEquals(0, settings.updateCalls.get())
            assertEquals(settings.initial, settings.settings.value)
        } finally {
            shadowOf(app).checkActivities(false)
            containerField.set(app, originalContainer)
            check(sentinel.delete() || !sentinel.exists())
            sourceFiles.values.forEach { source -> check(source.delete() || !source.exists()) }
        }
    }

    @Test
    fun decodedOpaquePngKeepsAuthoritativeRawMetadataInTheRecentsBoundary() {
        show(original, FileOpenMode.BUILT_IN)
        awaitImage(original)
        awaitCondition { recents.opened.any { it.id == firstId } }
        assertRecordedExactly(original)
        assertTrue("Rendering must decode the controlled image", streams.isNotEmpty())
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertEquals(baselineExports, exports())
        assertEquals(0, closed.get())
    }

    @Test
    fun explicitRecognizedPngHandoffUsesPngMimeButRecordsTheRawSource() {
        show(original, FileOpenMode.ASK)
        awaitCondition { compose.onAllNodesWithContentDescription(READER).fetchSemanticsNodes().isNotEmpty() }
        assertNull(shadowOf(app).peekNextStartedActivity())
        compose.onNodeWithContentDescription(READER).performScrollTo().performClick()
        awaitCondition { shadowOf(app).peekNextStartedActivity() != null && closed.get() == 1 }
        val view = checkNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(COMPONENT, view.component)
        assertEquals("image/png", view.type)
        assertEquals(original.name, view.clipData!!.description.label.toString())
        assertEquals(view.data, view.clipData!!.getItemAt(0).uri)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        assertArrayEquals(payloads.getValue(firstId), (exports() - baselineExports).single().readBytes())
        checkNotNull(app.contentResolver.openInputStream(checkNotNull(view.data))).use {
            assertArrayEquals(payloads.getValue(firstId), it.readBytes())
        }
        awaitCondition { recents.opened.any { it.id == firstId } }
        assertRecordedExactly(original)
        assertNull(shadowOf(app).peekNextStartedActivity())
    }

    @Test
    fun selectedGalleryImageRetainsItsOwnMetadataInsteadOfTheOriginalParentRecord() {
        val known = original.copy(name = "known-$runId.png", displayName = "known-$runId.png", mimeType = "image/png")
        show(known, FileOpenMode.BUILT_IN, gallery = listOf(known, second))
        awaitImage(known)
        compose.onNodeWithTag("image_gallery").performTouchInput { swipeLeft() }
        awaitCondition {
            compose.onAllNodesWithContentDescription(second.name).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText(second.name).fetchSemanticsNodes().isNotEmpty() &&
                recents.opened.any { it.id == nextId }
        }
        compose.runOnIdle { current.value = known.copy(name = "refreshed-$runId.png", modifiedAt = 2000) }
        compose.waitForIdle()
        compose.onNodeWithText(second.name).assertIsDisplayed()
        assertRecordedExactly(known)
        assertRecordedExactly(second)
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertEquals(baselineExports, exports())
        assertEquals(0, closed.get())
    }

    private fun assertRecordedExactly(expected: FileNode) {
        val matching = recents.opened.filter { it.id == expected.id }
        assertTrue("The real pane must call the recording repository for ${expected.name}", matching.isNotEmpty())
        matching.forEach {
                actual ->
            assertEquals("Recent must retain authoritative source metadata", expected, actual)
        }
        assertTrue(recents.opened.all { it.id == firstId || it.id == nextId })
    }

    private fun show(
        source: FileNode,
        mode: FileOpenMode,
        gallery: List<FileNode> = emptyList(),
    ) {
        settings.seed(mode)
        current.value = source
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (render.value) {
                    FilePreviewPane(
                        current.value,
                        onClose = { closed.incrementAndGet() },
                        gallery = gallery,
                    )
                }
            }
        }
        shown = true
        shadowOf(app).checkActivities(true)
    }

    private fun awaitImage(source: FileNode) =
        awaitCondition {
            compose.onAllNodesWithContentDescription(source.name).fetchSemanticsNodes().isNotEmpty()
        }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun exports() = File(app.cacheDir, "external-preview").listFiles().orEmpty().toSet()

    private fun registerPngReader() {
        val manager = shadowOf(app.packageManager)
        manager.addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = COMPONENT.packageName
                name = COMPONENT.className
                nonLocalizedLabel = READER
                enabled = true
                exported = true
                applicationInfo =
                    ApplicationInfo().apply {
                        packageName = COMPONENT.packageName
                        enabled = true
                    }
            },
        )
        manager.addIntentFilterForActivity(
            COMPONENT,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("content")
                addDataType("image/png")
            },
        )
    }

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun node(
        id: FileNodeId,
        name: String,
        mime: String,
    ) = FileNode(
        id, name, name, mime, -1, 1000, false, false, null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
    )

    private class TrackedInput(input: InputStream) : FilterInputStream(input) {
        val closed = AtomicBoolean()

        override fun close() {
            try {
                super.close()
            } finally {
                closed.set(true)
            }
        }
    }

    private class SandboxSettings : SettingsRepository {
        var initial =
            AppSettings(
                fileOpening = FileOpeningPreferences(defaultMode = FileOpenMode.BUILT_IN),
                themeMode = ThemeMode.DARK,
            )
            private set
        private val mutable = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = mutable
        val updateCalls = AtomicInteger()

        fun seed(mode: FileOpenMode) {
            initial = initial.copy(fileOpening = FileOpeningPreferences(defaultMode = mode))
            mutable.value = initial
        }

        override fun update(transform: (AppSettings) -> AppSettings) {
            updateCalls.incrementAndGet()
            mutable.value = transform(mutable.value)
        }
    }

    private class RecordingRecents : RecentsRepository {
        val opened = ConcurrentLinkedQueue<FileNode>()

        override fun observeOpened(limit: Int): Flow<List<RecentItem>> = flowOf(emptyList())

        override suspend fun recordOpened(node: FileNode) {
            opened += node
        }

        override suspend fun remove(id: FileNodeId) = Unit

        override suspend fun recentMedia(
            sinceMillis: Long,
            limit: Int,
        ): List<RecentItem> = emptyList()
    }

    private companion object {
        const val READER = "Authoritative recents PNG reader"
        val COMPONENT = ComponentName("recents.png", "recents.png.View")
    }
}
