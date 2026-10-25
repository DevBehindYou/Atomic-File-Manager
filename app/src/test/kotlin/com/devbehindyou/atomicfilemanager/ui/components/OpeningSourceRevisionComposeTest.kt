package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
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
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Parent metadata replacement must reach the actual user-selected external handoff. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    qualifiers = "w411dp-h891dp",
    application = AtomicApp::class,
    shadows = [OwnedExportFileProviderShadow::class],
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningSourceRevisionComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: AtomicApp get() = ApplicationProvider.getApplicationContext()
    private val runId = UUID.randomUUID().toString()
    private val firstId = FileNodeId.ftp("opening-revision", "/$runId/original")
    private val nextId = FileNodeId.ftp("opening-revision", "/$runId/replacement")
    private val originalNode = node(firstId, "original-$runId.atomic", OLD_MIME, 4, 1000)
    private val source = mutableStateOf(originalNode)
    private val render = mutableStateOf(true)
    private val payload = AtomicReference(byteArrayOf(1, 2, 3, 4))
    private val requests = ConcurrentLinkedQueue<FileNodeId>()
    private val closed = AtomicInteger()
    private val settings = SandboxSettings()
    private val containerField = AtomicApp::class.java.getDeclaredField("container").apply { isAccessible = true }
    private lateinit var originalContainer: AppContainer
    private lateinit var sentinel: File
    private lateinit var baselineExports: Set<File>
    private var shown = false

    @Before
    fun installOnlySandboxSourcesAndAReaderForBothMetadataVersions() {
        originalContainer = app.container
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                    assertTrue("Only this test's two source identities may be read", id == firstId || id == nextId)
                    requests += id
                    val bytes = payload.get().copyOf()
                    return FileResult.Success(InputStreamProvider { ByteArrayInputStream(bytes) })
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
        sentinel = File.createTempFile("opening-revision-control-", ".bin", directory)
        sentinel.writeBytes(byteArrayOf(29, 31))
        baselineExports = exports()
        registerReader()
    }

    @After
    fun disposeAndRemoveOnlyThisConsumerRunCopies() {
        try {
            if (shown) {
                compose.runOnIdle { render.value = false }
                compose.waitForIdle()
            }
            // Generic previews cannot create other copies: this run performs one explicit export only.
            val created = exports() - baselineExports
            assertTrue("At most this run's one selected handoff may have created a copy", created.size <= 1)
            created.forEach { check(it.delete()) }
            assertEquals(baselineExports, exports())
            assertArrayEquals(byteArrayOf(29, 31), sentinel.readBytes())
        } finally {
            shadowOf(app).checkActivities(false)
            containerField.set(app, originalContainer)
            check(sentinel.delete() || !sentinel.exists())
        }
    }

    @Test
    fun policyHandoffUsesUpdatedMetadataWhenTheParentReplacesTheSameSourceId() {
        replaceThenOpen(Entry.POLICY, sameId = true)
    }

    @Test
    fun fallbackHandoffUsesUpdatedMetadataWhenTheParentReplacesTheSameSourceId() {
        replaceThenOpen(Entry.FALLBACK, sameId = true)
    }

    @Test
    fun policyDifferentSourceIdReplacementIsAWorkingHandoffControl() {
        replaceThenOpen(Entry.POLICY, sameId = false)
    }

    @Test
    fun fallbackDifferentSourceIdReplacementIsAWorkingHandoffControl() {
        replaceThenOpen(Entry.FALLBACK, sameId = false)
    }

    @Test
    fun policyReaderChoicesRefreshWhenTheSameSourceReceivesADifferentMime() {
        shadowOf(app.packageManager).clearIntentFilterForActivity(COMPONENT)
        registerReader(mimes = listOf(OLD_MIME))
        registerReader(component = NEW_COMPONENT, label = NEW_READER, mimes = listOf(NEW_MIME))
        show(Entry.POLICY)
        assertTrue(compose.onAllNodesWithContentDescription(NEW_READER).fetchSemanticsNodes().isEmpty())
        compose.runOnIdle {
            source.value = originalNode.copy(name = "updated-$runId.revision", mimeType = NEW_MIME, modifiedAt = 2000)
        }
        awaitCondition {
            compose.onAllNodesWithContentDescription(NEW_READER).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(NEW_READER).performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodesWithContentDescription(READER).fetchSemanticsNodes().isEmpty())
        assertTrue(requests.isEmpty())
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertEquals(0, closed.get())
        assertEquals(0, settings.updateCalls.get())
        assertEquals(settings.initial, settings.settings.value)
    }

    private fun replaceThenOpen(
        entry: Entry,
        sameId: Boolean,
    ) {
        show(entry)
        assertEquals(0, requests.size)
        assertNull(shadowOf(app).peekNextStartedActivity())
        val replacementBytes = ByteArray(17) { (it + 41).toByte() }
        val replacement =
            node(
                if (sameId) firstId else nextId,
                "replacement-$runId.revision",
                NEW_MIME,
                replacementBytes.size.toLong(),
                2000,
            )
        compose.runOnIdle {
            payload.set(replacementBytes)
            source.value = replacement
        }
        compose.waitForIdle()
        if (entry == Entry.POLICY) {
            awaitReader()
            compose.onNodeWithContentDescription(READER).performScrollTo().performClick()
        } else {
            compose.onNodeWithTag("preview_open_external").performClick()
        }
        awaitCondition {
            shadowOf(app).peekNextStartedActivity() != null && (entry == Entry.FALLBACK || closed.get() == 1)
        }
        val launched = checkNotNull(shadowOf(app).nextStartedActivity)
        val view =
            if (entry == Entry.POLICY) {
                assertEquals(COMPONENT, launched.component)
                launched
            } else {
                assertEquals(Intent.ACTION_CHOOSER, launched.action)
                checkNotNull(launched.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
            }
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("The latest parent metadata must determine the launched MIME", NEW_MIME, view.type)
        assertEquals(replacement.name, view.clipData!!.description.label.toString())
        assertEquals(view.data, view.clipData!!.getItemAt(0).uri)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        val copied = (exports() - baselineExports).single()
        assertTrue("The staged extension must also use the current filename", copied.name.endsWith(".revision"))
        assertArrayEquals(replacementBytes, copied.readBytes())
        checkNotNull(app.contentResolver.openInputStream(checkNotNull(view.data))).use {
            assertArrayEquals(replacementBytes, it.readBytes())
        }
        assertEquals(listOf(replacement.id), requests.toList())
        assertEquals(if (entry == Entry.POLICY) 1 else 0, closed.get())
        assertNull(shadowOf(app).peekNextStartedActivity())
        compose.onNodeWithText(replacement.name).assertIsDisplayed()
        assertEquals(0, settings.updateCalls.get())
        assertEquals(settings.initial, settings.settings.value)
    }

    private fun show(entry: Entry) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (render.value) {
                    if (entry == Entry.POLICY) {
                        FilePreviewPane(source.value, onClose = { closed.incrementAndGet() })
                    } else {
                        BuiltInFilePreviewPane(source.value, onClose = { closed.incrementAndGet() })
                    }
                }
            }
        }
        shown = true
        if (entry == Entry.POLICY) {
            awaitReader()
            shadowOf(app).checkActivities(true)
        } else {
            compose.onNodeWithTag("preview_open_external").assertIsDisplayed()
            shadowOf(app).checkActivities(false)
        }
    }

    private fun awaitReader() =
        awaitCondition {
            compose.onAllNodesWithContentDescription(READER).fetchSemanticsNodes().isNotEmpty()
        }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun exports() = File(app.cacheDir, "external-preview").listFiles().orEmpty().toSet()

    private fun registerReader(
        component: ComponentName = COMPONENT,
        label: String = READER,
        mimes: List<String> = listOf(OLD_MIME, NEW_MIME),
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
                mimes.forEach { addDataType(it) }
            },
        )
    }

    private fun node(
        id: FileNodeId,
        name: String,
        mime: String,
        size: Long,
        modifiedAt: Long,
    ) = FileNode(
        id, name, name, mime, size, modifiedAt, false, false, null,
        StorageType.VIRTUAL, AccessFlags.FULL, null, null,
    )

    private enum class Entry { POLICY, FALLBACK }

    private class SandboxSettings : SettingsRepository {
        val initial =
            AppSettings(
                fileOpening = FileOpeningPreferences(defaultMode = FileOpenMode.ASK),
                themeMode = ThemeMode.DARK,
                showHiddenFiles = false,
            )
        private val mutable = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = mutable
        val updateCalls = AtomicInteger()

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
        const val OLD_MIME = "application/octet-stream"
        const val NEW_MIME = "application/x-atomic-revision"
        const val READER = "Revision reader"
        val COMPONENT = ComponentName("revision.reader", "revision.reader.View")
        const val NEW_READER = "Updated MIME reader"
        val NEW_COMPONENT = ComponentName("updated.reader", "updated.reader.View")
    }
}
