package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.TrashEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h892dp")
class TrashContentComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sameNameCardsDiscloseTheirOriginsAndKeepActionIdentity() {
        val internal = entry("internal", "/storage/emulated/0")
        val sd = entry("sd", "/storage/1234-5678")
        val restored = mutableListOf<TrashEntry>()
        val confirming = mutableListOf<TrashEntry>()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                TrashContent(
                    entries = listOf(internal, sd),
                    retentionDays = 30,
                    onRestore = { restored.addAll(it) },
                    onConfirmDelete = { confirming.addAll(it) },
                    onBrowseFiles = null,
                )
            }
        }

        compose.onNodeWithText("Originally /storage/emulated/0/Download").assertIsDisplayed()
        compose.onNodeWithText("Originally /storage/1234-5678/Download").assertIsDisplayed()
        compose.onNodeWithTag("trash_restore:internal").performClick()
        compose.onNodeWithTag("trash_delete:sd").performClick()
        compose.runOnIdle {
            assertEquals(listOf(internal), restored)
            assertEquals(listOf(sd), confirming)
        }
    }

    @Test
    fun emptyTrashOffersBrowseAndNoMutationActions() {
        var browseCount = 0
        var mutationCount = 0
        compose.setContent {
            AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                TrashContent(
                    entries = emptyList(),
                    retentionDays = 30,
                    onRestore = { mutationCount++ },
                    onConfirmDelete = { mutationCount++ },
                    onBrowseFiles = { browseCount++ },
                )
            }
        }

        compose.onNodeWithContentDescription("Your Trash is empty").assertIsDisplayed()
        compose.onNodeWithContentDescription("Browse files").performClick()
        compose.onNodeWithTag("trash_restore_all").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, browseCount)
            assertEquals(0, mutationCount)
        }
    }

    private fun entry(
        id: String,
        root: String,
    ) = TrashEntry(
        id = id,
        name = "notes.txt",
        isDirectory = false,
        size = 100,
        originalParent = FileNodeId.file("$root/Download"),
        trashedId = FileNodeId.file("$root/.Trash/$id/notes.txt"),
        holderId = FileNodeId.file("$root/.Trash/$id"),
        volumeRoot = FileNodeId.file(root),
        deletedAt = System.currentTimeMillis(),
        operationId = "test",
    )
}
