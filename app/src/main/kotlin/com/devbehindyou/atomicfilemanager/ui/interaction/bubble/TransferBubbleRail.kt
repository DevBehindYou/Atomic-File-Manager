package com.devbehindyou.atomicfilemanager.ui.interaction.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicBadge
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicElevation
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.hardShadow
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.ActiveDropTarget
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DropTargetType
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.fileDropTarget
import kotlin.math.roundToInt

@Composable
fun TransferBubbleRail(
    bubbles: List<TransferBubble>,
    dragController: FileDragController,
    onBubbleClick: (TransferBubble) -> Unit,
    onBubbleLongClick: (TransferBubble) -> Unit,
    onCreateBubble: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val persistence = remember(context) { BubblePositionPersistence(context) }
    val scope = rememberCoroutineScope()
    var fractionX by remember { mutableFloatStateOf(1f) }
    var fractionY by remember { mutableFloatStateOf(0.5f) }
    var railSize by remember { mutableStateOf(IntSize.Zero) }
    var isRepositioning by remember { mutableStateOf(false) }
    var hasPositionInteraction by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    var dragDistance by remember { mutableStateOf(Offset.Zero) }
    val displayedX by animateFloatAsState(
        targetValue = fractionX,
        animationSpec = if (isRepositioning || !hasPositionInteraction) snap() else spring(),
        label = "bubbleEdgeDock",
    )

    LaunchedEffect(persistence) {
        persistence.loadInitial {
            fractionX = it.x
            fractionY = it.y
        }
    }

    fun savePosition() {
        hasPositionInteraction = true
        persistence.save(scope, BubblePosition(fractionX, fractionY))
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(top = 64.dp, bottom = 64.dp)) {
        val density = LocalDensity.current
        val maxX = (with(density) { maxWidth.toPx() } - railSize.width).coerceAtLeast(0f)
        val maxY = (with(density) { maxHeight.toPx() } - railSize.height).coerceAtLeast(0f)
        Column(
            modifier =
                Modifier
                    .absoluteOffset { IntOffset((displayedX * maxX).roundToInt(), (fractionY * maxY).roundToInt()) }
                    .onSizeChanged { railSize = it }
                    .pointerInput(maxX, maxY) {
                        detectDragGestures(
                            onDragStart = {
                                hasPositionInteraction = true
                                persistence.markInteraction()
                                isRepositioning = true
                                velocityTracker.resetTracking()
                                dragDistance = Offset.Zero
                            },
                            onDragEnd = {
                                isRepositioning = false
                                fractionX = bubbleDockEdge(fractionX, velocityTracker.calculateVelocity().x, maxX)
                                savePosition()
                            },
                            onDragCancel = {
                                isRepositioning = false
                                fractionX = bubbleDockEdge(fractionX, 0f, maxX)
                                savePosition()
                            },
                        ) { change, amount ->
                            change.consume()
                            dragDistance += amount
                            velocityTracker.addPosition(change.uptimeMillis, dragDistance)
                            if (maxX > 0f) fractionX = (fractionX + amount.x / maxX).coerceIn(0f, 1f)
                            if (maxY > 0f) fractionY = (fractionY + amount.y / maxY).coerceIn(0f, 1f)
                        }
                    }
                    .semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Move bubbles left") {
                                    fractionX = 0f
                                    savePosition()
                                    true
                                },
                                CustomAccessibilityAction("Move bubbles right") {
                                    fractionX = 1f
                                    savePosition()
                                    true
                                },
                                CustomAccessibilityAction("Move bubbles up") {
                                    fractionY = (fractionY - 0.25f).coerceAtLeast(0f)
                                    savePosition()
                                    true
                                },
                                CustomAccessibilityAction("Move bubbles down") {
                                    fractionY = (fractionY + 0.25f).coerceAtMost(1f)
                                    savePosition()
                                    true
                                },
                            )
                    }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            for (bubble in bubbles) {
                BubbleItem(
                    bubble = bubble,
                    dragController = dragController,
                    onClick = { onBubbleClick(bubble) },
                    onLongClick = { onBubbleLongClick(bubble) },
                )
            }

            if (bubbles.size < TransferBubble.MAX_BUBBLES) {
                AtomicIconButton(
                    AtomicIcons.Add,
                    "Create transfer bubble",
                    onClick = onCreateBubble,
                    variant = AtomicIconButtonVariant.Toolbar,
                )
            }
        }
    }
}

internal fun bubbleDockEdge(
    fraction: Float,
    velocityX: Float,
    travelWidth: Float,
): Float {
    val projected = fraction + if (travelWidth > 0f) velocityX * 0.2f / travelWidth else 0f
    return if (projected < 0.5f) 0f else 1f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleItem(
    bubble: TransferBubble,
    dragController: FileDragController,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val bubbleNodeId = FileNodeId("atomic://bubble/${bubble.id}")

    val isTarget = dragController.currentDropTarget?.destinationId == bubbleNodeId
    val scale by animateFloatAsState(
        targetValue = if (isTarget) 1.15f else 1.0f,
        label = "bubbleScale",
    )
    val colors = Atomic.colors
    val containerColor by animateColorAsState(
        targetValue = if (isTarget) colors.accent else colors.surfaceCard,
        label = "bubbleColor",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .scale(scale)
                .size(BUBBLE_SIZE)
                .hardShadow(AtomicElevation.level3, colors.shadow, CircleShape)
                .background(containerColor, CircleShape)
                .border(AtomicBorder.structure, colors.borderStrong, CircleShape)
                .fileDropTarget(
                    controller = dragController,
                    target =
                        ActiveDropTarget(
                            id = bubbleNodeId.raw,
                            destinationId = bubbleNodeId,
                            type = DropTargetType.TRANSFER_BUBBLE,
                            displayName = bubble.displayName,
                        ),
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ).semantics { contentDescription = "${bubble.displayName}, ${bubble.itemCount} items" },
    ) {
        Icon(
            imageVector = AtomicIcons.Bubble,
            contentDescription = null,
            tint = if (isTarget) colors.onAccent else colors.content,
            modifier = Modifier.size(AtomicSize.icon),
        )
        AnimatedVisibility(
            visible = bubble.itemCount > 0,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            AtomicBadge(bubble.itemCount)
        }
    }
}

private val BUBBLE_SIZE = 56.dp
