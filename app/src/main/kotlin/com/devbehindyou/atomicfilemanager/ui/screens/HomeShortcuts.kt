package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.Favourite
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.RecentItem
import com.devbehindyou.atomicfilemanager.domain.model.RecentSource
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewDialog
import com.devbehindyou.atomicfilemanager.ui.components.FileThumbnail
import com.devbehindyou.atomicfilemanager.ui.components.iconFor
import com.devbehindyou.atomicfilemanager.ui.components.metaFor
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RECENT_ROWS = 6
private const val RECENT_MEDIA_DAYS = 7L
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Home shortcut cards backed by saved pins, open history and recent media. */
@Composable
fun HomeShortcuts(
    onOpenFolder: (FileNodeId) -> Unit,
    onExploreStorage: () -> Unit,
    onViewOpenedHistory: (() -> Unit)? = null,
    onManagePinnedLocations: (() -> Unit)? = null,
) {
    val container = (LocalContext.current.applicationContext as AtomicApp).container
    val favouritesRepo = container.favouritesRepository
    val recentsRepo = container.recentsRepository
    val getNode = container.getNodeUseCase
    val scope = rememberCoroutineScope()

    val favouriteFlow = remember(favouritesRepo) { favouritesRepo.observe() }
    val favourites by favouriteFlow.collectAsState(initial = emptyList())
    val openedFlow = remember(recentsRepo) { recentsRepo.observeOpened(RECENT_ROWS * 2) }
    val opened by openedFlow.collectAsState(initial = emptyList())
    val media by produceState(emptyList<RecentItem>(), recentsRepo) {
        value = recentsRepo.recentMedia(System.currentTimeMillis() - RECENT_MEDIA_DAYS * DAY_MILLIS, RECENT_ROWS)
    }
    // Looks every target up off the main thread; null means it no longer exists.
    val resolved by produceState(emptyMap<FileNodeId, FileNode?>(), favourites, opened, media) {
        val ids = favourites.map { it.id } + opened.map { it.id } + media.map { it.id }
        value =
            withContext(Dispatchers.IO) {
                ids.distinct().associateWith { id -> (getNode(id) as? FileResult.Success)?.value }
            }
    }
    val openedRows = opened.filter { resolved[it.id] != null }.take(RECENT_ROWS)
    val openedIds = opened.mapTo(HashSet()) { it.id }
    val addedRows = media.filter { it.id !in openedIds && resolved[it.id] != null }.take(RECENT_ROWS)
    var preview by remember { mutableStateOf<FileNode?>(null) }
    var missing by remember { mutableStateOf<Favourite?>(null) }

    val history: @Composable () -> Unit = {
        RecentShortcutCard(
            title = "Recently opened",
            emptyMessage = "Files you open in Atomic will appear here.",
            items = openedRows,
            resolved = resolved,
            onPreview = { preview = it },
            onRemove = { item -> scope.launch { recentsRepo.remove(item.id) } },
            modifier = Modifier.testTag("home_recent"),
            onViewAll = onViewOpenedHistory,
        )
    }
    val pins: @Composable () -> Unit = {
        PinnedShortcutCards(
            favourites = favourites,
            resolved = resolved,
            onOpen = { favourite, node ->
                when {
                    node == null -> missing = favourite
                    node.isDirectory -> onOpenFolder(node.id)
                    else -> preview = node
                }
            },
            onExploreStorage = onExploreStorage,
            onManagePinnedLocations = onManagePinnedLocations,
        )
    }
    if (LocalConfiguration.current.screenWidthDp >= 1120) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s24)) {
            Column(Modifier.weight(1.6f)) { history() }
            Column(Modifier.weight(1f)) { pins() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s24)) {
            history()
            pins()
        }
    }
    if (addedRows.isNotEmpty()) {
        RecentShortcutCard(
            title = "Recent media",
            items = addedRows,
            resolved = resolved,
            onPreview = { preview = it },
        )
    }

    missing?.let { favourite ->
        AtomicSheet(label = "Not found", onDismiss = { missing = null }) {
            AtomicText("“${favourite.name}” isn't there any more", AtomicTextRole.DisplayPushed)
            AtomicText(
                "It may have been moved, renamed, deleted, or be on storage that isn't connected.",
                AtomicTextRole.Body,
            )
            AtomicButton(
                "Remove from favourites",
                onClick = {
                    scope.launch { favouritesRepo.remove(favourite.id) }
                    missing = null
                },
                variant = AtomicButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth(),
            )
            AtomicButton("Keep", onClick = {
                missing = null
            }, variant = AtomicButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
        }
    }

    preview?.let { FilePreviewDialog(node = it, onDismiss = { preview = null }) }
}

@Composable
private fun RecentShortcutCard(
    title: String,
    items: List<RecentItem>,
    resolved: Map<FileNodeId, FileNode?>,
    onPreview: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: ((RecentItem) -> Unit)? = null,
    emptyMessage: String? = null,
    onViewAll: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16)) {
        if (onViewAll == null) {
            AtomicText(title, AtomicTextRole.DisplayCard)
        } else {
            AtomicSectionLabel(title, actionLabel = "View all", onAction = onViewAll)
        }
        Column(
            Modifier.fillMaxWidth()
                .background(Atomic.colors.surfaceCard, AtomicShape.lg)
                .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8),
        ) {
            if (items.isEmpty() && emptyMessage != null) {
                AtomicText(
                    emptyMessage,
                    AtomicTextRole.BodySecondary,
                    Modifier.padding(vertical = AtomicSpacing.s12),
                )
            }
            items.forEach { item ->
                val node = resolved[item.id] ?: return@forEach
                AtomicFileRow(
                    name = item.name,
                    meta = ShortcutText.recentMeta(item),
                    icon = iconFor(node),
                    leading = { FileThumbnail(node) },
                    onClick = { onPreview(node) },
                    onLongClick = onRemove?.let { remove -> { remove(item) } },
                )
            }
        }
    }
}

@Composable
private fun PinnedShortcutCards(
    favourites: List<Favourite>,
    resolved: Map<FileNodeId, FileNode?>,
    onOpen: (Favourite, FileNode?) -> Unit,
    onExploreStorage: () -> Unit,
    onManagePinnedLocations: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth().testTag("home_favourites"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s10),
    ) {
        AtomicSectionLabel("Pinned locations", actionLabel = "Manage", onAction = onManagePinnedLocations)
        if (favourites.isEmpty()) {
            AtomicText("Pin files or folders from Files to keep them here.", AtomicTextRole.BodySecondary)
        }
        favourites.forEach { favourite ->
            val node = resolved[favourite.id]
            val known = favourite.id in resolved
            PinnedLocationCard(
                favourite = favourite,
                node = node,
                detail = ShortcutText.favouriteMeta(node, known),
                onOpen = if (known) ({ onOpen(favourite, node) }) else null,
            )
        }
        ExploreStorageCard(onExploreStorage)
    }
}

@Composable
private fun ExploreStorageCard(onExploreStorage: () -> Unit) {
    val border = Atomic.colors.borderHair
    Column(
        Modifier.fillMaxWidth()
            .drawWithCache {
                val corner = CornerRadius(16.dp.toPx())
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                onDrawBehind {
                    drawRoundRect(border, cornerRadius = corner, style = Stroke(1.dp.toPx(), pathEffect = dash))
                }
            }.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicText("Need to make room?", AtomicTextRole.BodySecondary)
        AtomicButton("Explore storage", onExploreStorage, variant = AtomicButtonVariant.Ghost)
    }
}

/** Wording for [HomeShortcuts]; pure so it is unit-tested. */
internal object ShortcutText {
    fun favouriteMeta(
        node: FileNode?,
        known: Boolean,
    ): String =
        when {
            node != null -> metaFor(node)
            known -> "Missing · tap to remove"
            else -> "Checking…"
        }

    fun recentMeta(item: RecentItem): String {
        val size = if (item.size >= 0) "${FileUtils.formatBytes(item.size)} · " else ""
        val verb = if (item.source == RecentSource.OPENED) "Opened" else "Modified"
        return "$size$verb ${FileUtils.formatDate(item.at)}"
    }
}
