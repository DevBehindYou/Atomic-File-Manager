package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicStripAction
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.Favourite
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.model.getOrNull
import com.devbehindyou.atomicfilemanager.domain.repository.FavouritesRepository
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController
import com.devbehindyou.atomicfilemanager.ui.interaction.peek.QuickPeekController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real row gestures, view-model selections and contextual toolbar; no phone files or Room writes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w900dp-h600dp", application = AtomicApp::class)
class DualPaneSelectionComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rightMediaSelection() {
        val app = RuntimeEnvironment.getApplication() as AtomicApp
        val original = app.container
        val field = AtomicApp::class.java.getDeclaredField("container").apply { isAccessible = true }
        val fake =
            object : AppContainer by original {
                override val favouritesRepository =
                    object : FavouritesRepository {
                        override fun observe() = flowOf(emptyList<Favourite>())

                        override suspend fun add(node: FileNode): Unit = error("unexpected favourite write")

                        override suspend fun remove(id: FileNodeId): Unit = error("unexpected favourite write")
                    }
            }
        field.set(app, fake)
        val fixture = MemoryPanes()
        val renamed = mutableListOf<FileNodeId>()
        try {
            compose.setContent {
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    val left by fixture.left.uiState.collectAsState()
                    val right by fixture.right.uiState.collectAsState()
                    var rightActive by remember { mutableStateOf(false) }
                    val owner = if (rightActive) fixture.right else fixture.left
                    val active = if (rightActive) right else left
                    Column(Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                            DualPaneBrowseContent(
                                uiState = left, secondaryUiState = right,
                                dualPaneMode = DualPaneMode.DUAL_BROWSE,
                                viewModel = fixture.left, secondaryViewModel = fixture.right,
                                dragController = remember { FileDragController() },
                                quickPeekController = remember { QuickPeekController() },
                                nodeForPreview = null,
                                actions =
                                    DualPaneItemActions(
                                        onOpenFile = null, onPreviewNode = {}, onSecondaryPreviewNode = {},
                                        onClosePreview = {}, onShowDetails = {}, onRename = {}, onDelete = {},
                                        onHide = {}, onNewFolder = {},
                                        onPrimaryActivated = {
                                            rightActive = false
                                            fixture.right.clearSelection()
                                        },
                                        onSecondaryActivated = {
                                            rightActive = true
                                            fixture.left.clearSelection()
                                        },
                                    ),
                            )
                        }
                        val selected = active.rawItems.singleOrNull { it.id in active.selectedIds }
                        BrowseBottomBar(
                            uiState = active, isSelectionMode = active.selectedIds.isNotEmpty(),
                            onCopySelected = owner::copySelected, onCutSelected = owner::cutSelected,
                            onCompressSelected = {}, onDeleteSelected = {}, onShowDetails = {},
                            onClearClipboard = owner::clearClipboard, onPaste = {},
                            singleFileActions =
                                selected?.let { node ->
                                    listOf(
                                        AtomicStripAction("Rename", AtomicIcons.Document, {
                                            renamed += node.id
                                            owner.clearSelection()
                                        }),
                                    )
                                } ?: emptyList(),
                        )
                    }
                }
            }
            await { !fixture.left.uiState.value.isLoading && !fixture.right.uiState.value.isLoading }
            val rightRow = compose.onNodeWithTag("file_item_${fixture.rightFile.raw}")
            rightRow.performTouchInput { longClick() }
            await { fixture.right.uiState.value.selectedIds == setOf(fixture.rightFile) }
            assertTrue(fixture.left.uiState.value.selectedIds.isEmpty())
            compose.onNodeWithTag("file_item_menu_${fixture.rightFile.raw}").assertDoesNotExist()
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithContentDescription("Rename").performClick()
            assertEquals(listOf(fixture.rightFile), renamed)
            await { fixture.right.uiState.value.selectedIds.isEmpty() }

            rightRow.performTouchInput { longClick() }
            await { fixture.right.uiState.value.selectedIds == setOf(fixture.rightFile) }
            compose.onNodeWithTag("file_item_${fixture.leftFile.raw}").performTouchInput { longClick() }
            await { fixture.left.uiState.value.selectedIds == setOf(fixture.leftFile) }
            assertTrue(fixture.right.uiState.value.selectedIds.isEmpty())
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithContentDescription("Rename").performClick()
            assertEquals(listOf(fixture.rightFile, fixture.leftFile), renamed)
        } finally {
            fixture.close()
            field.set(app, original)
        }
    }

    private fun await(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private class MemoryPanes {
        private val backend = InMemoryBackend()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val queue =
            OperationQueue(
                engine = { operation, _ -> flowOf(OperationSnapshot(operation, OperationStatus.Cancelled)) },
                journal = null,
                scope = scope,
            )
        private val seed =
            runBlocking {
                val leftRoot = checkNotNull(backend.createDirectory(backend.rootId, "left").getOrNull()).id
                val rightRoot = checkNotNull(backend.createDirectory(backend.rootId, "right").getOrNull()).id
                Seed(
                    leftRoot,
                    rightRoot,
                    backend.putFile(leftRoot, "left.txt", byteArrayOf(1), "text/plain"),
                    backend.putFile(rightRoot, "right.jpg", byteArrayOf(1), "image/jpeg"),
                )
            }
        val leftFile = seed.leftFile
        val rightFile = seed.rightFile
        val left = model(seed.leftRoot)
        val right = model(seed.rightRoot)

        private fun model(folder: FileNodeId) =
            BrowseViewModel(
                folder,
                GetDirectoryListingUseCase { backend },
                GetNodeUseCase { backend },
                CreateDirectoryUseCase { backend },
                RenameFileUseCase { backend },
                DeleteFileUseCase { backend },
                queue,
                NoBubbles(),
            )

        fun close() {
            left.viewModelScope.cancel()
            right.viewModelScope.cancel()
            scope.cancel()
        }

        private data class Seed(
            val leftRoot: FileNodeId,
            val rightRoot: FileNodeId,
            val leftFile: FileNodeId,
            val rightFile: FileNodeId,
        )
    }

    private class NoBubbles : TransferBubbleRepository {
        override val bubbles = MutableStateFlow<List<TransferBubble>>(emptyList())

        override suspend fun createBubble(name: String?): Result<TransferBubble> = error("unexpected bubble mutation")

        override suspend fun addItemsToBubble(
            bubbleId: String,
            items: List<FileNode>,
        ): Result<Unit> =
            error(
                "unexpected bubble mutation",
            )

        override suspend fun removeItemFromBubble(
            bubbleId: String,
            itemId: Long,
        ): Result<Unit> =
            error(
                "unexpected bubble mutation",
            )

        override suspend fun clearBubble(bubbleId: String): Result<Unit> = error("unexpected bubble mutation")

        override suspend fun deleteBubble(bubbleId: String): Result<Unit> = error("unexpected bubble mutation")

        override suspend fun getBubble(bubbleId: String): TransferBubble? = error("unexpected bubble lookup")
    }
}
