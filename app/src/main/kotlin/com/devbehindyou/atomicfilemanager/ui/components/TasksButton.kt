package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicElevation
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.hardShadow
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

/** Anchored live task summary; opening or dismissing it never changes the running work. */
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
            val windowSize = LocalWindowInfo.current.containerSize
            val active by queue.active.collectAsState()
            val waiting by queue.queued.collectAsState()
            val network by transfers.active.collectAsState()
            val offset = with(LocalDensity.current) { (AtomicSize.touchTarget + AtomicSpacing.s8).roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, offset),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                TasksPanel(
                    active,
                    waiting,
                    network,
                    onCancelOperation = { queue.cancel(it.id) },
                    onCancelTransfer = transfers::cancel,
                    onDismiss = { expanded = false },
                    onOpenOperations = {
                        expanded = false
                        onOpenOperations()
                    },
                    windowSize = windowSize,
                )
            }
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
    windowSize: IntSize = LocalWindowInfo.current.containerSize,
) {
    val colors = Atomic.colors
    val density = LocalDensity.current
    val width =
        with(density) {
            minOf(
                AtomicSize.popupMaxWidth,
                (windowSize.width.toDp() - AtomicSpacing.s32).coerceAtLeast(AtomicSpacing.s2),
            )
        }
    val maxHeight = with(density) { (windowSize.height.toDp() * 0.7f).coerceAtMost(AtomicSize.popupMaxHeight) }
    val count = (if (active == null) 0 else 1) + waiting.size + transfers.size
    Column(
        Modifier.testTag("tasks_popup").width(width).heightIn(max = maxHeight)
            .hardShadow(AtomicElevation.level3, colors.shadow, AtomicShape.md)
            .background(colors.surfaceCard, AtomicShape.md)
            .border(AtomicBorder.structure, colors.borderStrong, AtomicShape.md)
            .padding(AtomicSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AtomicText("Tasks", AtomicTextRole.MonoLabel, color = colors.accentText)
                AtomicText(
                    if (count == 0) "All clear" else "${count - waiting.size} running · ${waiting.size} queued",
                    AtomicTextRole.MonoMeta,
                )
            }
            AtomicIconButton(AtomicIcons.Close, "Close tasks", onDismiss)
        }
        AtomicDivider()
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
            if (count == 0) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
                        modifier = Modifier.padding(vertical = AtomicSpacing.s12),
                    ) {
                        AtomicText("No tasks running", AtomicTextRole.Name)
                        AtomicText("Copies, moves, uploads and downloads appear here.", AtomicTextRole.BodySecondary)
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
        AtomicDivider()
        AtomicButton(
            "All operations →",
            onOpenOperations,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
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
        } else if (state == "Preparing" || state == "Transferring") {
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
