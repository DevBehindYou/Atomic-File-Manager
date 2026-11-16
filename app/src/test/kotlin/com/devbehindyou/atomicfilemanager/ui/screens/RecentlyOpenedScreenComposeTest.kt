package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.RecentItem
import com.devbehindyou.atomicfilemanager.domain.model.RecentSource
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.RecentsRepository
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecentlyOpenedScreenComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun previewUsesTheStoredProviderIdAndClearRequiresConfirmation() {
        val id = FileNodeId.sftp("backup", "/Shared/Notes.txt")
        val file =
            FileNode(
                id = id,
                name = "Notes.txt",
                displayName = "Notes.txt",
                mimeType = "text/plain",
                size = 45,
                modifiedAt = 0,
                isDirectory = false,
                isHidden = false,
                parentId = null,
                storageType = StorageType.VIRTUAL,
                access = AccessFlags.READ_ONLY,
                childCount = null,
                extras = null,
            )
        val item = RecentItem(id, file.name, file.mimeType, file.size, 42L, RecentSource.OPENED)
        val repo = OpenHistory(item)
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun getNode(id: FileNodeId): FileResult<FileNode> {
                    assertEquals(file.id, id)
                    return FileResult.Success(file)
                }
            }
        val getNode = GetNodeUseCase(io = Dispatchers.Main) { backend }
        val previews = mutableListOf<FileNodeId>()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                RecentlyOpenedHistory(repo, getNode) { previews += it.id }
            }
        }

        val row = compose.onNodeWithTag("recently_opened_item_${id.raw}")
        row.performClick()
        compose.runOnIdle { assertEquals(listOf(id), previews) }

        val clear = compose.onNodeWithContentDescription("Clear recent history")
        clear.performClick()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, repo.clearCount) }
        row.assertExists()

        clear.performClick()
        compose.onNodeWithContentDescription("Clear history").performClick()
        compose.runOnIdle { assertEquals(1, repo.clearCount) }
        row.assertDoesNotExist()
        compose.onNodeWithContentDescription("No files opened yet").assertExists()
        clear.assertIsNotEnabled()
    }

    private class OpenHistory(item: RecentItem) : RecentsRepository {
        private val opened = MutableStateFlow(listOf(item))
        var clearCount = 0
            private set

        override fun observeOpened(limit: Int) = opened.map { it.take(limit) }

        override suspend fun recordOpened(node: FileNode): Unit = error("Unexpected history record")

        override suspend fun remove(id: FileNodeId): Unit = error("Unexpected individual removal")

        override suspend fun clearOpened() {
            clearCount++
            opened.value = emptyList()
        }

        override suspend fun recentMedia(
            sinceMillis: Long,
            limit: Int,
        ): List<RecentItem> = error("Opened history does not query MediaStore")
    }
}
