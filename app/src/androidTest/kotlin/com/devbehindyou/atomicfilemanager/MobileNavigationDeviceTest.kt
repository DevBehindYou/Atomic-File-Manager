package com.devbehindyou.atomicfilemanager

import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicColorFactory
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileNavigationDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun systemBackReturnsFromStorageToHome() {
        compose.onNodeWithTag("tab_storage").performClick()
        compose.onNodeWithTag("storage_screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun browseBackReturnsToOriginatingStorageTab() {
        compose.onNodeWithTag("tab_storage").performClick()
        compose.onNodeWithTag("tab_browse").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("storage_screen").assertIsDisplayed()
    }

    @Test
    fun activityRecreationKeepsSelectedTab() {
        compose.onNodeWithTag("tab_storage").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("storage_screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun notificationOpenSurvivesRecreationWithoutReplayingAfterBack() {
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_OPERATIONS),
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("operations_screen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("operations_screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun chosenLightThemeRendersAfterRecreationAndOriginalChoiceIsRestored() {
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        val repository = (compose.activity.application as AtomicApp).container.settingsRepository
        val original = repository.settings.value.themeMode
        try {
            compose.runOnIdle { repository.update { it.copy(themeMode = ThemeMode.LIGHT) } }
            compose.activityRule.scenario.recreate()
            val pixels = compose.onNodeWithTag("home_screen").captureToImage().toPixelMap()
            assertEquals(AtomicColorFactory.light().background, pixels[1, 1].toArgb())
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repository.update { it.copy(themeMode = original) }
            }
        }
    }
}
