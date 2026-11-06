package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.webkit.MimeTypeMap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Actual opening sessions must apply detected rules once and reject stale or dismissed work. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    qualifiers = "w411dp-h891dp",
    application = AtomicApp::class,
    shadows = [OwnedExportFileProviderShadow::class],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningSelectedContentPolicyComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: AtomicApp get() = ApplicationProvider.getApplicationContext()
    private val runId = UUID.randomUUID().toString()
    private val firstId = FileNodeId.ftp("selected-policy", "/$runId/a")
    private val middleId = FileNodeId.ftp("selected-policy", "/$runId/b")
    private val latestId = FileNodeId.ftp("selected-policy", "/$runId/c")
    private val source = mutableStateOf(node(firstId, "first"))
    private val render = mutableStateOf(true)
    private val closed = AtomicInteger()
    private val settings = SandboxSettings()
    private val requests = ConcurrentLinkedQueue<FileNodeId>()
    private val streams = ConcurrentLinkedQueue<TrackedInput>()
    private val files = linkedMapOf<FileNodeId, File>()
    private val payloads = linkedMapOf<FileNodeId, ByteArray>()
    private val gates = linkedMapOf<FileNodeId, ReadGate>()
    private val containerField = AtomicApp::class.java.getDeclaredField("container").apply { isAccessible = true }
    private lateinit var originalContainer: AppContainer
    private lateinit var sentinel: File
    private lateinit var baselineExports: Set<File>
    private var shown = false

    @Before
    fun installOnlyOwnedPrivateStreamsAndAPngReader() {
        originalContainer = app.container
        listOf(firstId, middleId, latestId).zip(listOf(Color.GREEN, Color.RED, Color.BLUE)).forEach { (id, color) ->
            val bytes = png(color)
            val file = File.createTempFile("selected-policy-source-", ".png", app.cacheDir)
            file.writeBytes(bytes)
            payloads[id] = bytes
            files[id] = file
        }
        val backend =
            object : StorageBackend by InMemoryBackend() {
                // FTP-shaped fixture identities select this controlled FILE backend, not a network transport.
                override val type = BackendType.FILE

                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                    assertTrue("Only this run's three identities may be opened", id in files)
                    requests += id
                    return FileResult.Success(
                        InputStreamProvider {
                            TrackedInput(id, checkNotNull(files[id]).inputStream(), gates[id]).also { streams += it }
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
        sentinel = File.createTempFile("selected-policy-control-", ".bin", directory)
        sentinel.writeBytes(byteArrayOf(29, 31))
        baselineExports = exports()
        registerReader()
    }

    @After
    fun releaseEveryGateDisposeAndRemoveOnlyOwnedFiles() {
        // Release first, including on an assertion failure, so the host cannot strand a blocked worker.
        gates.values.forEach { it.release.countDown() }
        try {
            if (shown) {
                compose.runOnIdle { render.value = false }
                compose.waitForIdle()
            }
            awaitCondition { streams.all { it.closed.get() } }
            val created = exports() - baselineExports
            assertTrue("At most this run's one handoff may create a copy", created.size <= 1)
            created.forEach { check(it.delete()) }
            assertEquals(baselineExports, exports())
            assertArrayEquals(byteArrayOf(29, 31), sentinel.readBytes())
            files.forEach { (id, file) -> assertArrayEquals(payloads[id], file.readBytes()) }
        } finally {
            shadowOf(app).checkActivities(false)
            containerField.set(app, originalContainer)
            check(sentinel.delete() || !sentinel.exists())
            files.values.forEach { check(it.delete() || !it.exists()) }
        }
    }

    @Test
    fun savedPngMimeTargetAppliesAfterInitialDetectionExactlyOnce() {
        savedDetectedTarget(FileOpenRuleKey(FileOpenRuleScope.MIME, PNG_MIME))
    }

    @Test
    fun savedImageFamilyTargetAppliesAfterInitialDetectionExactlyOnce() {
        savedDetectedTarget(FileOpenRuleKey(FileOpenRuleScope.FAMILY, "IMAGE"))
    }

    @Test
    fun rapidParentReplacementDrainsFirstSkipsMiddleAndOnlyLatestOwnsChoicesAndHandoff() {
        val firstGate = ReadGate().also { gates[firstId] = it }
        val latestGate = ReadGate().also { gates[latestId] = it }
        try {
            show()
            awaitCondition { firstGate.entered.count == 0L }
            compose.runOnIdle { source.value = node(middleId, "middle") }
            compose.onNodeWithText(source.value.name).assertIsDisplayed()
            compose.runOnIdle { source.value = node(latestId, "latest") }
            assertCheckingLatestWithoutReaders()
            assertEquals(listOf(firstId), requests.toList())
            firstGate.release.countDown()
            awaitCondition {
                latestGate.entered.count == 0L && streams.filter { it.id == firstId }.all { it.closed.get() }
            }
            assertEquals(listOf(firstId, latestId), requests.toList())
            assertCheckingLatestWithoutReaders()
            latestGate.release.countDown()
            awaitCondition { compose.onAllNodesWithContentDescription(READER).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(streams.all { it.closed.get() })
            assertEquals(listOf(firstId, latestId), requests.toList())
            compose.onNodeWithText(source.value.name).assertIsDisplayed()
            assertNull(shadowOf(app).peekNextStartedActivity())
            assertEquals(0, closed.get())
            assertPreferencesUnchanged()
            compose.onNodeWithContentDescription(READER).performScrollTo().performClick()
            awaitCondition { shadowOf(app).peekNextStartedActivity() != null && closed.get() == 1 }
            assertHandoff(latestId)
            assertEquals(listOf(firstId, latestId, latestId), requests.toList())
        } finally {
            firstGate.release.countDown()
            latestGate.release.countDown()
        }
    }

    @Test
    fun explicitCloseBeforeBlockedPrefixReturnsCannotReopenEvenWhenCallerKeepsPaneMounted() {
        val firstGate = ReadGate().also { gates[firstId] = it }
        try {
            seedSavedTarget(FileOpenRuleKey(FileOpenRuleScope.MIME, PNG_MIME))
            show()
            awaitCondition { firstGate.entered.count == 0L }
            compose.onNodeWithContentDescription("Close").performClick()
            assertEquals(1, closed.get())
            assertTrue("Caller deliberately leaves composition mounted", render.value)
            firstGate.release.countDown()
            awaitCondition { streams.isNotEmpty() && streams.all { it.closed.get() } }
            compose.runOnIdle { source.value = source.value.copy(name = "dismissed-$runId.atomic_unknown") }
            compose.waitForIdle()
            assertTrue(compose.onAllNodesWithContentDescription(READER).fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodesWithText(CHECKING).fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodesWithTag("file_opening_choices").fetchSemanticsNodes().isEmpty())
            assertNull(shadowOf(app).peekNextStartedActivity())
            assertEquals(listOf(firstId), requests.toList())
            assertEquals(baselineExports, exports())
            assertEquals(1, closed.get())
            assertPreferencesUnchanged()
        } finally {
            firstGate.release.countDown()
        }
    }

    @Test
    fun knownPngExtensionAppliesSavedMimeTargetWithoutAContentProbe() {
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("png", PNG_MIME)
        source.value =
            source.value.copy(
                name = "known-$runId.png",
                displayName = "known-$runId.png",
                mimeType = "application/octet-stream",
            )
        seedSavedTarget(FileOpenRuleKey(FileOpenRuleScope.MIME, PNG_MIME))
        show()
        awaitCondition { shadowOf(app).peekNextStartedActivity() != null && closed.get() == 1 }
        assertHandoff(firstId)
        assertEquals(
            "Known metadata must acquire only the actual export, not a prefix probe",
            listOf(firstId),
            requests.toList(),
        )
        compose.runOnIdle { source.value = source.value.copy(displayName = "Recomposed known extension") }
        compose.waitForIdle()
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertEquals(1, closed.get())
        assertEquals(listOf(firstId), requests.toList())
        assertPreferencesUnchanged()
    }

    private fun savedDetectedTarget(key: FileOpenRuleKey) {
        seedSavedTarget(key)
        show()
        awaitCondition { shadowOf(app).peekNextStartedActivity() != null && closed.get() == 1 }
        assertHandoff(firstId)
        assertEquals(listOf(firstId, firstId), requests.toList())
        // Recompose the same mounted opening session after success: the saved target must not run twice.
        compose.runOnIdle { source.value = source.value.copy(displayName = "Recomposed selected source") }
        compose.waitForIdle()
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertEquals(1, closed.get())
        assertEquals(listOf(firstId, firstId), requests.toList())
        assertPreferencesUnchanged()
    }

    private fun seedSavedTarget(key: FileOpenRuleKey) {
        settings.seedOpening(
            FileOpeningPreferences(defaultMode = FileOpenMode.ASK).withRule(
                key,
                FileOpenTarget(FileOpenMode.EXTERNAL, COMPONENT.flattenToString(), READER),
            ),
        )
    }

    private fun assertCheckingLatestWithoutReaders() {
        compose.onNodeWithText(source.value.name).assertIsDisplayed()
        compose.onNodeWithContentDescription(CHECKING).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithContentDescription(READER).fetchSemanticsNodes().isEmpty())
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertEquals(0, closed.get())
        assertEquals(baselineExports, exports())
        assertPreferencesUnchanged()
    }

    private fun assertHandoff(id: FileNodeId) {
        val view = checkNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(COMPONENT, view.component)
        assertEquals(PNG_MIME, view.type)
        assertEquals(source.value.name, view.clipData!!.description.label.toString())
        assertEquals(view.data, view.clipData!!.getItemAt(0).uri)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        assertArrayEquals(payloads[id], (exports() - baselineExports).single().readBytes())
        checkNotNull(app.contentResolver.openInputStream(checkNotNull(view.data))).use {
            assertArrayEquals(payloads[id], it.readBytes())
        }
        assertTrue(streams.isNotEmpty() && streams.all { it.closed.get() })
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertPreferencesUnchanged()
    }

    private fun show() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (render.value) FilePreviewPane(source.value, onClose = { closed.incrementAndGet() })
            }
        }
        shown = true
        shadowOf(app).checkActivities(true)
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun assertPreferencesUnchanged() {
        assertEquals(0, settings.updateCalls.get())
        assertEquals(settings.initial, settings.settings.value)
    }

    private fun exports() = File(app.cacheDir, "external-preview").listFiles().orEmpty().toSet()

    private fun node(
        id: FileNodeId,
        label: String,
    ): FileNode =
        FileNode(
            id, "$label-$runId.atomic_unknown", "$label-$runId.atomic_unknown", "application/octet-stream",
            -1, 1000, false, false, null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )

    private fun registerReader() {
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
                addDataType(PNG_MIME)
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

    private class ReadGate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
    }

    private class TrackedInput(
        val id: FileNodeId,
        private val input: InputStream,
        private val gate: ReadGate?,
    ) : InputStream() {
        val closed = AtomicBoolean()
        private val firstRead = AtomicBoolean(true)

        private fun awaitRelease() {
            if (firstRead.compareAndSet(true, false) && gate != null) {
                gate.entered.countDown()
                if (!gate.release.await(30, TimeUnit.SECONDS)) throw IOException("Owned fixture read release timed out")
            }
        }

        override fun read(): Int {
            awaitRelease()
            return input.read()
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            awaitRelease()
            return input.read(buffer, offset, length)
        }

        override fun close() {
            try {
                input.close()
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
        const val PNG_MIME = "image/png"
        const val READER = "Detected PNG target"
        const val CHECKING = "Checking file type…"
        val COMPONENT = ComponentName("selected.png", "selected.png.View")
    }
}
