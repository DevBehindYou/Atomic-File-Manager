package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StorageUsageLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun changingStorageClearsTheOldBreakdownBeforeTheNewPlanCompletes() {
        val firstRoot = File("usage-first").absolutePath
        val secondRoot = File("usage-second").absolutePath
        val pendingEntered = CompletableDeferred<Unit>()
        val pendingCancelled = CompletableDeferred<Unit>()
        val activePlans = AtomicInteger()
        val source =
            object : StorageUsageSource {
                override suspend fun plan(volumes: List<StorageVolumeInfo>): StorageUsagePlan {
                    if (volumes.single().id == "second") {
                        activePlans.incrementAndGet()
                        pendingEntered.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            activePlans.decrementAndGet()
                            pendingCancelled.complete(Unit)
                        }
                    }
                    return StorageUsagePlan("first", listOf(firstRoot), StorageCapacity(1_000, 100, 1), 0)
                }

                override fun list(directory: String) =
                    flowOf<FileResult<List<StorageUsageEntry>>>(
                        FileResult.Success(
                            listOf(
                                StorageUsageEntry(File(firstRoot, "test.pdf").path, "test.pdf", 600, false, false),
                            ),
                        ),
                    )
            }
        val owner =
            object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this)
                override val lifecycle: Lifecycle get() = registry
            }
        owner.registry.currentState = Lifecycle.State.CREATED
        val volumes =
            mutableStateOf(
                listOf(
                    StorageVolumeInfo(
                        "first", "Internal", StorageType.INTERNAL_SHARED, 1_000, 100, false, true,
                        FileNodeId.file(firstRoot), false,
                    ),
                ),
            )
        val visible = mutableStateOf(true)
        val repository = StorageUsageRepository(source)
        val finished = MutableSharedFlow<OperationSnapshot>()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    if (visible.value) StorageUsageDashboard(volumes.value, repository, finished)
                }
            }
        }
        var bodyFailure: Throwable? = null
        try {
            // Attach the owner before STARTED, matching the controlled lifecycle fixture below.
            compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
            compose.waitUntil(5_000) {
                val node = compose.onAllNodesWithTag("storage_readable_total").fetchSemanticsNodes().singleOrNull()
                node?.config?.get(SemanticsProperties.Text)?.any { it.text == "600.0 B · 1 files found" } == true
            }
            compose.onNodeWithTag("storage_readable_total").assertTextEquals("600.0 B · 1 files found")
            compose.runOnIdle {
                volumes.value =
                    listOf(
                        volumes.value.single().copy(
                            id = "second", rootNodeId = FileNodeId.file(secondRoot),
                        ),
                    )
            }
            compose.waitUntil(5_000) { pendingEntered.isCompleted }
            assertEquals(1, activePlans.get())
            compose.onNodeWithTag("storage_readable_total").assertDoesNotExist()
        } catch (failure: Throwable) {
            bodyFailure = failure
        } finally {
            try {
                compose.runOnIdle {
                    visible.value = false
                    owner.registry.currentState = Lifecycle.State.DESTROYED
                }
                compose.waitUntil(5_000) {
                    activePlans.get() == 0 && (!pendingEntered.isCompleted || pendingCancelled.isCompleted)
                }
                if (pendingEntered.isCompleted) assertTrue(pendingCancelled.isCompleted)
            } catch (cleanupFailure: Throwable) {
                // Keep the original scope/assertion failure visible if teardown also fails.
                val original = bodyFailure
                if (original == null) bodyFailure = cleanupFailure else original.addSuppressed(cleanupFailure)
            }
        }
        bodyFailure?.let { throw it }
    }

    @Test
    fun scansAndOperationSubscriptionsExistOnlyWhileTheStorageScreenIsStarted() {
        val starts = AtomicInteger()
        val cancelled = AtomicInteger()
        val source =
            object : StorageUsageSource {
                override suspend fun plan(volumes: List<StorageVolumeInfo>) =
                    StorageUsagePlan("root", listOf("/root"), StorageCapacity(1000, 100, 1), 0)

                override fun list(directory: String) =
                    flow<FileResult<List<StorageUsageEntry>>> {
                        starts.incrementAndGet()
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled.incrementAndGet()
                        }
                    }
            }
        val owner =
            object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this)
                override val lifecycle: Lifecycle get() = registry
            }
        owner.registry.currentState = Lifecycle.State.CREATED
        val finished = MutableSharedFlow<OperationSnapshot>()
        val repository = StorageUsageRepository(source)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    StorageUsageDashboard(emptyList(), repository, finished)
                }
            }
        }
        compose.runOnIdle {
            assertEquals(0, starts.get())
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.waitUntil(5_000) { starts.get() == 1 && finished.subscriptionCount.value == 1 }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitUntil(5_000) { cancelled.get() == 1 && finished.subscriptionCount.value == 0 }
        compose.runOnIdle {
            assertEquals(1, starts.get())
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.waitUntil(5_000) { starts.get() == 2 }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.DESTROYED }
        compose.waitUntil(5_000) { cancelled.get() == 2 && finished.subscriptionCount.value == 0 }
    }
}
