package com.devbehindyou.atomicfilemanager.ui.startup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStartupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun failedSettingsOfferRetryAndCloseWithoutOpeningAppContent() {
        var retries = 0
        var closes = 0
        compose.setContent { SettingsStartupScreen(loading = false, onRetry = { retries++ }, onClose = { closes++ }) }
        compose.onNodeWithTag("settings_startup_screen").assertIsDisplayed()
        compose.onNodeWithTag("home_screen").assertDoesNotExist()
        compose.onNodeWithTag("settings_startup_retry").performClick()
        compose.onNodeWithTag("settings_startup_close").performClick()
        assertEquals(1, retries)
        assertEquals(1, closes)
    }

    @Test
    fun aRetryInProgressDoesNotOfferAnotherRetryButCanStillBeClosed() {
        var closes = 0
        compose.setContent {
            SettingsStartupScreen(loading = true, onRetry = { error("duplicate retry") }, onClose = { closes++ })
        }
        compose.onNodeWithTag("settings_startup_retry").assertDoesNotExist()
        compose.onNodeWithTag("settings_startup_close").performClick()
        assertEquals(1, closes)
    }
}
