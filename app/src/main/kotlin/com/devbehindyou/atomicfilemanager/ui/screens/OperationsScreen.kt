package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicActivityRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicOperationCard
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalDao
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalEntity
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import com.devbehindyou.atomicfilemanager.data.database.room.toFileOperation
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

private const val HISTORY_LIMIT = 50
private const val PERCENT = 100
private val FAILED_STATES = setOf(OperationJournalState.FAILED, OperationJournalState.PARTIAL)

/**
 * Operations (canvas "Operations"): what is running now, what waits behind it, and a history
 * from the operation journal. Interrupted operations can be resumed and failed ones retried;
 * both run the original request again through the queue.
 */
@Composable
fun OperationsScreen(
    queue: OperationQueue,
    journal: OperationJournalDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowConflict: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val active by queue.active.collectAsState()
    val waiting by queue.queued.collectAsState()
    val journalError by queue.journalError.collectAsState()
    val historyFlow = remember(journal) { journal.observeRecent(HISTORY_LIMIT) }
    val history by historyFlow.collectAsState(initial = emptyList())
    val busyIds = setOfNotNull(active?.operation?.id?.raw) + waiting.map { it.id.raw }
    val past = history.filter { it.id !in busyIds }

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("operations_screen"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = "Operations")
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(AtomicSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            if (journalError != null) {
                item {
                    AtomicEmptyState(
                        message = stringResource(R.string.operation_history_unavailable),
                        detail = stringResource(R.string.operation_history_unavailable_detail),
                        modifier = Modifier.testTag("operation_journal_error"),
                    )
                }
            }
            item { AtomicSectionLabel("Running", trailingText = "Keeps going if you leave") }
            val running = active
            val awaiting = running?.status as? OperationStatus.AwaitingInput
            if (running == null) {
                item { AtomicEmptyState(message = "Nothing is running.") }
            } else if (awaiting != null) {
                item {
                    AtomicActivityRow(
                        title = OperationText.waitingTitle(running.operation),
                        timestamp = "“${awaiting.conflict.source.name}” is already there",
                        actionLabel = "Decide",
                        onAction = onShowConflict,
                        modifier = Modifier.testTag("operation_waiting_for_decision"),
                    )
                }
            } else {
                item { RunningCard(running, onCancel = { queue.cancel(running.operation.id) }) }
            }
            items(waiting, key = { "waiting:${it.id.raw}" }) { operation ->
                AtomicActivityRow(
                    title = "${OperationText.verb(operation.type)} ${OperationText.items(operation.sources.size)}",
                    timestamp = "Waiting",
                    actionLabel = "Remove",
                    onAction = { queue.cancel(operation.id) },
                )
            }
            item { AtomicSectionLabel("History", trailingText = "${past.size} recent") }
            if (past.isEmpty()) {
                item {
                    AtomicEmptyState(
                        message = "No operations yet.",
                        detail = "Copies, moves and deletes show up here.",
                    )
                }
            }
            items(past, key = { it.id }) { entry ->
                val action = OperationText.action(entry.state)
                val runAgain: () -> Unit = { queue.enqueue(entry.toFileOperation()) }
                AtomicActivityRow(
                    title = OperationText.historyTitle(entry),
                    timestamp = OperationText.historyMeta(entry),
                    failed = entry.state in FAILED_STATES,
                    actionLabel = action,
                    onAction = if (action != null) runAgain else null,
                )
            }
        }
    }
}

@Composable
private fun RunningCard(
    snapshot: OperationSnapshot,
    onCancel: () -> Unit,
) {
    val operation = snapshot.operation
    val progress = (snapshot.status as? OperationStatus.Running)?.progress
    val fraction =
        when {
            progress == null -> 0f
            progress.bytesTotal > 0 -> progress.bytesDone.toFloat() / progress.bytesTotal
            progress.itemsTotal > 0 -> progress.itemsDone.toFloat() / progress.itemsTotal
            else -> 0f
        }.coerceIn(0f, 1f)
    AtomicOperationCard(
        title = "${OperationText.verb(operation.type)} ${OperationText.items(operation.sources.size)}",
        subtitle = OperationText.route(operation),
        fraction = fraction,
        progressText =
            if (progress == null) {
                "Preparing"
            } else {
                "Item ${progress.itemsDone} / ${progress.itemsTotal} · " +
                    "${FileUtils.formatBytes(progress.bytesDone)} / ${FileUtils.formatBytes(progress.bytesTotal)} · " +
                    "${(fraction * PERCENT).toInt()}%"
            },
        detail = progress?.currentName,
        onCancel = onCancel,
    )
}

/** Wording for the Operations screen; pure so it is unit-tested. */
internal object OperationText {
    fun verb(type: OperationType): String =
        when (type) {
            OperationType.COPY -> "Copy"
            OperationType.MOVE -> "Move"
            OperationType.DELETE -> "Delete"
            OperationType.RENAME -> "Rename"
            OperationType.EXTRACT -> "Extract"
            OperationType.COMPRESS -> "Compress"
            OperationType.HIDE_GALLERY, OperationType.FAST_OBSCURE, OperationType.MOVE_TO_PRIVATE -> "Hide"
            OperationType.UNHIDE_GALLERY, OperationType.RESTORE_OBSCURE, OperationType.RESTORE_FROM_TRASH -> "Restore"
            OperationType.TRASH -> "Move to Trash"
        }

    fun items(count: Int): String = if (count == 1) "1 item" else "$count items"

    fun waitingTitle(operation: FileOperation): String =
        "${verb(operation.type)} ${items(operation.sources.size)} · waiting for your decision"

    fun route(operation: FileOperation): String {
        val from = operation.sources.first().raw.substringAfter(':').substringBeforeLast('/').ifEmpty { "/" }
        val to = operation.destination?.raw?.substringAfter(':')
        return if (to == null) from else "$from → $to"
    }

    fun historyTitle(entry: OperationJournalEntity): String {
        val what = "${verb(entry.type)} ${items(entry.sources.size)}"
        return when (entry.state) {
            OperationJournalState.COMPLETED -> "$what · done"
            OperationJournalState.PARTIAL -> "$what · some failed"
            OperationJournalState.FAILED -> "$what · failed"
            OperationJournalState.CANCELLED -> "$what · cancelled"
            OperationJournalState.INTERRUPTED -> "$what · interrupted"
            OperationJournalState.PAUSED -> "$what · paused"
            OperationJournalState.QUEUED, OperationJournalState.RUNNING -> what
        }
    }

    fun historyMeta(entry: OperationJournalEntity): String {
        val progress = if (entry.itemsTotal > 0) " · ${entry.itemsDone} / ${entry.itemsTotal}" else ""
        val error = entry.errorMessage?.let { " · $it" }.orEmpty()
        return FileUtils.formatDate(entry.updatedAt) + progress + error
    }

    /** Resume for work a dead process left behind, Retry for failures; finished work has none. */
    fun action(state: OperationJournalState): String? =
        when (state) {
            OperationJournalState.INTERRUPTED, OperationJournalState.PAUSED -> "Resume"
            OperationJournalState.FAILED, OperationJournalState.PARTIAL -> "Retry"
            else -> null
        }
}
