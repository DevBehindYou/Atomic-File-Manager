package com.devbehindyou.atomicfilemanager.ui.security

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h820dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivateFilesAuthGateComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lockedRouteDoesNotComposePrivateContent() {
        var denied = 0
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                AuthGate(required = true, title = "Unlock private files", onDenied = { denied++ }, manualUnlock = true) {
                    Text("Example private file")
                }
            }
        }

        compose.onNodeWithContentDescription("Your private space").assertIsDisplayed()
        compose.onNodeWithText("Example private file").assertDoesNotExist()
        compose.onNodeWithText("Go back").performClick()
        assertEquals(1, denied)
    }
}
