package com.devbehindyou.atomicfilemanager.ui.startup

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupFeedbackComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun settingsAnnouncesLoadingAndKeepsFailureActions() {
        val loading = mutableStateOf(true)
        val actions = mutableListOf<String>()
        compose.setContent {
            SettingsStartupScreen(
                loading = loading.value,
                onRetry = { actions += "retry" },
                onClose = { actions += "close" },
            )
        }

        compose.onNodeWithTag("settings_startup_loading")
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        compose.onNodeWithTag("settings_startup_retry").assertDoesNotExist()
        compose.onNodeWithTag("settings_startup_close").assertExists()

        compose.runOnIdle { loading.value = false }
        compose.onNodeWithTag("settings_startup_loading").assertDoesNotExist()
        compose.onNodeWithTag("settings_startup_retry").performClick()
        compose.onNodeWithTag("settings_startup_close").performClick()
        assertEquals(listOf("retry", "close"), actions)
    }

    @Test
    fun storageAnnouncesLoadingAndRetainsTheRetryAction() {
        val state = mutableStateOf<StorageReadinessState>(StorageReadinessState.Loading())
        var retries = 0
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                StorageReadinessContent(state.value, onRetry = { retries++ })
            }
        }

        compose.onNodeWithTag("storage_readiness_loading")
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        compose.onNodeWithTag("storage_readiness_retry").assertDoesNotExist()

        compose.runOnIdle { state.value = StorageReadinessState.Failed() }
        compose.onNodeWithTag("storage_readiness_failed").assertExists()
        compose.onNodeWithTag("storage_readiness_retry").performClick()
        assertEquals(1, retries)
    }
}
