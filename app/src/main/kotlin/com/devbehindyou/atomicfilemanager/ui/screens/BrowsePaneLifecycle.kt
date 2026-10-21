package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

/** The Activity retains primary state; its listing and observers belong to the visible screen. */
@Composable
internal fun PrimaryBrowseVisibility(
    viewModel: BrowseViewModel,
    openRequest: Int,
) {
    DisposableEffect(viewModel, openRequest) {
        viewModel.onVisible(openRequest)
        onDispose { viewModel.onHidden() }
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

/** Also clears a model created by a composition that was abandoned before being applied. */
private class SecondaryBrowseModel(factory: ViewModelProvider.Factory) : RememberObserver {
    private val store = ViewModelStore()
    val viewModel = ViewModelProvider(store, factory)[BrowseViewModel::class.java]

    override fun onRemembered() = Unit

    override fun onForgotten() = store.clear()

    override fun onAbandoned() = store.clear()
}
