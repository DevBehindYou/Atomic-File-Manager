package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.navigation.StorageToolPage
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageAccessToolsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun storageAccessExplainsUnavailableMediaAndKeepsRealActionsReachable() {
        val events = mutableListOf<String>()
        val connected = volume("sd", "SD card", mounted = true, rootId = FileNodeId.file("/storage/example"))
        val disconnected = volume("usb", "USB drive", mounted = false)
        val unavailable = volume("second-sd", "Second SD card", mounted = true)

        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                StorageToolScreen(
                    page = StorageToolPage.STORAGE_ACCESS,
                    onBack = {},
                    volumes = listOf(connected, disconnected, unavailable),
                    hasStorageAccess = true,
                    onRequestStorageAccess = { events += "permission" },
                    onBrowseVolume = { events += it.id },
                    onBrowseFolder = {},
                    onCompareFolders = { _, _ -> },
                    onOpenFolderCompare = {},
                    onOpenFiles = { events += "files" },
                    onOpenTool = { events += it.name },
                    onNotify = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Choose the access you need").assertIsDisplayed()
        compose.onNodeWithText("Allowed by Android").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Available to browse").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not mounted. Reconnect this storage.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unavailable. Reconnect and try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Shared storage").performScrollTo().performClick()
        compose.onNodeWithText("SD card").performScrollTo().performClick()
        compose.onNodeWithTag("link_folder_button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Optional Shizuku").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Continue to Files").performScrollTo().performClick()

        assertEquals(listOf("permission", connected.id, StorageToolPage.SHIZUKU_LABS.name, "files"), events)
    }

    private fun volume(
        id: String,
        label: String,
        mounted: Boolean,
        rootId: FileNodeId? = null,
    ): StorageVolumeInfo =
        StorageVolumeInfo(
            id = id,
            label = label,
            type = StorageType.SD_CARD,
            totalBytes = 1_000,
            freeBytes = 500,
            isRemovable = true,
            isMounted = mounted,
            rootNodeId = rootId,
            requiresGrant = rootId == null,
        )
}
