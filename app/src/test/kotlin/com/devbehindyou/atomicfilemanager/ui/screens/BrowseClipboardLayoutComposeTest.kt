package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseClipboardLayoutComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun copiedFolderActionsStayWithinA320DpWindow() = verifyCompactActions(fontScale = 1f)

    @Test
    fun copiedFolderActionsStayReachableAtLargerText() = verifyCompactActions(fontScale = 1.6f)

    private fun verifyCompactActions(fontScale: Float) {
        val source = FileNodeId.file("/fixture/source")
        val destination = FileNodeId.file("/fixture/destination")
        val state =
            BrowseUiState(
                destination,
                clipboard = ClipboardState(listOf(folder(source, "Source")), ClipboardOp.COPY),
            )
        val actions = mutableListOf<String>()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.width(320.dp)) {
                        BrowseBottomBar(
                            uiState = state,
                            isSelectionMode = false,
                            onCopySelected = {},
                            onCutSelected = {},
                            onCompressSelected = {},
                            onDeleteSelected = {},
                            onShowDetails = {},
                            onClearClipboard = { actions += "cancel" },
                            onPaste = { actions += "paste" },
                            onCompareFolders = { from, to ->
                                assertEquals(source, from)
                                assertEquals(destination, to)
                                actions += "compare"
                            },
                        )
                    }
                }
            }
        }

        val status = compose.onNodeWithContentDescription("1 ready to copy").getUnclippedBoundsInRoot()
        for (label in listOf("Cancel", "Compare", "Paste here")) {
            val button = compose.onNodeWithContentDescription(label).assertIsDisplayed()
            val bounds = button.getUnclippedBoundsInRoot()
            assertTrue("Status should be above $label", status.bottom <= bounds.top)
            assertTrue("$label must fit within the narrow bar", bounds.left >= 0.dp && bounds.right <= 320.dp)
            button.performClick()
        }
        compose.runOnIdle { assertEquals(listOf("cancel", "compare", "paste"), actions) }
    }
}
