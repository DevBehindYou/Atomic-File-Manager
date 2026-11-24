package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InAppPluginsScreenComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun compactInventoryStacksCards() {
        showInventory()

        val first = compose.onNodeWithTag("plugins_details_image").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("plugins_details_video").getUnclippedBoundsInRoot()
        assertEquals(first.right.value, second.right.value, 1f)
        assertTrue(first.bottom < second.top)
    }

    @Test
    @Config(qualifiers = "w720dp-h640dp")
    fun expandedInventoryPairsCards() {
        showInventory()

        val first = compose.onNodeWithTag("plugins_details_image").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("plugins_details_video").getUnclippedBoundsInRoot()
        assertEquals(first.top.value, second.top.value, 1f)
        assertTrue(first.right < second.left)
    }

    private fun showInventory() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                InAppPluginsScreen(onBack = {})
            }
        }
    }
}
