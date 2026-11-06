package com.devbehindyou.atomicfilemanager.ui.startup

import androidx.annotation.MainThread
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
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

internal sealed interface SettingsStartupState {
    data object Loading : SettingsStartupState

    data class Ready(val repository: SettingsRepository) : SettingsStartupState

    data class Failed(val cause: Exception) : SettingsStartupState
}

/**
 * Loads the existing, fully seeded repository before app content can read it on Main.
 * The factory must return the process-shared repository; cancelling an Activity only
 * cancels its wait and never replaces that repository or publishes a default snapshot.
 */
internal class SettingsStartupLoader(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val repository: () -> SettingsRepository,
) {
    private val mutableState = MutableStateFlow<SettingsStartupState>(SettingsStartupState.Loading)
    val state: StateFlow<SettingsStartupState> = mutableState.asStateFlow()
    private var loading: Job? = null

    @MainThread
    fun start() {
        if (loading?.isActive == true || state.value is SettingsStartupState.Ready) return
        mutableState.value = SettingsStartupState.Loading
        loading =
            scope.launch {
                try {
                    val loaded =
                        withContext(ioDispatcher) {
                            repository().also { it.settings.value }
                        }
                    currentCoroutineContext().ensureActive()
                    mutableState.value = SettingsStartupState.Ready(loaded)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    currentCoroutineContext().ensureActive()
                    mutableState.value = SettingsStartupState.Failed(failure)
                }
            }
    }
}
