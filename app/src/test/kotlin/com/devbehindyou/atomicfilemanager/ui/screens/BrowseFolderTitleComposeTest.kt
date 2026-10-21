package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseFolderTitleComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun storageNameUsesTheWholeFirstRowAndMetadataIsBelowAtTheRight() {
        val title = "Internal shared storage"
        show(title, 320.dp)
        val layout = titleLayout()
        assertEquals(1, layout.lineCount)
        assertFalse(layout.isLineEllipsized(0))
        assertRowsAndAccessibleSummary(title)
    }

    @Test
    fun narrowWindowEllipsizesOnlyTheLongTitleAndRetainsItsFullAccessibleName() {
        val title = "A very long folder name with important details at the end"
        show(title, 220.dp, fontScale = 1.4f)
        val layout = titleLayout()
        assertEquals(1, layout.lineCount)
        assertTrue(layout.isLineEllipsized(0))
        assertRowsAndAccessibleSummary(title)
    }

    private fun show(
        title: String,
        width: Dp,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    BrowseFolderTitle(title, 13, SortOption.DATE_MODIFIED, Modifier.width(width))
                }
            }
        }
    }

    private fun titleLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("browse_folder_title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(results)
        }
        return results.single()
    }

    private fun assertRowsAndAccessibleSummary(title: String) {
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithContentDescription("13 items · Date descending").assertExists()
        val name = compose.onNodeWithTag("browse_folder_title").fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithTag("browse_folder_metadata").fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithTag("browse_folder_summary").fetchSemanticsNode().boundsInRoot
        assertTrue("The complete title row must be above the metadata", name.bottom <= metadata.top)
        assertEquals("Metadata must end at the right edge", metadata.right, summary.right, 1f)
        assertEquals("Metadata must not take width away from the title", metadata.width, name.width, 1f)
        assertTrue("Metadata must remain inside its row", summary.left >= metadata.left)
    }
}
