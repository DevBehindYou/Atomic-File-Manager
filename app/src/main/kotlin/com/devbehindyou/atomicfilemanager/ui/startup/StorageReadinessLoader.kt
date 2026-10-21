package com.devbehindyou.atomicfilemanager.ui.startup

import androidx.annotation.MainThread
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Access and volumes come from one successful read, never an unverified default permission. */
data class StorageSnapshot(val hasAccess: Boolean, val volumes: List<StorageVolumeInfo>)

sealed interface StorageReadinessState {
    val snapshot: StorageSnapshot?

    data class Loading(override val snapshot: StorageSnapshot? = null) : StorageReadinessState

    data class Ready(override val snapshot: StorageSnapshot) : StorageReadinessState

    data class Failed(override val snapshot: StorageSnapshot? = null) : StorageReadinessState
}

/** Owns latest-request publication; the last known snapshot remains usable during refresh. */
internal class StorageReadinessLoader(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readSnapshot: () -> StorageSnapshot,
) {
    private val mutableState = MutableStateFlow<StorageReadinessState>(StorageReadinessState.Loading())
    val state: StateFlow<StorageReadinessState> = mutableState.asStateFlow()
    private var loading: Job? = null
    private var revision = 0L

    @MainThread
    fun refresh() {
        val requestedRevision = ++revision
        val previous = state.value.snapshot
        loading?.cancel()
        mutableState.value = StorageReadinessState.Loading(previous)
        loading =
            scope.launch {
                try {
                    val snapshot = withContext(ioDispatcher) { readSnapshot() }
                    currentCoroutineContext().ensureActive()
                    if (revision == requestedRevision) mutableState.value = StorageReadinessState.Ready(snapshot)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (revision == requestedRevision) mutableState.value = StorageReadinessState.Failed(previous)
                }
            }
    }
}
