package com.devbehindyou.atomicfilemanager

import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicColorFactory
import com.devbehindyou.atomicfilemanager.data.volume.StorageVolumes
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class MobileNavigationDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun filesStorageShortcutsViewsAndToolbarOptionsRemainUsable() {
        val repository = (compose.activity.application as AtomicApp).container.settingsRepository
        val original = repository.settings.value
        try {
            compose.onNodeWithTag("tab_browse").performClick()
            compose.onNodeWithTag("browse_storage_shortcuts").assertIsDisplayed()
            val volumes =
                runBlocking {
                    withContext(Dispatchers.IO) { StorageVolumes.enumerate(compose.activity) }
                }.filter { it.isMounted && it.rootNodeId != null }
            for (volume in volumes.take(2)) {
                compose.onNodeWithContentDescription("Open storage ${volume.label}").performScrollTo().performClick()
                compose.onNodeWithTag("browse_current_storage").assertTextEquals("Current storage: ${volume.label}")
            }
            volumes.firstOrNull()?.let { volume ->
                compose.onNodeWithContentDescription("Open storage ${volume.label}").performScrollTo().performClick()
            }
            compose.onNodeWithTag("tasks_button").performClick()
            compose.onNodeWithContentDescription("Close tasks").performClick()
            compose.onNodeWithTag("browse_options_button").performClick()
            compose.onNodeWithText("View").performClick()
            compose.onNodeWithText("Grid").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("file_grid").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(FileViewMode.GRID, repository.settings.value.fileViewMode)
            compose.onNodeWithTag("browse_options_button").performClick()
            compose.onNodeWithText("Sort by").performClick()
            compose.onNodeWithText("Size").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back to file options").performClick()
            compose.onNodeWithText("Show hidden files").performClick()
            assertEquals(!original.showHiddenFiles, repository.settings.value.showHiddenFiles)
            compose.onNodeWithText("New folder").performClick()
            compose.onNodeWithContentDescription("Cancel").performClick()
            compose.onNodeWithTag("browse_options_button").performClick()
            compose.onNodeWithText("View").performClick()
            compose.onNodeWithText("List").performClick()
            compose.onNodeWithTag("file_list").assertIsDisplayed()
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repository.update {
                    it.copy(
                        fileViewMode = original.fileViewMode,
                        showHiddenFiles = original.showHiddenFiles,
                    )
                }
            }
        }
    }

    @Test
    fun tasksPopupDismissesAndLinksToOperations() {
        compose.onNodeWithTag("tasks_button").performClick()
        compose.onNodeWithTag("tasks_popup").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close tasks").performClick()
        compose.onNodeWithTag("tasks_popup").assertDoesNotExist()
        compose.onNodeWithTag("tasks_button").performClick()
        compose.onNodeWithContentDescription("All operations →").performClick()
        compose.onNodeWithTag("operations_screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

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
        recreateActivity()
        compose.onNodeWithTag("storage_screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun notificationOpenSurvivesRecreationWithoutReplayingAfterBack() {
        compose.onNodeWithTag("home_screen").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(
                // ActivityScenario matches lifecycle events against the launch intent's
                // action/categories. Retain that identity while delivering new extras.
                Intent(activity.intent)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_OPERATIONS),
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("operations_screen").fetchSemanticsNodes().isNotEmpty()
        }
        recreateActivity()
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
            recreateActivity()
            val pixels = compose.onNodeWithTag("home_screen").captureToImage().toPixelMap()
            assertEquals(AtomicColorFactory.light().background, pixels[1, 1].toArgb())
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repository.update { it.copy(themeMode = original) }
            }
        }
    }

    private fun recreateActivity() {
        compose.waitForIdle()
        val executor = Executors.newSingleThreadExecutor()
        try {
            // A blocking recreate waits for Android idle while the splash waits for a
            // ready composition. Keep advancing Compose frames while Android recreates.
            val recreated = executor.submit { compose.activityRule.scenario.recreate() }
            compose.waitUntil(timeoutMillis = 10_000) { recreated.isDone }
            recreated.get()
        } finally {
            executor.shutdownNow()
        }
        compose.waitForIdle()
    }
}
