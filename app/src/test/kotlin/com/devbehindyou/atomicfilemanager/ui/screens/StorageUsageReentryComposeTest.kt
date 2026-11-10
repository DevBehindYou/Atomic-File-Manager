package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Leaving Analysis and coming back must neither scan again nor hide what this process already knows. A finished
 * breakdown returns marked as previous and possibly stale; a scan the user left mid-way is reported as stopped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StorageUsageReentryComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reenteringAfterACompleteScanShowsThePreviousBreakdownWithoutScanning() =
        withDashboard { fixture ->
            analyzeToCompletion()
            fixture.leaveAndReturn()
            compose.onNodeWithTag("storage_usage_status")
                .assertTextEquals("Analyze file types when you need a breakdown.")
            compose.onNodeWithText("Showing the previous complete file breakdown from", substring = true)
                .assertExists()
            compose.onNodeWithTag("storage_readable_total").assertTextEquals("600.0 B · 1 files found")
            compose.runOnIdle {
                assertEquals("Entry must not plan or list anything", 1, fixture.plans.get())
                assertEquals(1, fixture.listings.get())
            }
        }

    @Test
    fun reenteringAfterLeavingMidScanSaysTheScanStoppedAndKeepsTheLastCompleteResult() =
        withDashboard { fixture ->
            analyzeToCompletion()
            fixture.block.set(true)
            compose.onNodeWithTag("refresh_storage_usage").performScrollTo().assertIsEnabled().performClick()
            compose.waitForIdle()
            compose.waitUntil(5_000) { fixture.blockedListing.isCompleted }
            fixture.leaveAndReturn()
            compose.waitUntil(5_000) { fixture.active.get() == 0 }
            compose.onNodeWithTag("storage_usage_status")
                .assertTextEquals(
                    "The last file type scan stopped before it finished. Analyze file types again when ready.",
                )
            compose.onNodeWithContentDescription("Analyze file types").assertExists()
            compose.onNodeWithText("Showing the previous complete file breakdown from", substring = true)
                .assertExists()
            compose.onNodeWithTag("storage_readable_total").assertTextEquals("600.0 B · 1 files found")
            compose.runOnIdle { assertEquals(2, fixture.plans.get()) }
        }

    @Test
    fun aResultForOtherStorageIsNotShownAfterAccessChanges() =
        withDashboard { fixture ->
            analyzeToCompletion()
            fixture.visible.value = false
            compose.waitForIdle()
            // A revoked grant changes what may be read; the old breakdown no longer describes this storage.
            fixture.volumes.value = listOf(fixture.volumes.value.single().copy(requiresGrant = true))
            fixture.visible.value = true
            compose.waitForIdle()
            compose.onNodeWithTag("storage_usage_status")
                .assertTextEquals("Analyze file types when you need a breakdown.")
            compose.onNodeWithTag("storage_readable_total").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, fixture.plans.get()) }
        }

    private fun analyzeToCompletion() {
        compose.onNodeWithTag("refresh_storage_usage").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            val node = compose.onAllNodesWithTag("storage_readable_total").fetchSemanticsNodes().singleOrNull()
            node?.config?.get(SemanticsProperties.Text)?.any { it.text == "600.0 B · 1 files found" } == true &&
                compose.onAllNodesWithTag("storage_usage_status").fetchSemanticsNodes().single()
                    .config[SemanticsProperties.Text].any { it.text.startsWith("Readable-folder scan complete") }
        }
    }

    private fun Fixture.leaveAndReturn() {
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
    }

    private fun withDashboard(assertions: (Fixture) -> Unit) {
        val fixture = Fixture()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides fixture.owner) {
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (fixture.visible.value) {
                            StorageUsageDashboard(fixture.volumes.value, fixture.repository, fixture.finished)
                        }
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
                compose.waitUntil(5_000) { fixture.active.get() == 0 }
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
        val plans = AtomicInteger()
        val listings = AtomicInteger()
        val active = AtomicInteger()
        val block = AtomicBoolean(false)
        val blockedListing = CompletableDeferred<Unit>()
        val finished = MutableSharedFlow<OperationSnapshot>()
        private val root = File("usage-reentry-root").absolutePath
        val volumes =
            mutableStateOf(
                listOf(
                    StorageVolumeInfo(
                        "primary", "Internal", StorageType.INTERNAL_SHARED, 1_000, 100, false, true,
                        FileNodeId.file(root), false,
                    ),
                ),
            )
        val repository =
            StorageUsageRepository(
                object : StorageUsageSource {
                    override suspend fun plan(volumes: List<StorageVolumeInfo>): StorageUsagePlan {
                        plans.incrementAndGet()
                        return StorageUsagePlan("primary", listOf(root), StorageCapacity(1_000, 100, 1), 0)
                    }

                    override fun list(directory: String) =
                        flow<FileResult<List<StorageUsageEntry>>> {
                            listings.incrementAndGet()
                            active.incrementAndGet()
                            try {
                                if (block.get()) {
                                    blockedListing.complete(Unit)
                                    awaitCancellation()
                                }
                                emit(
                                    FileResult.Success(
                                        listOf(StorageUsageEntry(File(root, "a.pdf").path, "a.pdf", 600, false, false)),
                                    ),
                                )
                            } finally {
                                active.decrementAndGet()
                            }
                        }
                },
            )
    }
}
