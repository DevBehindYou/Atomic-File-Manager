package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommandPaletteComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactSheetShowsTwoAccessibleTilesPerRow() {
        var dismissed = false
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                CommandPaletteSheet(onRun = {}, onDismiss = { dismissed = true })
            }
        }

        val overlay = compose.onNodeWithTag("commands_overlay").getUnclippedBoundsInRoot()
        val sheet = compose.onNodeWithTag("commands_sheet").getUnclippedBoundsInRoot()
        assertEquals((overlay.left + 16.dp).value, sheet.left.value, 1f)
        assertEquals((overlay.right - 16.dp).value, sheet.right.value, 1f)
        assertEquals(overlay.bottom.value, sheet.bottom.value, 1f)

        val files = compose.onNodeWithTag("palette_files").getUnclippedBoundsInRoot()
        val newFolder = compose.onNodeWithTag("palette_new_folder").getUnclippedBoundsInRoot()
        val tasks = compose.onNodeWithTag("palette_operations").getUnclippedBoundsInRoot()
        assertEquals(files.top.value, newFolder.top.value, 1f)
        assertTrue(files.right < newFolder.left)
        assertTrue(tasks.top > files.top)

        compose.onNodeWithContentDescription("Close commands").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun typingAndEnterRunOnlyTheHighestRankedCommand() {
        val run = mutableListOf<PaletteCommand>()
        compose.setContent {
            AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                CommandPaletteSheet(onRun = { run.add(it) }, onDismiss = {})
            }
        }

        val query = compose.onNodeWithTag("palette_query")
        query.performTextReplacement("no such command")
        compose.onNodeWithText("No command matches “no such command”.").assertExists()
        compose.onNodeWithTag("palette_files").assertDoesNotExist()

        query.performTextReplacement("recycle bin")
        compose.onNodeWithTag("palette_trash").assertExists()
        query.performClick().performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.runOnIdle { assertEquals(listOf(PaletteCommand.TRASH), run) }
    }
}
