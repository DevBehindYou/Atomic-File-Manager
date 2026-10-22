package com.devbehindyou.atomicfilemanager

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseViewModel
import com.devbehindyou.atomicfilemanager.ui.shortcuts.OpenTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Reads disposable app-private folders; copying only stages an in-memory clipboard. */
@RunWith(AndroidJUnit4::class)
class PrimaryBrowseRecreationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigationAndActivityRecreationRetainPrimaryFolderClipboardAndBackHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root =
            runBlocking(Dispatchers.IO) {
                File(context.cacheDir, "browse-visibility-${UUID.randomUUID()}").apply {
                    check(mkdir())
                    check(File(this, "Child").mkdir())
                    check(File(this, "Source").mkdir())
                }
            }
        val rootId = FileNodeId.file(root.absolutePath)
        val childId = FileNodeId.file(File(root, "Child").absolutePath)
        val sourceId = FileNodeId.file(File(root, "Source").absolutePath)
        try {
            compose.activityRule.scenario.onActivity { activity ->
                activity.startActivity(
                    Intent(activity.intent)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN, OpenTarget.Folder(rootId.raw).encode()),
                )
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("file_item_${childId.raw}").fetchSemanticsNodes().isNotEmpty()
            }
            lateinit var model: BrowseViewModel
            compose.activityRule.scenario.onActivity { model = existingModel(it, rootId) }
            compose.runOnIdle {
                model.toggleSelection(sourceId)
                model.copySelected()
            }
            val clipboard = checkNotNull(model.uiState.value.clipboard)
            compose.onNodeWithTag("file_item_${childId.raw}").performClick()
            awaitFolder(model, childId)
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("tab_browse").performClick()
            awaitFolder(model, childId)
            assertSame(clipboard, model.uiState.value.clipboard)

            backgroundAndReturn()
            awaitFolder(model, childId)
            assertSame(clipboard, model.uiState.value.clipboard)

            recreateActivity()
            compose.activityRule.scenario.onActivity { assertSame(model, existingModel(it, rootId)) }
            awaitFolder(model, childId)
            assertSame(clipboard, model.uiState.value.clipboard)
            assertEquals(listOf(sourceId), clipboard.items.map { it.id })
            compose.runOnIdle { assertTrue(model.navigateBack()) }
            awaitFolder(model, rootId)
        } finally {
            runCatching { compose.onNodeWithTag("tab_home").performClick() }
            runBlocking(Dispatchers.IO) {
                check(root.parentFile?.canonicalFile == context.cacheDir.canonicalFile)
                root.deleteRecursively()
            }
        }
    }

    private fun existingModel(
        activity: MainActivity,
        root: FileNodeId,
    ): BrowseViewModel =
        ViewModelProvider(
            activity,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T = error("Expected retained primary model")
            },
        )["browse:${root.raw}", BrowseViewModel::class.java]

    private fun awaitFolder(
        model: BrowseViewModel,
        folder: FileNodeId,
    ) {
        compose.waitUntil(5_000) {
            val state = model.uiState.value
            state.currentFolderId == folder && !state.isLoading && !state.isRefreshing
        }
    }

    private fun recreateActivity() {
        compose.waitForIdle()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val recreated = worker.submit { compose.activityRule.scenario.recreate() }
            compose.waitUntil(10_000) { recreated.isDone }
            recreated.get()
        } finally {
            worker.shutdownNow()
        }
        compose.waitForIdle()
    }

    private fun backgroundAndReturn() {
        compose.waitForIdle()
        val worker = Executors.newSingleThreadExecutor()
        var failure: Throwable? = null
        try {
            worker.submit { compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED) }
                .get(10, TimeUnit.SECONDS)
        } catch (backgroundFailure: Throwable) {
            failure = backgroundFailure
        } finally {
            // Restore the Activity even when entering the background fails or times out.
            val resumeFailure =
                runCatching {
                    worker.submit { compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) }
                        .get(10, TimeUnit.SECONDS)
                }.exceptionOrNull()
            worker.shutdownNow()
            if (resumeFailure != null) {
                val original = failure
                if (original == null) failure = resumeFailure else original.addSuppressed(resumeFailure)
            }
        }
        failure?.let { throw it }
        compose.waitForIdle()
    }
}
