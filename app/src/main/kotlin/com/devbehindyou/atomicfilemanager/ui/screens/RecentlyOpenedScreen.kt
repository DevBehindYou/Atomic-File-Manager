package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.RecentItem
import com.devbehindyou.atomicfilemanager.domain.repository.RecentsRepository
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewDialog
import com.devbehindyou.atomicfilemanager.ui.components.FileThumbnail
import com.devbehindyou.atomicfilemanager.ui.components.iconFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val HISTORY_ROWS = 100

@Composable
internal fun RecentlyOpenedScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as AtomicApp).container
    var preview by remember { mutableStateOf<FileNode?>(null) }
    Column(
        Modifier.fillMaxSize().background(Atomic.colors.background)
            .statusBarsPadding().navigationBarsPadding().testTag("recently_opened_screen"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = "Recently opened")
        RecentlyOpenedHistory(container.recentsRepository, container.getNodeUseCase) { preview = it }
    }
    preview?.let { FilePreviewDialog(node = it, onDismiss = { preview = null }) }
}

@Composable
internal fun RecentlyOpenedHistory(
    repository: RecentsRepository,
    getNode: GetNodeUseCase,
    onPreview: (FileNode) -> Unit,
) {
    val openedFlow = remember(repository) { repository.observeOpened(HISTORY_ROWS) }
    val opened by openedFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var showClear by rememberSaveable { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var openingId by remember { mutableStateOf<FileNodeId?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = clearing || openingId != null

    fun open(item: RecentItem) {
        if (clearing || openingId != null) return
        openingId = item.id
        error = null
        scope.launch {
            try {
                val file = getNode.openedFile(item.id)
                if (file == null) {
                    error = "“${item.name}” is unavailable."
                } else {
                    onPreview(file)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Couldn’t open “${item.name}”."
            } finally {
                openingId = null
            }
        }
    }

    fun clear() {
        if (clearing || openingId != null) return
        showClear = false
        clearing = true
        error = null
        scope.launch {
            try {
                repository.clearOpened()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Couldn’t clear recent history. Try again."
            } finally {
                clearing = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AtomicSpacing.screenMargin, vertical = AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        item(key = "history-actions") {
            Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
                AtomicText(
                    "Files you opened recently. Clearing history won’t delete files.",
                    AtomicTextRole.BodySecondary,
                )
                AtomicButton(
                    "Clear recent history",
                    onClick = { showClear = true },
                    variant = AtomicButtonVariant.Ghost,
                    enabled = !busy && opened?.isNotEmpty() == true,
                )
                error?.let { AtomicText(it, AtomicTextRole.BodySecondary, color = Atomic.colors.error) }
                if (clearing) AtomicLoading("Clearing history…")
            }
        }
        val entries = opened
        when {
            entries == null -> item(key = "history-loading") { AtomicLoading("Loading history…") }
            entries.isEmpty() ->
                item(key = "history-empty") {
                    AtomicEmptyState(
                        message = "No files opened yet",
                        detail = "Files you open in Atomic will appear here.",
                    )
                }
            else ->
                items(entries, key = { "history:${it.id.raw}" }) { entry ->
                    RecentlyOpenedRow(entry, getNode, openingId == entry.id) { open(entry) }
                }
        }
    }
    if (showClear) ClearOpenedHistorySheet(onDismiss = { showClear = false }, onConfirm = ::clear)
}

/** Metadata lookup belongs to the visible row. Opening rechecks access immediately before preview. */
@Composable
private fun RecentlyOpenedRow(
    item: RecentItem,
    getNode: GetNodeUseCase,
    opening: Boolean,
    onOpen: () -> Unit,
) {
    val node by produceState<FileNode?>(null, getNode, item.id) {
        value =
            try {
                getNode.openedFile(item.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
    }
    val file = node
    AtomicFileRow(
        name = item.name,
        meta = if (opening) "Opening…" else ShortcutText.recentMeta(item),
        icon = file?.let(::iconFor) ?: AtomicIcons.Document,
        onClick = onOpen,
        modifier = Modifier.testTag("recently_opened_item_${item.id.raw}"),
        leading = file?.let { resolved -> @Composable { FileThumbnail(resolved) } },
    )
}

private suspend fun GetNodeUseCase.openedFile(id: FileNodeId): FileNode? =
    (invoke(id) as? FileResult.Success)?.value?.takeIf { !it.isDirectory && it.access.readable }

@Composable
private fun ClearOpenedHistorySheet(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AtomicSheet(label = "Clear recent history?", onDismiss = onDismiss) {
        AtomicText("This removes the list of opened files. Your files stay on storage.", AtomicTextRole.Body)
        AtomicButton(
            "Clear history",
            onClick = onConfirm,
            variant = AtomicButtonVariant.Destructive,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            "Cancel",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
