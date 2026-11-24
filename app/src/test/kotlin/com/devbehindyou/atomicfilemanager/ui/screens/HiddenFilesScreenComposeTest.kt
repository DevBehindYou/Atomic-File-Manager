package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.HiddenItem
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.repository.HiddenFilesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h900dp", application = Application::class)
class HiddenFilesScreenComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun visibilitySwitchOnlyChangesTheFilesPreference() {
        val visibility = mutableStateOf(false)
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                HiddenFilesScreen(
                    repository = repository,
                    onNavigateBack = {},
                    visibility = HiddenFilesVisibility(visibility.value) { visibility.value = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Hidden is not encrypted").assertExists()
        compose.onNodeWithText("Notes.txt").assertHasNoClickAction()
        compose.onNodeWithText("Private.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show hidden files").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Notes.txt").assertExists()
        compose.onNodeWithText("Private.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Lock now").assertDoesNotExist()
    }

    @Test
    fun privateRouteOnlyShowsPrivateEntriesAndForwardsAnExplicitRelock() {
        var locks = 0
        compose.setContent {
            AtomicTheme(darkTheme = true, wallpaperAccent = false) {
                HiddenFilesScreen(
                    repository = repository,
                    onNavigateBack = {},
                    privateOnly = true,
                    onLockPrivateFiles = { locks++ },
                )
            }
        }

        compose.onNodeWithText("Private.txt").assertHasNoClickAction()
        compose.onNodeWithText("Notes.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show hidden files").assertDoesNotExist()
        compose.onNodeWithContentDescription("Lock now").performClick()
        compose.runOnIdle { assertEquals(1, locks) }
    }

    private val repository =
        object : HiddenFilesRepository {
            override val hiddenItems =
                MutableStateFlow(
                    listOf(
                        hiddenItem("notes", "Notes.txt", HideMode.FAST_OBSCURE),
                        hiddenItem("private", "Private.txt", HideMode.PRIVATE_STORAGE),
                    ),
                )

            override suspend fun hideFromGallery(node: FileNode): Result<HiddenItem> = unexpectedMutation()

            override suspend fun unhideFromGallery(item: HiddenItem): Result<Unit> = unexpectedMutation()

            override suspend fun fastObscure(node: FileNode): Result<HiddenItem> = unexpectedMutation()

            override suspend fun restoreFastObscured(item: HiddenItem): Result<Unit> = unexpectedMutation()

            override suspend fun moveToPrivateStorage(node: FileNode): Result<HiddenItem> = unexpectedMutation()

            override suspend fun restoreFromPrivateStorage(
                item: HiddenItem,
                destinationParent: FileNodeId,
            ): Result<Unit> = unexpectedMutation()

            override suspend fun deleteHiddenItem(item: HiddenItem): Result<Unit> = unexpectedMutation()

            override suspend fun recoverUnfinishedOperations(): Unit = unexpectedMutation()
        }

    private fun hiddenItem(
        id: String,
        name: String,
        mode: HideMode,
    ) = HiddenItem(
        id = id,
        originalLocation = "/Documents/$name",
        currentLocation = "/Hidden/$id.vault",
        originalName = name,
        size = 12,
        mode = mode,
        hiddenAt = 1L,
    )

    private fun unexpectedMutation(): Nothing = error("The presentation cannot change file records")
}
