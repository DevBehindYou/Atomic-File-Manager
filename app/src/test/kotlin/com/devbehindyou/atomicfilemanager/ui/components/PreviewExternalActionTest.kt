package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreviewExternalActionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun displayedImageCanBeOpenedExternallyAfterGalleryChangesItsIdentity() {
        val first = image("first.png")
        val second = image("second.jpg")
        val shown = mutableStateOf(first)
        val requested = mutableListOf<FileNodeId>()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                PreviewExternalAction(
                    first,
                    shown.value,
                    onOpenOriginal = null,
                    onOpenShown = { requested += it.id },
                    onOpenDefault = {},
                    busy = false,
                )
            }
        }
        compose.onNodeWithTag("preview_open_external").performClick()
        compose.runOnIdle { shown.value = second }
        compose.onNodeWithTag("preview_open_external").assertIsDisplayed().performClick()
        assertEquals(listOf(first.id, second.id), requested)
    }

    @Test
    fun legacyOriginalFileCallbackStaysHiddenForAnotherGalleryImage() {
        val first = image("first.png")
        val shown = mutableStateOf(first)
        var originalOpens = 0
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                PreviewExternalAction(
                    first,
                    shown.value,
                    onOpenOriginal = { originalOpens++ },
                    onOpenShown = null,
                    onOpenDefault = {},
                    busy = false,
                )
            }
        }
        compose.onNodeWithTag("preview_open_external").performClick()
        compose.runOnIdle { shown.value = image("second.png") }
        compose.onNodeWithTag("preview_open_external").assertDoesNotExist()
        assertEquals(1, originalOpens)
    }

    private fun image(name: String) =
        FileNode(
            FileNodeId.file("/fixture/$name"), name, name, "image/png", 1, 0, false, false, null,
            StorageType.INTERNAL_SHARED, AccessFlags.FULL, null, null,
        )
}
