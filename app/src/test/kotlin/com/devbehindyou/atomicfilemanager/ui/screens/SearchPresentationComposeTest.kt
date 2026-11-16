package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.SearchFilter
import com.devbehindyou.atomicfilemanager.domain.model.SearchHit
import com.devbehindyou.atomicfilemanager.domain.model.SearchIndexState
import com.devbehindyou.atomicfilemanager.domain.repository.SearchIndex
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchPresentationComposeTest {
    @get:Rule val compose = createComposeRule()

    private val root = FileNodeId.saf("content://fixture/tree/primary%3ATest")
    private val folder =
        SearchHit(
            id = FileNodeId.saf("content://fixture/tree/primary%3ATest/document/primary%3ATest%2FWork"),
            name = "Work folder",
            parentId = root,
            isDirectory = true,
            size = -1,
            modifiedAt = 0,
            mimeType = null,
        )

    @Test
    fun folderFilterAndClearKeepSearchResultsInSync() {
        val index = FakeIndex()
        val destinations = mutableListOf<FileNodeId>()
        showSearch(index) { destinations += it }

        compose.onNodeWithText("Folders").performClick().assertIsSelected()
        compose.onNodeWithTag("search_field").performTextReplacement("Work")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText(folder.name).assertIsDisplayed()
        compose.runOnIdle { assertEquals("Work" to SearchFilter.FOLDERS, index.queries.last()) }

        compose.onNodeWithText(folder.name).performClick()
        compose.onNodeWithContentDescription("Open the folder holding ${folder.name}").performClick()
        compose.runOnIdle { assertEquals(listOf(folder.id, root), destinations) }

        compose.onNodeWithContentDescription("Clear search").performClick().assertIsNotEnabled()
        compose.onNodeWithText(folder.name).assertDoesNotExist()
        compose.onNodeWithText("Search file names across accessible storage.").assertIsDisplayed()
    }

    @Test
    fun indexStatusShowsLiveMetricsAndRebuildsOnlyAccessibleRoots() {
        val index = FakeIndex()
        showSearch(index)

        compose.onNodeWithContentDescription("Index status").performClick()
        val status = compose.onNodeWithTag("search_index_status")
        status.assertTextContains("12 items", substring = true)
        compose.onNodeWithText("Folders that couldn’t be read: 2").assertIsDisplayed()

        compose.onNodeWithTag("search_rebuild").performClick().assertIsNotEnabled()
        status.assertTextContains("Indexing · 12 items so far")
        compose.runOnIdle { assertEquals(listOf(listOf(root)), index.rebuilds) }
    }

    private fun showSearch(
        index: SearchIndex,
        openFolder: (FileNodeId) -> Unit = {},
    ) {
        val getNode = GetNodeUseCase { error("Opening a folder should use its index identity directly") }
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                SearchScreen(index, listOf(root), getNode, onBack = {}, onOpenFolder = openFolder)
            }
        }
    }

    private inner class FakeIndex : SearchIndex {
        override val state = MutableStateFlow(SearchIndexState(indexedCount = 12, builtAt = 1L, unreadable = 2))
        val queries = mutableListOf<Pair<String, SearchFilter>>()
        val rebuilds = mutableListOf<List<FileNodeId>>()

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<SearchHit> = error("Search must forward the selected filter")

        override suspend fun search(
            query: String,
            limit: Int,
            filter: SearchFilter,
        ): List<SearchHit> {
            queries += query to filter
            return listOf(folder)
        }

        override fun refreshIfStale(roots: List<FileNodeId>) = Unit

        override fun rebuild(roots: List<FileNodeId>) {
            rebuilds += roots
            state.value = state.value.copy(building = true)
        }

        override suspend fun refreshFolders(folders: Collection<FileNodeId>) = Unit
    }
}
