package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Looper
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
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
class PrimaryBrowseVisibilityTest {
    private val compose = createComposeRule()
    private val fixture = BrowsePaneFixture()
    private val store = ViewModelStore()
    private val models = mutableListOf<BrowseViewModel>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @After
    fun clearRetainedOwner() {
        val jobs = models.map { it.viewModelScope.coroutineContext.job }
        compose.runOnIdle { store.clear() }
        awaitCondition { jobs.all { it.isCompleted } }
    }

    @Test
    fun hiddenPrimaryDoesNotListOrRefreshForOperationsAndReturningKeepsTheModel() {
        val visible = mutableStateOf(false)
        val redraw = mutableStateOf(false)
        val model = model("first", fixture.root)
        compose.setContent {
            BasicText(if (redraw.value) "Updated" else "Initial")
            if (visible.value) PrimaryBrowseVisibility(model, 0)
        }
        compose.runOnIdle {
            assertEquals(0, fixture.started.get())
            visible.value = true
        }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle { redraw.value = true }
        compose.runOnIdle {
            assertEquals(1, fixture.started.get())
            visible.value = false
        }
        awaitCondition { fixture.cancelled.get() == 1 }
        compose.runOnIdle { fixture.finishOperation() }
        awaitCondition { fixture.completed.get() == 1 }
        compose.runOnIdle {
            assertEquals(1, fixture.started.get())
            assertTrue(model.viewModelScope.isActive)
            visible.value = true
        }
        awaitCondition { fixture.started.get() == 2 }
        compose.runOnIdle {
            assertSame(model, model("first", fixture.root))
        }
    }

    @Test
    fun changedExplicitOpenWhileReturningStartsOnlyOneListing() {
        val visible = mutableStateOf(true)
        val request = mutableStateOf(0)
        val model = model("first", fixture.root)
        compose.setContent { if (visible.value) PrimaryBrowseVisibility(model, request.value) }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle { visible.value = false }
        awaitCondition { fixture.cancelled.get() == 1 }
        compose.runOnIdle {
            request.value = 1
            visible.value = true
        }
        awaitCondition { fixture.started.get() == 2 }
        compose.runOnIdle { assertEquals(2, fixture.started.get()) }
    }

    @Test
    fun replacingTheActiveRootPausesTheOldPrimaryWithoutDestroyingItsStateOwner() {
        val first = model("first", fixture.root)
        val second = model("second", FileNodeId.file("/other"))
        val current = mutableStateOf(first)
        compose.setContent { PrimaryBrowseVisibility(current.value, 0) }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle { current.value = second }
        awaitCondition { fixture.started.get() == 2 && fixture.cancelled.get() == 1 }
        compose.runOnIdle {
            assertTrue(first.viewModelScope.isActive)
            assertTrue(second.viewModelScope.isActive)
            fixture.finishOperation()
        }
        awaitCondition { fixture.completed.get() == 1 && fixture.started.get() == 3 }
        // Only the visible second model refreshes for this completion.
        compose.runOnIdle { assertEquals(3, fixture.started.get()) }
    }

    private fun model(
        key: String,
        folder: FileNodeId,
    ): BrowseViewModel =
        ViewModelProvider(store, fixture.factory(folder, startVisible = false))[key, BrowseViewModel::class.java]
            .also { if (it !in models) models += it }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
