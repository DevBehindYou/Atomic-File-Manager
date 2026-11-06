package com.devbehindyou.atomicfilemanager.ui.interaction.drag

import androidx.compose.runtime.Composable
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuSheet

/**
 * Asks what a drop should do: copy or move into a folder, or stage into a transfer bubble.
 * Dismissing the sheet cancels the drop; nothing happens until a button is pressed.
 */
@Composable
fun DropDecisionDialog(
    payload: DragPayload,
    target: ActiveDropTarget,
    onDecision: (DropDecision) -> Unit,
) {
    val itemCount = payload.selectionCount
    val targetName = target.displayName
    val cancel = { onDecision(DropDecision.Cancel) }

    if (target.type == DropTargetType.TRANSFER_BUBBLE) {
        val bubbleId = target.destinationId.raw.removePrefix("atomic://bubble/")
        AtomicMenuSheet(
            label = "Add to $targetName",
            onDismiss = cancel,
            intro = {
                AtomicText(
                    if (itemCount == 1) {
                        "Stage \"${payload.items.first().displayName}\" in $targetName to move or copy later."
                    } else {
                        "Stage $itemCount items in $targetName to move or copy later."
                    },
                    AtomicTextRole.Body,
                )
            },
            actions =
                listOf(
                    AtomicMenuAction("Add to bubble", AtomicIcons.Bubble, {
                        onDecision(DropDecision.AddToBubble(payload, bubbleId))
                    }),
                    AtomicMenuAction("Cancel", AtomicIcons.Close, cancel),
                ),
        )
        return
    }

    AtomicMenuSheet(
        label = if (itemCount == 1) "Drop 1 item" else "Drop $itemCount items",
        onDismiss = cancel,
        intro = {
            AtomicText(
                if (itemCount == 1) {
                    "Copy or move \"${payload.items.first().name}\" into \"$targetName\"?"
                } else {
                    "Copy or move $itemCount items into \"$targetName\"?"
                },
                AtomicTextRole.Body,
            )
        },
        actions =
            listOf(
                AtomicMenuAction("Move here", AtomicIcons.Move, {
                    onDecision(DropDecision.Move(payload, target.destinationId))
                }),
                AtomicMenuAction("Copy here", AtomicIcons.Copy, {
                    onDecision(DropDecision.Copy(payload, target.destinationId))
                }),
                AtomicMenuAction("Cancel", AtomicIcons.Close, cancel),
            ),
    )
}
