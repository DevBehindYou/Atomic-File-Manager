package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.data.repository.SyncPairStore
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Saved folder syncs on Storage (ALL_IN_ONE_PLAN.md 4.1): a tap opens the comparison again with
 * the saved mode, and the plan is still shown before anything runs. Hidden until one is saved.
 */
@Composable
fun SyncPairsSection(onCompareFolders: (FileNodeId, FileNodeId) -> Unit) {
    val context = LocalContext.current
    val preferences =
        remember(context) {
            StoragePreferenceAccess(
                mutex = StoragePreferenceLocks.syncPairs,
            ) { SyncPairStore(context.applicationContext) }
        }
    val scope = rememberCoroutineScope()
    var pairs by remember(preferences) { mutableStateOf<List<SyncPair>?>(null) }
    var refreshRound by remember(preferences) { mutableIntStateOf(0) }
    var failed by remember(preferences) { mutableStateOf(false) }
    var removing by remember(preferences) { mutableStateOf(false) }
    LaunchedEffect(preferences, refreshRound) {
        failed = false
        try {
            pairs = preferences.access { it.all() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    // Pairs are saved on the compare screen; reload when coming back to Storage.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver {
                    _,
                    event,
                ->
                if (event == Lifecycle.Event.ON_RESUME) refreshRound++
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val loadedPairs = pairs
    if (failed) {
        AtomicText(stringResource(R.string.storage_syncs_load_error), AtomicTextRole.BodySecondary)
        AtomicButton(
            stringResource(R.string.action_retry),
            onClick = { refreshRound++ },
            variant = AtomicButtonVariant.Text,
        )
    }
    if (loadedPairs.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        AtomicSectionLabel("Saved folder syncs")
        Column {
            loadedPairs.forEach { pair ->
                AtomicFileRow(
                    name = SyncPairText.title(pair),
                    meta = CompareText.mode(pair.mode),
                    icon = AtomicIcons.DualPane,
                    onClick = { onCompareFolders(FileNodeId(pair.leftRaw), FileNodeId(pair.rightRaw)) },
                    trailing = {
                        AtomicIconButton(
                            AtomicIcons.Close,
                            "Forget ${SyncPairText.title(pair)}",
                            onClick = {
                                if (!removing) {
                                    removing = true
                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        try {
                                            pairs =
                                                preferences.update { store ->
                                                    store.remove(pair)
                                                    store.all()
                                                }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            failed = true
                                        } finally {
                                            removing = false
                                        }
                                    }
                                }
                            },
                            variant = AtomicIconButtonVariant.Destructive,
                            enabled = !removing,
                        )
                    },
                )
            }
        }
    }
}

/** Wording for saved pairs; pure so it is unit-tested. */
internal object SyncPairText {
    fun title(pair: SyncPair): String =
        "${CompareText.folderName(pair.leftRaw)} → ${CompareText.folderName(pair.rightRaw)}"
}
