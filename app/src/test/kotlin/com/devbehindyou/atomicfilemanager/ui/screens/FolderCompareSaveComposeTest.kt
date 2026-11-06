package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.getOrNull
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.FolderComparer
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FolderCompareSaveComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savingRequiresConfirmationAndFailureCanBeRetried() {
        val backend = InMemoryBackend()
        val comparer = FolderComparer { backend }
        val external = mutableStateOf(FolderCompareSaveState(null))
        val requests = mutableListOf<SyncMode>()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                FolderCompareScreen(
                    left = backend.rootId,
                    right = backend.rootId,
                    comparer = comparer,
                    onRun = { error("test must not run file operations") },
                    onBack = {},
                    onSave = { requests += it },
                    saveState = external.value,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("save_sync_pair").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("save_sync_pair").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Saved for one-tap sync").assertDoesNotExist()

        compose.runOnIdle { external.value = FolderCompareSaveState(null, saving = true) }
        compose.onNodeWithTag("save_sync_pair").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Saving…").assertExists()
        compose.onNodeWithContentDescription("Saved for one-tap sync").assertDoesNotExist()

        compose.runOnIdle { external.value = FolderCompareSaveState(null, failed = true) }
        compose.onNodeWithText("Could not save this pair. Try again.").assertExists()
        compose.onNodeWithTag("save_sync_pair").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Saved for one-tap sync").assertDoesNotExist()
        assertEquals(listOf(SyncMode.COPY_NEW, SyncMode.COPY_NEW), requests)

        compose.runOnIdle { external.value = FolderCompareSaveState(SyncMode.COPY_NEW) }
        compose.onNodeWithContentDescription("Saved for one-tap sync").assertExists()
        compose.onNodeWithTag("save_sync_pair").assertIsNotEnabled()
    }

    @Test
    fun storedMirrorModeIsSelectedAndReviewedAsMirror() {
        val fixture = comparisonWithExtraRightFile()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                FolderCompareScreen(
                    left = fixture.left,
                    right = fixture.right,
                    comparer = FolderComparer { fixture.backend },
                    onRun = { error("review must not run file operations") },
                    onBack = {},
                    savedMode = SyncMode.MIRROR,
                )
            }
        }
        awaitReviewButton()
        compose.onNodeWithTag("sync_mode_MIRROR").assertIsOn()
        compose.onNodeWithTag("sync_mode_COPY_NEW").assertIsOff()
        compose.onNodeWithTag("review_sync_button").performClick()
        compose.onNodeWithText(MIRROR_SUMMARY).assertExists()
        compose.onNodeWithText("− extra.txt").assertExists()
        compose.onNodeWithTag("start_sync_button").assertExists()
    }

    @Test
    fun unsavedMirrorChoiceAndOpenReviewSurviveStateRestoration() {
        val fixture = comparisonWithExtraRightFile()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                FolderCompareScreen(
                    left = fixture.left,
                    right = fixture.right,
                    comparer = FolderComparer { fixture.backend },
                    onRun = { error("restoration must not run file operations") },
                    onBack = {},
                    savedMode = SyncMode.COPY_NEW,
                    onSave = { error("an unsaved choice must not write preferences") },
                    saveState = FolderCompareSaveState(SyncMode.COPY_NEW),
                )
            }
        }
        awaitReviewButton()
        compose.onNodeWithTag("sync_mode_COPY_NEW").assertIsOn()
        compose.onNodeWithTag("sync_mode_MIRROR").performClick().assertIsOn()
        compose.onNodeWithTag("review_sync_button").performClick()
        compose.onNodeWithText(MIRROR_SUMMARY).assertExists()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(MIRROR_SUMMARY).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("− extra.txt").assertExists()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithTag("sync_mode_MIRROR").assertIsOn()
        compose.onNodeWithTag("sync_mode_COPY_NEW").assertIsOff()
        compose.onNodeWithTag("save_sync_pair").assertIsEnabled()
    }

    private fun awaitReviewButton() {
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("review_sync_button").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun comparisonWithExtraRightFile(): CompareFixture =
        runBlocking {
            val backend = InMemoryBackend()
            val left = checkNotNull(backend.createDirectory(backend.rootId, "left").getOrNull()).id
            val right = checkNotNull(backend.createDirectory(backend.rootId, "right").getOrNull()).id
            backend.putFile(right, "extra.txt", byteArrayOf(1))
            CompareFixture(backend, left, right)
        }

    private data class CompareFixture(val backend: InMemoryBackend, val left: FileNodeId, val right: FileNodeId)

    private companion object {
        const val MIRROR_SUMMARY = "Move 1 extra item to Trash."
    }
}
