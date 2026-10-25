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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.StorageBackendSelector
import com.devbehindyou.atomicfilemanager.data.preview.OwnedExportFileProviderShadow
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleKey
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleScope
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
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

/** Selected unknown content can identify a reader without weakening metadata or explicit rules. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    qualifiers = "w411dp-h891dp",
    application = AtomicApp::class,
    shadows = [OwnedExportFileProviderShadow::class],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningUnknownContentComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: AtomicApp get() = ApplicationProvider.getApplicationContext()
    private val runId = UUID.randomUUID().toString()
    private val sourceId = FileNodeId.ftp("opening-detection", "/$runId/opaque")
    private val render = mutableStateOf(true)
    private val closed = AtomicInteger()
    private val requests = ConcurrentLinkedQueue<FileNodeId>()
    private val streams = ConcurrentLinkedQueue<TrackedInput>()
    private val settings = SandboxSettings()
    private val containerField = AtomicApp::class.java.getDeclaredField("container").apply { isAccessible = true }
    private lateinit var originalContainer: AppContainer
    private lateinit var sourceFile: File
    private lateinit var sentinel: File
    private lateinit var baselineExports: Set<File>
    private lateinit var payload: ByteArray
    private var shown = false

    @Before
    fun installOnlyOneOwnedSourceAndAPngOnlyReader() {
        originalContainer = app.container
        payload = png()
        sourceFile = File.createTempFile("opening-detection-source-", ".png", app.cacheDir)
        sourceFile.writeBytes(payload)
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                    assertEquals("Only this run's source may be opened", sourceId, id)
                    requests += id
                    return FileResult.Success(
                        InputStreamProvider {
                            TrackedInput(sourceFile.inputStream()).also { streams += it }
                        },
                    )
                }
            }
        val controlled =
            object : AppContainer by originalContainer {
                override val settingsRepository: SettingsRepository = settings
                override val storageBackendSelector = StorageBackendSelector(mapOf(BackendType.FTP to backend))
                override val recentsRepository = NoOpRecents
            }
        containerField.set(app, controlled)
        val directory = File(app.cacheDir, "external-preview")
        check(directory.mkdirs() || directory.isDirectory)
        sentinel = File.createTempFile("opening-detection-control-", ".bin", directory)
        sentinel.writeBytes(byteArrayOf(29, 31))
        baselineExports = exports()
        registerReader(PNG_COMPONENT, PNG_READER, PNG_MIME)
    }

    @After
    fun disposeAndRemoveOnlyTheCapturedOwnedFixtures() {
        try {
            if (shown) {
                compose.runOnIdle { render.value = false }
                compose.waitForIdle()
            }
            awaitCondition { streams.all { it.closed.get() } }
            val created = exports() - baselineExports
            assertTrue("Only the one selected handoff may create an export", created.size <= 1)
            created.forEach { check(it.delete()) }
            assertEquals(baselineExports, exports())
            assertArrayEquals(byteArrayOf(29, 31), sentinel.readBytes())
            assertArrayEquals("Opening must preserve its private source", payload, sourceFile.readBytes())
        } finally {
            shadowOf(app).checkActivities(false)
            containerField.set(app, originalContainer)
            check(sentinel.delete() || !sentinel.exists())
            check(sourceFile.delete() || !sourceFile.exists())
        }
    }

    @Test
    fun selectedOpaquePngDiscoversItsReaderAndHandsOffTheExactBytes() {
        show(node())
        awaitReader(PNG_READER)
        assertTrue(
            "Discovery must read and close the selected source",
            streams.isNotEmpty() && streams.all { it.closed.get() },
        )
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertPreferencesUnchanged()
        openAndAssert(PNG_READER, PNG_COMPONENT, PNG_MIME)
    }

    @Test
    fun unrecognizedContentKeepsTheGenericReaderAndDoesNotInventAPngType() {
        payload = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        sourceFile.writeBytes(payload)
        registerReader(BINARY_COMPONENT, BINARY_READER, BINARY_MIME)
        show(node())
        awaitReader(BINARY_READER)
        assertTrue(compose.onAllNodesWithContentDescription(PNG_READER).fetchSemanticsNodes().isEmpty())
        assertTrue(streams.all { it.closed.get() })
        assertNull(shadowOf(app).peekNextStartedActivity())
        openAndAssert(BINARY_READER, BINARY_COMPONENT, BINARY_MIME)
    }

    @Test
    fun validExplicitMimeRemainsAuthoritativeWithoutAContentProbe() {
        registerReader(PDF_COMPONENT, PDF_READER, PDF_MIME)
        // The intentionally conflicting PNG payload must not override trusted explicit metadata.
        show(node(PDF_MIME))
        awaitReader(PDF_READER)
        assertTrue(compose.onAllNodesWithContentDescription(PNG_READER).fetchSemanticsNodes().isEmpty())
        assertTrue("Known metadata discovery must not read the selected file", requests.isEmpty())
        assertTrue(streams.isEmpty())
        openAndAssert(PDF_READER, PDF_COMPONENT, PDF_MIME)
    }

    @Test
    fun explicitExtensionAskRuleWinsOverMimeAndGlobalAutomaticTargets() {
        registerReader(PDF_COMPONENT, PDF_READER, PDF_MIME)
        settings.seedOpening(
            FileOpeningPreferences(defaultMode = FileOpenMode.BUILT_IN).withRule(
                FileOpenRuleKey(FileOpenRuleScope.MIME, PDF_MIME),
                FileOpenTarget(FileOpenMode.EXTERNAL, PDF_COMPONENT.flattenToString(), PDF_READER),
            ).withRule(
                FileOpenRuleKey(FileOpenRuleScope.EXTENSION, UNKNOWN_EXTENSION),
                FileOpenTarget(FileOpenMode.ASK),
            ),
        )
        show(node(PDF_MIME))
        awaitReader(PDF_READER)
        compose.onNodeWithTag("file_opening_choices").assertIsDisplayed()
        assertNull("An explicit Ask rule must prevent automatic launch", shadowOf(app).peekNextStartedActivity())
        assertTrue(requests.isEmpty())
        assertEquals(0, closed.get())
        assertEquals(baselineExports, exports())
        assertPreferencesUnchanged()
    }

    @Test
    fun identifyActionCanDisambiguateAnOpaquePngWithoutReadingTheGenericAskFastPath() {
        registerReader(BINARY_COMPONENT, BINARY_READER, BINARY_MIME)
        show(node())
        awaitReader(BINARY_READER)
        compose.onNodeWithContentDescription(BINARY_READER).performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodesWithContentDescription(PNG_READER).fetchSemanticsNodes().isEmpty())
        assertTrue("Compatible generic Ask choices must not probe automatically", requests.isEmpty())
        assertTrue(streams.isEmpty())
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertEquals(0, closed.get())
        assertEquals(baselineExports, exports())
        assertPreferencesUnchanged()
        compose.onNodeWithContentDescription("Identify file type").performScrollTo().performClick()
        awaitReader(PNG_READER)
        assertTrue(
            "The explicit identification action must read and close its selected source",
            streams.isNotEmpty() && streams.all { it.closed.get() },
        )
        assertTrue(compose.onAllNodesWithContentDescription(BINARY_READER).fetchSemanticsNodes().isEmpty())
        assertNull(
            "Manual identification must not automatically launch a reader",
            shadowOf(app).peekNextStartedActivity(),
        )
        assertEquals(0, closed.get())
        assertEquals(baselineExports, exports())
        assertPreferencesUnchanged()
        openAndAssert(PNG_READER, PNG_COMPONENT, PNG_MIME)
    }

    private fun show(selected: FileNode) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (render.value) FilePreviewPane(selected, onClose = { closed.incrementAndGet() })
            }
        }
        shown = true
        shadowOf(app).checkActivities(true)
    }

    private fun awaitReader(label: String) =
        awaitCondition {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }

    private fun openAndAssert(
        label: String,
        component: ComponentName,
        mime: String,
    ) {
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        awaitCondition { shadowOf(app).peekNextStartedActivity() != null && closed.get() == 1 }
        val view = checkNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(component, view.component)
        assertEquals(mime, view.type)
        assertEquals(node().name, view.clipData!!.description.label.toString())
        assertEquals(view.data, view.clipData!!.getItemAt(0).uri)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        assertArrayEquals(payload, (exports() - baselineExports).single().readBytes())
        checkNotNull(app.contentResolver.openInputStream(checkNotNull(view.data))).use {
            assertArrayEquals(payload, it.readBytes())
        }
        assertTrue(requests.isNotEmpty() && requests.all { it == sourceId })
        assertTrue(streams.isNotEmpty() && streams.all { it.closed.get() })
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertPreferencesUnchanged()
    }

    private fun assertPreferencesUnchanged() {
        assertEquals(0, settings.updateCalls.get())
        assertEquals(settings.initial, settings.settings.value)
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun exports() = File(app.cacheDir, "external-preview").listFiles().orEmpty().toSet()

    private fun node(mime: String = BINARY_MIME) =
        FileNode(
            sourceId, "opaque-$runId.$UNKNOWN_EXTENSION", "opaque-$runId.$UNKNOWN_EXTENSION", mime,
            payload.size.toLong(), 1000, false, false, null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )

    private fun registerReader(
        component: ComponentName,
        label: String,
        mime: String,
    ) {
        val manager = shadowOf(app.packageManager)
        manager.addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = component.packageName
                name = component.className
                nonLocalizedLabel = label
                enabled = true
                exported = true
                applicationInfo =
                    ApplicationInfo().apply {
                        packageName = component.packageName
                        enabled = true
                    }
            },
        )
        manager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("content")
                addDataType(mime)
            },
        )
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.GREEN)
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

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
                fileOpening = FileOpeningPreferences(defaultMode = FileOpenMode.ASK),
                themeMode = ThemeMode.DARK,
                showHiddenFiles = false,
            )
            private set
        private val mutable = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = mutable
        val updateCalls = AtomicInteger()

        fun seedOpening(opening: FileOpeningPreferences) {
            initial = initial.copy(fileOpening = opening)
            mutable.value = initial
        }

        override fun update(transform: (AppSettings) -> AppSettings) {
            updateCalls.incrementAndGet()
            mutable.value = transform(mutable.value)
        }
    }

    private object NoOpRecents : RecentsRepository {
        override fun observeOpened(limit: Int): Flow<List<RecentItem>> = flowOf(emptyList())

        override suspend fun recordOpened(node: FileNode) = Unit

        override suspend fun remove(id: FileNodeId) = Unit

        override suspend fun recentMedia(
            sinceMillis: Long,
            limit: Int,
        ): List<RecentItem> = emptyList()
    }

    private companion object {
        const val UNKNOWN_EXTENSION = "atomic_unknown"
        const val BINARY_MIME = "application/octet-stream"
        const val PNG_MIME = "image/png"
        const val PDF_MIME = "application/pdf"
        const val PNG_READER = "Selected PNG reader"
        const val BINARY_READER = "Generic binary reader"
        const val PDF_READER = "Metadata PDF reader"
        val PNG_COMPONENT = ComponentName("detect.png", "detect.png.View")
        val BINARY_COMPONENT = ComponentName("detect.binary", "detect.binary.View")
        val PDF_COMPONENT = ComponentName("detect.pdf", "detect.pdf.View")
    }
}
