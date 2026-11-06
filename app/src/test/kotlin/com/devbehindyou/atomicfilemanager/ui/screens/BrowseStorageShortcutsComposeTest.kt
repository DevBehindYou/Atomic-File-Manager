package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrowseStorageShortcutsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun friendlyStorageNamesAndSelectionUpdateWhileClicksKeepVolumeIdentity() {
        val internal = volume("internal", "Internal shared storage", StorageType.INTERNAL_SHARED)
        val sd = volume("sd", "Samsung SD card", StorageType.SD_CARD)
        val current = mutableStateOf(internal)
        var opened: StorageVolumeInfo? = null
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                BrowseStorageShortcuts(listOf(internal, sd), current.value, current.value.rootNodeId!!) { opened = it }
            }
        }
        compose.onNodeWithText("Internal").assertExists()
        compose.onNodeWithText("Samsung SD card").assertExists()
        compose.onNodeWithText("Current storage: Internal shared storage").assertExists()
        compose.onNodeWithContentDescription("Open storage Internal shared storage").assertIsSelected()
        compose.onNodeWithContentDescription("Open storage Samsung SD card").assertIsNotSelected().performClick()
        compose.runOnIdle {
            assertEquals(sd, opened)
            current.value = sd
        }
        compose.onNodeWithContentDescription("Open storage Samsung SD card").assertIsSelected()
        compose.onNodeWithText("Current storage: Samsung SD card").assertExists()
    }

    @Test
    fun mountedStorageAwaitingAccessRemainsReachableButRemovedStorageIsAbsent() {
        val grant = volume("sd", "SD card", StorageType.SD_CARD).copy(rootNodeId = null, requiresGrant = true)
        val removed = volume("removed", "Removed USB", StorageType.USB).copy(isMounted = false)
        val inaccessible = volume("unavailable", "Unavailable", StorageType.USB).copy(rootNodeId = null)
        var opened: StorageVolumeInfo? = null
        compose.setContent {
            AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                BrowseStorageShortcuts(
                    listOf(grant, removed, inaccessible),
                    null,
                    FileNodeId.saf("content://provider/document/opaque"),
                ) { opened = it }
            }
        }
        compose.onNodeWithContentDescription("Open storage Removed USB").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open storage Unavailable").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open storage SD card").performClick()
        compose.runOnIdle { assertEquals(grant, opened) }
        compose.onNodeWithText("Current storage: Document storage").assertExists()
    }

    private fun volume(
        id: String,
        label: String,
        type: StorageType,
    ) = StorageVolumeInfo(
        id,
        label,
        type,
        0,
        0,
        type != StorageType.INTERNAL_SHARED,
        true,
        FileNodeId.file("/$id"),
        false,
    )
}
