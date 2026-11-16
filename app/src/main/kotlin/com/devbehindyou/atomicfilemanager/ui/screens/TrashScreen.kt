package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicConfirmSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDangerAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDangerZone
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.model.TrashEntry
import com.devbehindyou.atomicfilemanager.domain.repository.TrashStore
import com.devbehindyou.atomicfilemanager.domain.usecase.TrashManager
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * Trash (ALL_IN_ONE_PLAN.md 1.1, canvas "Trash"): what was deleted, where it goes back to and how
 * long it stays. Restore runs through the operation queue; delete forever and Empty Trash ask first.
 */
@Composable
fun TrashScreen(
    store: TrashStore,
    trashManager: TrashManager,
    queue: OperationQueue,
    retentionDays: Int,
    onBack: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBrowseFiles: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    val entriesFlow = remember(store) { store.observeAll() }
    val entries by entriesFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var confirmForever by remember { mutableStateOf<List<TrashEntry>?>(null) }

    fun restore(items: List<TrashEntry>) {
        if (items.isEmpty()) return
        queue.enqueue(
            FileOperation(
                id = OperationId.random(),
                type = OperationType.RESTORE_FROM_TRASH,
                sources = items.map { it.trashedId },
                destination = null,
                options = OperationOptions(),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("trash_screen"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = "Trash")
        TrashContent(
            entries = entries,
            retentionDays = retentionDays,
            onRestore = ::restore,
            onConfirmDelete = { confirmForever = it },
            onBrowseFiles = onBrowseFiles,
            modifier = Modifier.weight(1f),
        )
    }

    confirmForever?.let { doomed ->
        AtomicConfirmSheet(
            label = "Delete forever",
            headline = TrashText.foreverHeadline(doomed),
            body = TrashText.foreverBody(doomed),
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = {
                confirmForever = null
                scope.launch {
                    val failed =
                        withContext(Dispatchers.IO) {
                            doomed.count { trashManager.deleteForever(it) !is FileResult.Success }
                        }
                    onNotify(TrashText.foreverResult(doomed.size - failed, failed))
                }
            },
            onDismiss = { confirmForever = null },
        )
    }
}

@Composable
internal fun TrashContent(
    entries: List<TrashEntry>?,
    retentionDays: Int,
    onRestore: (List<TrashEntry>) -> Unit,
    onConfirmDelete: (List<TrashEntry>) -> Unit,
    onBrowseFiles: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val summary = remember(entries) { TrashText.summary(entries.orEmpty()) }
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AtomicSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        if (entries == null) {
            item { AtomicLoading("Loading Trash…") }
            return@LazyColumn
        }
        if (entries.isEmpty()) {
            item {
                AtomicEmptyState(
                    message = "Your Trash is empty",
                    detail = "Files moved to Trash can be restored within $retentionDays days.",
                    actionLabel = "Browse files",
                    onAction = onBrowseFiles,
                )
            }
            return@LazyColumn
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
                AtomicSectionLabel(summary, trailingText = "Kept $retentionDays days")
                AtomicText("Review original locations before restoring.", AtomicTextRole.BodySecondary)
            }
        }
        items(entries, key = { it.id }) { entry ->
            TrashEntryCard(
                entry = entry,
                retentionDays = retentionDays,
                onRestore = { onRestore(listOf(entry)) },
                onConfirmDelete = { onConfirmDelete(listOf(entry)) },
            )
        }
        item {
            AtomicButton(
                "Restore all",
                onClick = { onRestore(entries) },
                variant = AtomicButtonVariant.Solid,
                modifier = Modifier.fillMaxWidth().testTag("trash_restore_all"),
            )
        }
        item {
            AtomicDangerZone(
                warning = "Emptying the Trash deletes every item in it for good.",
                actions = listOf(AtomicDangerAction("Empty Trash", onExecute = { onConfirmDelete(entries) })),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrashEntryCard(
    entry: TrashEntry,
    retentionDays: Int,
    onRestore: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val colors = Atomic.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceCard, AtomicShape.lg)
                .border(AtomicBorder.hair, colors.borderHair, AtomicShape.lg)
                .padding(AtomicSpacing.s16)
                .testTag("trash_entry"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicText(entry.name, AtomicTextRole.NameLarge, Modifier.semantics { heading() })
        AtomicText(TrashText.origin(entry), AtomicTextRole.BodySecondary)
        AtomicText(
            TrashText.retention(entry, retentionDays, System.currentTimeMillis()),
            AtomicTextRole.MonoMeta,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
        ) {
            AtomicButton(
                "Restore",
                onClick = onRestore,
                modifier = Modifier.testTag("trash_restore:${entry.id}"),
            )
            AtomicButton(
                "Delete permanently",
                onClick = onConfirmDelete,
                modifier = Modifier.testTag("trash_delete:${entry.id}"),
                variant = AtomicButtonVariant.Ghost,
                contentColor = colors.error,
            )
        }
    }
}

/** Wording for the Trash screen and its snackbars; pure so it is unit-tested. */
internal object TrashText {
    fun items(count: Int): String = if (count == 1) "1 item" else "$count items"

    fun summary(entries: List<TrashEntry>): String {
        val bytes = entries.sumOf { it.size.coerceAtLeast(0) }
        return if (entries.isEmpty()) "Nothing here" else "${items(entries.size)} · ${FileUtils.formatBytes(bytes)}"
    }

    fun origin(entry: TrashEntry): String =
        if (entry.originalParent.prefix == FileNodeId.Prefix.FILE) {
            val path = entry.originalParent.raw.removePrefix(FileNodeId.Prefix.FILE.scheme)
            "Originally $path"
        } else {
            "Original location unavailable"
        }

    fun retention(
        entry: TrashEntry,
        retentionDays: Int,
        now: Long,
    ): String {
        val size = if (entry.size >= 0) " · ${FileUtils.formatBytes(entry.size)}" else ""
        val left = ((entry.deletedAt + retentionDays * DAY_MILLIS - now) / DAY_MILLIS).coerceAtLeast(0)
        val days = if (left == 1L) "1 day left" else "$left days left"
        return "Deleted ${FileUtils.formatDate(entry.deletedAt)}$size · $days"
    }

    fun foreverHeadline(entries: List<TrashEntry>): String =
        if (entries.size == 1) "Delete forever?" else "Delete ${entries.size} items forever?"

    fun foreverBody(entries: List<TrashEntry>): String {
        val subject = if (entries.size == 1) "“${entries.first().name}”" else items(entries.size)
        return "Deletes $subject permanently. This can't be undone."
    }

    fun foreverResult(
        deleted: Int,
        failed: Int,
    ): String =
        if (failed == 0) {
            "Deleted ${items(
                deleted,
            )} for good."
        } else {
            "Deleted ${items(deleted)}; ${items(failed)} could not be deleted."
        }

    /** Snackbar after a Trash operation. */
    fun moved(
        moved: Int,
        failed: Int,
    ): String =
        if (failed == 0) {
            "Moved ${items(
                moved,
            )} to Trash."
        } else {
            "Moved ${items(moved)} to Trash; $failed failed."
        }

    fun restored(
        restored: Int,
        failed: Int,
    ): String = if (failed == 0) "Restored ${items(restored)}." else "Restored ${items(restored)}; $failed failed."
}
