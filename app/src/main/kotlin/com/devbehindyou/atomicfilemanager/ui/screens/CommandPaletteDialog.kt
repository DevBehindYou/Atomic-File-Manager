package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.focusRing
import com.devbehindyou.atomicfilemanager.ui.components.BalancedPopupHeader

private val CommandLabel = AtomicTypography.body.copy(fontSize = 14.sp, lineHeight = 20.sp)
private val CompactWidth = 760.dp

@Composable
internal fun CommandPaletteDialog(
    onRun: (PaletteCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = if (query.isBlank()) paletteBrowseOrder else CommandPalette.match(query)
    val dismiss = rememberUpdatedState(onDismiss)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0.6f) }

        BoxWithConstraints(Modifier.fillMaxSize().imePadding().testTag("commands_overlay")) {
            val compact = maxWidth < CompactWidth
            val shape =
                if (compact) {
                    AtomicShape.sheetTop
                } else {
                    RoundedCornerShape(AtomicShape.sheetTopRadius)
                }
            val width = minOf(520.dp, (maxWidth - AtomicSpacing.s32).coerceAtLeast(1.dp))
            val height = (maxHeight - AtomicSpacing.s32).coerceAtLeast(1.dp)
            Box(
                Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { dismiss.value() } },
            )
            Column(
                Modifier.align(if (compact) Alignment.BottomCenter else Alignment.Center)
                    .width(width)
                    .heightIn(max = height)
                    .background(Atomic.colors.surfaceCard, shape)
                    .border(1.dp, Atomic.colors.borderHair, shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .testTag("commands_sheet")
                    .verticalScroll(rememberScrollState())
                    .padding(if (compact) AtomicSpacing.screenMargin else AtomicSpacing.s24),
            ) {
                BalancedPopupHeader(
                    title = "Tools & commands",
                    closeDescription = "Close commands",
                    onClose = onDismiss,
                    testTagPrefix = "commands",
                    buttonVariant = AtomicIconButtonVariant.Inset,
                    controlsGap = AtomicSpacing.s8,
                )
                AtomicDivider(Modifier.padding(top = AtomicSpacing.s12, bottom = AtomicSpacing.screenMargin))
                CommandSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onSubmit = { matches.firstOrNull()?.let(onRun) },
                )
                Spacer(Modifier.height(18.dp))
                if (matches.isEmpty()) {
                    AtomicText("No command matches “$query”.", AtomicTextRole.BodySecondary)
                } else {
                    CommandPaletteGrid(matches, columns = if (compact) 2 else 3, onRun = onRun)
                }
            }
        }
    }
}

@Composable
private fun CommandSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = Atomic.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6)) {
        Text("Find a command", color = colors.contentSecondary, style = CommandLabel)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier =
                Modifier.fillMaxWidth()
                    .testTag("palette_query")
                    .semantics { contentDescription = "Find a command" }
                    .onPreviewKeyEvent {
                        if (it.key == Key.Enter) {
                            if (it.type == KeyEventType.KeyUp) onSubmit()
                            true
                        } else {
                            false
                        }
                    },
            singleLine = true,
            textStyle = AtomicTypography.body.copy(color = colors.content),
            cursorBrush = SolidColor(colors.accentText),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            interactionSource = interaction,
            decorationBox = { field ->
                Box(
                    Modifier.defaultMinSize(minHeight = AtomicSize.touchTarget)
                        .focusRing(focused, colors.accentText, AtomicShape.radiusSm)
                        .background(colors.surfaceInset, AtomicShape.sm)
                        .border(1.dp, if (focused) colors.accentText else colors.borderHair, AtomicShape.sm)
                        .padding(AtomicSpacing.s12),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        AtomicText("Search commands", AtomicTextRole.BodySecondary, maxLines = 1)
                    }
                    field()
                }
            },
        )
    }
}

@Composable
private fun CommandPaletteGrid(
    commands: List<PaletteCommand>,
    columns: Int,
    onRun: (PaletteCommand) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        commands.chunked(columns).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
            ) {
                row.forEach { command ->
                    CommandTile(command, Modifier.weight(1f).fillMaxHeight()) { onRun(command) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CommandTile(
    command: PaletteCommand,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = Atomic.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.testTag("palette_${command.name.lowercase()}")
            .focusRing(focused, colors.accentText, 14.dp)
            .background(colors.surfaceInset, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s10),
    ) {
        Icon(command.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.accentText)
        Text(command.title, color = colors.content, style = CommandLabel)
    }
}
