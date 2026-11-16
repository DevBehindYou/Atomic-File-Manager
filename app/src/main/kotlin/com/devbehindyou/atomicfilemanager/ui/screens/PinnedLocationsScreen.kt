package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.domain.model.Favourite
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.FavouritesRepository
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewDialog
import com.devbehindyou.atomicfilemanager.ui.components.iconFor
import com.devbehindyou.atomicfilemanager.ui.components.metaFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PinnedLocationsScreen(
    onBack: () -> Unit,
    onOpenFolder: (FileNodeId) -> Unit,
    onBrowseFiles: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as AtomicApp).container
    var preview by remember { mutableStateOf<FileNode?>(null) }

    Column(
        Modifier.fillMaxSize().background(Atomic.colors.background)
            .statusBarsPadding().navigationBarsPadding().testTag("pinned_locations_screen"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = "Pinned locations")
        PinnedLocationsList(
            repository = container.favouritesRepository,
            getNode = container.getNodeUseCase,
            onOpenFolder = onOpenFolder,
            onOpenFile = { preview = it },
            onBrowseFiles = onBrowseFiles,
        )
    }

    preview?.let { FilePreviewDialog(node = it, onDismiss = { preview = null }) }
}

@Composable
internal fun PinnedLocationsList(
    repository: FavouritesRepository,
    getNode: GetNodeUseCase,
    onOpenFolder: (FileNodeId) -> Unit,
    onOpenFile: (FileNode) -> Unit,
    onBrowseFiles: () -> Unit,
) {
    val flow = remember(repository) { repository.observe() }
    val favourites by flow.collectAsState(initial = null)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AtomicSpacing.screenMargin, vertical = AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s10),
    ) {
        item(key = "introduction") {
            AtomicText("Shortcuts to files and folders you use often.", AtomicTextRole.BodySecondary)
        }
        if (favourites?.isEmpty() == true) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Atomic.colors.surfaceCard, AtomicShape.lg)
                        .padding(AtomicSpacing.s16),
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                ) {
                    AtomicText("No pinned locations", AtomicTextRole.DisplayCard)
                    AtomicText("Pin files or folders from Files to keep them here.", AtomicTextRole.BodySecondary)
                }
            }
        }
        items(favourites.orEmpty(), key = { "pin:${it.id.raw}" }) { favourite ->
            ManagedPin(
                favourite = favourite,
                repository = repository,
                getNode = getNode,
                onOpenFolder = onOpenFolder,
                onOpenFile = onOpenFile,
            )
        }
        item(key = "browse") {
            AtomicButton("Browse files", onClick = onBrowseFiles, variant = AtomicButtonVariant.Ghost)
        }
    }
}

@Composable
private fun ManagedPin(
    favourite: Favourite,
    repository: FavouritesRepository,
    getNode: GetNodeUseCase,
    onOpenFolder: (FileNodeId) -> Unit,
    onOpenFile: (FileNode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var removing by remember(favourite.id) { mutableStateOf(false) }
    var removeFailed by remember(favourite.id) { mutableStateOf(false) }
    val resolved by produceState<FileResult<FileNode>?>(null, getNode, favourite.id) {
        value = getNode(favourite.id)
    }
    val node = (resolved as? FileResult.Success)?.value?.takeIf { it.access.readable }
    val detail =
        when {
            node != null -> metaFor(node)
            resolved == null -> "Checking…"
            else -> "Location unavailable"
        }
    val onOpen = node?.let { found -> { if (found.isDirectory) onOpenFolder(favourite.id) else onOpenFile(found) } }

    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
        PinnedLocationCard(favourite = favourite, node = node, detail = detail, onOpen = onOpen) {
            AtomicIconButton(
                icon = AtomicIcons.Close,
                contentDescription = "Unpin ${favourite.name}",
                enabled = !removing,
                onClick = {
                    removing = true
                    removeFailed = false
                    scope.launch {
                        try {
                            repository.remove(favourite.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            removeFailed = true
                        } finally {
                            removing = false
                        }
                    }
                },
            )
        }
        if (removeFailed) {
            AtomicText(
                "Couldn’t remove this shortcut. Try again.",
                AtomicTextRole.BodySecondary,
                color = Atomic.colors.error,
            )
        }
    }
}

@Composable
internal fun PinnedLocationCard(
    favourite: Favourite,
    node: FileNode?,
    detail: String,
    onOpen: (() -> Unit)?,
    managementAction: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val fallbackIcon = if (favourite.isDirectory) AtomicIcons.Files else AtomicIcons.Document
    val icon = node?.let(::iconFor) ?: fallbackIcon
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Atomic.colors.surfaceCard),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val open = if (onOpen == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onOpen)
        Row(
            Modifier.weight(1f).then(open).padding(AtomicSpacing.s16),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(AtomicSize.iconSmall), tint = Atomic.colors.accentText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
                AtomicText(favourite.name, AtomicTextRole.Name)
                AtomicText(detail, AtomicTextRole.BodySecondary)
            }
            if (onOpen != null) {
                Icon(AtomicIcons.ChevronRight, null, Modifier.size(18.dp), tint = Atomic.colors.accentText)
            }
        }
        if (managementAction != null) {
            Row(Modifier.padding(end = AtomicSpacing.s8)) { managementAction() }
        }
    }
}
