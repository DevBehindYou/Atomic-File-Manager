package com.devbehindyou.atomicfilemanager.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.data.repository.SyncPairStore
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.usecase.FolderComparer
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncMode
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface ComparePairStore {
    fun findMode(
        left: String,
        right: String,
    ): SyncMode?

    fun save(pair: SyncPair)
}

internal data class ComparePreferenceState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val mode: SyncMode = SyncMode.COPY_NEW,
    val save: FolderCompareSaveState = FolderCompareSaveState(null),
)

/** Owns one immutable pair; storage access and construction happen through the IO boundary. */
internal class FolderComparePreferences(
    private val left: String,
    private val right: String,
    private val scope: CoroutineScope,
    private val preferences: StoragePreferenceAccess<ComparePairStore>,
) {
    private val mutableState = MutableStateFlow(ComparePreferenceState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L
    private var choiceRevision = 0L

    fun load() {
        loadJob?.cancel()
        val request = ++generation
        val revision = choiceRevision
        mutableState.update { it.copy(loading = true, loadFailed = false) }
        loadJob =
            scope.launch {
                try {
                    val saved = preferences.access { it.findMode(left, right) }
                    if (request == generation) {
                        mutableState.update {
                            it.copy(
                                loading = false,
                                mode = if (choiceRevision == revision) saved ?: SyncMode.COPY_NEW else it.mode,
                                save = it.save.copy(savedMode = saved),
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (request == generation) mutableState.update { it.copy(loading = false, loadFailed = true) }
                }
            }
    }

    fun select(mode: SyncMode) {
        choiceRevision++
        mutableState.update { it.copy(mode = mode) }
    }

    fun save(mode: SyncMode) {
        val current = state.value
        if (current.loading || current.loadFailed || current.save.saving) return
        val pair = SyncPair(left, right, mode)
        mutableState.update { it.copy(save = it.save.copy(saving = true, failed = false)) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                preferences.update { it.save(pair) }
                mutableState.update { it.copy(save = FolderCompareSaveState(mode)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(save = it.save.copy(saving = false, failed = true)) }
            }
        }
    }
}

internal fun comparePairPreferences(
    context: Context,
    io: CoroutineDispatcher = Dispatchers.IO,
): StoragePreferenceAccess<ComparePairStore> =
    StoragePreferenceAccess(io, StoragePreferenceLocks.syncPairs) {
        val store = SyncPairStore(context.applicationContext)
        object : ComparePairStore {
            override fun findMode(
                left: String,
                right: String,
            ) = store.find(left, right)?.mode

            override fun save(pair: SyncPair) = store.save(pair)
        }
    }

/** Keeps loading and failed preference reads outside the editable comparison and review UI. */
@Composable
internal fun StoredFolderCompareScreen(
    left: FileNodeId,
    right: FileNodeId,
    comparer: FolderComparer,
    onRun: (List<FileOperation>) -> Unit,
    onBack: () -> Unit,
) {
    key(left, right) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val preferences =
            remember(context, scope) {
                FolderComparePreferences(left.raw, right.raw, scope, comparePairPreferences(context))
            }
        val state by preferences.state.collectAsState()
        LaunchedEffect(preferences) { preferences.load() }
        if (!state.loading && !state.loadFailed) {
            FolderCompareScreen(
                left, right, comparer, onRun, onBack,
                savedMode = state.mode,
                onSave = preferences::save,
                saveState = state.save,
                onModeChanged = preferences::select,
            )
        } else {
            BackHandler(onBack = onBack)
            Column(
                Modifier.fillMaxSize().background(Atomic.colors.background)
                    .statusBarsPadding().navigationBarsPadding(),
            ) {
                AtomicPushedHeader(onBack = onBack, title = "Compare")
                if (state.loading) {
                    AtomicLoading(stringResource(R.string.compare_preferences_loading))
                } else {
                    AtomicEmptyState(
                        message = stringResource(R.string.compare_preferences_load_error),
                        detail = stringResource(R.string.compare_preferences_load_error_detail),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = preferences::load,
                        modifier = Modifier.padding(AtomicSpacing.s16),
                    )
                }
            }
        }
    }
}
