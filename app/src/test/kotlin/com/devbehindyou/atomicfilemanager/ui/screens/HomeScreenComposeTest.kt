package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileCategory
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.startup.StorageReadinessState
import com.devbehindyou.atomicfilemanager.ui.startup.StorageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every Home entry point still reaches the destination it did before the Atomic rebuild. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private val phone =
        StorageVolumeInfo(
            id = "primary",
            label = "Phone",
            type = StorageType.INTERNAL_SHARED,
            totalBytes = 128L shl 30,
            freeBytes = 86L shl 30,
            isRemovable = false,
            isMounted = true,
            rootNodeId = FileNodeId.file("/storage/emulated/0"),
            requiresGrant = false,
        )

    private val events = mutableListOf<String>()

    private fun show(hasAccess: Boolean = true, readiness: MutableState<StorageReadinessState>? = null) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                HomeScreen(
                    volumes = listOf(phone),
                    hasStorageAccess = hasAccess,
                    onNavigateToVolume = { events += "volume:${it.id}" },
                    onNavigateToCategory = { events += "category:$it" },
                    onNavigateToFolder = { events += "folder" },
                    onRequestStorageAccess = { events += "grant" },
                    onOpenPrivateFiles = { events += "private" },
                    onOpenStorageDetails = { events += "details" },
                    storageReadiness = readiness?.value ?: StorageReadinessState.Ready(StorageSnapshot(hasAccess, listOf(phone))),
                    onRetryStorage = { events += "retry" },
                )
            }
        }
    }

    @Test
    fun unresolvedCheckAndFailureNeverOfferPermissionGrantAndRetryKeepsVolumeNavigation() {
        val readiness = mutableStateOf<StorageReadinessState>(StorageReadinessState.Loading(StorageSnapshot(false, listOf(phone))))
        show(hasAccess = false, readiness = readiness)
        compose.onNodeWithTag("permission_warning_card").assertDoesNotExist()
        compose.onNodeWithTag("storage_readiness_loading").assertExists()
        compose.runOnIdle { readiness.value = StorageReadinessState.Failed(StorageSnapshot(false, listOf(phone))) }
        compose.onNodeWithTag("permission_warning_card").assertDoesNotExist()
        compose.onNodeWithTag("storage_readiness_retry").performClick()
        compose.onNodeWithTag("volume_item_primary").performScrollTo().performClick()
        assertEquals(listOf("retry", "volume:primary"), events)
        compose.runOnIdle { readiness.value = StorageReadinessState.Ready(StorageSnapshot(false, listOf(phone))) }
        compose.onNodeWithTag("permission_warning_card").assertExists()
        compose.onNodeWithTag("storage_readiness_retry").assertDoesNotExist()
    }

    @Test
    fun storageCardAndDetailsOpenTheirDestinations() {
        show()
        compose.onNodeWithTag("volume_item_primary").performClick()
        compose.onNodeWithContentDescription("Details →").performClick()
        assertEquals(listOf("volume:primary", "details"), events)
    }

    @Test
    fun categoryTilesOpenCategoriesAndPrivateOpensPrivateFiles() {
        show()
        compose.onNodeWithTag("category_image").performScrollTo().performClick()
        compose.onNodeWithTag("category_more").performScrollTo().performClick()
        compose.onNodeWithTag("app_private_storage_item").performScrollTo().performClick()
        assertEquals(listOf("category:${FileCategory.IMAGE}", "category:${FileCategory.OTHER}", "private"), events)
    }

    @Test
    fun missingAccessShowsTheGrantButton() {
        show(hasAccess = false)
        compose.onNodeWithTag("permission_warning_card").assertExists()
        compose.onNodeWithContentDescription("Grant access").performClick()
        assertEquals(listOf("grant"), events)
    }
}
