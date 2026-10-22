package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrimaryBrowseStartedLifecycleTest {
    private val compose = createComposeRule()
    private val fixture = BrowsePaneFixture()
    private val store = ViewModelStore()
    private val models = mutableListOf<BrowseViewModel>()
    private val owners = mutableListOf<StartedBrowseOwner>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @After
    fun clearRetainedOwnerAfterPendingCancellation() {
        val jobs = models.map { it.viewModelScope.coroutineContext.job }
        compose.runOnIdle {
            owners.forEach { it.registry.currentState = Lifecycle.State.DESTROYED }
            store.clear()
        }
        awaitCondition { jobs.all { it.isCompleted } }
    }

    @Test
    fun composedStoppedScreenDoesNoWorkAndStartStopReturnRefreshesOnce() {
        val owner = owner()
        val model = model()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { PrimaryBrowseVisibility(model, 0) }
        }
        compose.runOnIdle {
            assertEquals(0, fixture.started.get())
            assertTrue(model.viewModelScope.coroutineContext.job.children.none { it.isActive })
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.RESUMED
            owner.registry.currentState = Lifecycle.State.STARTED
            assertEquals(1, fixture.started.get())
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        awaitCondition { fixture.cancelled.get() == 1 && noScreenWork(model) }
        compose.runOnIdle { fixture.finishOperation() }
        awaitCondition { fixture.completed.get() == 1 }
        compose.runOnIdle {
            assertEquals(1, fixture.started.get())
            assertTrue(model.viewModelScope.isActive)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { fixture.started.get() == 2 }
        compose.runOnIdle { assertSame(model, model()) }
    }

    @Test
    fun changedRequestWhileStoppedOverridesDeferredOldFolderOnReturn() {
        val owner = owner()
        val request = mutableStateOf(0)
        val model = model()
        val child = FileNodeId.file("/lifecycle-child")
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                PrimaryBrowseVisibility(model, request.value)
            }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle { model.navigateToStorage(child, "Child") }
        awaitCondition { fixture.started.get() == 2 && fixture.listedFolders.last() == child }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        awaitCondition { fixture.cancelled.get() == 2 && noScreenWork(model) }
        compose.runOnIdle {
            model.refresh()
            request.value = 1
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, fixture.started.get())
            assertTrue(noScreenWork(model))
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { fixture.started.get() == 3 }
        compose.runOnIdle {
            assertEquals(fixture.root, fixture.listedFolders.last())
            assertEquals(fixture.root, model.uiState.value.currentFolderId)
            assertTrue(!model.navigateBack())
            assertEquals(3, fixture.started.get())
        }
    }

    @Test
    fun replacingLifecycleOwnerPausesUntilNewOwnerStartsAndRemovesOldObserver() {
        val first = owner()
        val second = owner()
        val current = mutableStateOf(first)
        val model = model()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides current.value) { PrimaryBrowseVisibility(model, 0) }
        }
        compose.runOnIdle { first.registry.currentState = Lifecycle.State.STARTED }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle { current.value = second }
        awaitCondition { fixture.cancelled.get() == 1 && noScreenWork(model) }
        compose.runOnIdle {
            first.registry.currentState = Lifecycle.State.CREATED
            first.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { noScreenWork(model) }
        compose.runOnIdle {
            assertEquals(1, fixture.started.get())
            second.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { fixture.started.get() == 2 }
    }

    @Test
    fun disposalRemovesObserverWithoutClearingRetainedModelOrGlobalQueue() {
        val owner = owner()
        val visible = mutableStateOf(true)
        val model = model()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (visible.value) PrimaryBrowseVisibility(model, 0)
            }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle { visible.value = false }
        awaitCondition { fixture.cancelled.get() == 1 && noScreenWork(model) }
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.STARTED
            fixture.finishOperation()
        }
        awaitCondition { fixture.completed.get() == 1 && noScreenWork(model) }
        compose.runOnIdle {
            assertEquals(1, fixture.started.get())
            assertTrue(model.viewModelScope.isActive)
        }
    }

    @Test
    fun filterChangesWhileStoppedAreRetainedWithoutStartingBackgroundFiltering() {
        val owner = owner()
        val model = model()
        val item =
            FileNode(
                FileNodeId.file("/visible.txt"), "visible.txt", "visible.txt", "text/plain", 1, 1,
                false, false, fixture.root, StorageType.INTERNAL_PRIVATE, AccessFlags.READ_ONLY, null, null,
            )
        fixture.listingItems = listOf(item)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { PrimaryBrowseVisibility(model, 0) }
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        awaitCondition { model.uiState.value.filteredItems == listOf(item) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        awaitCondition { fixture.cancelled.get() == 1 && noScreenWork(model) }
        compose.runOnIdle { model.onSearchQueryChanged("absent") }
        // Join any wrongly launched filter too, so an asynchronous empty result cannot escape the assertion.
        awaitCondition { noScreenWork(model) }
        compose.runOnIdle {
            assertEquals("absent", model.uiState.value.searchQuery)
            assertEquals(listOf(item), model.uiState.value.filteredItems)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { fixture.started.get() == 2 && model.uiState.value.filteredItems.isEmpty() }
    }

    private fun owner(): StartedBrowseOwner = StartedBrowseOwner().also { owners += it }

    private fun model(): BrowseViewModel {
        val factory = fixture.factory(fixture.root, startVisible = false)
        return ViewModelProvider(store, factory)["primary", BrowseViewModel::class.java]
            .also { if (it !in models) models += it }
    }

    private fun noScreenWork(model: BrowseViewModel): Boolean =
        model.viewModelScope.coroutineContext.job.children.none { it.isActive }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}

private class StartedBrowseOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle get() = registry
}
