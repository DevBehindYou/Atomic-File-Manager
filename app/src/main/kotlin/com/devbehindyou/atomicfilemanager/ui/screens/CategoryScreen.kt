package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.data.volume.PhoneFileIndex
import com.devbehindyou.atomicfilemanager.data.volume.PhoneIndexSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.FileCollection
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewDialog
import com.devbehindyou.atomicfilemanager.ui.components.FileThumbnail
import com.devbehindyou.atomicfilemanager.ui.components.iconFor
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import java.util.Locale

/** A result can outlive its producer briefly while Compose starts filtering a newer input. */
private class CollectionResult(
    val scan: Any,
    val collection: FileCollection,
    val query: String,
    val snapshot: PhoneIndexSnapshot,
    val files: List<FileNode>,
)

@Composable
fun CategoryScreen(
    collection: FileCollection,
    roots: List<FileNodeId>,
    index: PhoneFileIndex,
    onBack: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val app = LocalContext.current.applicationContext as AtomicApp
    val settings by app.container.settingsRepository.settings.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    val scan = remember(index, roots, refresh) { Any() }
    var snapshot by remember(scan) { mutableStateOf(PhoneIndexSnapshot()) }
    var scanFailed by remember(scan) { mutableStateOf(false) }
    var query by rememberSaveable(collection) { mutableStateOf("") }
    var searchVisible by rememberSaveable(collection) { mutableStateOf(false) }
    var preview by remember(scan, collection) { mutableStateOf<FileNode?>(null) }
    LaunchedEffect(scan) {
        if (roots.isEmpty()) return@LaunchedEffect
        index.scan(roots, refresh > 0)
            .catch { failure ->
                if (failure is CancellationException || failure !is Exception) throw failure
                scanFailed = true
            }.collect { snapshot = it }
    }
    val result by produceState<CollectionResult?>(null, scan, snapshot, collection, query) {
        val source = snapshot
        val files =
            withContext(Dispatchers.Default) {
                source.files
                    .filter { collection.matches(it) && it.name.contains(query, ignoreCase = true) }
                    .sortedByDescending { it.modifiedAt }
            }
        value = CollectionResult(scan, collection, query, source, files)
    }
    val current = result?.takeIf { it.scan === scan && it.collection == collection && it.query == query }
    val files = current?.files.orEmpty()
    val settled = current?.snapshot === snapshot
    val hasRoots = roots.isNotEmpty()
    Column(
        Modifier.fillMaxSize().background(Atomic.colors.background)
            .statusBarsPadding().navigationBarsPadding().testTag("category_screen"),
    ) {
        AtomicPushedHeader(
            onBack = onBack,
            title = collection.title,
            actions = {
                if (hasRoots) {
                    AtomicIconButton(
                        if (searchVisible) AtomicIcons.Close else AtomicIcons.Search,
                        if (searchVisible) "Close collection search" else "Search this collection",
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) query = ""
                        },
                    )
                    AtomicIconButton(AtomicIcons.Refresh, "Refresh category", onClick = { refresh++ })
                }
            },
        )
        CategoryFiles(files, settings.fileViewMode, onOpen = { preview = it }) {
            if (hasRoots) {
                Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
                    AtomicText("${collection.title} across accessible storage", AtomicTextRole.BodySecondary)
                    if (searchVisible) {
                        AtomicTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = "Search ${collection.title.lowercase()}",
                            placeholder = "Name contains…",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CategoryFeedback(
                        scanFailed = scanFailed,
                        scanning = !snapshot.complete || !settled,
                        unreadable = snapshot.unreadable,
                        visibleCount = files.size,
                        query = query,
                        onRetry = { refresh++ },
                    )
                }
            } else {
                CategoryMessage(
                    title = "This collection needs storage access",
                    detail = "Allow access to find ${collection.title.lowercase()} in shared storage.",
                    action = "Review access",
                    onAction = onGrantAccess,
                )
            }
        }
    }
    preview?.let { FilePreviewDialog(node = it, onDismiss = { preview = null }, gallery = files) }
}

@Composable
private fun CategoryFeedback(
    scanFailed: Boolean,
    scanning: Boolean,
    unreadable: Int,
    visibleCount: Int,
    query: String,
    onRetry: () -> Unit,
) {
    if (unreadable > 0) {
        val folders = if (unreadable == 1) "folder was" else "folders were"
        AtomicText("$unreadable protected or unavailable $folders skipped.", AtomicTextRole.BodySecondary)
    }
    when {
        scanFailed ->
            CategoryMessage(
                title = "This collection could not load",
                detail = "Try again to check the available storage.",
                action = "Try again",
                onAction = onRetry,
            )
        scanning -> {
            val label = if (visibleCount == 0) "Loading this collection" else "$visibleCount files found so far"
            AtomicLoading(label)
        }
        visibleCount == 0 ->
            CategoryMessage(
                title =
                    when {
                        query.isNotBlank() -> "No matching files"
                        unreadable > 0 -> "No files found in readable folders"
                        else -> "Nothing here yet"
                    },
                detail = if (query.isNotBlank()) "Try another name." else "Choose another collection to keep browsing.",
            )
    }
}

/** Shared scroll surface keeps the caption and recovery actions reachable with large text. */
@Composable
internal fun CategoryFiles(
    files: List<FileNode>,
    mode: FileViewMode,
    onOpen: (FileNode) -> Unit,
    header: @Composable () -> Unit,
) {
    val width = LocalConfiguration.current.screenWidthDp
    val grid = mode == FileViewMode.GRID
    val margins =
        when {
            width <= 380 -> AtomicSpacing.s16
            width < 760 -> AtomicSpacing.screenMargin
            width < 1120 -> AtomicSpacing.s24
            width < 1400 -> 40.dp
            else -> AtomicSpacing.s56
        }
    val columns =
        when {
            !grid -> GridCells.Fixed(1)
            width < 760 -> GridCells.Fixed(2)
            else -> GridCells.Adaptive(150.dp)
        }
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize().testTag(if (grid) "category_grid" else "category_list"),
        contentPadding =
            PaddingValues(start = margins, end = margins, top = AtomicSpacing.s12, bottom = AtomicSpacing.s24),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(if (grid) AtomicSpacing.s12 else 0.dp),
    ) {
        item(key = "category_header", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(bottom = if (grid) AtomicSpacing.s4 else AtomicSpacing.s12)) { header() }
        }
        items(files, key = { it.id.raw }) { file ->
            val meta = categoryFileMeta(file, includeDate = !grid && width >= 760)
            val fileModifier = Modifier.testTag("category_file_${file.id.raw}")
            if (grid) {
                CategoryFileTile(file, meta, fileModifier) { onOpen(file) }
            } else {
                AtomicFileRow(
                    name = file.name,
                    meta = meta,
                    icon = iconFor(file),
                    leading = { FileThumbnail(file) },
                    onClick = { onOpen(file) },
                    hidden = file.isHidden,
                    modifier = fileModifier,
                )
            }
        }
    }
}

@Composable
private fun CategoryFileTile(
    file: FileNode,
    meta: String,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(AtomicShape.sm).background(Atomic.colors.surfaceCard)
            .clickable(role = Role.Button, onClick = onOpen).padding(AtomicSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        Box(
            Modifier.fillMaxWidth().height(104.dp).clip(AtomicShape.sm).background(Atomic.colors.surfaceInset),
            contentAlignment = Alignment.Center,
        ) {
            FileThumbnail(file, size = 104.dp)
        }
        AtomicText(file.name, AtomicTextRole.Name, maxLines = 2)
        AtomicText(meta, AtomicTextRole.MonoMeta, maxLines = 1)
    }
}

@Composable
private fun CategoryMessage(
    title: String,
    detail: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(vertical = 36.dp, horizontal = AtomicSpacing.s10)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AtomicText(title, AtomicTextRole.DisplayCard, textAlign = TextAlign.Center)
        AtomicText(detail, AtomicTextRole.BodySecondary, textAlign = TextAlign.Center)
        if (action != null && onAction != null) AtomicButton(action, onClick = onAction)
    }
}

private fun categoryFileMeta(
    file: FileNode,
    includeDate: Boolean,
): String {
    val type =
        file.name.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT) ?: "File"
    val size = if (file.size < 0) "Unknown size" else FileUtils.formatBytes(file.size)
    val summary = "$type · $size"
    return if (includeDate && file.modifiedAt > 0) "$summary · ${FileUtils.formatDate(file.modifiedAt)}" else summary
}
