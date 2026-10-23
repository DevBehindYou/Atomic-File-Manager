package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

/** Activity-retained child stores for saved Browse tabs; ordinary screen hiding does not retire them. */
internal class PrimaryBrowseStoreRegistry : ViewModel() {
    private class Entry(factory: ViewModelProvider.Factory) {
        private val store = ViewModelStore()
        val model = ViewModelProvider(store, factory)[BrowseViewModel::class.java]

        fun retire() = model.retireAfterAcceptedWork(store::clear)
    }

    private val entries = mutableMapOf<String, Entry>()
    // The initial active screen may acquire its model before the shell's first SideEffect.
    private var allowedKeys: Set<String>? = null
    private var cleared = false

    /** Reconcile every saved tab, including inactive tabs, without constructing unseen models. */
    @MainThread
    fun reconcile(tabs: BrowseTabsState) {
        if (cleared) return
        val keys = tabs.tabs.mapTo(mutableSetOf()) { primaryBrowseViewModelKey(it.stateKey, FileNodeId(it.folderRaw)) }
        allowedKeys = keys
        val removed = entries.keys.filter { it !in keys }.mapNotNull { entries.remove(it) }
        // Remove first: callbacks retain only their own entry and can never clear its replacement.
        removed.forEach(Entry::retire)
    }

    @MainThread
    fun model(
        tabKey: String,
        initialRoot: FileNodeId,
        factory: ViewModelProvider.Factory,
    ): BrowseViewModel {
        check(!cleared) { "Primary Browse owner has been cleared" }
        val key = primaryBrowseViewModelKey(tabKey, initialRoot)
        check(allowedKeys?.contains(key) != false) { "Browse session or root is no longer retained" }
        return entries.getOrPut(key) { Entry(factory) }.model
    }

    /** Read an existing entry without creating or reviving a closed session. */
    @MainThread
    fun existing(
        tabKey: String,
        initialRoot: FileNodeId,
    ): BrowseViewModel? = entries[primaryBrowseViewModelKey(tabKey, initialRoot)]?.model

    @MainThread
    override fun onCleared() {
        cleared = true
        allowedKeys = emptySet()
        val removed = entries.values.toList()
        entries.clear()
        removed.forEach(Entry::retire)
    }

    companion object {
        @MainThread
        fun obtain(owner: ViewModelStoreOwner): PrimaryBrowseStoreRegistry =
            ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())[PrimaryBrowseStoreRegistry::class.java]
    }
}
