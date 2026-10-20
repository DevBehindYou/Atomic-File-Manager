package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicActivityRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicCommandGrid
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicConfirmSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDangerAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDangerZone
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
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
) {
    BackHandler(onBack = onBack)
    val entriesFlow = remember(store) { store.observeAll() }
    val entries by entriesFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<TrashEntry?>(null) }
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
        val list = entries
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(AtomicSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            if (list == null) return@LazyColumn
            item { AtomicSectionLabel(TrashText.summary(list), trailingText = "Kept $retentionDays days") }
            if (list.isEmpty()) {
                item {
                    AtomicEmptyState(
                        message = "Trash is empty.",
                        detail = "Deleted files stay here for $retentionDays days, then go for good.",
                    )
                }
                return@LazyColumn
            }
            items(list, key = { it.id }) { entry ->
                AtomicActivityRow(
                    title = entry.name,
                    timestamp = TrashText.meta(entry, retentionDays, System.currentTimeMillis()),
                    actionLabel = "Restore",
                    onAction = { restore(listOf(entry)) },
                    modifier =
                        Modifier
                            .clickable(onClickLabel = "More actions", role = Role.Button) { selected = entry }
                            .testTag("trash_entry"),
                )
            }
            item {
                AtomicButton(
                    "Restore all",
                    onClick = { restore(list) },
                    variant = AtomicButtonVariant.Solid,
                    modifier = Modifier.fillMaxWidth().testTag("trash_restore_all"),
                )
            }
            item {
                AtomicDangerZone(
                    warning = "Emptying the Trash deletes every item in it for good.",
                    actions = listOf(AtomicDangerAction("Empty Trash", onExecute = { confirmForever = list })),
                )
            }
        }
    }

    selected?.let { entry ->
        AtomicSheet(label = "In Trash", onDismiss = { selected = null }) {
            AtomicText(entry.name, AtomicTextRole.NameLarge)
            AtomicText(TrashText.meta(entry, retentionDays, System.currentTimeMillis()), AtomicTextRole.MonoMeta)
            AtomicCommandGrid(
                listOf(
                    AtomicMenuAction("Restore", AtomicIcons.Restore, {
                        restore(listOf(entry))
                        selected = null
                    }),
                    AtomicMenuAction(
                        "Delete forever",
                        AtomicIcons.Trash,
                        onClick = {
                            confirmForever = listOf(entry)
                            selected = null
                        },
                        destructive = true,
                    ),
                ),
            )
        }
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

/** Wording for the Trash screen and its snackbars; pure so it is unit-tested. */
internal object TrashText {
    fun items(count: Int): String = if (count == 1) "1 item" else "$count items"

    fun summary(entries: List<TrashEntry>): String {
        val bytes = entries.filter { it.size > 0 }.sumOf { it.size }
        return if (entries.isEmpty()) "Nothing here" else "${items(entries.size)} · ${FileUtils.formatBytes(bytes)}"
    }

    /** "Deleted 2026-10-01 08:11 · restores to /Download · 2.0 MB · 23 days left". */
    fun meta(
        entry: TrashEntry,
        retentionDays: Int,
        now: Long,
    ): String {
        val where = entry.originalParent.raw.removePrefix(entry.volumeRoot.raw).ifEmpty { "/" }
        val size = if (entry.size >= 0) " · ${FileUtils.formatBytes(entry.size)}" else ""
        val left = ((entry.deletedAt + retentionDays * DAY_MILLIS - now) / DAY_MILLIS).coerceAtLeast(0)
        val days = if (left == 1L) "1 day left" else "$left days left"
        return "Deleted ${FileUtils.formatDate(entry.deletedAt)} · restores to $where$size · $days"
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
