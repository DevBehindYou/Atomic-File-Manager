package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.Favourite
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.FavouritesRepository
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PinnedLocationsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun availablePinOpensItsProviderIdAndUnavailablePinCanBeRemoved() {
        val folderId = FileNodeId.saf("content://provider/tree/ABCD%3AProjects")
        val missingId = FileNodeId.sftp("other-storage", "/Projects")
        val folder = Favourite(folderId, "Linked Projects", isDirectory = true, addedAt = 1)
        val missing = Favourite(missingId, "Remote Projects", isDirectory = true, addedAt = 2)
        val pins = MutableStateFlow(listOf(folder, missing))
        val removed = mutableListOf<FileNodeId>()
        val opened = mutableListOf<FileNodeId>()
        val repository =
            object : FavouritesRepository {
                override fun observe() = pins

                override suspend fun add(node: FileNode): Unit = error("Unexpected pin creation")

                override suspend fun remove(id: FileNodeId) {
                    removed.add(id)
                    pins.value = pins.value.filterNot { it.id == id }
                }
            }
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun getNode(id: FileNodeId): FileResult<FileNode> =
                    if (id == folderId) {
                        FileResult.Success(linkedFolder(folder))
                    } else {
                        FileResult.Failure(FileError.FileNotFound(id.raw))
                    }
            }

        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                PinnedLocationsList(
                    repository = repository,
                    getNode = GetNodeUseCase(Dispatchers.Unconfined) { backend },
                    onOpenFolder = { opened.add(it) },
                    onOpenFile = { error("Expected to open a folder") },
                    onBrowseFiles = { error("Unexpected Browse action") },
                )
            }
        }

        compose.onNodeWithText("Location unavailable").assertExists()
        compose.onNodeWithText("Remote Projects").assertHasNoClickAction()
        compose.onNodeWithText("Linked Projects").performClick()
        compose.onNodeWithContentDescription("Unpin Remote Projects").performClick()
        compose.onNodeWithText("Remote Projects").assertDoesNotExist()
        compose.onNodeWithText("Linked Projects").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(folderId), opened)
            assertEquals(listOf(missingId), removed)
        }
    }

    private fun linkedFolder(pin: Favourite) =
        FileNode(
            id = pin.id,
            name = pin.name,
            displayName = pin.name,
            mimeType = null,
            size = -1,
            modifiedAt = 0,
            isDirectory = true,
            isHidden = false,
            parentId = null,
            storageType = StorageType.SD_CARD,
            access = AccessFlags.READ_ONLY,
            childCount = null,
            extras = null,
        )
}
