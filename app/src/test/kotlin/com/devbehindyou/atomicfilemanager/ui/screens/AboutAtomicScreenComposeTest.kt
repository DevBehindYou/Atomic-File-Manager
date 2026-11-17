package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
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
class AboutAtomicScreenComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactAboutExposesRuntimeVersionAndInteractiveRows() {
        val events = mutableListOf<String>()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                AboutAtomicScreen(
                    version = "0.2.7-test",
                    onBack = { events += "back" },
                    onOpenBundledTools = { events += "tools" },
                )
            }
        }

        compose.onNodeWithContentDescription("Version 0.2.7-test").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bundled tools").performClick()

        compose.onNodeWithTag("about_help").performScrollTo().performClick()
        compose.onNodeWithText("1. Open Files and choose a storage location.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close help").performClick()

        compose.onNodeWithTag("about_notices").performScrollTo().performClick()
        compose.onNodeWithText("SIL Open Font License 1.1.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tabler Icons", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Apache License 2.0", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Close font and icon credits").performClick()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(listOf("tools", "back"), events) }
    }
}
