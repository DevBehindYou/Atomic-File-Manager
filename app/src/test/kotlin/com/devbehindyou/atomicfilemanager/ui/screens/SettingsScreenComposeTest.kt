package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    private fun show(destinations: SettingsDestinations = SettingsDestinations(onOpenAbout = {}, onOpenPlugins = {})) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                var showPlugins by remember { mutableStateOf(false) }
                if (showPlugins) {
                    InAppPluginsScreen(onBack = { showPlugins = false })
                } else {
                    SettingsScreen(
                        settingsRepository = repository,
                        hasStorageAccess = true,
                        onRequestStorageAccess = {},
                        onClearScanCache = { cacheClears++ },
                        onNotify = { messages += it },
                        destinations = destinations.copy(onOpenPlugins = { showPlugins = true }),
                    )
                }
            }
        }
    }

    @Test
    fun groupedRootKeepsPreferenceActionsInTheirDestinations() {
        show()
        listOf("Make it yours", "Access & privacy", "About").forEach { heading ->
            compose.onNodeWithContentDescription(heading).assertExists()
        }
        listOf(
            "Appearance",
            "File browsing",
            "Opening files",
            "In-app plugins",
            "Storage permissions",
            "Private files",
            "Hidden files",
            "Transfer bubbles",
            "About Atomic",
            "Cloud Connect",
        ).forEach { destination ->
            compose.onNodeWithContentDescription(destination).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Dark").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show hidden files").assertDoesNotExist()
        compose.onNodeWithContentDescription("Execute").assertDoesNotExist()
    }

    @Test
    fun aboutOpensTheSharedDestination() {
        var aboutOpens = 0
        show(SettingsDestinations(onOpenAbout = { aboutOpens++ }, onOpenPlugins = {}))
        compose.onNodeWithContentDescription("About Atomic").performScrollTo().performClick()
        assertEquals(1, aboutOpens)
    }

    @Test
    fun cloudStatusExplainsUnavailableAccountsAndPointsToRealConnections() {
        show()
        val initial = repository.settings.value

        compose.onNodeWithContentDescription("Cloud Connect").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Direct cloud access is planned").assertIsDisplayed()
        compose.onNodeWithText("Storage → Connections").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Sign in").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close settings dialog").performClick()

        compose.onNodeWithTag("settings_preferences").assertDoesNotExist()
        assertEquals(initial, repository.settings.value)
    }

    @Test
    fun privateSettingsOnlyOpenTheShellDestinationOnExplicitAction() {
        var privateOpens = 0
        show(SettingsDestinations(onOpenAbout = {}, onOpenPlugins = {}, onOpenPrivateFiles = { privateOpens++ }))
        compose.onNodeWithContentDescription("Private files").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Lock private files").assertExists()
        assertEquals(0, privateOpens)
        compose.onNodeWithContentDescription("Open Private files").performScrollTo().performClick()
        assertEquals(1, privateOpens)
        compose.onNodeWithTag("settings_preferences").assertDoesNotExist()
    }

    @Test
    fun allEightBuiltInPluginsShowTheirVersionAndReturnWithoutChangingPreferences() {
        show()
        val initial = repository.settings.value
        compose.onNodeWithContentDescription("In-app plugins").performScrollTo().performClick()
        val plugins =
            listOf(
                "image",
                "video",
                "audio",
                "pdf",
                "document",
                "code",
                "markdown",
                "archive",
            )
        plugins.forEach { plugin ->
            compose.onNodeWithTag("plugins_details_$plugin").performScrollTo().performClick()
            compose.onNodeWithText("Version v0.2").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Current limitations").assertExists()
            compose.onNodeWithContentDescription("Back to plugins").performClick()
            compose.onNodeWithText("Version v0.2").assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("settings_screen").assertExists()
        compose.onNodeWithContentDescription("Back to plugins").assertDoesNotExist()
        assertEquals(initial, repository.settings.value)
        assertEquals(0, cacheClears)
        assertEquals(emptyList<String>(), messages)
    }

    @Test
    fun themeChipSetsTheThemeMode() {
        show()
        compose.onNodeWithContentDescription("Appearance").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Dark").performClick()
        assertEquals(ThemeMode.DARK, repository.settings.value.themeMode)
    }

    @Test
    fun hiddenFilesPreferencesToggleTheSetting() {
        show()
        compose.onNodeWithContentDescription("Hidden files").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show hidden files").performScrollTo().performClick()
        assertFalse(repository.settings.value.showHiddenFiles)
    }

    @Test
    fun dangerZoneClearsTheScanCacheAndSaysSo() {
        show()
        compose.onNodeWithContentDescription("File browsing").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Execute").performScrollTo().performClick()
        assertEquals(1, cacheClears)
        assertEquals(listOf("File scan cache cleared."), messages)
    }
}
