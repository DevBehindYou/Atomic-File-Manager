package com.devbehindyou.atomicfilemanager

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseViewModel
import com.devbehindyou.atomicfilemanager.ui.screens.PrimaryBrowseStoreRegistry
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

/** Actual MainActivity tab wiring; only disposable app-cache folders and in-memory copying. */
@RunWith(AndroidJUnit4::class)
class BrowseTabSessionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun closingHighestTabAndReopeningSameFolderCreatesFreshStateAcrossRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = createFixture(context)
        var failure: Throwable? = null
        var survivor: BrowseViewModel? = null
        try {
            Log.i("TabSessionTest", "stage=open-surviving-tab")
            openFixture(fixture.rootId)
            awaitItem(fixture.survivorId)
            val primary = existingPrimary(fixture.rootId)
            survivor = primary
            compose.runOnIdle {
                primary.toggleSelection(fixture.sourceId)
                primary.copySelected()
            }
            val clipboard = checkNotNull(primary.uiState.value.clipboard)
            clickItem(fixture.survivorId)
            awaitFolder(primary, fixture.survivorId)
            compose.runOnIdle { primary.onSearchQueryChanged("Target") }
            Log.i("TabSessionTest", "stage=open-highest-tab")
            openInNewTab(fixture.targetId)

            awaitItem(fixture.targetSourceId)
            longPressItem(fixture.targetSourceId)
            compose.onNodeWithContentDescription("Copy").performClick()
            compose.onNodeWithTag("paste_button").assertExists()
            clickItem(fixture.childId)
            awaitDescription("Close tab Child")
            Log.i("TabSessionTest", "stage=close-old-tab")
            compose.onNodeWithContentDescription("Close tab Child").performClick()

            awaitFolder(primary, fixture.survivorId)
            assertSame(clipboard, primary.uiState.value.clipboard)
            assertEquals("Target", primary.uiState.value.searchQuery)
            Log.i("TabSessionTest", "stage=reopen-same-folder")
            openInNewTab(fixture.targetId)
            assertFreshTarget(fixture)

            Log.i("TabSessionTest", "stage=recreate")
            recreateActivity()
            assertFreshTarget(fixture)
            // A fresh Target has no closed tab's Child -> Target history, search or selection.
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitTag("home_screen")
            compose.onNodeWithTag("tab_browse").performClick()
            assertFreshTarget(fixture)

            Log.i("TabSessionTest", "stage=verify-survivor")
            compose.onNode(
                hasContentDescription("Survivor") and hasAnyAncestor(hasTestTag("browse_tabs")),
            ).performClick()
            awaitFolder(primary, fixture.survivorId)
            assertSame(primary, existingPrimary(fixture.rootId))
            assertSame(clipboard, primary.uiState.value.clipboard)
            assertEquals(listOf(fixture.sourceId), clipboard.items.map { it.id })
            assertEquals("Target", primary.uiState.value.searchQuery)
            compose.runOnIdle { assertTrue(primary.navigateBack()) }
            awaitFolder(primary, fixture.rootId)
        } catch (testFailure: Throwable) {
            failure = testFailure
        } finally {
            Log.i("TabSessionTest", "stage=teardown")
            failure =
                preserveFailure(failure) {
                    compose.runOnIdle {
                        survivor?.clearSelection()
                        survivor?.clearClipboard()
                    }
                    compose.onNodeWithTag("tab_home").performClick()
                    awaitTag("home_screen")
                }
            failure = preserveFailure(failure) { removeFixture(context, fixture) }
        }
        failure?.let { throw it }
    }

    private fun assertFreshTarget(fixture: Fixture) {
        awaitItem(fixture.childId)
        awaitItem(fixture.targetSourceId)
        compose.onNodeWithTag("paste_button").assertDoesNotExist()
        awaitDescription("Close tab Target")
        compose.onNodeWithContentDescription("Close tab Target").assertExists()
    }

    private fun openFixture(root: FileNodeId) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(
                Intent(activity.intent)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN, OpenTarget.Folder(root.raw).encode()),
            )
        }
    }

    private fun openInNewTab(folder: FileNodeId) {
        awaitItem(folder)
        longPressItem(folder)
        val more = compose.activity.getString(R.string.selection_more_actions)
        compose.onNodeWithContentDescription(more).performClick()
        compose.onNodeWithContentDescription("New tab").performClick()
    }

    private fun clickItem(id: FileNodeId) {
        awaitItem(id)
        compose.onNodeWithTag("file_item_${id.raw}").performClick()
    }

    private fun longPressItem(id: FileNodeId) {
        compose.onNodeWithTag("file_item_${id.raw}").performTouchInput { longClick() }
    }

    private fun awaitItem(id: FileNodeId) = awaitTag("file_item_${id.raw}")

    private fun awaitDescription(description: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitFolder(
        model: BrowseViewModel,
        folder: FileNodeId,
    ) {
        compose.waitUntil(10_000) {
            val state = model.uiState.value
            state.currentFolderId == folder && !state.isLoading && !state.isRefreshing
        }
    }

    private fun existingPrimary(root: FileNodeId): BrowseViewModel {
        lateinit var model: BrowseViewModel
        compose.activityRule.scenario.onActivity { activity ->
            model =
                checkNotNull(
                    ViewModelProvider(
                        activity,
                        object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                error("Expected existing primary registry")
                        },
                    )[PrimaryBrowseStoreRegistry::class.java].existing("", root),
                )
        }
        return model
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

    private fun createFixture(context: Context): Fixture =
        runBlocking(Dispatchers.IO) {
            val root = File(context.cacheDir, "browse-session-${UUID.randomUUID()}")
            val directories =
                listOf(root, File(root, "Source"), File(root, "Survivor"), File(root, "Survivor/Target")) +
                    listOf(File(root, "Survivor/Target/Child"), File(root, "Survivor/Target/Source"))
            check(root.mkdir()) { "Could not create owned fixture root" }
            val created = mutableListOf(root)
            try {
                directories.drop(1).forEach {
                    check(it.mkdir()) { "Could not create owned fixture directory" }
                    created += it
                }
            } catch (creationFailure: Throwable) {
                preserveFailure(creationFailure) { removeFixture(context, Fixture(root, created)) }
                throw creationFailure
            }
            Fixture(root, directories)
        }

    private fun removeFixture(
        context: Context,
        fixture: Fixture,
    ) = runBlocking(Dispatchers.IO) {
        val root = fixture.root
        check(root.parentFile?.canonicalFile == context.cacheDir.canonicalFile)
        check(root.name.startsWith("browse-session-"))
        val owner = root.name.removePrefix("browse-session-")
        check(UUID.fromString(owner).toString() == owner)
        val canonicalRoot = File(context.cacheDir.canonicalFile, root.name)
        check(root.canonicalFile == canonicalRoot)
        fixture.directories.asReversed().forEach { directory ->
            check(directory.canonicalFile.toPath().startsWith(canonicalRoot.toPath()))
            if (directory.exists()) {
                check(directory.isDirectory && directory.list()?.isEmpty() == true) { "Owned directory is not empty" }
                check(directory.delete()) { "Could not remove empty owned directory" }
            }
        }
    }

    private fun preserveFailure(
        original: Throwable?,
        action: () -> Unit,
    ): Throwable? {
        val next = runCatching(action).exceptionOrNull() ?: return original
        if (original == null) return next
        original.addSuppressed(next)
        return original
    }

    private data class Fixture(val root: File, val directories: List<File>) {
        val rootId = FileNodeId.file(root.absolutePath)
        val sourceId = FileNodeId.file(File(root, "Source").absolutePath)
        val survivorId = FileNodeId.file(File(root, "Survivor").absolutePath)
        val targetId = FileNodeId.file(File(root, "Survivor/Target").absolutePath)
        val childId = FileNodeId.file(File(root, "Survivor/Target/Child").absolutePath)
        val targetSourceId = FileNodeId.file(File(root, "Survivor/Target/Source").absolutePath)
    }
}
