package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

/** Retain primary state while expensive work belongs to a composed, STARTED screen. */
@Composable
internal fun PrimaryBrowseVisibility(
    viewModel: BrowseViewModel,
    openRequest: Int,
) = BrowsePaneVisibility(viewModel, openRequest, paneVisible = true)

/** Preview hides observation without resetting the retained secondary location or clipboard. */
@Composable
internal fun SecondaryBrowseVisibility(
    viewModel: BrowseViewModel,
    listingVisible: Boolean,
) = BrowsePaneVisibility(viewModel, openRequest = 0, paneVisible = listingVisible)

@Composable
private fun BrowsePaneVisibility(
    viewModel: BrowseViewModel,
    openRequest: Int,
    paneVisible: Boolean,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, openRequest, lifecycle, paneVisible) {
        if (!paneVisible) {
            viewModel.onHidden()
            return@DisposableEffect onDispose { viewModel.onHidden() }
        }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onVisible(openRequest)
                    Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> viewModel.onHidden()
                    else -> Unit
                }
            }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.onHidden()
        // addObserver catches up synchronously, including ON_START for an already-started owner.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.onHidden()
        }
    }
}

/**
 * The secondary pane belongs to this Browse composition, not the Activity. Keep it while
 * switching between browse and preview on a wide layout; release it on leaving Browse,
 * changing tab/root, or returning to a single pane. Compact layouts never call [factory].
 */
@Composable
internal fun rememberSecondaryBrowseViewModel(
    enabled: Boolean,
    tabKey: String,
    initialFolderId: FileNodeId,
    factory: () -> ViewModelProvider.Factory,
): BrowseViewModel? {
    if (!enabled) return null
    return remember(tabKey, initialFolderId) { SecondaryBrowseModel(factory()) }.viewModel
}

/** Retires observation immediately; accepted work drains before this entry's own store is cleared. */
internal class SecondaryBrowseModel(factory: ViewModelProvider.Factory) : RememberObserver {
    private val store = ViewModelStore()
    val viewModel = ViewModelProvider(store, factory)[BrowseViewModel::class.java]

    override fun onRemembered() = Unit

    override fun onForgotten() = viewModel.retireAfterAcceptedWork(store::clear)

    override fun onAbandoned() = viewModel.retireAfterAcceptedWork(store::clear)
}
