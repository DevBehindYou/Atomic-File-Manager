package com.devbehindyou.atomicfilemanager.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.usecase.ArchiveEntryInfo
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class ArchiveBrowserRow(
    val path: String,
    val name: String,
    val file: ArchiveEntryInfo? = null,
) {
    val key: String get() = if (file == null) "folder:$path" else "file:${file.path}"
}

private val archiveSeparators = Regex("/+")

/** Groups display paths only. Archive storage identities and extraction never use these paths. */
internal suspend fun archiveFolderRows(
    entries: List<ArchiveEntryInfo>,
    folder: String,
): List<ArchiveBrowserRow> {
    val context = currentCoroutineContext()
    val prefix = if (folder.isEmpty()) "" else "$folder/"
    val folders = linkedMapOf<String, ArchiveBrowserRow>()
    val files = mutableListOf<ArchiveBrowserRow>()
    for (entry in entries) {
        context.ensureActive()
        val displayPath = archiveSeparators.replace(entry.path.trim('/'), "/")
        if (!displayPath.startsWith(prefix) || displayPath.length == prefix.length) continue
        val remaining = displayPath.substring(prefix.length)
        val first = remaining.substringBefore('/')
        if (first != remaining || entry.isDirectory) {
            val child = prefix + first
            folders.putIfAbsent(child, ArchiveBrowserRow(child, first))
        } else {
            files.add(ArchiveBrowserRow(displayPath, entry.name, file = entry))
        }
    }
    return folders.values + files
}

@Composable
internal fun ArchiveContents(
    entries: List<ArchiveEntryInfo>,
    modifier: Modifier = Modifier,
) {
    var folder by rememberSaveable { mutableStateOf("") }
    var details by remember(folder, entries) { mutableStateOf<ArchiveEntryInfo?>(null) }
    BackHandler(enabled = folder.isNotEmpty() && details == null) { folder = folder.substringBeforeLast('/', "") }
    Column(
        modifier.fillMaxSize().padding(horizontal = AtomicSpacing.s16).testTag("archive_contents"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Atomic.colors.surfaceCard, AtomicShape.lg).padding(AtomicSpacing.s12),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtomicIconTile(AtomicIcons.Archive)
            Column {
                AtomicText(stringResource(R.string.archive_contents_title), AtomicTextRole.Name)
                AtomicText(
                    pluralStringResource(R.plurals.archive_entry_count, entries.size, entries.size),
                    AtomicTextRole.MonoMeta,
                )
            }
        }
        if (folder.isNotEmpty()) {
            AtomicButton(
                stringResource(R.string.archive_parent_folder),
                onClick = { folder = folder.substringBeforeLast('/', "") },
                variant = AtomicButtonVariant.Ghost,
                leadingIcon = AtomicIcons.Back,
            )
            AtomicText(folder, AtomicTextRole.MonoMeta, Modifier.testTag("archive_current_folder"))
        }
        key(entries, folder) {
            val rows by produceState<List<ArchiveBrowserRow>?>(null) {
                value = withContext(Dispatchers.Default) { archiveFolderRows(entries, folder) }
            }
            ArchiveRows(rows) { row ->
                if (row.file == null) folder = row.path else details = row.file
            }
        }
    }
    details?.let { ArchiveEntryDetails(it, onDismiss = { details = null }) }
}

@Composable
private fun ArchiveRows(
    rows: List<ArchiveBrowserRow>?,
    onSelect: (ArchiveBrowserRow) -> Unit,
) {
    when {
        rows == null -> AtomicLoading(stringResource(R.string.archive_loading_entries))
        rows.isEmpty() -> AtomicText(stringResource(R.string.archive_empty_entries), AtomicTextRole.BodySecondary)
        else ->
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.key }) { row ->
                    val file = row.file
                    AtomicFileRow(
                        name = row.name,
                        meta =
                            if (file == null) {
                                stringResource(R.string.archive_folder_entry)
                            } else {
                                FileUtils.formatBytes(file.uncompressedSize)
                            },
                        icon = if (file == null) AtomicIcons.FolderOpen else AtomicIcons.Document,
                        onClick = { onSelect(row) },
                    )
                    AtomicDivider()
                }
            }
    }
}

@Composable
private fun ArchiveEntryDetails(
    file: ArchiveEntryInfo,
    onDismiss: () -> Unit,
) {
    AtomicSheet(label = file.name, onDismiss = onDismiss) {
        AtomicFactSheet(
            listOf(
                AtomicFact(stringResource(R.string.archive_entry_path), file.path, monoValue = true),
                AtomicFact(stringResource(R.string.archive_entry_size), FileUtils.formatBytes(file.uncompressedSize)),
                AtomicFact(stringResource(R.string.archive_packed_size), FileUtils.formatBytes(file.compressedSize)),
            ),
        )
    }
}
