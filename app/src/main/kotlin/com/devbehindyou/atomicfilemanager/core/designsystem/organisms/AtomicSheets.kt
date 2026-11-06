package com.devbehindyou.atomicfilemanager.core.designsystem.organisms

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBreakpoints
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicWidthClass

private const val SCRIM_ALPHA = 0.54f
private val PANEL_MAX_WIDTH = 560.dp
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp

/**
 * Atomic sheet (spec §9.7): paper, 28 dp top corners, drag handle, 54 % scrim; a mono accent
 * [label] over a 1 dp rule, then [content]. On medium and expanded widths it is a centred
 * panel instead of a bottom sheet (ATOMIC_UI_PLAN.md §8). Replaces Material alert dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtomicSheet(
    label: String,
    onDismiss: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Atomic.colors
    val compact = AtomicBreakpoints.widthClass(LocalConfiguration.current.screenWidthDp.dp) == AtomicWidthClass.COMPACT
    val body: @Composable () -> Unit = {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = AtomicSpacing.s16)
                    .padding(bottom = AtomicSpacing.s24),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
                AtomicText(label, AtomicTextRole.MonoLabel, color = colors.accentText)
                AtomicDivider(strong = true)
            }
            content()
        }
    }
    if (compact) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = AtomicShape.sheetTop,
            containerColor = colors.background,
            contentColor = colors.content,
            scrimColor = Color.Black.copy(alpha = SCRIM_ALPHA),
            tonalElevation = 0.dp,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = AtomicSpacing.s10)
                        .size(HANDLE_WIDTH, HANDLE_HEIGHT)
                        .background(colors.contentSecondary, AtomicShape.pill),
                )
            },
        ) {
            Box(Modifier.navigationBarsPadding()) { body() }
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Box(
                Modifier
                    .widthIn(max = PANEL_MAX_WIDTH)
                    .background(colors.background, AtomicShape.md)
                    .border(AtomicBorder.structure, colors.borderStrong, AtomicShape.md)
                    .padding(top = AtomicSpacing.s16),
            ) { body() }
        }
    }
}

/**
 * Confirmation sheet: says exactly what will happen, with numbers, before it happens
 * (spec §9.9 destructive confirm). [destructive] uses the destructive button.
 */
@Composable
fun AtomicConfirmSheet(
    label: String,
    headline: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AtomicSheet(label = label, onDismiss = onDismiss) {
        AtomicText(headline, AtomicTextRole.DisplayPushed)
        AtomicText(body, AtomicTextRole.Body)
        details?.invoke(this)
        AtomicButton(
            text = confirmLabel,
            onClick = onConfirm,
            variant = if (destructive) AtomicButtonVariant.Destructive else AtomicButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            dismissLabel,
            onClick = onDismiss,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Information sheet (spec §9.7): label, two-line headline, body, one "Got it" button. */
@Composable
fun AtomicInfoSheet(
    label: String,
    headline: String,
    body: String,
    onDismiss: () -> Unit,
    buttonLabel: String = "Got it",
) {
    AtomicSheet(label = label, onDismiss = onDismiss) {
        AtomicText(headline, AtomicTextRole.DisplayPushed)
        AtomicText(body, AtomicTextRole.Body)
        AtomicButton(
            buttonLabel,
            onClick = onDismiss,
            variant = AtomicButtonVariant.Solid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
