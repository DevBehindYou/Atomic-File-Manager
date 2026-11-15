package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryFilesComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun changingViewKeepsTheSamePreviewTargetAndUsesTwoColumnsOnPhone() {
        val first = fixture("notes.txt", "/storage/emulated/0")
        val second = fixture("readme.txt", "/storage/ABCD-1234")
        val mode = mutableStateOf(FileViewMode.LIST)
        val opened = mutableListOf<FileNodeId>()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                CategoryFiles(listOf(first, second), mode.value, onOpen = { opened.add(it.id) }) {
                    AtomicText("Documents across accessible storage", AtomicTextRole.BodySecondary)
                }
            }
        }

        compose.onNodeWithText("Documents across accessible storage").assertExists()
        compose.onNodeWithTag("category_list").assertExists()
        assertTrue(bounds(second).top > bounds(first).top)
        compose.onNodeWithText(second.name).performClick()

        compose.runOnIdle { mode.value = FileViewMode.GRID }
        compose.onNodeWithTag("category_grid").assertExists()
        assertEquals(bounds(first).top, bounds(second).top)
        assertTrue(bounds(second).left > bounds(first).right)
        compose.onNodeWithText(second.name).performClick()
        compose.runOnIdle { assertEquals(listOf(second.id, second.id), opened) }
    }

    private fun bounds(file: FileNode) =
        compose.onNodeWithTag("category_file_${file.id.raw}").getUnclippedBoundsInRoot()

    private fun fixture(name: String, parent: String) =
        FileNode(
            id = FileNodeId.file("$parent/$name"),
            name = name,
            displayName = name,
            mimeType = "text/plain",
            size = 1024,
            modifiedAt = 0,
            isDirectory = false,
            isHidden = false,
            parentId = FileNodeId.file(parent),
            storageType = if (parent == "/storage/emulated/0") StorageType.INTERNAL_SHARED else StorageType.SD_CARD,
            access = AccessFlags.READ_ONLY,
            childCount = null,
            extras = null,
        )
}
