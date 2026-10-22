package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsageEntry
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsagePlan
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsageRepository
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsageSource
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.ReadFileContentUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.StorageAnalyzerUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageAnalysisOptInComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun openingAnalysisShowsUnscannedActionsWithoutStartingEitherScanner() = withFixture { fixture ->
        compose.onNodeWithText("Analyze file types").assertExists()
        compose.onNodeWithTag("cleanup_unscanned").assertExists()
        compose.onNodeWithTag("refresh_storage_usage").assertIsEnabled()
        compose.onNodeWithTag("analyze_cleanup_button").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(0, fixture.usagePlans.get())
            assertEquals(0, fixture.usageStarts.get())
            assertEquals(0, fixture.deepStarts.get())
            assertEquals(0, fixture.finished.subscriptionCount.value)
        }
    }

    @Test
    fun fileTypeActionStartsOnlyMetadataBlocksCleanupAndCancelsWhenAnalysisLeaves() = withFixture { fixture ->
        compose.onNodeWithTag("refresh_storage_usage").performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { fixture.usageStarts.get() == 1 }
        compose.onNodeWithTag("analyze_cleanup_button").assertIsNotEnabled()
        assertEquals(1, fixture.usagePlans.get())
        assertEquals(0, fixture.deepStarts.get())
        assertEquals(1, fixture.activeUsage.get())
        fixture.expectUsageCancellation = true
    }

    @Test
    fun cleanupActionStartsOnlyDeepAnalysisAndDisablesFileTypeAction() = withFixture { fixture ->
        compose.onNodeWithTag("analyze_cleanup_button").performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { fixture.deepStarts.get() == 1 }
        compose.onNodeWithTag("refresh_storage_usage").performScrollTo().assertIsNotEnabled()
        assertEquals(0, fixture.usagePlans.get())
        assertEquals(0, fixture.usageStarts.get())
        assertEquals(1, fixture.activeDeep.get())
        fixture.expectDeepCancellation = true
    }

    @Test
    fun recentCleanupResultIsShownWithoutStartingANewScanOnEntry() {
        val fixture = Fixture()
        fixture.blockDeep.set(false)
        runBlocking(Dispatchers.IO) { fixture.analyzer.analyze(fixture.memory.rootId).toList() }
        assertEquals(1, fixture.deepStarts.get())
        fixture.blockDeep.set(true)
        withFixture(fixture) {
            compose.onNodeWithTag("cleanup_unscanned").assertDoesNotExist()
            compose.onNodeWithText("You could free", substring = true).assertExists()
            compose.onNodeWithContentDescription("Rescan storage").assertIsEnabled()
            compose.runOnIdle {
                assertEquals(1, fixture.deepStarts.get())
                assertEquals(0, fixture.usagePlans.get())
                assertEquals(0, fixture.activeDeep.get())
            }
        }
    }

    @Test
    fun changingAnalysisRootCancelsMetadataAndRequiresAnotherExplicitAction() = withFixture { fixture ->
        val secondRoot = runBlocking(Dispatchers.IO) {
            (fixture.memory.createDirectory(fixture.memory.rootId, "second") as FileResult.Success).value.id
        }
        compose.onNodeWithTag("refresh_storage_usage").performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { fixture.usageStarts.get() == 1 }
        compose.onNodeWithTag("analyze_cleanup_button").assertIsNotEnabled()
        fixture.expectUsageCancellation = true

        compose.runOnIdle { fixture.rootId.value = secondRoot }
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            fixture.usageCancelled.isCompleted && fixture.activeUsage.get() == 0 &&
                fixture.finished.subscriptionCount.value == 0
        }
        compose.waitForIdle()
        compose.onNodeWithText("Analyze file types").assertExists()
        compose.onNodeWithTag("analyze_cleanup_button").assertIsEnabled()
        assertEquals(1, fixture.usagePlans.get())
        assertEquals(1, fixture.usageStarts.get())
        assertEquals(0, fixture.deepStarts.get())

        compose.onNodeWithTag("analyze_cleanup_button").performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { fixture.deepStarts.get() == 1 }
        compose.onNodeWithTag("refresh_storage_usage").performScrollTo().assertIsNotEnabled()
        assertEquals(listOf(secondRoot), fixture.deepRoots.toList())
        assertEquals(1, fixture.activeDeep.get())
        assertEquals(1, fixture.usageStarts.get())
        fixture.expectDeepCancellation = true
    }

    private fun withFixture(fixture: Fixture = Fixture(), assertions: (Fixture) -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides fixture.owner) {
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    if (fixture.visible.value) {
                        StorageIntelligenceContent(
                            rootId = fixture.rootId.value,
                            analyzer = fixture.analyzer,
                            usageRepository = fixture.repository,
                            volumes = fixture.volumes,
                            finished = fixture.finished,
                            retentionDays = 30,
                            onTrash = { error("No cleanup mutation is authorized by this test") },
                            onNavigateBack = {},
                        )
                    }
                }
            }
        }
        var failure: Throwable? = null
        try {
            compose.runOnIdle { fixture.owner.registry.currentState = Lifecycle.State.STARTED }
            compose.waitForIdle()
            assertions(fixture)
        } catch (problem: Throwable) {
            failure = problem
        } finally {
            try {
                compose.runOnIdle {
                    fixture.visible.value = false
                    fixture.owner.registry.currentState = Lifecycle.State.DESTROYED
                }
                compose.waitForIdle()
                compose.waitUntil(5_000) {
                    fixture.activeUsage.get() == 0 && fixture.activeDeep.get() == 0 &&
                        fixture.finished.subscriptionCount.value == 0
                }
                compose.waitForIdle()
                if (fixture.expectUsageCancellation) assertTrue(fixture.usageCancelled.isCompleted)
                if (fixture.expectDeepCancellation) assertTrue(fixture.deepCancelled.isCompleted)
            } catch (cleanupFailure: Throwable) {
                val original = failure
                if (original == null) failure = cleanupFailure else original.addSuppressed(cleanupFailure)
            }
        }
        failure?.let { throw it }
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.CREATED }
        override val lifecycle: Lifecycle get() = registry
    }

    private class Fixture {
        val owner = TestOwner()
        val visible = mutableStateOf(true)
        val usagePlans = AtomicInteger()
        val usageStarts = AtomicInteger()
        val deepStarts = AtomicInteger()
        val activeUsage = AtomicInteger()
        val activeDeep = AtomicInteger()
        val usageCancelled = CompletableDeferred<Unit>()
        val deepCancelled = CompletableDeferred<Unit>()
        val blockDeep = AtomicBoolean(true)
        var expectUsageCancellation = false
        var expectDeepCancellation = false
        val memory = InMemoryBackend()
        val rootId = mutableStateOf(memory.rootId)
        val deepRoots = ConcurrentLinkedQueue<FileNodeId>()
        val finished = MutableSharedFlow<OperationSnapshot>()
        private val root = File("analysis-usage-root").absolutePath
        val volumes = listOf(StorageVolumeInfo(
            "primary", "Internal", StorageType.INTERNAL_SHARED, 1_000, 200, false, true,
            FileNodeId.file(root), false,
        ))
        private val backend = object : StorageBackend by memory {
            override fun listChildren(id: FileNodeId) = flow<FileResult<List<FileNode>>> {
                deepRoots.add(id)
                deepStarts.incrementAndGet()
                activeDeep.incrementAndGet()
                try {
                    if (blockDeep.get()) awaitCancellation() else emitAll(memory.listChildren(id))
                } finally {
                    if (blockDeep.get()) deepCancelled.complete(Unit)
                    activeDeep.decrementAndGet()
                }
            }
        }
        val analyzer = StorageAnalyzerUseCase({ backend }, ReadFileContentUseCase { backend })
        val repository = StorageUsageRepository(object : StorageUsageSource {
            override suspend fun plan(volumes: List<StorageVolumeInfo>): StorageUsagePlan {
                usagePlans.incrementAndGet()
                return StorageUsagePlan("primary", listOf(root), StorageCapacity(1_000, 200, 1), 0)
            }

            override fun list(directory: String) = flow<FileResult<List<StorageUsageEntry>>> {
                usageStarts.incrementAndGet()
                activeUsage.incrementAndGet()
                try {
                    awaitCancellation()
                } finally {
                    usageCancelled.complete(Unit)
                    activeUsage.decrementAndGet()
                }
            }
        })
    }
}
