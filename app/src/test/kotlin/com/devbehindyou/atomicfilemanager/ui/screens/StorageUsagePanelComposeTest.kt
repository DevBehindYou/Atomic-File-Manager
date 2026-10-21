package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsagePhase
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsageUpdate
import com.devbehindyou.atomicfilemanager.domain.model.CategoryUsage
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageUsagePanelComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun capacityAndExclusiveFileCategoriesExposeTheirDifferentScopes() {
        show(StorageUsageUpdate(StorageUsagePhase.COMPLETE, snapshot()))
        compose.onNodeWithText("Total").assertExists()
        compose.onNodeWithText("Used").assertExists()
        compose.onNodeWithText("Free").assertExists()
        compose.onNodeWithText("800.0 B").assertExists()
        compose.onNodeWithTag("storage_readable_total").assertTextContains("600.0 B")
        compose.onNodeWithContentDescription(
            "PDFs, 16.7% of observed shared-file bytes",
        ).performScrollTo().assertExists()
        compose.onNodeWithTag("storage_usage_OTHER").performScrollTo().assertExists()
        compose.onNodeWithTag("storage_unattributed_estimate").performScrollTo().assertTextContains("200.0 B")
    }

    @Test
    fun deniedRefreshRetainsLastGoodAndShowsPartialCoverageInsteadOfEmptySuccess() {
        var refreshed = 0
        val partial = snapshot().copy(categories = emptyMap(), scannedFiles = 0, unreadable = 3, complete = false)
        show(StorageUsageUpdate(StorageUsagePhase.PARTIAL, partial, snapshot())) { refreshed++ }
        compose.onNodeWithTag("storage_usage_status").assertTextContains("3 unreadable/unavailable")
        compose.onNodeWithTag("storage_readable_total").assertTextContains("600.0 B")
        compose.onNodeWithTag("refresh_storage_usage").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, refreshed)
    }

    @Test
    fun unknownMountedCapacityDoesNotInventFreeOrTotalZeroValues() {
        val unavailable = snapshot().copy(capacity = StorageCapacity(0, 0, 1, 1), complete = false, unreadable = 1)
        show(StorageUsageUpdate(StorageUsagePhase.PARTIAL, unavailable))
        compose.onNodeWithText("Capacity unavailable. Grant access or reconnect storage, then refresh.").assertExists()
        compose.onNodeWithText("Total").assertDoesNotExist()
        compose.onNodeWithText("Free").assertDoesNotExist()
        compose.onNodeWithTag("storage_usage_status").assertTextContains("Some mounted capacity is unknown")
    }

    @Test
    fun incompletePreviousEvidenceIsExplicitlyPartialAndDoesNotBecomeCompleteWhileRefreshing() {
        show(StorageUsageUpdate(previousObserved = snapshot().copy(complete = false, unreadable = 1)))
        compose.onNodeWithTag("storage_readable_total").assertTextContains("600.0 B")
        compose.onNodeWithText("Showing the previous partial file breakdown", substring = true).assertExists()
        compose.onNodeWithTag("storage_usage_status").assertTextContains("Scanning files")
        compose.onNodeWithText("Readable-folder scan complete", substring = true).assertDoesNotExist()
    }

    private fun show(
        update: StorageUsageUpdate,
        onRefresh: () -> Unit = {},
    ) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) { StorageUsagePanel(update, onRefresh) }
            }
        }
    }

    private fun snapshot() =
        StorageUsageSnapshot(
            StorageCapacity(1000, 200, 2),
            mapOf(
                StorageUsageCategory.PDFS to CategoryUsage(100, 1),
                StorageUsageCategory.TEXT to CategoryUsage(200, 2),
                StorageUsageCategory.OTHER to CategoryUsage(300, 3),
            ),
            6, 2, 0, 0, false, true, 1_000,
        )
}
