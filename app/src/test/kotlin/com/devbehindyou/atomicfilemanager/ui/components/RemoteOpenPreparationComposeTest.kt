package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.StorageBackendSelector
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
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Real remote export and UI owners; no production progress API or injection seam is assumed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = AtomicApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteOpenPreparationComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: AtomicApp get() = ApplicationProvider.getApplicationContext()
    private val render = mutableStateOf(true)
    private val closeRequests = AtomicInteger()
    private val sourceId = FileNodeId.ftp("controlled-export", "/${UUID.randomUUID()}.atomic")
    private val preferences = SandboxSettings()
    private val blockedReads = mutableListOf<PausedAfterFirstChunk>()
    private val containerField = AtomicApp::class.java.getDeclaredField("container").apply { isAccessible = true }
    private lateinit var original: AppContainer
    private lateinit var sentinel: File
    private lateinit var baselineExports: Set<File>
    private var shown = false
    private var acquire: suspend () -> FileResult<InputStreamProvider> =
        { error("Configure this test's remote source") }

    @Before
    fun installOnlyTheSandboxBackendAndCompatibleReader() {
        original = app.container
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                    assertEquals(sourceId, id)
                    return acquire()
                }
            }
        val selector = StorageBackendSelector(mapOf(BackendType.FTP to backend))
        val controlled =
            object : AppContainer by original {
                override val settingsRepository: SettingsRepository = preferences
                override val storageBackendSelector = selector
                override val recentsRepository = NoOpRecents
            }
        containerField.set(app, controlled)
        val directory = File(app.cacheDir, "external-preview")
        check(directory.mkdirs() || directory.isDirectory)
        sentinel = File.createTempFile("atomic-remote-open-control-", ".bin", directory)
        sentinel.writeBytes(byteArrayOf(73))
        baselineExports = exports()
        registerReader()
    }

    @After
    fun disposeThenReleaseReadsAndRemoveOnlyTheOwnedSentinel() {
        try {
            try {
                if (shown) {
                    compose.runOnIdle { render.value = false }
                    // Apply disposal while reads remain gated; releasing EOF first could launch the old owner.
                    compose.waitForIdle()
                }
            } finally {
                blockedReads.forEach { it.release.countDown() }
            }
            if (shown) awaitCondition { exports() == baselineExports }
            assertTrue("Existing export sentinel must survive every request", sentinel.isFile)
            assertEquals(73, sentinel.readBytes().single().toInt())
        } finally {
            blockedReads.forEach { it.release.countDown() }
            shadowOf(app).checkActivities(false)
            containerField.set(app, original)
            assertTrue("Remove only this test's uniquely owned sentinel", sentinel.delete() || !sentinel.exists())
        }
    }

    @Test
    fun policyChooserOffersCancelDuringActualRemoteAcquisitionWithoutSavingAlways() {
        cancelAcquisition(Entry.POLICY)
    }

    @Test
    fun builtInFallbackOffersCancelDuringActualRemoteAcquisition() {
        cancelAcquisition(Entry.FALLBACK)
    }

    @Test
    fun unknownLengthPolicyExportShowsIndeterminateActualCopiedBytes() {
        copiedBytesAreIndeterminate(Entry.POLICY, size = -1)
    }

    @Test
    fun staleLengthFallbackExportDoesNotInventAPercentageFromMetadata() {
        copiedBytesAreIndeterminate(Entry.FALLBACK, size = 1)
    }

    @Test
    fun cancellationDuringABlockedReadWaitsForOwnedCopyCleanupBeforeRestoringChoices() {
        val stream = pausedStream()
        acquire = { FileResult.Success(InputStreamProvider { stream }) }
        show(Entry.POLICY, size = -1)
        startExport(Entry.POLICY)
        awaitCondition { stream.secondReadEntered.count == 0L }
        val owned = ownedCopyAfterFirstWrite()
        cancelAction().assertIsDisplayed().performClick()
        // InputStream has no transport-abort contract. Cancellation must not pretend cleanup has completed.
        openAction(Entry.POLICY).assertIsNotEnabled()
        assertTrue("A blocked read still owns its partial copy", owned.isFile)
        assertNoLaunchOrPreferenceMutation()
        compose.runOnIdle { stream.release.countDown() }
        awaitCondition { stream.closed.get() && exports() == baselineExports }
        openAction(Entry.POLICY).assertIsEnabled()
        assertNoLaunchOrPreferenceMutation()
    }

    @Test
    fun closingPolicyChooserCancelsPendingAcquisitionAndRemovesItsCopy() {
        closingCancelsAcquisition(Entry.POLICY)
    }

    @Test
    fun closingBuiltInFallbackCancelsPendingAcquisitionAndRemovesItsCopy() {
        closingCancelsAcquisition(Entry.FALLBACK)
    }

    private fun cancelAcquisition(entry: Entry) {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        acquire = {
            try {
                entered.complete(Unit)
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        show(entry, size = -1)
        startExport(entry)
        awaitCondition { entered.isCompleted && (exports() - baselineExports).size == 1 }
        assertBusy(entry)
        cancelAction().assertIsDisplayed().performClick()
        awaitCondition { cancelled.isCompleted && exports() == baselineExports }
        openAction(entry).assertIsEnabled()
        assertNoLaunchOrPreferenceMutation()
    }

    private fun copiedBytesAreIndeterminate(
        entry: Entry,
        size: Long,
    ) {
        val stream = pausedStream()
        acquire = { FileResult.Success(InputStreamProvider { stream }) }
        show(entry, size)
        startExport(entry)
        awaitCondition { stream.secondReadEntered.count == 0L }
        val owned = ownedCopyAfterFirstWrite()
        assertBusy(entry)
        val progress = compose.onNodeWithTag("external_export_progress").assertIsDisplayed().fetchSemanticsNode().config
        assertEquals(ProgressBarRangeInfo.Indeterminate, progress[SemanticsProperties.ProgressBarRangeInfo])
        val descriptions =
            if (progress.contains(SemanticsProperties.ContentDescription)) {
                progress[SemanticsProperties.ContentDescription]
            } else {
                emptyList()
            }
        val texts =
            if (progress.contains(SemanticsProperties.Text)) {
                progress[SemanticsProperties.Text].map { it.text }
            } else {
                emptyList()
            }
        val label = (descriptions + texts).joinToString(" ")
        assertTrue("Progress must describe actual written bytes", label.contains(FileUtils.formatBytes(owned.length())))
        assertFalse("Unknown or stale metadata must not create a percentage", label.contains('%'))
        assertNoLaunchOrPreferenceMutation()
        // Teardown cancels the UI owner before releasing this read, preventing an accidental old-source launch.
    }

    private fun closingCancelsAcquisition(entry: Entry) {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        acquire = {
            try {
                entered.complete(Unit)
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        show(entry, size = -1)
        startExport(entry)
        awaitCondition { entered.isCompleted && (exports() - baselineExports).size == 1 }
        assertBusy(entry)
        val closeLabel = if (entry == Entry.POLICY) app.getString(R.string.action_close) else "Close preview"
        compose.onNodeWithContentDescription(closeLabel).performClick()
        awaitCondition { cancelled.isCompleted && exports() == baselineExports }
        assertEquals(1, closeRequests.get())
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertEquals(0, preferences.updateCalls.get())
        assertEquals(preferences.initial, preferences.settings.value)
    }

    private fun show(
        entry: Entry,
        size: Long,
    ) {
        val node =
            FileNode(
                id = sourceId,
                name = "remote.atomic",
                displayName = "Remote export fixture",
                mimeType = MIME,
                size = size,
                modifiedAt = 0,
                isDirectory = false,
                isHidden = false,
                parentId = null,
                storageType = StorageType.VIRTUAL,
                access = AccessFlags.READ_ONLY,
                childCount = null,
                extras = null,
            )
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (render.value) {
                    val close = {
                        closeRequests.incrementAndGet()
                        render.value = false
                    }
                    if (entry == Entry.POLICY) {
                        FilePreviewPane(node, onClose = close)
                    } else {
                        BuiltInFilePreviewPane(node, onClose = close)
                    }
                }
            }
        }
        shown = true
        if (entry == Entry.POLICY) {
            awaitCondition {
                compose.onAllNodesWithContentDescription(READER).fetchSemanticsNodes().isNotEmpty()
            }
            // Real discovery and explicit reader launch validation remain enabled for the policy path.
            shadowOf(app).checkActivities(true)
            compose.onNode(isToggleable()).performScrollTo().performClick()
        } else {
            compose.onNodeWithTag("preview_open_external").assertIsDisplayed()
            // The fallback starts a system chooser; its shadow queue must capture any attempted launch.
            shadowOf(app).checkActivities(false)
        }
    }

    private fun startExport(entry: Entry) {
        val action = openAction(entry)
        if (entry == Entry.POLICY) action.performScrollTo()
        action.performClick()
    }

    private fun openAction(entry: Entry): SemanticsNodeInteraction =
        if (entry == Entry.POLICY) {
            compose.onNodeWithContentDescription(READER)
        } else {
            compose.onNodeWithTag("preview_open_external")
        }

    private fun cancelAction() = compose.onNodeWithContentDescription("Cancel")

    private fun assertBusy(entry: Entry) {
        openAction(entry).assertIsNotEnabled()
    }

    private fun assertNoLaunchOrPreferenceMutation() {
        assertEquals(0, closeRequests.get())
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertEquals(0, preferences.updateCalls.get())
        assertEquals(preferences.initial, preferences.settings.value)
    }

    private fun ownedCopyAfterFirstWrite(): File {
        val owned = (exports() - baselineExports).single()
        assertTrue("Real bytes must have reached the partial export", owned.length() > 0)
        assertTrue("Only the bounded first chunk has been written", owned.length() <= CHUNK_BYTES)
        return owned
    }

    private fun pausedStream() = PausedAfterFirstChunk().also { blockedReads += it }

    private fun exports(): Set<File> = File(app.cacheDir, "external-preview").listFiles().orEmpty().toSet()

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun registerReader() {
        val manager = shadowOf(app.packageManager)
        val component = ComponentName("controlled.reader", "controlled.reader.View")
        manager.addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = component.packageName
                name = component.className
                nonLocalizedLabel = READER
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
                addDataType(MIME)
            },
        )
    }

    private enum class Entry { POLICY, FALLBACK }

    private class PausedAfterFirstChunk : InputStream() {
        val secondReadEntered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = AtomicBoolean()
        private val first = AtomicBoolean(true)

        override fun read(): Int = error("The real exporter uses buffered reads")

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (first.compareAndSet(true, false)) {
                val count = minOf(length, CHUNK_BYTES.toInt())
                buffer.fill(73, offset, offset + count)
                return count
            }
            secondReadEntered.countDown()
            check(release.await(15, TimeUnit.SECONDS)) { "Test must release its own paused source" }
            return -1
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class SandboxSettings : SettingsRepository {
        val initial =
            AppSettings(
                fileOpening =
                    FileOpeningPreferences(defaultMode = FileOpenMode.ASK).withRule(
                        FileOpenRuleKey(FileOpenRuleScope.EXTENSION, "txt"),
                        FileOpenTarget(FileOpenMode.ASK),
                    ),
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
        const val MIME = "application/octet-stream"
        const val READER = "Controlled reader"
        const val CHUNK_BYTES = 8192L
    }
}
