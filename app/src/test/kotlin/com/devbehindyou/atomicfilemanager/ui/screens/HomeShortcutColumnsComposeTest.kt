package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1200dp-h900dp")
class HomeShortcutColumnsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shortcutsUseTheirContentWidthAndStackWhenTextIsLarge() {
        val width = mutableIntStateOf(840)
        val fontScale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                Box(Modifier.width(width.intValue.dp)) {
                    HomeShortcutColumns(
                        history = { Box(Modifier.width(120.dp).height(72.dp).testTag("history")) },
                        pins = { Box(Modifier.width(120.dp).height(72.dp).testTag("pins")) },
                    )
                }
            }
        }

        val (wideHistory, widePins) = bounds()
        assertEquals(wideHistory.top, widePins.top)
        assertTrue(wideHistory.right < widePins.left)

        compose.runOnIdle { width.intValue = 760 }
        assertStacked()

        compose.runOnIdle {
            width.intValue = 840
            fontScale.value = 1.6f
        }
        assertStacked()
    }

    private fun assertStacked() {
        val (history, pins) = bounds()
        assertEquals(history.left, pins.left)
        assertTrue(history.bottom < pins.top)
    }

    private fun bounds() =
        compose.onNodeWithTag("history").getUnclippedBoundsInRoot() to
            compose.onNodeWithTag("pins").getUnclippedBoundsInRoot()
}
