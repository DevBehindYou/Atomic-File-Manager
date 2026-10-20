package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrowseOptionsComposeTest {
    @get:Rule val compose = createComposeRule()

    private val viewMode = mutableStateOf(FileViewMode.LIST)
    private val sort = mutableStateOf(SortOption.NAME)
    private val filter = mutableStateOf(BrowseFileFilter.ALL)
    private val hidden = mutableStateOf(false)
    private var foldersCreated = 0
    private var hiddenOpened = 0

    @Test
    fun choicesReportViewSortAndFilterThenDismiss() {
        show()
        open()
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("List").assertIsSelected()
        compose.onNodeWithText("Grid").performClick()
        compose.onNodeWithTag("browse_options_popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(FileViewMode.GRID, viewMode.value) }
        open()
        compose.onNodeWithText("Sort by").performClick()
        compose.onNodeWithText("Date modified").performClick()
        compose.runOnIdle { assertEquals(SortOption.DATE_MODIFIED, sort.value) }
        open()
        compose.onNodeWithText("Filter").performClick()
        compose.onNodeWithText("Images").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(BrowseFileFilter.IMAGES, filter.value) }
        compose.onNodeWithTag("browse_options_popup").assertDoesNotExist()
    }

    @Test
    fun hiddenToggleStaysOpenAndNewFolderDismissesBeforeRouting() {
        show()
        open()
        compose.onNodeWithText("Show hidden files").performScrollTo().performClick()
        compose.onNodeWithText("Show hidden files").assertIsOn()
        compose.onNodeWithTag("browse_options_popup").assertExists()
        compose.runOnIdle { assertTrue(hidden.value) }
        compose.onNodeWithText("New folder").performScrollTo().performClick()
        compose.onNodeWithTag("browse_options_popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, foldersCreated) }
        open()
        compose.onNodeWithText("Manage hidden files").performScrollTo().performClick()
        compose.onNodeWithTag("browse_options_popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, hiddenOpened) }
    }

    @Test
    fun backAndCloseDoNotApplyAChoiceAndReopeningStartsAtMainPage() {
        show()
        open()
        compose.onNodeWithText("Filter").performClick()
        compose.onNodeWithContentDescription("Back to file options").performClick()
        compose.onNodeWithText("New folder").assertExists()
        compose.onNodeWithContentDescription("Close file options").performClick()
        compose.runOnIdle { assertEquals(BrowseFileFilter.ALL, filter.value) }
        open()
        compose.onNodeWithText("View").assertExists()
        compose.onNodeWithContentDescription("Close file options").performClick()
    }

    private fun show() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                BrowseOptionsButton(
                    viewMode.value,
                    { viewMode.value = it },
                    sort.value,
                    { sort.value = it },
                    filter.value,
                    { filter.value = it },
                    hidden.value,
                    { hidden.value = it },
                    { foldersCreated++ },
                    { hiddenOpened++ },
                )
            }
        }
    }

    private fun open() {
        compose.onNodeWithTag("browse_options_button").performClick()
    }
}
