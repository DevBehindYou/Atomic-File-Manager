package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsageRepository
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.domain.usecase.StorageAnalyzerUseCase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = AtomicApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageScreenLeanComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun capacityAndAnalysisNavigationNeverConstructEitherScanner() {
        val app = RuntimeEnvironment.getApplication() as AtomicApp
        val original = app.container
        val field = AtomicApp::class.java.getDeclaredField("container").apply { isAccessible = true }
        val guarded =
            object : AppContainer by original {
                override val storageUsageRepository: StorageUsageRepository
                    get() = error("Storage must not construct the recursive file-type scanner")
                override val storageAnalyzerUseCase: StorageAnalyzerUseCase
                    get() = error("Storage must not construct the deep cleanup scanner")
            }
        val root = FileNodeId.file("/example")
        val volume =
            StorageVolumeInfo(
                "primary", "Internal", StorageType.INTERNAL_SHARED,
                1_000, 400, false, true, root, false,
            )
        var opened: FileNodeId? = null
        field.set(app, guarded)
        try {
            compose.setContent {
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    StorageScreen(listOf(volume), onBrowseVolume = {}, onOpenStorageIntelligence = { opened = it })
                }
            }
            compose.onNodeWithTag("storage_capacity_summary").assertIsDisplayed()
            compose.onNodeWithText("Total").assertExists()
            compose.onNodeWithTag("storage_usage_dashboard").assertDoesNotExist()
            compose.onNodeWithTag("storage_readable_total").assertDoesNotExist()
            compose.onNodeWithTag("open_storage_intelligence_button").performScrollTo().performClick()
            assertEquals(root, opened)
        } finally {
            field.set(app, original)
        }
    }
}
