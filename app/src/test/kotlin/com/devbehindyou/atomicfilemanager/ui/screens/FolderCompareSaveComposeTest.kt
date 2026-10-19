package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.FolderComparer
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncMode
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
        compose.onNodeWithText("Saved for one-tap sync").assertDoesNotExist()

        compose.runOnIdle { external.value = FolderCompareSaveState(null, saving = true) }
        compose.onNodeWithTag("save_sync_pair").assertIsNotEnabled()
        compose.onNodeWithText("Saving…").assertExists()
        compose.onNodeWithText("Saved for one-tap sync").assertDoesNotExist()

        compose.runOnIdle { external.value = FolderCompareSaveState(null, failed = true) }
        compose.onNodeWithText("Could not save this pair. Try again.").assertExists()
        compose.onNodeWithTag("save_sync_pair").assertIsEnabled().performClick()
        compose.onNodeWithText("Saved for one-tap sync").assertDoesNotExist()
        assertEquals(listOf(SyncMode.COPY_NEW, SyncMode.COPY_NEW), requests)

        compose.runOnIdle { external.value = FolderCompareSaveState(SyncMode.COPY_NEW) }
        compose.onNodeWithText("Saved for one-tap sync").assertExists()
        compose.onNodeWithTag("save_sync_pair").assertIsNotEnabled()
    }
}
