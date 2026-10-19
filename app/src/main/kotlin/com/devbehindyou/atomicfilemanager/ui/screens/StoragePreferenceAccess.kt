package com.devbehindyou.atomicfilemanager.ui.screens

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Keeps preference construction, reads and read-modify-write operations off the UI thread. */
internal class StoragePreferenceAccess<T>(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mutex: Mutex = Mutex(),
    createStore: () -> T,
) {
    private val store by lazy(createStore)

    suspend fun <R> access(block: (T) -> R): R =
        mutex.withLock {
            withContext(ioDispatcher) { block(store) }
        }

    /** Finishes an accepted metadata change even if its screen closes, without publishing to a closed screen. */
    suspend fun <R> update(block: (T) -> R): R {
        val caller = currentCoroutineContext()
        try {
            return withContext(NonCancellable) { access(block) }
        } finally {
            caller.ensureActive()
        }
    }
}

/** Preserves accepted update/read ordering when a Storage screen is replaced. */
internal object StoragePreferenceLocks {
    val savedServers = Mutex()
    val syncPairs = Mutex()
}
