package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.repository.FolderSortMemory
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.CreateDirectoryUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.DeleteFileUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.RenameFileUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/** Real wide BrowseScreen consumers, with independent primary and secondary listing counters. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h800dp", application = AtomicApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecondaryBrowseScreenVisibilityComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aComposedStoppedWideScreenDoesNotStartItsSecondaryListing() {
        withScreen(Lifecycle.State.CREATED) { fixture ->
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Stopped secondary pane must stay paused from construction", 0, fixture.started.get())
        }
    }

    @Test
    fun switchingTheRealWideScreenToPreviewStopsItsRetainedSecondaryListing() {
        withScreen(Lifecycle.State.STARTED) { fixture ->
            awaitCondition { fixture.emitted.get() == 1 }
            compose.onNodeWithContentDescription("Switch to preview pane").performClick()
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Preview hides the secondary listing without retiring its model", 1, fixture.cancelled.get())
        }
    }

    private fun withScreen(
        initialState: Lifecycle.State,
        test: (Fixture) -> Unit,
    ) {
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher().immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val app = RuntimeEnvironment.getApplication() as AtomicApp
        val original = app.container
        val field = AtomicApp::class.java.getDeclaredField("container").apply { isAccessible = true }
        val memory = InMemoryBackend()
        val started = AtomicInteger()
        val emitted = AtomicInteger()
        val cancelled = AtomicInteger()
        val secondaryBackend =
            object : StorageBackend by memory {
                override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
                    flow {
                        started.incrementAndGet()
                        try {
                            emit(FileResult.Success(emptyList()))
                            emitted.incrementAndGet()
                            awaitCancellation()
                        } finally {
                            cancelled.incrementAndGet()
                        }
                    }
            }
        val sorts =
            object : FolderSortMemory {
                override suspend fun get(folderRaw: String): String? = null

                override suspend fun set(
                    folderRaw: String,
                    value: String,
                ) = Unit
            }
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
        val metadata = GetNodeUseCase(Dispatchers.Main.immediate) { memory }
        val primaryStore = ViewModelStore()
        val primary =
            ViewModelProvider(
                primaryStore,
                BrowseViewModel.provideFactory(
                    memory.rootId,
                    GetDirectoryListingUseCase(Dispatchers.Main.immediate) { memory },
                    metadata,
                    CreateDirectoryUseCase { memory },
                    RenameFileUseCase { memory },
                    DeleteFileUseCase { memory },
                    queue,
                    original.transferBubbleRepository,
                    folderSorts = sorts,
                    startVisible = false,
                ),
            )[BrowseViewModel::class.java]
        field.set(
            app,
            object : AppContainer by original {
                override val getDirectoryListingUseCase =
                    GetDirectoryListingUseCase(Dispatchers.Main.immediate) { secondaryBackend }
                override val getNodeUseCase = metadata
                override val settingsRepository = settings
                override val operationQueue = queue
                override val folderSortMemory = sorts
            },
        )
        val owner = ScreenVisibilityOwner(initialState)
        val render = mutableStateOf(true)
        try {
            compose.setContent {
                AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                        if (render.value) {
                            BrowseScreen(memory.rootId, onNavigateBack = {}, viewModel = primary)
                        }
                    }
                }
            }
            test(Fixture(started, emitted, cancelled))
        } finally {
            compose.runOnIdle {
                render.value = false
                owner.registry.currentState = Lifecycle.State.DESTROYED
                primaryStore.clear()
            }
            awaitCondition {
                cancelled.get() == started.get() && primary.viewModelScope.coroutineContext.job.isCompleted
            }
            scope.cancel()
            shadowOf(Looper.getMainLooper()).idle()
            field.set(app, original)
            Dispatchers.resetMain()
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private class Fixture(val started: AtomicInteger, val emitted: AtomicInteger, val cancelled: AtomicInteger)
}

private class ScreenVisibilityOwner(initialState: Lifecycle.State) : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }
    override val lifecycle: Lifecycle get() = registry
}
