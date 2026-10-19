package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.Favourite
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.RecentItem
import com.devbehindyou.atomicfilemanager.domain.model.RecentSource
import com.devbehindyou.atomicfilemanager.domain.model.mergeRecents
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

/**
 * Home > Favourites and Recent (ALL_IN_ONE_PLAN.md 1.2, screens/HOME.md). Each section is
 * hidden while empty. A favourite whose target is gone stays visible as "Missing" with a remove
 * option (FR-6.5); a recent file that is gone is dropped.
 */
@Composable
fun HomeShortcuts(onOpenFolder: (FileNodeId) -> Unit) {
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
    val recents =
        mergeRecents(
            opened = opened.filter { resolved[it.id] != null },
            media = media,
            limit = RECENT_ROWS,
        )
    var preview by remember { mutableStateOf<FileNode?>(null) }
    var missing by remember { mutableStateOf<Favourite?>(null) }

    if (favourites.isNotEmpty()) {
        Column(Modifier.testTag("home_favourites"), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
            AtomicSectionLabel("Favourites", trailingText = "${favourites.size}")
            favourites.forEach { favourite ->
                val node = resolved[favourite.id]
                val known = favourite.id in resolved
                AtomicFileRow(
                    name = favourite.name,
                    meta = ShortcutText.favouriteMeta(node, known),
                    icon =
                        node?.let(::iconFor)
                            ?: if (favourite.isDirectory) AtomicIcons.Files else AtomicIcons.Document,
                    hidden = known && node == null,
                    leading = node?.let { resolvedNode -> @Composable { FileThumbnail(resolvedNode) } },
                    onClick = {
                        when {
                            node == null && known -> missing = favourite
                            node == null -> Unit
                            node.isDirectory -> onOpenFolder(node.id)
                            else -> preview = node
                        }
                    },
                )
            }
        }
    }

    if (recents.isNotEmpty()) {
        Column(Modifier.testTag("home_recent"), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
            AtomicSectionLabel("Recent", trailingText = "Last $RECENT_MEDIA_DAYS days")
            recents.forEach { item ->
                val node = resolved[item.id]
                AtomicFileRow(
                    name = item.name,
                    meta = ShortcutText.recentMeta(item),
                    icon = node?.let(::iconFor) ?: AtomicIcons.Document,
                    leading = node?.let { resolvedNode -> @Composable { FileThumbnail(resolvedNode) } },
                    onClick = { node?.let { preview = it } },
                    onLongClick = { scope.launch { recentsRepo.remove(item.id) } },
                )
            }
        }
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
        val verb = if (item.source == RecentSource.OPENED) "Opened" else "Added"
        return "$size$verb ${FileUtils.formatDate(item.at)}"
    }
}
