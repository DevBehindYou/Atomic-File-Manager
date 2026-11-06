package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import kotlinx.coroutines.job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Exercises the production model-key boundary with retained, real BrowseViewModels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowseTabSessionModelTest {
    private val compose = createComposeRule()
    private val fixture = BrowsePaneFixture()
    private val store = ViewModelStore()
    private val models = mutableListOf<BrowseViewModel>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @After
    fun clearRetainedModelsBeforeResettingMain() {
        val jobs = models.map { it.viewModelScope.coroutineContext.job }
        compose.runOnIdle { store.clear() }
        awaitCondition { jobs.all { it.isCompleted } }
    }

    @Test
    fun reopeningTheSameNumericTabAndFolderCannotResurrectClosedNavigationOrClipboard() {
        compose.setContent {}
        val source =
            FileNode(
                FileNodeId.file("/session-source.txt"), "source.txt", "source.txt", "text/plain", 1, 1,
                false, false, fixture.root, StorageType.INTERNAL_PRIVATE, AccessFlags.READ_ONLY, null, null,
            )
        fixture.listingItems = listOf(source)
        val opened = BrowseTabsState.single(fixture.root.raw).open(fixture.root.raw)
        val oldTab = opened.active
        val old = model(oldTab)
        val sibling = model(opened.tabs.first())
        compose.runOnIdle {
            sibling.onSearchQueryChanged("surviving sibling")
            old.onVisible(oldTab.openRequest)
        }
        awaitCondition { old.uiState.value.rawItems == listOf(source) }
        compose.runOnIdle {
            old.toggleSelection(source.id)
            old.copySelected()
            old.onSearchQueryChanged("closed-session")
            old.navigateTo(source.copy(id = FileNodeId.file("/session-child"), name = "child", isDirectory = true))
        }
        awaitCondition { old.uiState.value.currentFolderId == FileNodeId.file("/session-child") }
        val clipboard = checkNotNull(old.uiState.value.clipboard)
        compose.runOnIdle {
            old.toggleSelection(source.id)
            old.onHidden()
        }

        val reopened = opened.close(oldTab.id).open(oldTab.folderRaw)
        val fresh = model(reopened.active)
        compose.runOnIdle {
            assertEquals(oldTab.id, reopened.activeId)
            assertEquals(oldTab.openRequest, reopened.active.openRequest)
            assertEquals(oldTab.folderRaw, reopened.active.folderRaw)
            assertNotSame(old, fresh)
            assertEquals(fixture.root, fresh.uiState.value.currentFolderId)
            assertEquals("", fresh.uiState.value.searchQuery)
            assertTrue(fresh.uiState.value.selectedIds.isEmpty())
            assertNull(fresh.uiState.value.clipboard)
            assertFalse(fresh.navigateBack())
            assertSame(clipboard, old.uiState.value.clipboard)
            assertEquals(setOf(source.id), old.uiState.value.selectedIds)
            assertEquals("closed-session", old.uiState.value.searchQuery)
            assertSame(sibling, model(reopened.tabs.first()))
            assertEquals("surviving sibling", sibling.uiState.value.searchQuery)
            assertTrue(old.navigateBack())
        }
    }

    @Test
    fun savedSessionKeysReuseOpenModelsAndClosingASiblingDoesNotReplaceTheSurvivor() {
        compose.setContent {}
        val opened = BrowseTabsState.single(fixture.root.raw).open(fixture.root.raw).open(fixture.root.raw)
        val first = opened.tabs[1]
        val second = opened.active
        val firstModel = model(first)
        val secondModel = model(second)
        compose.runOnIdle {
            firstModel.onSearchQueryChanged("first tab")
            secondModel.onSearchQueryChanged("second tab")
        }
        val restored = BrowseTabsState.decode(opened.encode(), fixture.root.raw)
        val survivor = restored.close(second.id)
        compose.runOnIdle {
            assertSame(firstModel, model(survivor.active))
            assertSame(secondModel, model(restored.active))
            assertEquals("first tab", firstModel.uiState.value.searchQuery)
            assertEquals("second tab", secondModel.uiState.value.searchQuery)
            assertEquals(
                "browse:${fixture.root.raw}",
                primaryBrowseViewModelKey(opened.tabs.first().stateKey, fixture.root),
            )
        }
    }

    private fun model(tab: BrowseTab): BrowseViewModel {
        val root = FileNodeId(tab.folderRaw)
        val key = primaryBrowseViewModelKey(tab.stateKey, root)
        return ViewModelProvider(store, fixture.factory(root, startVisible = false))[key, BrowseViewModel::class.java]
            .also { if (it !in models) models += it }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
