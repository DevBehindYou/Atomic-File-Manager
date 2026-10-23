package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.AtomicAppContent
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.data.volume.PhoneFileIndex
import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.getOrNull
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.ui.shortcuts.OpenTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the real shell's explicit-root mutations without depending on model-store internals. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = AtomicApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrimaryBrowseRootReplacementComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun homeRetainsClipboardButExplicitRootAToBToADoesNotResurrectTheReplacedModel() {
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher().immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val app = RuntimeEnvironment.getApplication() as AtomicApp
        val original = app.container
        val containerField = AtomicApp::class.java.getDeclaredField("container").apply { isAccessible = true }
        val memory = InMemoryBackend("Root A")
        val (source, otherRoot, otherSource) =
            runBlocking {
                val source = checkNotNull(memory.createDirectory(memory.rootId, "Source").getOrNull())
                val other = checkNotNull(memory.createDirectory(memory.rootId, "Root B").getOrNull())
                val otherSource = checkNotNull(memory.createDirectory(other.id, "Other source").getOrNull())
                Triple(source, other, otherSource)
            }
        val listing = GetDirectoryListingUseCase { memory }
        val settings =
            object : SettingsRepository {
                override val settings = MutableStateFlow(AppSettings())

                override fun update(transform: (AppSettings) -> AppSettings) {
                    settings.value = transform(settings.value)
                }
            }
        val queue =
            OperationQueue(
                engine = { operation, _ -> flowOf(OperationSnapshot(operation, OperationStatus.Cancelled)) },
                journal = null,
                scope = scope,
            )
        containerField.set(
            app,
            object : AppContainer by original {
                override val getDirectoryListingUseCase = listing
                override val getNodeUseCase = GetNodeUseCase { memory }
                override val settingsRepository = settings
                override val operationQueue = queue
                override val phoneFileIndex = PhoneFileIndex(listing)
            },
        )
        val request = mutableStateOf<String?>(OpenTarget.Folder(memory.rootId.raw).encode())
        val render = mutableStateOf(true)
        var owner: ViewModelStoreOwner? = null
        try {
            compose.setContent {
                owner = LocalView.current.findViewTreeViewModelStoreOwner()
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    if (render.value) {
                        AtomicAppContent(
                            openRequest = request.value,
                            onOpenRequestHandled = { handled -> if (request.value == handled) request.value = null },
                        )
                    }
                }
            }
            awaitItem(source.id)
            compose.onNodeWithTag("file_item_${source.id.raw}").performTouchInput { longClick() }
            compose.onNodeWithContentDescription("Copy").performClick()
            compose.onNodeWithTag("paste_button").assertExists()
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("tab_browse").performClick()
            awaitItem(source.id)
            compose.onNodeWithTag("paste_button").assertExists()

            compose.runOnIdle { request.value = OpenTarget.Folder(otherRoot.id.raw).encode() }
            awaitItem(otherSource.id)
            compose.onNodeWithTag("paste_button").assertDoesNotExist()
            compose.runOnIdle { request.value = OpenTarget.Folder(memory.rootId.raw).encode() }
            awaitItem(source.id)
            compose.onNodeWithTag("paste_button").assertDoesNotExist()
        } finally {
            compose.runOnIdle {
                render.value = false
                owner?.viewModelStore?.clear()
            }
            compose.waitForIdle()
            scope.cancel()
            shadowOf(Looper.getMainLooper()).idle()
            containerField.set(app, original)
            Dispatchers.resetMain()
        }
    }

    private fun awaitItem(id: FileNodeId) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithTag("file_item_${id.raw}").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

