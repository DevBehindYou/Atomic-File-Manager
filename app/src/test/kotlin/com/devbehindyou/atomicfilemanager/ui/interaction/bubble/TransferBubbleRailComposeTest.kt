package com.devbehindyou.atomicfilemanager.ui.interaction.bubble

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual Compose pointer dispatch; these are host consumers, not physical-device claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h800dp")
class TransferBubbleRailComposeTest {
    @get:Rule
    val compose = createComposeRule()
    private var clicked: String? = null
    private var held: String? = null
    private var creations = 0

    @Before
    fun resetDockPosition() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            BubblePositionPersistence(context).save(this, BubblePosition(1f, 0.5f)).await().getOrThrow()
        }

    @Test
    fun compactRailScrollsToSeventhBubbleAndKeepsClickAndHold() {
        content(count = 7)
        val seventh = compose.onNodeWithContentDescription("Bubble 7, 0 items")
        seventh.assertIsNotDisplayed()
        repeat(3) { compose.onNodeWithTag("transfer_bubble_rail").performTouchInput { swipeUp() } }
        seventh.assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals("bubble-7", clicked) }
        seventh.performTouchInput { longClick() }
        compose.runOnIdle { assertEquals("bubble-7", held) }
        compose.onNodeWithContentDescription("Create transfer bubble").assertDoesNotExist()
    }

    @Test
    fun compactRailKeepsCreateActionReachableBelowSeven() {
        content(count = 6)
        repeat(3) { compose.onNodeWithTag("transfer_bubble_rail").performTouchInput { swipeUp() } }
        compose.onNodeWithContentDescription("Create transfer bubble").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, creations) }
    }

    @Test
    fun overflowScrollingDoesNotStealHorizontalRailDocking() {
        content(count = 7)
        val rail = compose.onNodeWithTag("transfer_bubble_rail")
        val viewport = compose.onNodeWithTag("bubble_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue(rail.fetchSemanticsNode().boundsInRoot.left > viewport.center.x)
        rail.performTouchInput { swipe(center, center - Offset(viewport.width * 0.75f, 0f), durationMillis = 600) }
        compose.waitForIdle()
        assertTrue(rail.fetchSemanticsNode().boundsInRoot.left < viewport.center.x)
        compose.onNodeWithContentDescription("Bubble 1, 0 items").assertIsDisplayed()
    }

    @Test
    fun railStillMovesVerticallyWhenAllContentsFit() {
        content(count = 1, height = 640.dp)
        val rail = compose.onNodeWithTag("transfer_bubble_rail")
        val before = rail.fetchSemanticsNode().boundsInRoot.top
        rail.performTouchInput { swipe(center, center - Offset(0f, 90f), durationMillis = 600) }
        compose.waitForIdle()
        assertTrue(rail.fetchSemanticsNode().boundsInRoot.top < before - 24f)
    }

    private fun content(
        count: Int,
        height: Dp = 360.dp,
    ) {
        val bubbles = List(count) { index -> TransferBubble("bubble-${index + 1}", "Bubble ${index + 1}") }
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Box(Modifier.requiredSize(320.dp, height).testTag("bubble_viewport")) {
                    TransferBubbleRail(
                        bubbles = bubbles,
                        dragController = FileDragController(),
                        onBubbleClick = { clicked = it.id },
                        onBubbleLongClick = { held = it.id },
                        onCreateBubble = { creations++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }
}
