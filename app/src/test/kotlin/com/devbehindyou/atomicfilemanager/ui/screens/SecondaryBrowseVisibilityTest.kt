package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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
class SecondaryBrowseVisibilityTest {
    private val compose = createComposeRule()
    private val fixture = BrowsePaneFixture()
    private val render = mutableStateOf(true)
    private val wide = mutableStateOf(true)
    private val listingVisible = mutableStateOf(true)
    private val tab = mutableStateOf("first")
    private val owner = mutableStateOf(SecondaryVisibilityOwner())
    private val models = mutableListOf<BrowseViewModel>()
    private var current: BrowseViewModel? = null

    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @After
    fun retireComposedOwnersAndJoinTheirJobsBeforeResettingMain() {
        val jobs = models.map { it.viewModelScope.coroutineContext.job }
        compose.runOnIdle {
            render.value = false
            owner.value.registry.currentState = Lifecycle.State.DESTROYED
        }
        awaitCondition { jobs.all { it.isCompleted } }
    }

    @Test
    fun stoppedAndPreviewHiddenPanesStayIdleAndReturnRefreshesOnlyWhenBothStartedAndVisible() {
        show()
        compose.runOnIdle {
            assertEquals(0, fixture.started.get())
            assertTrue(noScreenWork(checkNotNull(current)))
            owner.value.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle {
            owner.value.registry.currentState = Lifecycle.State.RESUMED
            owner.value.registry.currentState = Lifecycle.State.STARTED
            assertEquals(1, fixture.started.get())
            listingVisible.value = false
        }
        awaitCondition { fixture.cancelled.get() == 1 && noScreenWork(checkNotNull(current)) }
        compose.runOnIdle {
            fixture.finishOperation()
            checkNotNull(current).refresh()
            owner.value.registry.currentState = Lifecycle.State.CREATED
            listingVisible.value = true
        }
        awaitCondition { fixture.completed.get() == 1 }
        compose.runOnIdle {
            assertEquals(1, fixture.started.get())
            assertTrue(noScreenWork(checkNotNull(current)))
            owner.value.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { fixture.started.get() == 2 }
        compose.runOnIdle { assertEquals(1, fixture.factories) }
    }

    @Test
    fun previewAndStopReturnKeepSecondaryNavigationClipboardSelectionAndBackHistory() {
        val source = item("Source")
        val child = item("Child", directory = true)
        fixture.listingItems = listOf(source, child)
        owner.value.registry.currentState = Lifecycle.State.STARTED
        show()
        awaitCondition { checkNotNull(current).uiState.value.rawItems.size == 2 }
        val model = checkNotNull(current)
        compose.runOnIdle {
            model.toggleSelection(source.id)
            model.copySelected()
            model.navigateTo(child)
        }
        awaitCondition { fixture.started.get() == 2 && model.uiState.value.currentFolderId == child.id }
        val clipboard = checkNotNull(model.uiState.value.clipboard)
        compose.runOnIdle {
            model.toggleSelection(source.id)
            model.onSearchQueryChanged("Source")
            listingVisible.value = false
        }
        awaitCondition { fixture.cancelled.get() == 2 && noScreenWork(model) }
        compose.runOnIdle {
            owner.value.registry.currentState = Lifecycle.State.CREATED
            model.refresh()
            listingVisible.value = true
        }
        compose.runOnIdle {
            assertSame(model, current)
            assertEquals(2, fixture.started.get())
            assertSame(clipboard, model.uiState.value.clipboard)
            owner.value.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { fixture.started.get() == 3 && !model.uiState.value.isRefreshing }
        compose.runOnIdle {
            assertEquals(child.id, model.uiState.value.currentFolderId)
            assertSame(clipboard, model.uiState.value.clipboard)
            assertEquals(setOf(source.id), model.uiState.value.selectedIds)
            assertEquals("Source", model.uiState.value.searchQuery)
            assertTrue(model.navigateBack())
        }
        awaitCondition { fixture.started.get() == 4 && model.uiState.value.currentFolderId == fixture.root }
    }

    @Test
    fun compactAndPreviewOnlyConstructionRemainLazyAndReturningWideUsesAFreshPausedOwner() {
        wide.value = false
        listingVisible.value = false
        show()
        compose.runOnIdle {
            assertEquals(0, fixture.factories)
            assertEquals(0, fixture.started.get())
            wide.value = true
        }
        compose.runOnIdle {
            assertEquals(1, fixture.factories)
            assertEquals(0, fixture.started.get())
        }
        val old = checkNotNull(current)
        compose.runOnIdle { wide.value = false }
        awaitCondition { !old.viewModelScope.isActive }
        compose.runOnIdle { wide.value = true }
        compose.runOnIdle {
            assertNotSame(old, current)
            assertEquals(2, fixture.factories)
            owner.value.registry.currentState = Lifecycle.State.STARTED
        }
        compose.runOnIdle {
            assertEquals(0, fixture.started.get())
            listingVisible.value = true
        }
        awaitCondition { fixture.started.get() == 1 }
    }

    @Test
    fun replacingLifecycleOwnerWhilePreviewHiddenCannotReactivateFromTheOldOwner() {
        owner.value.registry.currentState = Lifecycle.State.STARTED
        show()
        awaitCondition { fixture.started.get() == 1 }
        val originalOwner = owner.value
        val model = checkNotNull(current)
        compose.runOnIdle {
            listingVisible.value = false
            owner.value = SecondaryVisibilityOwner()
        }
        awaitCondition { fixture.cancelled.get() == 1 && noScreenWork(model) }
        compose.runOnIdle {
            originalOwner.registry.currentState = Lifecycle.State.CREATED
            originalOwner.registry.currentState = Lifecycle.State.STARTED
            listingVisible.value = true
        }
        compose.runOnIdle {
            assertEquals(1, fixture.started.get())
            assertSame(model, current)
            owner.value.registry.currentState = Lifecycle.State.STARTED
        }
        awaitCondition { fixture.started.get() == 2 }
    }

    @Test
    fun acceptedCreateAndGlobalQueueCompletionWhilePreviewHiddenDoNotRestartTheListing() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.beforeCreate = { _, _ ->
            entered.complete(Unit)
            release.await()
        }
        owner.value.registry.currentState = Lifecycle.State.STARTED
        show()
        awaitCondition { fixture.started.get() == 1 }
        val model = checkNotNull(current)
        try {
            compose.runOnIdle { model.createFolder("Accepted") }
            awaitCondition { entered.isCompleted }
            compose.runOnIdle { listingVisible.value = false }
            awaitCondition { fixture.cancelled.get() == 1 }
            compose.runOnIdle {
                assertTrue(model.viewModelScope.isActive)
                owner.value.registry.currentState = Lifecycle.State.CREATED
                release.complete(Unit)
                fixture.finishOperation()
            }
            awaitCondition { fixture.completed.get() == 1 && noScreenWork(model) }
            compose.runOnIdle {
                assertEquals(1, fixture.started.get())
                owner.value.registry.currentState = Lifecycle.State.STARTED
            }
            compose.runOnIdle {
                assertEquals(1, fixture.started.get())
                listingVisible.value = true
            }
            awaitCondition { fixture.started.get() == 2 }
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun changingTabsDuringPreviewRetiresOldOwnerAndOnlyStartsTheNewVisiblePane() {
        listingVisible.value = false
        owner.value.registry.currentState = Lifecycle.State.STARTED
        show()
        val old = checkNotNull(current)
        compose.runOnIdle { tab.value = "second" }
        awaitCondition { !old.viewModelScope.isActive }
        compose.runOnIdle {
            assertNotSame(old, current)
            assertEquals(0, fixture.started.get())
            listingVisible.value = true
        }
        awaitCondition { fixture.started.get() == 1 }
    }

    private fun show() {
        compose.setContent {
            if (render.value) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner.value) {
                    val model =
                        rememberSecondaryBrowseViewModel(wide.value, tab.value, fixture.root) {
                            fixture.factory(fixture.root, startVisible = false)
                        }
                    current = model
                    if (model != null) {
                        if (model !in models) models += model
                        SecondaryBrowseVisibility(model, listingVisible.value)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun item(
        name: String,
        directory: Boolean = false,
    ): FileNode =
        FileNode(
            FileNodeId.file("/$name"), name, name, null, 0, 0, directory, false,
            fixture.root, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )

    private fun noScreenWork(model: BrowseViewModel): Boolean =
        model.viewModelScope.coroutineContext.job.children.none { it.isActive }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}

private class SecondaryVisibilityOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle get() = registry
}
