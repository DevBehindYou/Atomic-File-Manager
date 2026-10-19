package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.CompareEntry
import com.devbehindyou.atomicfilemanager.domain.usecase.CompareState
import com.devbehindyou.atomicfilemanager.domain.usecase.FolderComparer
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncMode
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPlan
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPlanner
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

/** A saved mode is acknowledged only after its metadata write succeeds. */
data class FolderCompareSaveState(val savedMode: SyncMode?, val saving: Boolean = false, val failed: Boolean = false)

/**
 * Folder compare and sync (ALL_IN_ONE_PLAN.md 4.1): what differs between [left] and [right], on
 * any two storages, and a one-way sync from left to right. The plan is always shown before it
 * runs; mirror deletes go to Trash. Nothing runs in the background on its own.
 */
@Composable
fun FolderCompareScreen(
    left: FileNodeId,
    right: FileNodeId,
    comparer: FolderComparer,
    onRun: (List<FileOperation>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The mode this pair was saved with, when it was. */
    savedMode: SyncMode? = null,
    /** Saves the pair with a mode for one-tap re-runs; null hides the button. */
    onSave: ((SyncMode) -> Unit)? = null,
    saveState: FolderCompareSaveState? = null,
    onModeChanged: ((SyncMode) -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    var reloads by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<FileResult<List<CompareEntry>>?>(null) }
    var filter by rememberSaveable(left, right) { mutableStateOf<CompareState?>(null) }
    var mode by rememberSaveable(left, right) { mutableStateOf(savedMode ?: SyncMode.COPY_NEW) }
    var savedAs by rememberSaveable(left, right) { mutableStateOf(savedMode) }
    var reviewing by rememberSaveable(left, right) { mutableStateOf(false) }
    val confirmedMode = if (saveState != null) saveState.savedMode else savedAs

    LaunchedEffect(left, right, reloads) {
        result = null
        result = comparer.compare(left, right)
    }
    val entries = (result as? FileResult.Success<List<CompareEntry>>)?.value
    val shown = entries?.let { CompareText.visible(it, filter) }

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("folder_compare"),
    ) {
        AtomicPushedHeader(
            onBack = onBack,
            title = stringResource(R.string.compare_title),
            eyebrow = "${CompareText.folderName(left.raw)} → ${CompareText.folderName(right.raw)}",
        )
        if (entries != null) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s4),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            ) {
                AtomicChip(
                    "Differences ${entries.count { it.state != CompareState.SAME }}",
                    selected = filter == null,
                    onSelectedChange = { filter = null },
                )
                CompareState.entries.forEach { state ->
                    val count = entries.count { it.state == state }
                    if (count > 0) {
                        AtomicChip(
                            "${CompareText.state(state)} $count",
                            selected = filter == state,
                            onSelectedChange = { filter = state },
                        )
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(AtomicSpacing.s16)) {
            when {
                result == null -> item { AtomicLoading("Comparing…") }
                result is FileResult.Failure ->
                    item {
                        AtomicEmptyState(
                            message = "Couldn't read one of the folders.",
                            detail = "Check that both are still there and reachable, then try again.",
                            actionLabel = "Try again",
                            onAction = { reloads++ },
                        )
                    }
                shown.isNullOrEmpty() -> item { AtomicEmptyState(message = CompareText.NOTHING_DIFFERS) }
                else ->
                    items(shown, key = { it.path }) { entry ->
                        AtomicFileRow(
                            name = entry.path,
                            meta = CompareText.meta(entry),
                            icon = CompareText.icon(entry),
                            onClick = {},
                        )
                    }
            }
        }
        if (entries != null) {
            Column(
                Modifier.padding(AtomicSpacing.s16),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
                    SyncMode.entries.forEach { option ->
                        AtomicChip(
                            CompareText.mode(option),
                            selected = mode == option,
                            onSelectedChange = {
                                mode = option
                                onModeChanged?.invoke(option)
                            },
                        )
                    }
                }
                if (onSave != null) {
                    AtomicButton(
                        when {
                            saveState?.saving == true -> stringResource(R.string.action_saving)
                            confirmedMode == mode -> "Saved for one-tap sync"
                            else -> "Save this pair"
                        },
                        onClick = {
                            onSave(mode)
                            if (saveState == null) savedAs = mode
                        },
                        enabled = confirmedMode != mode && saveState?.saving != true,
                        variant = AtomicButtonVariant.Text,
                        modifier = Modifier.testTag("save_sync_pair"),
                    )
                    if (saveState?.failed == true) {
                        AtomicText(
                            stringResource(R.string.compare_preferences_save_error),
                            AtomicTextRole.BodySecondary,
                        )
                    }
                }
                AtomicButton(
                    "Review sync",
                    onClick = { reviewing = true },
                    variant = AtomicButtonVariant.Solid,
                    modifier = Modifier.fillMaxWidth().testTag("review_sync_button"),
                )
            }
        }
    }

    if (reviewing && entries != null) {
        val plan = remember(entries, mode) { SyncPlanner.plan(entries, mode) }
        AtomicSheet(label = CompareText.mode(mode), onDismiss = { reviewing = false }) {
            AtomicText(CompareText.summary(plan), AtomicTextRole.Body)
            CompareText.preview(plan).forEach { AtomicText(it, AtomicTextRole.BodySecondary) }
            if (plan.isEmpty) {
                AtomicButton("Close", onClick = { reviewing = false }, modifier = Modifier.fillMaxWidth())
            } else {
                AtomicButton(
                    "Start sync",
                    onClick = {
                        reviewing = false
                        onRun(SyncPlanner.operations(plan, System.currentTimeMillis()))
                    },
                    variant = if (plan.trash.isEmpty()) AtomicButtonVariant.Solid else AtomicButtonVariant.Destructive,
                    modifier = Modifier.fillMaxWidth().testTag("start_sync_button"),
                )
                AtomicButton(
                    "Cancel",
                    onClick = { reviewing = false },
                    variant = AtomicButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Wording for [FolderCompareScreen]; pure so it is unit-tested. */
internal object CompareText {
    const val NOTHING_DIFFERS = "Nothing differs here."
    private const val PREVIEW_LINES = 8

    fun visible(
        entries: List<CompareEntry>,
        filter: CompareState?,
    ): List<CompareEntry> =
        if (filter == null) entries.filter { it.state != CompareState.SAME } else entries.filter { it.state == filter }

    fun state(state: CompareState): String =
        when (state) {
            CompareState.ONLY_LEFT -> "Only left"
            CompareState.ONLY_RIGHT -> "Only right"
            CompareState.NEWER_LEFT -> "Newer left"
            CompareState.NEWER_RIGHT -> "Newer right"
            CompareState.DIFFERENT -> "Different"
            CompareState.SAME -> "Same"
        }

    fun mode(mode: SyncMode): String =
        when (mode) {
            SyncMode.COPY_NEW -> "Copy new to right"
            SyncMode.MIRROR -> "Mirror to right"
        }

    fun meta(entry: CompareEntry): String =
        listOfNotNull(
            state(entry.state),
            entry.left?.let { "left ${facts(it)}" },
            entry.right?.let { "right ${facts(it)}" },
        ).joinToString(" · ")

    fun icon(entry: CompareEntry) =
        if ((entry.left ?: entry.right)?.isDirectory == true) AtomicIcons.Files else AtomicIcons.Document

    fun summary(plan: SyncPlan): String {
        if (plan.isEmpty) return "The right folder already has everything this sync would copy."
        val parts =
            listOfNotNull(
                plan.copies.size.takeIf { it > 0 }?.let { "copy ${count(it, "new item")}" },
                plan.replacements.size.takeIf { it > 0 }?.let { "replace ${count(it, "file")}" },
                plan.trash.size.takeIf { it > 0 }?.let { "move ${count(it, "extra item")} to Trash" },
            )
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }

    /** The first few changes, so the user sees what will happen before it runs. */
    fun preview(plan: SyncPlan): List<String> {
        val lines =
            plan.copies.map { "+ ${it.path}" } +
                plan.replacements.map { "↻ ${it.path}" } +
                plan.trash.map { "− ${it.name}" }
        if (lines.size <= PREVIEW_LINES) return lines
        return lines.take(PREVIEW_LINES) + "and ${lines.size - PREVIEW_LINES} more"
    }

    fun folderName(raw: String): String =
        raw.substringAfter(
            ':',
        ).trimEnd('/').substringAfterLast('/').ifBlank { "Storage" }

    private fun facts(node: FileNode): String =
        if (node.isDirectory) {
            "folder"
        } else {
            listOf(
                if (node.size >= 0) FileUtils.formatBytes(node.size) else "?",
                if (node.modifiedAt > 0) FileUtils.formatDate(node.modifiedAt) else "?",
            ).joinToString(" ")
        }

    private fun count(
        n: Int,
        noun: String,
    ): String = if (n == 1) "1 $noun" else "$n ${noun}s"
}
