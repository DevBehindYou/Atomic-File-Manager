package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.ui.components.FileItemPresentation
import com.devbehindyou.atomicfilemanager.ui.components.FileListItem
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises actual adaptive lazy layouts and shared item actions without touching device files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w500dp-h800dp", application = Application::class)
class FileGridComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun gridAdaptsToPaneWidthAndListUsesOneColumn() {
        val mode = mutableStateOf(FileViewMode.GRID)
        val width = mutableStateOf(AtomicSize.fileGridCellMin * 2 + AtomicSpacing.s56)
        val nodes = fixtures(3)
        showFiles(nodes, mode, width)
        val first = item(nodes[0]).fetchSemanticsNode().boundsInRoot
        val second = item(nodes[1]).fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(second.left > first.left)

        compose.runOnIdle { width.value = AtomicSize.fileGridCellMin + AtomicSpacing.s24 }
        val narrowFirst = item(nodes[0]).fetchSemanticsNode().boundsInRoot
        val narrowSecond = item(nodes[1]).fetchSemanticsNode().boundsInRoot
        assertEquals(narrowFirst.left, narrowSecond.left, 1f)
        assertTrue(narrowSecond.top > narrowFirst.top)

        compose.runOnIdle {
            width.value = AtomicSize.fileGridCellMin * 2 + AtomicSpacing.s56
            mode.value = FileViewMode.LIST
        }
        compose.onNodeWithTag("file_grid").assertDoesNotExist()
        compose.onNodeWithTag("file_list").assertIsDisplayed()
        val listFirst = item(nodes[0]).fetchSemanticsNode().boundsInRoot
        val listSecond = item(nodes[1]).fetchSemanticsNode().boundsInRoot
        assertEquals(listFirst.left, listSecond.left, 1f)
        assertTrue(listSecond.top > listFirst.top)
    }

    @Test
    fun gridRetainsOpenLongPressSelectionAndAccessibleActionsWhenSwitchingViews() {
        val mode = mutableStateOf(FileViewMode.GRID)
        val nodes = fixtures(2)
        val selected = mutableStateOf<FileNodeId?>(null)
        val actions = mutableListOf<String>()
        showFiles(nodes, mode, selected = selected, actions = actions)
        item(nodes[0]).performClick()
        assertEquals(listOf("open:${nodes[0].name}"), actions)
        item(nodes[1]).performTouchInput { longClick() }
        item(nodes[1]).assertIsSelected()
        compose.onNodeWithTag("file_item_menu_${nodes[1].id.raw}").assertDoesNotExist()
        val custom = item(nodes[1]).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(custom.single { it.label == "Rename" }.action()) }
        assertEquals("rename:${nodes[1].name}", actions.last())
        compose.onNodeWithTag("file_thumbnail_${nodes[1].id.raw}", useUnmergedTree = true)
            .assertWidthIsEqualTo(AtomicSize.gridThumbnail)
            .assertHeightIsEqualTo(AtomicSize.gridThumbnail)
        compose.onNodeWithTag("file_type_icon_${nodes[1].id.raw}", useUnmergedTree = true)
            .assertWidthIsEqualTo(AtomicSize.gridThumbnail)

        compose.runOnIdle { mode.value = FileViewMode.LIST }
        item(nodes[1]).assertIsSelected()
        compose.onNodeWithTag("file_thumbnail_${nodes[1].id.raw}", useUnmergedTree = true)
            .assertWidthIsEqualTo(AtomicSize.iconTile)
        item(nodes[1]).performClick()
        assertEquals(null, selected.value)
    }

    @Test
    fun bothLazyLayoutsCanReachFilesBeyondTheInitialViewport() {
        val mode = mutableStateOf(FileViewMode.GRID)
        val nodes = fixtures(80)
        showFiles(nodes, mode)
        compose.onNodeWithTag("file_grid").performScrollToIndex(nodes.lastIndex)
        item(nodes.last()).assertIsDisplayed()
        compose.runOnIdle { mode.value = FileViewMode.LIST }
        item(nodes.first()).assertIsDisplayed()
        compose.onNodeWithTag("file_list").performScrollToIndex(nodes.lastIndex)
        item(nodes.last()).assertIsDisplayed()
        compose.runOnIdle { mode.value = FileViewMode.GRID }
        item(nodes.last()).assertIsDisplayed()
    }

    private fun item(node: FileNode) = compose.onNodeWithTag("file_item_${node.id.raw}")

    private fun fixtures(count: Int) =
        (0 until count).map { index ->
            thumbnailNode(FileNodeId.file("/grid-fixture/note-$index.txt"), "note-$index.txt", "text/plain")
        }

    private fun showFiles(
        nodes: List<FileNode>,
        mode: MutableState<FileViewMode>,
        width: MutableState<Dp> = mutableStateOf(AtomicSize.fileGridCellMin * 2 + AtomicSpacing.s56),
        selected: MutableState<FileNodeId?> = mutableStateOf(null),
        actions: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                Box(Modifier.width(width.value).fillMaxHeight()) {
                    FileBrowserLayout(nodes, mode.value, remember { FileDragController() }) { node ->
                        FileListItem(
                            node = node,
                            presentation = FileItemPresentation(viewMode = mode.value),
                            isSelectionMode = selected.value != null,
                            isSelected = selected.value == node.id,
                            onToggleSelect = { selected.value = node.id },
                            onClick = {
                                if (selected.value == null) {
                                    actions += "open:${node.name}"
                                } else {
                                    selected.value = if (selected.value == node.id) null else node.id
                                }
                            },
                            onShowDetails = { actions += "details:${node.name}" },
                            onRename = { actions += "rename:${node.name}" },
                            onDelete = { actions += "delete:${node.name}" },
                        )
                    }
                }
            }
        }
    }
}
