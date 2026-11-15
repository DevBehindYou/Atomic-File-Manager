package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicProgressBar
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.data.operations.OperationQueue
import com.devbehindyou.atomicfilemanager.data.share.ShareTransfer
import com.devbehindyou.atomicfilemanager.data.share.ShareTransferDirection
import com.devbehindyou.atomicfilemanager.data.share.ShareTransfers
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationProgress
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.ui.screens.OperationText
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Live task summary; opening or dismissing it never changes the running work. */
@Composable
fun TasksButton(
    queue: OperationQueue,
    transfers: ShareTransfers,
    onOpenOperations: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val countFlow =
        remember(queue, transfers) {
            combine(queue.active.map { if (it == null) 0 else 1 }, queue.queued, transfers.active.map { it.size }) {
                    active, waiting, network ->
                active + waiting.size + network
            }.distinctUntilChanged()
        }
    val count by countFlow.collectAsState(initial = 0)
    Box {
        AtomicIconButton(
            icon = AtomicIcons.Tasks,
            contentDescription = if (count == 0) "Tasks" else "Tasks, $count active",
            onClick = { expanded = !expanded },
            variant = if (expanded) AtomicIconButtonVariant.Accent else AtomicIconButtonVariant.Ghost,
            modifier = Modifier.testTag("tasks_button"),
        )
        if (count > 0) {
            AtomicText(
                if (count > 99) "99+" else count.toString(),
                AtomicTextRole.MonoMeta,
                color = Atomic.colors.onAccent,
                modifier =
                    Modifier.align(Alignment.TopEnd).background(Atomic.colors.accent, AtomicShape.sm)
                        .padding(horizontal = AtomicSpacing.s4, vertical = AtomicSpacing.s2),
            )
        }
        if (expanded) {
            val active by queue.active.collectAsState()
            val waiting by queue.queued.collectAsState()
            val network by transfers.active.collectAsState()
            TasksPanel(
                active = active,
                waiting = waiting,
                transfers = network,
                onCancelOperation = { queue.cancel(it.id) },
                onCancelTransfer = transfers::cancel,
                onDismiss = { expanded = false },
                onOpenOperations = {
                    expanded = false
                    onOpenOperations()
                },
            )
        }
    }
}

@Composable
internal fun TasksPanel(
    active: OperationSnapshot?,
    waiting: List<FileOperation>,
    transfers: List<ShareTransfer>,
    onCancelOperation: (FileOperation) -> Unit,
    onCancelTransfer: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenOperations: () -> Unit,
) {
    val isIdle = active == null && waiting.isEmpty() && transfers.isEmpty()
    AtomicTopDialog(onDismiss = onDismiss, testTag = "tasks_popup") {
        BalancedPopupHeader(
            title = "Tasks",
            closeDescription = "Close tasks",
            onClose = onDismiss,
            testTagPrefix = "tasks",
            buttonVariant = AtomicIconButtonVariant.Inset,
            controlsGap = AtomicSpacing.s8,
        )
        AtomicDivider(modifier = Modifier.padding(top = AtomicSpacing.s12, bottom = AtomicSpacing.screenMargin))
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
            if (isIdle) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
                        modifier = Modifier.fillMaxWidth().padding(vertical = AtomicSpacing.s24),
                    ) {
                        AtomicText("No tasks running", AtomicTextRole.DisplayCard, textAlign = TextAlign.Center)
                        AtomicText(
                            "Copy, move, extraction and network transfers appear here.",
                            AtomicTextRole.BodySecondary,
                            textAlign = TextAlign.Center,
                        )
                        AtomicButton("Task history", onOpenOperations, variant = AtomicButtonVariant.Ghost)
                    }
                }
            }
            if (active != null) {
                item(key = "operation:${active.operation.id.raw}") {
                    val progress =
                        when (val status = active.status) {
                            is OperationStatus.Running -> status.progress
                            is OperationStatus.Paused -> status.progress
                            else -> null
                        }
                    TaskRow(
                        title = TaskText.title(active.operation),
                        detail = progress?.currentName ?: OperationText.items(active.operation.sources.size),
                        state =
                            when (active.status) {
                                is OperationStatus.AwaitingInput -> "Needs your decision"
                                is OperationStatus.Running -> "Running"
                                is OperationStatus.Paused -> "Paused"
                                OperationStatus.Recovering -> "Recovering"
                                else -> "Preparing"
                            },
                        progress = progress,
                        onCancel = { onCancelOperation(active.operation) },
                        onReview = if (active.status is OperationStatus.AwaitingInput) onOpenOperations else null,
                    )
                }
            }
            items(transfers, key = { "transfer:${it.id}" }) { transfer ->
                TaskRow(
                    title =
                        if (transfer.direction == ShareTransferDirection.UPLOAD) {
                            "Upload · Wi-Fi"
                        } else {
                            "Download · Wi-Fi"
                        },
                    detail = transfer.name,
                    state = "Transferring",
                    progress = OperationProgress(0, 0, transfer.bytesDone, transfer.bytesTotal, transfer.name, 0, null),
                    onCancel = { onCancelTransfer(transfer.id) },
                )
            }
            items(waiting, key = { "queued:${it.id.raw}" }) { operation ->
                TaskRow(
                    TaskText.title(operation),
                    OperationText.items(operation.sources.size),
                    "Queued",
                    null,
                    onCancel = { onCancelOperation(operation) },
                )
            }
        }
        if (!isIdle) {
            AtomicButton(
                "All tasks",
                onOpenOperations,
                modifier = Modifier.padding(top = AtomicSpacing.s12),
            )
        }
    }
}

@Composable
private fun TaskRow(
    title: String,
    detail: String,
    state: String,
    progress: OperationProgress?,
    onCancel: () -> Unit,
    onReview: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AtomicText(title, AtomicTextRole.MonoLabel, color = Atomic.colors.content)
                AtomicText(detail, AtomicTextRole.BodySecondary, maxLines = 1)
            }
            AtomicIconButton(AtomicIcons.Close, "Cancel $title task", onCancel)
        }
        val fraction = progress?.let(TaskText::fraction)
        if (fraction != null) {
            AtomicProgressBar(fraction, "$title, ${(fraction * 100).toInt()} percent")
            AtomicText(TaskText.progress(progress), AtomicTextRole.MonoMeta)
        } else if (state in listOf("Preparing", "Running", "Transferring")) {
            AtomicLoading(state)
        } else {
            AtomicText(state, AtomicTextRole.MonoMeta)
        }
        if (onReview != null) AtomicButton("Review conflict →", onReview, variant = AtomicButtonVariant.Ghost)
    }
}

internal object TaskText {
    private val network =
        setOf(
            FileNodeId.Prefix.SFTP,
            FileNodeId.Prefix.FTP,
            FileNodeId.Prefix.FTPS,
            FileNodeId.Prefix.SMB,
            FileNodeId.Prefix.WEBDAV,
        )

    fun title(operation: FileOperation): String {
        if (operation.type != OperationType.COPY && operation.type != OperationType.MOVE) {
            return OperationText.verb(
                operation.type,
            )
        }
        val fromNetwork = operation.sources.any { it.prefix in network }
        val toNetwork = operation.destination?.prefix in network
        val transfer =
            when {
                toNetwork && !fromNetwork -> "Upload"
                fromNetwork && !toNetwork -> "Download"
                else -> return OperationText.verb(operation.type)
            }
        return if (operation.type == OperationType.MOVE) "$transfer · Move" else transfer
    }

    fun fraction(progress: OperationProgress): Float? =
        when {
            progress.bytesTotal > 0 -> progress.bytesDone.toFloat() / progress.bytesTotal
            progress.itemsTotal > 0 -> progress.itemsDone.toFloat() / progress.itemsTotal
            else -> null
        }?.coerceIn(0f, 1f)

    fun progress(progress: OperationProgress): String {
        val amount =
            if (progress.bytesTotal > 0) {
                "${FileUtils.formatBytes(progress.bytesDone)} / ${FileUtils.formatBytes(progress.bytesTotal)}"
            } else {
                "${progress.itemsDone} / ${progress.itemsTotal} items"
            }
        val speed = if (progress.bytesPerSecond > 0) " · ${FileUtils.formatBytes(progress.bytesPerSecond)}/s" else ""
        return amount + speed
    }
}
