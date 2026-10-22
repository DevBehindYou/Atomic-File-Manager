package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowseSecondaryWorkDrainComposeTest {
    private val compose = createComposeRule()
    private val fixture = BrowsePaneFixture()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test
    fun removingPaneStopsObservationButDrainsBothAcceptedCreateAndRenameBeforeClearingStore() {
        val create = CompletableDeferred<Unit>()
        val rename = CompletableDeferred<Unit>()
        val entered = AtomicInteger()
        val finished = AtomicInteger()
        fixture.beforeCreate = { _, _ ->
            entered.incrementAndGet()
            create.await()
            finished.incrementAndGet()
        }
        fixture.beforeRename = { _, _ ->
            entered.incrementAndGet()
            rename.await()
            finished.incrementAndGet()
        }
        val visible = mutableStateOf(true)
        var model: BrowseViewModel? = null
        compose.setContent {
            if (visible.value) {
                model = rememberSecondaryBrowseViewModel(true, "tab", fixture.root) { fixture.factory(fixture.root) }
            }
        }
        awaitCondition { fixture.started.get() == 1 }
        val old = checkNotNull(model)
        try {
            compose.runOnIdle {
                old.createFolder("accepted")
                old.rename(FileNodeId("memory:rename-target"), "renamed")
            }
            awaitCondition { entered.get() == 2 }
            compose.runOnIdle { visible.value = false }
            awaitCondition { fixture.cancelled.get() == 1 }
            compose.runOnIdle {
                assertTrue("Accepted mutations must retain their own model until they drain", old.viewModelScope.isActive)
                create.complete(Unit)
            }
            awaitCondition { finished.get() == 1 }
            compose.runOnIdle {
                assertTrue(old.viewModelScope.isActive)
                rename.complete(Unit)
            }
            awaitCondition { !old.viewModelScope.isActive }
            compose.runOnIdle {
                assertEquals(2, finished.get())
                assertEquals("Late success must not restart a forgotten listing", 1, fixture.started.get())
            }
        } finally {
            create.complete(Unit)
            rename.complete(Unit)
            compose.runOnIdle { visible.value = false }
            awaitCondition { !old.viewModelScope.isActive }
        }
    }

    @Test
    fun anOldRootDrainingAfterRootAToBToACannotClearTheFreshRootAModel() {
        val release = CompletableDeferred<Unit>()
        val entered = AtomicInteger()
        fixture.beforeCreate = { _, _ ->
            entered.incrementAndGet()
            release.await()
        }
        val root = mutableStateOf(fixture.root)
        val visible = mutableStateOf(true)
        var model: BrowseViewModel? = null
        compose.setContent {
            if (visible.value) {
                model = rememberSecondaryBrowseViewModel(true, "tab", root.value) { fixture.factory(root.value) }
            }
        }
        awaitCondition { fixture.started.get() == 1 }
        val original = checkNotNull(model)
        try {
            compose.runOnIdle { original.createFolder("accepted") }
            awaitCondition { entered.get() == 1 }
            compose.runOnIdle { root.value = FileNodeId("memory:other-root") }
            awaitCondition { fixture.started.get() == 2 }
            val middle = checkNotNull(model)
            compose.runOnIdle { root.value = fixture.root }
            awaitCondition { fixture.started.get() == 3 }
            val fresh = checkNotNull(model)
            compose.runOnIdle {
                assertNotSame(original, fresh)
                assertFalse(middle.viewModelScope.isActive)
                assertTrue(original.viewModelScope.isActive)
                release.complete(Unit)
            }
            awaitCondition { !original.viewModelScope.isActive }
            compose.runOnIdle {
                assertTrue(fresh.viewModelScope.isActive)
                assertEquals(3, fixture.started.get())
            }
        } finally {
            release.complete(Unit)
            compose.runOnIdle { visible.value = false }
            awaitCondition { !original.viewModelScope.isActive && !checkNotNull(model).viewModelScope.isActive }
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
