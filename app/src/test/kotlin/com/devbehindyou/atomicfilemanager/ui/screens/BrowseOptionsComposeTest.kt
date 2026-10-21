package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseOptionsComposeTest {
    @get:Rule val compose = createComposeRule()

    private val viewMode = mutableStateOf(FileViewMode.LIST)
    private val sort = mutableStateOf(SortOption.NAME)
    private val filter = mutableStateOf(BrowseFileFilter.ALL)
    private val hidden = mutableStateOf(false)
    private var foldersCreated = 0
    private var hiddenOpened = 0

    @Test
    fun popupHeadingsStayCenteredBetweenEqualControlAreasOnMainAndSubpages() {
        show(fontScale = 1.5f)
        open()
        assertBalancedPopupHeading()
        compose.onNodeWithContentDescription("Back to file options").assertDoesNotExist()
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithContentDescription("Back to file options").assertExists()
        assertBalancedPopupHeading()
    }

    @Test
    fun popupWrapsItsOptionsAndShrinksForTheShortViewPage() {
        // Large text makes the main page exceed the minimum width even with host font metrics.
        show(fontScale = 1.5f)
        open()
        val main = compose.onNodeWithTag("browse_options_popup").getUnclippedBoundsInRoot()
        val mainWidth = main.right - main.left
        assertTrue("Main options should not fill a phone window", mainWidth < 320.dp)
        compose.onNodeWithText("View").performClick()
        val view = compose.onNodeWithTag("browse_options_popup").getUnclippedBoundsInRoot()
        assertTrue("Short submenu should use less width: main=$main, view=$view", view.right - view.left < mainWidth)
        assertTrue("Short submenu should use less height", view.bottom - view.top < main.bottom - main.top)
        compose.onNodeWithText("List").assertIsSelected()
        compose.onNodeWithText("Grid").performClick()
        compose.runOnIdle { assertEquals(FileViewMode.GRID, viewMode.value) }
    }

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
        compose.onNodeWithText("Date descending").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SortOption.DATE_MODIFIED, sort.value) }
        open()
        compose.onNodeWithText("Filter").performClick()
        compose.onNodeWithText("Images").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(BrowseFileFilter.IMAGES, filter.value) }
        compose.onNodeWithTag("browse_options_popup").assertDoesNotExist()
    }

    @Test
    fun allSixSortDirectionsReportTheirChoiceAndRestoreTheSelectedRadio() {
        val choices =
            listOf(
                "Name A–Z" to SortOption.NAME,
                "Name Z–A" to SortOption.NAME_DESCENDING,
                "Date ascending" to SortOption.DATE_MODIFIED_ASCENDING,
                "Date descending" to SortOption.DATE_MODIFIED,
                "Size ascending" to SortOption.SIZE_ASCENDING,
                "Size descending" to SortOption.SIZE,
            )
        show()
        for ((label, option) in choices) {
            open()
            compose.onNodeWithText("Sort by").performClick()
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithTag("browse_options_popup").assertDoesNotExist()
            compose.runOnIdle { assertEquals(option, sort.value) }
            open()
            compose.onNodeWithText("Sort by").performClick()
            compose.onNodeWithText(label).performScrollTo().assertIsSelected()
            compose.onNodeWithContentDescription("Close file options").performClick()
        }
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

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
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
    }

    private fun open() {
        compose.onNodeWithTag("browse_options_button").performClick()
    }

    private fun assertBalancedPopupHeading() {
        val leading = compose.onNodeWithTag("browse_options_leading").fetchSemanticsNode().boundsInRoot
        val trailing = compose.onNodeWithTag("browse_options_trailing").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("browse_options_heading").fetchSemanticsNode().boundsInRoot
        val popup = compose.onNodeWithTag("browse_options_popup").fetchSemanticsNode().boundsInRoot
        assertEquals("Back and close areas must reserve equal width", leading.width, trailing.width, 0f)
        assertEquals("Title must center between the controls", (leading.right + trailing.left) / 2f, title.center.x, 1f)
        assertTrue("Title must not overlap the controls", title.left >= leading.right && title.right <= trailing.left)
        assertTrue("Controls must stay inside the popup", leading.left >= popup.left && trailing.right <= popup.right)
    }
}
