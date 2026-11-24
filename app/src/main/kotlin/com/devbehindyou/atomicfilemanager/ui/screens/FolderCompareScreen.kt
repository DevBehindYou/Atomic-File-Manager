package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicInfoSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.getOrNull
import com.devbehindyou.atomicfilemanager.domain.usecase.CompareEntry
import com.devbehindyou.atomicfilemanager.domain.usecase.CompareState
import com.devbehindyou.atomicfilemanager.domain.usecase.FolderComparer
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncMode
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPlan
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPlanner
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CancellationException

/** A saved mode is acknowledged only after its metadata write succeeds. */
data class FolderCompareSaveState(val savedMode: SyncMode?, val saving: Boolean = false, val failed: Boolean = false)

/**
 * Folder compare and sync (ALL_IN_ONE_PLAN.md 4.1): what differs between [left] and [right], on
 * any two storages, and a one-way sync from left to right. The plan is always shown before it
 * runs; mirror deletes go to Trash. Nothing runs in the background on its own.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    getNode: GetNodeUseCase? = null,
) {
    BackHandler(onBack = onBack)
    val initiallySaved = if (saveState != null) saveState.savedMode != null else savedMode != null
    var requests by rememberSaveable(left, right) { mutableIntStateOf(if (initiallySaved) 1 else 0) }
    var result by remember(left, right, requests) { mutableStateOf<FileResult<List<CompareEntry>>?>(null) }
    var inspectedEntry by remember(left, right, requests) { mutableStateOf<CompareEntry?>(null) }
    var filter by rememberSaveable(left, right) { mutableStateOf<CompareState?>(null) }
    var mode by rememberSaveable(left, right) { mutableStateOf(savedMode ?: SyncMode.COPY_NEW) }
    var savedAs by rememberSaveable(left, right) { mutableStateOf(savedMode) }
    var reviewing by rememberSaveable(left, right) { mutableStateOf(false) }
    val confirmedMode = if (saveState != null) saveState.savedMode else savedAs

    LaunchedEffect(left, right, requests) {
        if (requests > 0) result = comparer.compare(left, right)
    }
    val entries = (result as? FileResult.Success<List<CompareEntry>>)?.value
    val shown = remember(entries, filter) { entries?.let { CompareText.visible(it, filter) } }
    val counts = remember(entries) { entries.orEmpty().groupingBy { it.state }.eachCount() }
    val differences = (entries?.size ?: 0) - (counts[CompareState.SAME] ?: 0)
    val comparing = requests > 0 && result == null
    val leftLocation = compareLocationLabel(left, getNode)
    val rightLocation = compareLocationLabel(right, getNode)

    fun compare() {
        filter = null
        reviewing = false
        requests++
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("folder_compare"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = stringResource(R.string.compare_title))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("folder_compare_entries"),
            contentPadding = PaddingValues(AtomicSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            item {
                CompareLocations(
                    left = leftLocation,
                    right = rightLocation,
                    comparing = comparing,
                    compared = entries != null,
                    onCompare = ::compare,
                )
            }
            if (entries != null) {
                item {
                    CompareResultFilters(differences, counts, filter) { filter = it }
                }
            }
            when {
                comparing -> item { AtomicLoading("Comparing…") }
                requests == 0 ->
                    item {
                        AtomicEmptyState(
                            message = "No changes made",
                            detail = "A comparison reads both locations. Sync requires a separate confirmation.",
                        )
                    }
                result is FileResult.Failure ->
                    item {
                        AtomicEmptyState(
                            message = "Couldn't read one of the folders.",
                            detail = "Check that both are still there and reachable, then try again.",
                            actionLabel = "Try again",
                            onAction = ::compare,
                        )
                    }
                shown.isNullOrEmpty() -> item { AtomicEmptyState(message = CompareText.NOTHING_DIFFERS) }
                else ->
                    items(shown, key = { it.path }) { entry ->
                        AtomicFileRow(
                            name = entry.path,
                            meta = CompareText.meta(entry),
                            icon = CompareText.icon(entry),
                            onClick = { inspectedEntry = entry },
                        )
                    }
            }
        }
        if (entries != null) {
            Column(
                Modifier.padding(AtomicSpacing.s16),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
                ) {
                    SyncMode.entries.forEach { option ->
                        AtomicChip(
                            CompareText.mode(option),
                            selected = mode == option,
                            onSelectedChange = {
                                mode = option
                                onModeChanged?.invoke(option)
                            },
                            modifier = Modifier.testTag("sync_mode_${option.name}"),
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
                    "Review left → right",
                    onClick = { reviewing = true },
                    variant = AtomicButtonVariant.Solid,
                    modifier = Modifier.fillMaxWidth().testTag("review_sync_button"),
                )
            }
        }
    }

    inspectedEntry?.let { entry ->
        AtomicInfoSheet(
            label = "Difference details",
            headline = entry.path,
            body = CompareText.meta(entry),
            onDismiss = { inspectedEntry = null },
        )
    }

    if (reviewing && entries != null) {
        val plan = remember(entries, mode) { SyncPlanner.plan(entries, mode) }
        AtomicSheet(label = CompareText.mode(mode), onDismiss = { reviewing = false }) {
            AtomicText("Source: $leftLocation", AtomicTextRole.BodySecondary)
            AtomicText("Destination: $rightLocation", AtomicTextRole.BodySecondary)
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

@Composable
private fun compareLocationLabel(
    id: FileNodeId,
    getNode: GetNodeUseCase?,
): String {
    var displayName by remember(id, getNode) { mutableStateOf<String?>(null) }
    LaunchedEffect(id, getNode) {
        if (getNode == null || id.prefix == FileNodeId.Prefix.FILE) return@LaunchedEffect
        displayName =
            try {
                getNode(id).getOrNull()?.displayName
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
    }
    val location = CompareText.location(id)
    val name = displayName
    return if (name.isNullOrBlank() || location == name) location else "$location / $name"
}

@Composable
private fun CompareLocations(
    left: String,
    right: String,
    comparing: Boolean,
    compared: Boolean,
    onCompare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        AtomicText("Compare first. Review the direction before copying.", AtomicTextRole.BodySecondary)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sideBySide = maxWidth >= 520.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            if (sideBySide) {
                Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
                    CompareLocationCard("Left location", left, "Source", Modifier.weight(1f))
                    CompareLocationCard("Right location", right, "Destination", Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
                    CompareLocationCard("Left location", left, "Source")
                    CompareLocationCard("Right location", right, "Destination")
                }
            }
        }
        AtomicButton(
            text = if (compared) "Compare again" else "Compare folders",
            onClick = onCompare,
            enabled = !comparing,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("run_folder_compare"),
        )
    }
}

@Composable
private fun CompareLocationCard(
    title: String,
    path: String,
    side: String,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceCard, AtomicShape.lg)
            .border(AtomicBorder.hair, colors.borderHair, AtomicShape.lg)
            .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
    ) {
        AtomicText(title, AtomicTextRole.DisplayCard)
        AtomicText(path, AtomicTextRole.Name, maxLines = 3)
        AtomicText(side, AtomicTextRole.MonoMeta)
    }
}

@Composable
private fun CompareResultFilters(
    differences: Int,
    counts: Map<CompareState, Int>,
    filter: CompareState?,
    onFilter: (CompareState?) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicChip(
            "Differences $differences",
            selected = filter == null,
            onSelectedChange = { onFilter(null) },
        )
        CompareState.entries.forEach { state ->
            val count = counts[state] ?: 0
            if (count > 0) {
                val label = CompareText.state(state)
                AtomicChip("$label $count", selected = filter == state, onSelectedChange = { onFilter(state) })
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
            (
                plan.copies.asSequence().map { "+ ${it.path}" } +
                    plan.replacements.asSequence().map { "↻ ${it.path}" } +
                    plan.trash.asSequence().map { "− ${it.name}" }
            ).take(PREVIEW_LINES).toList()
        val remaining = plan.copies.size + plan.replacements.size + plan.trash.size - lines.size
        return if (remaining > 0) lines + "and $remaining more" else lines
    }

    fun folderName(raw: String): String =
        raw.substringAfter(
            ':',
        ).trimEnd('/').substringAfterLast('/').ifBlank { "Storage" }

    fun location(id: FileNodeId): String =
        when (id.prefix) {
            FileNodeId.Prefix.FILE -> id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme)
            FileNodeId.Prefix.SAF -> "Linked folder"
            FileNodeId.Prefix.USB -> "USB storage"
            FileNodeId.Prefix.SHIZUKU -> "Restricted folder"
            FileNodeId.Prefix.MEDIA -> "Shared storage"
            FileNodeId.Prefix.FTP,
            FileNodeId.Prefix.FTPS,
            FileNodeId.Prefix.SFTP,
            FileNodeId.Prefix.SMB,
            FileNodeId.Prefix.WEBDAV,
            -> "Network folder"
            null -> "Selected folder"
        }

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
