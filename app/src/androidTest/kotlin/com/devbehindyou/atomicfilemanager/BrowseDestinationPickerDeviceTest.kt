package com.devbehindyou.atomicfilemanager

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseBottomBar
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseClipboardActions
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseUiState
import com.devbehindyou.atomicfilemanager.ui.screens.ClipboardOp
import com.devbehindyou.atomicfilemanager.ui.screens.ClipboardState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class BrowseDestinationPickerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun separateVolumeAndSubfolderResolveToTheExactDirectory() {
        val context = compose.activity.applicationContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "atomic-destination-").toFile()
        val source = File(directory, "source").apply { check(mkdir()) }
        val item = File(source, "notes.txt").apply { writeText("owned fixture") }
        val other = File(directory, "other").apply { check(mkdir()) }
        val target = File(other, "Backups").apply { check(mkdir()) }
        try {
            val initial = BrowseUiState(currentFolderId = FileNodeId.file(source.path), currentFolderName = "Source")
            val sourceNode =
                runBlocking {
                    val result = FileSystemBackend(context).getNode(FileNodeId.file(item.path))
                    (result as FileResult.Success).value
                }
            val clip = ClipboardState(listOf(sourceNode), ClipboardOp.COPY, "Source")
            val volume =
                StorageVolumeInfo(
                    id = "fixture",
                    label = "Fixture storage",
                    type = StorageType.INTERNAL_PRIVATE,
                    totalBytes = 10_000_000,
                    freeBytes = 4_000_000,
                    isRemovable = true,
                    isMounted = true,
                    rootNodeId = FileNodeId.file(other.path),
                    requiresGrant = false,
                )
            val state = mutableStateOf(initial.copy(clipboard = clip, isLoading = false))
            var accepted: FileNodeId? = null
            compose.setContent {
                AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        BrowseBottomBar(
                            uiState = state.value,
                            isSelectionMode = false,
                            onCopySelected = {},
                            onCutSelected = {},
                            onCompressSelected = {},
                            onDeleteSelected = {},
                            onShowDetails = {},
                            onClearClipboard = {},
                            clipboardActions =
                                BrowseClipboardActions(
                                    paste = {},
                                    volumes = listOf(volume),
                                    pasteAt = { held, selected ->
                                        assertEquals(clip, held)
                                        accepted = selected
                                        state.value = state.value.copy(clipboard = null)
                                        true
                                    },
                                ),
                        )
                    }
                }
            }
            compose.onNodeWithTag("choose_destination_button").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("destination_volume_fixture")).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("destination_volume_fixture").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Backups").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Backups").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("destination_confirm") and isEnabled()).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("destination_confirm").performScrollTo().performClick()
            compose.waitUntil(10_000) { state.value.clipboard == null }
            compose.runOnIdle { assertEquals(FileNodeId.file(target.path), accepted) }
            assertEquals("owned fixture", item.readText())
            assertTrue(target.list().orEmpty().isEmpty())
        } finally {
            for (owned in listOf(item, target, source, other, directory)) {
                assertTrue(owned.delete() || !owned.exists())
            }
        }
    }
}
