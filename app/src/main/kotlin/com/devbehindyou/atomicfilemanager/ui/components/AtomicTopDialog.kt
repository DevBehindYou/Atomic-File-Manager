package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic

/** Native equivalent of the approved dialog.popover, shared by top-level options and submenus. */
@Composable
internal fun AtomicTopDialog(
    onDismiss: () -> Unit,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dismiss = rememberUpdatedState(onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0.6f) }
        BoxWithConstraints(Modifier.fillMaxSize().testTag("${testTag}_overlay")) {
            val top = minOf(if (maxWidth < 760.dp) 112.dp else 120.dp, maxHeight / 4)
            val width = minOf(420.dp, (maxWidth - 32.dp).coerceAtLeast(1.dp))
            val shape = RoundedCornerShape(24.dp)
            Box(
                Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { dismiss.value() } },
            )
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = top)
                    .width(width).heightIn(max = (maxHeight - top - 16.dp).coerceAtLeast(1.dp))
                    .background(Atomic.colors.surfaceCard, shape)
                    .border(1.dp, Atomic.colors.borderHair, shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .testTag(testTag)
                    .padding(if (maxWidth < 760.dp) 20.dp else 24.dp),
                content = content,
            )
        }
    }
}
