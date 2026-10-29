package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.repeatOnLifecycle
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.usecase.LineEnding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextCodec
import com.devbehindyou.atomicfilemanager.domain.usecase.TextEncoding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import kotlinx.coroutines.Dispatchers

/**
 * Plain text editor (ALL_IN_ONE_PLAN.md 2.3): monospace, line numbers while lines don't wrap,
 * find and replace. Saving keeps the file's encoding and line breaks and never writes in place
 * (see TextFileEditor). Closing with unsaved changes asks first. [onSaved] gets the saved file.
 */
@Composable
fun TextEditorDialog(
    node: FileNode,
    onDismiss: () -> Unit,
    onSaved: (FileNode) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as AtomicApp
    val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner())
    val sessions =
        remember(owner, app) {
            ViewModelProvider(
                owner,
                TextEditorSessionsViewModel.factory(
                    io = Dispatchers.IO,
                    open = { app.container.textFileEditor.open(it) },
                    save = { current, text, format -> app.container.textFileEditor.save(current, text, format) },
                ),
            )["atomic:text-editor-sessions", TextEditorSessionsViewModel::class.java]
        }
    val session = remember(sessions, node.id) { sessions.session(node) }
    val activity = context.editorActivity()
    DisposableEffect(sessions, session, activity) {
        onDispose {
            if (activity?.isChangingConfigurations != true) sessions.release(session, force = true)
        }
    }
    TextEditorSessionDialog(
        node,
        session,
        onDismiss = { if (sessions.release(session)) onDismiss() },
        onSaved = onSaved,
    )
}

@Composable
internal fun TextEditorSessionDialog(
    node: FileNode,
    session: TextEditorSession,
    onDismiss: () -> Unit,
    onSaved: (FileNode) -> Unit,
) {
    val state by session.state.collectAsState()
    val current = state.current ?: node
    val latestSaved by rememberUpdatedState(onSaved)
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirmClose by rememberSaveable(session) { mutableStateOf(false) }
    var findOpen by rememberSaveable(session) { mutableStateOf(false) }
    var wrap by rememberSaveable(session) { mutableStateOf(false) }
    LaunchedEffect(session) { session.load() }
    LaunchedEffect(session, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            session.state.collect { snapshot ->
                val pending = snapshot.acknowledgement
                if (pending != null) session.acknowledgeSave(pending.revision)?.let { latestSaved(it) }
            }
        }
    }
    val close: () -> Unit = {
        if (!state.saving) {
            if (state.dirty) confirmClose = true else onDismiss()
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = close)
        Column(
            Modifier.fillMaxSize().background(Atomic.colors.background).imePadding().testTag("text_editor"),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = AtomicSpacing.s10,
                    end = AtomicSpacing.s16,
                    top = AtomicSpacing.s4,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
            ) {
                AtomicIconButton(
                    AtomicIcons.Close,
                    stringResource(R.string.editor_close),
                    onClick = close,
                    enabled = !state.saving,
                    variant = AtomicIconButtonVariant.Back,
                )
                Column(Modifier.weight(1f)) {
                    AtomicText(current.name, AtomicTextRole.Name, maxLines = 1)
                    val status =
                        when {
                            state.saveUnconfirmed -> stringResource(R.string.editor_save_unconfirmed)
                            state.saveError != null -> EditorText.saveFailed(checkNotNull(state.saveError))
                            else -> EditorText.meta(state.format, state.dirty)
                        }
                    AtomicText(status, AtomicTextRole.MonoMeta, maxLines = 1)
                }
                AtomicButton(
                    stringResource(if (state.saving) R.string.editor_saving else R.string.editor_save),
                    onClick = { session.save() },
                    enabled = state.dirty && !state.saving && state.format != null,
                    variant = AtomicButtonVariant.Solid,
                    modifier = Modifier.testTag("text_editor_save"),
                )
            }
            AtomicDivider(strong = true, modifier = Modifier.padding(top = AtomicSpacing.s4))

            when {
                state.loading -> ViewerState(loading = true, error = null)
                state.openError != null ->
                    Column(Modifier.padding(AtomicSpacing.s16)) {
                        AtomicText(EditorText.openFailed(checkNotNull(state.openError)), AtomicTextRole.Body)
                        AtomicButton(
                            stringResource(R.string.editor_retry),
                            onClick = session::load,
                            modifier = Modifier.testTag("text_editor_retry"),
                        )
                    }
                state.format != null -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s4),
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AtomicIconButton(
                            AtomicIcons.Undo,
                            stringResource(R.string.editor_undo),
                            onClick = session::undo,
                            enabled = state.canUndo && !state.loading && !state.saving,
                        )
                        AtomicIconButton(
                            AtomicIcons.Redo,
                            stringResource(R.string.editor_redo),
                            onClick = session::redo,
                            enabled = state.canRedo && !state.loading && !state.saving,
                        )
                        AtomicChip(
                            stringResource(R.string.editor_find),
                            selected = findOpen,
                            onSelectedChange = { findOpen = it },
                        )
                        AtomicChip(
                            stringResource(R.string.editor_wrap),
                            selected = wrap,
                            onSelectedChange = { wrap = it },
                        )
                    }
                    if (findOpen) FindBar(state.value, enabled = !state.saving, onChange = session::edit)
                    AtomicDivider()
                    EditorBody(
                        state.value,
                        wrap,
                        readOnly = state.saving,
                        onChange = session::edit,
                        onUndo = session::undo,
                        onRedo = session::redo,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (confirmClose) {
        AtomicSheet(label = stringResource(R.string.editor_unsaved), onDismiss = { confirmClose = false }) {
            AtomicText(stringResource(R.string.editor_discard_question), AtomicTextRole.DisplayPushed)
            AtomicText(stringResource(R.string.editor_discard_detail, current.name), AtomicTextRole.Body)
            AtomicButton(
                stringResource(R.string.editor_discard),
                onClick = {
                    confirmClose = false
                    onDismiss()
                },
                variant = AtomicButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth().testTag("text_editor_discard"),
            )
            AtomicButton(
                stringResource(R.string.editor_keep_editing),
                onClick = { confirmClose = false },
                variant = AtomicButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Context.editorActivity(): Activity? {
    var current = this
    repeat(32) {
        when (current) {
            is Activity -> return current as Activity
            is ContextWrapper -> current = (current as ContextWrapper).baseContext
            else -> return null
        }
    }
    return null
}

@Composable
private fun EditorBody(
    value: TextFieldValue,
    wrap: Boolean,
    readOnly: Boolean,
    onChange: (TextFieldValue) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    val mono = AtomicTypography.monoMeta.copy(color = colors.content)
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val scroll =
        if (wrap) {
            Modifier.fillMaxSize().verticalScroll(vertical)
        } else {
            Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal)
        }
    Box(modifier.fillMaxWidth()) {
        Row(scroll.padding(AtomicSpacing.s8)) {
            // Line numbers only line up while each line is one row, so they hide when wrapping.
            if (!wrap) {
                val lines = value.text.count { it == '\n' } + 1
                Text(
                    text = (1..lines).joinToString("\n"),
                    style = mono.copy(color = colors.contentMuted),
                    modifier = Modifier.background(colors.surfaceInset).padding(horizontal = AtomicSpacing.s6),
                )
                Spacer(Modifier.width(AtomicSpacing.s8))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                readOnly = readOnly,
                textStyle = mono,
                cursorBrush = SolidColor(colors.content),
                modifier =
                    Modifier.testTag("text_editor_field")
                        .onPreviewKeyEvent { event ->
                            val undoKey = event.key == Key.Z
                            val redoKey = event.key == Key.Y && !event.isShiftPressed
                            if (!event.isCtrlPressed || event.isAltPressed || (!undoKey && !redoKey)) {
                                false
                            } else {
                                if (event.type == KeyEventType.KeyDown && !readOnly) {
                                    if (redoKey || event.isShiftPressed) onRedo() else onUndo()
                                }
                                true
                            }
                        }.then(if (wrap) Modifier.fillMaxWidth() else Modifier),
            )
        }
    }
}

@Composable
private fun FindBar(
    value: TextFieldValue,
    enabled: Boolean,
    onChange: (TextFieldValue) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    val matches = remember(value.text, query) { TextCodec.findAll(value.text, query) }
    val selected = matches.indexOfFirst { it.first == value.selection.min && it.last + 1 == value.selection.max }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            AtomicTextField(
                query,
                { query = it },
                label = stringResource(R.string.editor_find),
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag("find_query"),
            )
            AtomicTextField(
                replacement,
                { replacement = it },
                label = stringResource(R.string.editor_replace_with),
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicText(
                EditorText.matches(selected, matches.size, query),
                AtomicTextRole.MonoLabel,
                modifier = Modifier.weight(1f),
            )
            AtomicButton(
                stringResource(R.string.editor_next),
                onClick = {
                    val next = matches.firstOrNull { it.first >= value.selection.max } ?: matches.first()
                    onChange(value.copy(selection = TextRange(next.first, next.last + 1)))
                },
                enabled = enabled && matches.isNotEmpty(),
                variant = AtomicButtonVariant.Text,
            )
            AtomicButton(
                stringResource(R.string.editor_replace_all),
                onClick = {
                    val text = value.text.replace(query, replacement, ignoreCase = true)
                    onChange(TextFieldValue(text, TextRange(0)))
                },
                enabled = enabled && matches.isNotEmpty(),
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.testTag("find_replace_all"),
            )
        }
    }
}

/** Wording for [TextEditorDialog]; pure so it is unit-tested. */
internal object EditorText {
    fun meta(
        format: TextFormat?,
        dirty: Boolean,
    ): String {
        if (format == null) return "Opening…"
        val encoding =
            when (format.encoding) {
                TextEncoding.UTF8 -> "UTF-8"
                TextEncoding.UTF8_BOM -> "UTF-8 BOM"
                TextEncoding.UTF16LE -> "UTF-16 LE"
                TextEncoding.UTF16BE -> "UTF-16 BE"
                TextEncoding.LATIN1 -> "Latin-1"
            }
        val ending =
            when (format.lineEnding) {
                LineEnding.LF -> "LF"
                LineEnding.CRLF -> "CRLF"
                LineEnding.CR -> "CR"
            }
        return "$encoding · $ending" + if (dirty) " · unsaved" else ""
    }

    fun matches(
        selected: Int,
        count: Int,
        query: String,
    ): String =
        when {
            query.isEmpty() -> "Type to find"
            count == 0 -> "No matches"
            selected >= 0 -> "${selected + 1} of $count"
            count == 1 -> "1 match"
            else -> "$count matches"
        }

    fun openFailed(error: FileError): String =
        when (error) {
            is FileError.FileTooLarge -> "Files over ${error.limit / (1024 * 1024)} MB open read-only in the preview."
            is FileError.UnsupportedFormat -> "This file isn't plain text."
            else -> "The file couldn't be read."
        }

    fun saveFailed(error: FileError): String =
        when (error) {
            is FileError.DiskFull -> "Not saved · not enough space"
            is FileError.AccessDenied, is FileError.PermissionDenied, is FileError.ReadOnlyStorage ->
                "Not saved · this storage doesn't allow changes"
            else -> "Not saved · the original is unchanged"
        }
}
