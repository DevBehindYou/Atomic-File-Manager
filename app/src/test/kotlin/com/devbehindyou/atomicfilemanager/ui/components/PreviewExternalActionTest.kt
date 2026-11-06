package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
                    onOpen =
                        resolvePreviewExternalAction(
                            first,
                            shown.value,
                            onOpenOriginal = null,
                            onOpenShown = { requested += it.id },
                            onOpenDefault = {},
                        ),
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
                    onOpen =
                        resolvePreviewExternalAction(
                            first,
                            shown.value,
                            onOpenOriginal = { originalOpens++ },
                            onOpenShown = null,
                            onOpenDefault = {},
                        ),
                    busy = false,
                )
            }
        }
        compose.onNodeWithTag("preview_open_external").performClick()
        compose.runOnIdle { shown.value = image("second.png") }
        compose.onNodeWithTag("preview_open_external").assertDoesNotExist()
        assertEquals(1, originalOpens)
    }

    @Test
    fun detailsBodyUsesDisplayedFileRouteInsteadOfLaunchingTheLegacySystemChooser() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val node = image("installer.apk").copy(mimeType = "application/vnd.android.package-archive")
        val requested = mutableListOf<FileNodeId>()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                val open =
                    resolvePreviewExternalAction(
                        node,
                        node,
                        onOpenOriginal = null,
                        onOpenShown = { requested += it.id },
                        onOpenDefault = {
                            app.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_VIEW), "Open with")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                GenericFileExternalAction(onOpen = open, busy = false)
            }
        }
        compose.onNodeWithTag("generic_open_external").assertIsDisplayed().performClick()
        assertEquals(listOf(node.id), requested)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private fun image(name: String) =
        FileNode(
            FileNodeId.file("/fixture/$name"), name, name, "image/png", 1, 0, false, false, null,
            StorageType.INTERNAL_SHARED, AccessFlags.FULL, null, null,
        )
}
