package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Looper
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowsePaneLifecycleTest {
    val compose = createComposeRule()

    val fixture = BrowsePaneFixture()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test
    fun compactPaneDoesNotCreateFactoryOrStartListing() {
        compose.setContent {
            rememberSecondaryBrowseViewModel(false, "tab", fixture.root) { fixture.factory(fixture.root) }
        }
        compose.runOnIdle {
            assertEquals(0, fixture.factories)
            assertEquals(0, fixture.started.get())
        }
    }

    @Test
    fun removingPaneCancelsItsListingAndOperationCollectors() {
        val visible = mutableStateOf(true)
        var model: BrowseViewModel? = null
        compose.setContent {
            if (visible.value) {
                model = rememberSecondaryBrowseViewModel(true, "tab", fixture.root) { fixture.factory(fixture.root) }
            }
        }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle { visible.value = false }
        awaitCondition { fixture.cancelled.get() == 1 }
        compose.runOnIdle {
            assertFalse(checkNotNull(model).viewModelScope.isActive)
            fixture.finishOperation()
        }
        awaitCondition { fixture.completed.get() == 1 }
        compose.runOnIdle { assertEquals(1, fixture.started.get()) }
    }

    @Test
    fun returningToCompactLayoutClearsSecondaryAndReopeningCreatesOneFreshListing() {
        val wide = mutableStateOf(true)
        compose.setContent {
            rememberSecondaryBrowseViewModel(wide.value, "tab", fixture.root) { fixture.factory(fixture.root) }
        }
        awaitCondition { fixture.started.get() == 1 }
        compose.runOnIdle { wide.value = false }
        awaitCondition { fixture.cancelled.get() == 1 }
        compose.runOnIdle { wide.value = true }
        awaitCondition { fixture.started.get() == 2 }
        compose.runOnIdle {
            assertEquals(2, fixture.factories)
            assertEquals(1, fixture.cancelled.get())
        }
    }

    @Test
    fun tabSwitchAtSameFolderReplacesModelAndCancelsOldListing() {
        val tab = mutableStateOf("first")
        var model: BrowseViewModel? = null
        compose.setContent {
            model = rememberSecondaryBrowseViewModel(true, tab.value, fixture.root) { fixture.factory(fixture.root) }
        }
        awaitCondition { fixture.started.get() == 1 }
        val original = compose.runOnIdle { model }
        compose.runOnIdle { tab.value = "second" }
        awaitCondition { fixture.started.get() == 2 && fixture.cancelled.get() == 1 }
        compose.runOnIdle {
            assertNotSame(original, model)
            assertFalse(checkNotNull(original).viewModelScope.isActive)
        }
    }

    @Test
    fun rootReplacementClearsOldModelButOrdinaryRecompositionKeepsIt() {
        val folder = mutableStateOf(fixture.root)
        val redraw = mutableStateOf(false)
        var model: BrowseViewModel? = null
        compose.setContent {
            BasicText(if (redraw.value) "Updated" else "Initial")
            model = rememberSecondaryBrowseViewModel(true, "tab", folder.value) { fixture.factory(folder.value) }
        }
        awaitCondition { fixture.started.get() == 1 }
        val original = compose.runOnIdle { model }
        compose.runOnIdle { redraw.value = true }
        compose.runOnIdle {
            assertSame(original, model)
            assertEquals(1, fixture.factories)
            folder.value = FileNodeId.file("/other")
        }
        awaitCondition { fixture.started.get() == 2 && fixture.cancelled.get() == 1 }
        compose.runOnIdle {
            assertNotSame(original, model)
            assertFalse(checkNotNull(original).viewModelScope.isActive)
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            // ViewModel cancellation resumes on Android's main Handler. Compose's frame
            // clock does not execute messages on Robolectric's paused Android looper.
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
