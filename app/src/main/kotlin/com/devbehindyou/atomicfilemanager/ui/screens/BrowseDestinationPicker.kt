package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.components.BreadcrumbItem
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrowseDestinationPicker(
    clip: ClipboardState,
    initial: BrowseUiState,
    volumes: List<StorageVolumeInfo>,
    onConfirm: (FileNodeId) -> Boolean,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AtomicApp
    val container = app.container
    val scope = rememberCoroutineScope()
    var path by remember {
        val crumbs = initial.breadcrumbs.takeIf { it.lastOrNull()?.path == initial.currentFolderId.raw }
        mutableStateOf(crumbs ?: listOf(BreadcrumbItem(initial.currentFolderName, initial.currentFolderId.raw)))
    }
    var attempt by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var confirmFailed by remember { mutableStateOf(false) }
    val destination = FileNodeId(path.last().path)
    val currentVolume = currentStorageVolume(volumes, destination, path)
    val available = volumes.filter { it.isMounted && it.rootNodeId != null && !it.requiresGrant }
    val confirmLabel =
        stringResource(
            if (clip.operation == ClipboardOp.COPY) R.string.destination_copy else R.string.destination_move,
        )
    val operationSummary =
        if (clip.operation == ClipboardOp.COPY) R.plurals.destination_copy_count else R.plurals.destination_move_count

    fun changePath(next: List<BreadcrumbItem>) {
        path = next
        confirmFailed = false
    }

    AtomicSheet(label = stringResource(R.string.destination_title), onDismiss = onDismiss) {
        AtomicText(
            pluralStringResource(operationSummary, clip.items.size, clip.items.size),
            AtomicTextRole.Name,
            Modifier.fillMaxWidth().background(Atomic.colors.surfaceInset, AtomicShape.sm).padding(AtomicSpacing.s12),
        )
        clip.sourceLabel?.let {
            AtomicText(stringResource(R.string.destination_from, it), AtomicTextRole.BodySecondary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            available.forEach { volume ->
                key(volume.id) {
                    AtomicChip(
                        label = volume.label,
                        selected = volume.id == currentVolume?.id,
                        onSelectedChange = {
                            changePath(listOf(BreadcrumbItem(volume.label, checkNotNull(volume.rootNodeId).raw)))
                        },
                        enabled = !checking,
                    )
                }
            }
        }
        AtomicText(path.joinToString(" / ") { it.name }, AtomicTextRole.Name, Modifier.testTag("destination_path"))
        currentVolume?.takeIf { it.totalBytes > 0 }?.let {
            AtomicText(
                stringResource(R.string.destination_available, FileUtils.formatBytes(it.freeBytes)),
                AtomicTextRole.MonoMeta,
            )
        }
        if (path.size > 1) {
            AtomicButton(
                stringResource(R.string.destination_parent),
                onClick = { changePath(path.dropLast(1)) },
                enabled = !checking,
                variant = AtomicButtonVariant.Text,
                leadingIcon = AtomicIcons.Back,
            )
        }
        key(destination, attempt) {
            val loadedFolder by produceState<FileResult<FileNode>?>(null) {
                value = container.getNodeUseCase(destination)
            }
            val folder = (loadedFolder as? FileResult.Success)?.value
            val listing by produceState<DestinationFolders?>(null) {
                val folders = linkedMapOf<FileNodeId, FileNode>()
                var failed = false
                container.getDirectoryListingUseCase(destination).collect { batch ->
                    when (batch) {
                        is FileResult.Success ->
                            batch.value.filter { it.isDirectory && it.access.readable }
                                .forEach { folders[it.id] = it }
                        is FileResult.Failure -> failed = true
                    }
                }
                value = DestinationFolders(folders.values.toList(), failed)
            }
            DestinationFolderList(listing, enabled = !checking, onRetry = { attempt++ }) { next ->
                changePath(path + BreadcrumbItem(next.displayName, next.id.raw))
            }
            if (confirmFailed || (loadedFolder != null && !canUseDestination(folder, destination, clip))) {
                AtomicText(stringResource(R.string.destination_unavailable), AtomicTextRole.BodySecondary)
            }
            AtomicButton(
                confirmLabel,
                onClick = {
                    if (!checking) {
                        checking = true
                        scope.launch {
                            try {
                                val verified = (container.getNodeUseCase(destination) as? FileResult.Success)?.value
                                if (canUseDestination(verified, destination, clip) && onConfirm(destination)) {
                                    onDismiss()
                                } else {
                                    confirmFailed = true
                                }
                            } finally {
                                checking = false
                            }
                        }
                    }
                },
                enabled = !checking && listing?.failed == false && canUseDestination(folder, destination, clip),
                modifier = Modifier.fillMaxWidth().testTag("destination_confirm"),
            )
        }
    }
}

internal fun canUseDestination(
    folder: FileNode?,
    id: FileNodeId,
    clip: ClipboardState,
): Boolean =
    folder != null && folder.id == id && folder.isDirectory && folder.access.writable && clip.items.none { it.id == id }

private data class DestinationFolders(
    val folders: List<FileNode>,
    val failed: Boolean,
)

@Composable
private fun DestinationFolderList(
    listing: DestinationFolders?,
    enabled: Boolean,
    onRetry: () -> Unit,
    onOpen: (FileNode) -> Unit,
) {
    when {
        listing == null -> AtomicLoading(stringResource(R.string.destination_loading))
        listing.failed ->
            AtomicButton(
                stringResource(R.string.action_retry),
                onClick = onRetry,
                enabled = enabled,
                variant = AtomicButtonVariant.Ghost,
            )
        listing.folders.isEmpty() ->
            AtomicText(stringResource(R.string.destination_no_folders), AtomicTextRole.BodySecondary)
        else ->
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(listing.folders, key = { it.id.raw }) { child ->
                    AtomicFileRow(
                        name = child.displayName,
                        meta = stringResource(R.string.destination_folder),
                        icon = AtomicIcons.FolderOpen,
                        onClick = { if (enabled) onOpen(child) },
                    )
                }
            }
    }
}
