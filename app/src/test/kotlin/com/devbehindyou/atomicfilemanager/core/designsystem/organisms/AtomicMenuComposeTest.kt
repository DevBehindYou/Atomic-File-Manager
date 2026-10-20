package com.devbehindyou.atomicfilemanager.core.designsystem.organisms

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w500dp-h800dp", application = Application::class)
class AtomicMenuComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun commandsReflowIntoColumnsAndHeightShrinksWithTheActualContent() {
        val width = mutableStateOf(320.dp)
        val labels = mutableStateOf(listOf("Copy", "Move", "Rename", "Info"))
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                AtomicCommandGrid(
                    labels.value.map { AtomicMenuAction(it, AtomicIcons.Document, {}) },
                    Modifier.width(width.value).testTag("command_grid"),
                )
            }
        }
        val first = bounds("Copy")
        val second = bounds("Move")
        val third = bounds("Rename")
        assertEquals(first.top, second.top)
        assertTrue(second.left > first.right)
        assertTrue(third.top > first.bottom)
        compose.runOnIdle { width.value = 500.dp }
        assertEquals(bounds("Copy").top, bounds("Rename").top)
        assertTrue(bounds("Info").top > bounds("Copy").bottom)
        val twoRowsHeight = compose.onNodeWithTag("command_grid").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { labels.value = listOf("Copy", "Move") }
        val oneRowHeight = compose.onNodeWithTag("command_grid").fetchSemanticsNode().boundsInRoot.height
        assertTrue(oneRowHeight < twoRowsHeight)
    }

    @Test
    fun callbacksAndDisabledDestructiveSemanticsSurviveTheCardPresentation() {
        val invoked = mutableListOf<String>()
        compose.setContent {
            AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                AtomicCommandGrid(
                    listOf(
                        AtomicMenuAction("Copy", AtomicIcons.Copy, { invoked += "copy" }),
                        AtomicMenuAction(
                            "Delete forever",
                            AtomicIcons.Trash,
                            { invoked += "delete" },
                            destructive = true,
                        ),
                        AtomicMenuAction("Save APK", AtomicIcons.Apk, { invoked += "save" }, enabled = false),
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("Copy").performClick()
        compose.onNodeWithContentDescription("Delete forever")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Destructive action"))
            .performClick()
        compose.onNodeWithContentDescription("Save APK").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("copy", "delete"), invoked) }
    }

    private fun bounds(label: String) = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
}
