package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Atomic Settings screen still writes every setting it shows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())

        override fun update(transform: (AppSettings) -> AppSettings) {
            settings.value = transform(settings.value)
        }
    }

    private val repository = FakeSettings()
    private var cacheClears = 0
    private val messages = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                SettingsScreen(
                    settingsRepository = repository,
                    hasStorageAccess = true,
                    onRequestStorageAccess = {},
                    onClearScanCache = { cacheClears++ },
                    onNotify = { messages += it },
                )
            }
        }
    }

    @Test
    fun allEightBuiltInPluginsShowTheirVersionAndReturnWithoutChangingPreferences() {
        show()
        val initial = repository.settings.value
        compose.onNodeWithContentDescription("In-app plugins").performScrollTo().performClick()
        val plugins =
            listOf(
                "Image viewer", "Video player", "Audio player", "PDF reader",
                "Document reader", "Code and text editor", "Markdown viewer", "Archive manager",
            )
        plugins.forEachIndexed { index, plugin ->
            compose.onNodeWithContentDescription(plugin).performScrollTo().performClick()
            compose.onNodeWithText("Version v0.2").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Current limitations").assertExists()
            if (index < plugins.lastIndex) {
                compose.onNodeWithContentDescription("Back to plugins").performClick()
                compose.onNodeWithText("Version v0.2").assertDoesNotExist()
            }
        }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("settings_screen").assertExists()
        compose.onNodeWithContentDescription("Back to plugins").assertDoesNotExist()
        assertEquals(initial, repository.settings.value)
        assertEquals(0, cacheClears)
        assertEquals(emptyList<String>(), messages)
    }

    @Test
    fun themeChipSetsTheThemeMode() {
        show()
        compose.onNodeWithContentDescription("Dark").performClick()
        assertEquals(ThemeMode.DARK, repository.settings.value.themeMode)
    }

    @Test
    fun hiddenFilesCardTogglesTheSetting() {
        show()
        compose.onNodeWithContentDescription("Show hidden files").performScrollTo().performClick()
        assertFalse(repository.settings.value.showHiddenFiles)
    }

    @Test
    fun dangerZoneClearsTheScanCacheAndSaysSo() {
        show()
        compose.onNodeWithContentDescription("Execute").performScrollTo().performClick()
        assertEquals(1, cacheClears)
        assertEquals(listOf("File scan cache cleared."), messages)
    }
}
