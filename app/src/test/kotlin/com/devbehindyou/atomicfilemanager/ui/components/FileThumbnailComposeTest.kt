package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.data.preview.ThumbnailKind
import com.devbehindyou.atomicfilemanager.data.preview.ThumbnailSource
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FileThumbnailComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rowLeadingThumbnailKeepsClickBehavior() {
        val node = thumbnailNode(name = "notes.txt", mime = "text/plain")
        var opens = 0
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                AtomicFileRow(
                    name = node.name,
                    meta = "fixture",
                    icon = iconFor(node),
                    onClick = { opens++ },
                    leading = { FileThumbnail(node) },
                )
            }
        }
        compose.onNodeWithTag("file_thumbnail_${node.id.raw}", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(node.name).performClick()
        assertEquals(1, opens)
    }

    @Test
    fun failedFolderPeekKeepsTypeIcon() {
        val folder = thumbnailNode(name = "folder").copy(isDirectory = true)
        val pending = CompletableDeferred<List<ThumbnailSource>>()
        var entered = false
        var finished = false
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                FileThumbnail(folder, folderLoader = {
                    entered = true
                    try {
                        pending.await()
                    } finally {
                        finished = true
                    }
                })
            }
        }
        await { entered }
        compose.onNodeWithTag("file_type_icon_${folder.id.raw}").assertIsDisplayed()
        compose.onNodeWithTag("folder_miniature").assertDoesNotExist()
        compose.runOnIdle { pending.completeExceptionally(IOException("directory unavailable")) }
        await { finished }
        compose.onNodeWithTag("file_type_icon_${folder.id.raw}").assertIsDisplayed()
        compose.onNodeWithTag("folder_miniature").assertDoesNotExist()
    }

    @Test
    fun offscreenFolderPeekIsCancelled() {
        val folder = thumbnailNode(name = "folder").copy(isDirectory = true)
        val show = mutableStateOf(true)
        var entered = false
        var cancelled = false
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (show.value) {
                    FileThumbnail(folder, folderLoader = {
                        entered = true
                        try {
                            CompletableDeferred<List<ThumbnailSource>>().await()
                        } finally {
                            cancelled = true
                        }
                    })
                }
            }
        }
        await { entered }
        compose.runOnIdle { show.value = false }
        await { cancelled }
        compose.onNodeWithTag("file_thumbnail_${folder.id.raw}").assertDoesNotExist()
    }

    @Test
    fun folderMiniatureUsesAtMostFourSlotsAndRetainsFolderIcon() {
        val folder = thumbnailNode(name = "folder").copy(isDirectory = true)
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                FileThumbnail(folder, folderLoader = {
                    (0..6).map { ThumbnailSource(Any(), "fixture-$it", ThumbnailKind.IMAGE) }
                })
            }
        }
        compose.onNodeWithTag("folder_miniature").assertExists()
        compose.onNodeWithTag("file_type_icon_${folder.id.raw}").assertIsDisplayed()
        (0..3).forEach { compose.onNodeWithTag("thumbnail_image_fixture-$it").assertExists() }
        (4..6).forEach { compose.onNodeWithTag("thumbnail_image_fixture-$it").assertDoesNotExist() }
    }

    @Test
    fun fileHasNoMoreBoxAndLongPressStillSelectsWithAccessibleActions() {
        val node = thumbnailNode(name = "notes.txt", mime = "text/plain")
        val selected = mutableStateOf(false)
        val actions = mutableListOf<String>()
        showRow(node, selected, actions)
        compose.onNodeWithTag("file_item_menu_${node.id.raw}").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options for ${node.name}").assertDoesNotExist()
        val row = compose.onNodeWithTag("file_item_${node.id.raw}")
        row.performTouchInput { longClick() }
        row.assertIsSelected()
        val custom = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertTrue(custom.map { it.label }.containsAll(listOf("Details", "Rename", "Delete")))
        compose.runOnIdle { custom.forEach { assertTrue(it.action()) } }
        assertEquals(listOf("details", "rename", "delete"), actions)
    }

    @Test
    fun folderRetainsArrowAndSelectionWithoutMoreBox() {
        val folder = thumbnailNode(name = "folder").copy(isDirectory = true)
        val selected = mutableStateOf(false)
        showRow(folder, selected, mutableListOf())
        compose.onNodeWithText("→").assertExists()
        compose.onNodeWithTag("file_item_menu_${folder.id.raw}").assertDoesNotExist()
        val row = compose.onNodeWithTag("file_item_${folder.id.raw}")
        row.performTouchInput { longClick() }
        row.assertIsSelected()
        compose.onNodeWithText("→").assertDoesNotExist()
    }

    private fun showRow(
        node: FileNode,
        selected: androidx.compose.runtime.MutableState<Boolean>,
        actions: MutableList<String>,
    ) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                FileListItem(
                    node = node,
                    isSelectionMode = selected.value,
                    isSelected = selected.value,
                    onToggleSelect = { selected.value = true },
                    onClick = {},
                    onShowDetails = { actions += "details" },
                    onRename = { actions += "rename" },
                    onDelete = { actions += "delete" },
                )
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
