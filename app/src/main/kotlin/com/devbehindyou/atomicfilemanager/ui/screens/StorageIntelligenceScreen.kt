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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicStatTile
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicWarningBox
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicConfirmSheet
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsageRepository
import com.devbehindyou.atomicfilemanager.data.volume.StorageVolumes
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.domain.usecase.CleanupRules
import com.devbehindyou.atomicfilemanager.domain.usecase.StorageAnalyzerUseCase
import com.devbehindyou.atomicfilemanager.ui.components.FileThumbnail
import com.devbehindyou.atomicfilemanager.ui.components.iconFor
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Wording for the cleanup confirm; pure so it is unit-tested. */
internal object CleanupText {
    fun confirmBody(
        bytes: Long,
        retentionDays: Int,
    ): String =
        "You get ${FileUtils.formatBytes(bytes)} back once they leave the Trash. " +
            "You can restore them for $retentionDays days; after that they are deleted for good."
}

private val StorageAnalysisCategory.label: String
    get() =
        when (this) {
            StorageAnalysisCategory.LARGE_FILES -> "Large files"
            StorageAnalysisCategory.DUPLICATE_FILES -> "Duplicates"
            StorageAnalysisCategory.EMPTY_FOLDERS -> "Empty folders"
            StorageAnalysisCategory.TEMP_AND_CACHE -> "Temp & cache"
            StorageAnalysisCategory.OLD_SCREENSHOTS -> "Old screenshots"
            StorageAnalysisCategory.OLD_DOWNLOADS -> "Old downloads"
            StorageAnalysisCategory.INSTALLED_APKS -> "Installed APKs"
        }

private fun StorageAnalysisCategory.emptyMessage(): String =
    when (this) {
        StorageAnalysisCategory.LARGE_FILES -> "No files over 50 MB."
        StorageAnalysisCategory.DUPLICATE_FILES -> "No duplicate files found."
        StorageAnalysisCategory.EMPTY_FOLDERS -> "No empty folders found."
        StorageAnalysisCategory.TEMP_AND_CACHE -> "No temporary or cache files found."
        StorageAnalysisCategory.OLD_SCREENSHOTS -> "No screenshots older than ${CleanupRules.SCREENSHOT_AGE_DAYS} days."
        StorageAnalysisCategory.OLD_DOWNLOADS -> "No large downloads older than ${CleanupRules.DOWNLOAD_AGE_DAYS} days."
        StorageAnalysisCategory.INSTALLED_APKS -> "No install files for apps already on this phone."
    }

/**
 * Storage analysis and smart cleanup (ATOMIC_UI_PLAN.md §7.4, ALL_IN_ONE_PLAN.md 2.6): what could
 * be freed, one chip per card with the reason it suggests its files, selectable rows, and a confirm
 * sheet that names the count and size first. Only safe cards (empty folders, temp files) start
 * selected. Cleanup moves files to the Trash, so it can be undone.
 */
@Composable
fun StorageIntelligenceScreen(
    rootId: FileNodeId,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFile: ((FileNode) -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as AtomicApp
    val settings by app.container.settingsRepository.settings.collectAsState()
    val volumes by produceState<List<StorageVolumeInfo>?>(initialValue = null, key1 = app) {
        value = withContext(Dispatchers.IO) { StorageVolumes.enumerate(app) }
    }
    StorageIntelligenceContent(
        rootId = rootId,
        analyzer = app.container.storageAnalyzerUseCase,
        usageRepository = app.container.storageUsageRepository,
        volumes = volumes.orEmpty(),
        volumesReady = volumes != null,
        finished = app.container.operationQueue.finished,
        retentionDays = settings.trashRetentionDays,
        onTrash = { targets ->
            app.container.operationQueue.runAndAwait(
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.TRASH,
                    sources = targets.map { it.id },
                    destination = null,
                    options = OperationOptions(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        onOpenFile = onOpenFile,
    )
}

@Composable
internal fun StorageIntelligenceContent(
    rootId: FileNodeId,
    analyzer: StorageAnalyzerUseCase,
    usageRepository: StorageUsageRepository,
    volumes: List<StorageVolumeInfo>,
    finished: Flow<OperationSnapshot>,
    retentionDays: Int,
    onTrash: suspend (List<FileNode>) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFile: ((FileNode) -> Unit)? = null,
    volumesReady: Boolean = true,
) {
    BackHandler(onBack = onNavigateBack)
    val coroutineScope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current

    var isScanning by remember(rootId) { mutableStateOf(false) }
    var fileTypesRunning by remember(rootId) { mutableStateOf(false) }
    var scanJob by remember(rootId) { mutableStateOf<Job?>(null) }
    var scanError by remember(rootId) { mutableStateOf(false) }
    var scannedFilesCount by remember(rootId) { mutableIntStateOf(0) }
    var currentCategory by rememberSaveable { mutableStateOf(StorageAnalysisCategory.LARGE_FILES) }
    var analysisResult by remember(rootId) { mutableStateOf(StorageAnalysisResult()) }
    var scannedAt by remember(rootId) { mutableStateOf<Long?>(null) }
    var selected by remember(rootId) { mutableStateOf(emptySet<String>()) }
    var showConfirmDelete by remember(rootId) { mutableStateOf(false) }
    val fileTypes =
        rememberStorageUsageAnalysis(
            volumes,
            usageRepository,
            finished,
            allowScan = !isScanning && volumesReady,
            onRunningChange = { fileTypesRunning = it },
            onRequest = { !isScanning },
            analysisKey = rootId,
        )

    fun startScan() {
        if (isScanning || fileTypesRunning) return
        isScanning = true
        scanError = false
        scannedFilesCount = 0
        scanJob =
            coroutineScope.launch {
                try {
                    analyzer.analyze(rootId).collect { progress ->
                        scannedFilesCount = progress.scannedFilesCount
                        if (progress.isComplete) {
                            analysisResult = progress.result ?: StorageAnalysisResult(isPartial = true)
                            scannedAt = System.currentTimeMillis()
                            selected = StorageCleanupSelection.initialSelection(analysisResult, currentCategory)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    scanError = true
                } finally {
                    isScanning = false
                }
            }
    }

    DisposableEffect(owner, rootId) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) scanJob?.cancel()
            }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            scanJob?.cancel()
        }
    }

    LaunchedEffect(rootId) {
        // A recent result is shown again at once; Rescan walks the storage on request (§16.2 H6).
        val last = analyzer.lastAnalysis(rootId)
        if (last != null) {
            analysisResult = last.result
            scannedAt = last.scannedAt
            selected = StorageCleanupSelection.initialSelection(last.result, currentCategory)
        }
    }

    val toggle: (FileNode) -> Unit = { node ->
        selected = if (node.id.raw in selected) selected - node.id.raw else selected + node.id.raw
    }
    val selectedNodes = StorageCleanupSelection.selectedNodes(analysisResult, currentCategory, selected)

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("storage_intelligence_screen"),
    ) {
        AtomicPushedHeader(
            onBack = onNavigateBack,
            title = "Analysis",
            actions = {
                AtomicIconButton(
                    AtomicIcons.Refresh,
                    if (scannedAt == null) "Analyze storage" else "Rescan storage",
                    onClick = { if (!isScanning) startScan() },
                    enabled = !isScanning && !fileTypesRunning,
                    modifier = Modifier.testTag("analyze_cleanup_button"),
                )
            },
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(AtomicSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            item(key = "file-types") {
                StorageUsagePanel(fileTypes.update, onRefresh = fileTypes.start, allowRefresh = fileTypes.enabled)
            }
            item { AtomicSectionLabel("Cleanup analysis") }
            item {
                if (isScanning) {
                    AtomicLoading("Scanning · $scannedFilesCount files")
                } else if (scannedAt != null) {
                    AtomicStatTile(
                        value = FileUtils.formatBytes(analysisResult.totalPotentialSavingsBytes),
                        caption =
                            "You could free · ${analysisResult.scannedFilesCount} files scanned" +
                                AnalysisAge.suffix(scannedAt, System.currentTimeMillis()),
                        featured = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
                    ) {
                        AtomicText(
                            if (scanError) {
                                "Cleanup analysis could not finish. Try again."
                            } else {
                                "Cleanup hasn't been analyzed. Start a scan when you need suggestions."
                            },
                            AtomicTextRole.BodySecondary,
                            Modifier.testTag("cleanup_unscanned"),
                        )
                        AtomicButton(
                            "Analyze storage",
                            onClick = ::startScan,
                            enabled = !fileTypesRunning,
                            modifier = Modifier.fillMaxWidth().testTag("start_cleanup_analysis"),
                        )
                    }
                }
            }
            if (analysisResult.isPartial && !isScanning) {
                item {
                    AtomicWarningBox(
                        title = "Partial scan",
                        body = "Some folders couldn't be read or were too deep. Results may be incomplete.",
                    )
                }
            }
            if (scanError && scannedAt != null && !isScanning) {
                item {
                    AtomicWarningBox("Analysis stopped", "Previous cleanup results are shown. Analyze again to update.")
                }
            }
            if (scannedAt != null || isScanning) {
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                    ) {
                        StorageAnalysisCategory.entries.forEach { category ->
                            AtomicChip(
                                label = "${category.label} ${StorageCleanupSelection.count(analysisResult, category)}",
                                selected = currentCategory == category,
                                onSelectedChange = {
                                    currentCategory = category
                                    selected = StorageCleanupSelection.initialSelection(analysisResult, category)
                                },
                            )
                        }
                    }
                }
            }
            if (scannedAt != null || isScanning) {
                item {
                    AtomicText(CleanupRules.reason(currentCategory), AtomicTextRole.BodySecondary)
                }
            }
            if (isScanning) {
                item { AtomicEmptyState(message = "Results appear when the scan finishes.") }
            } else if (scannedAt != null) {
                categoryItems(
                    result = analysisResult,
                    category = currentCategory,
                    selected = selected,
                    onToggle = toggle,
                    onSelectionChange = { selected = it },
                    onOpenFile = onOpenFile,
                )
            }
        }

        if (selectedNodes.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Atomic.colors.background)) {
                AtomicDivider(strong = true)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s10),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                ) {
                    AtomicText(
                        "${selectedNodes.size} selected · ${FileUtils.formatBytes(
                            StorageCleanupSelection.bytes(selectedNodes),
                        )}",
                        AtomicTextRole.MonoLabel,
                        modifier = Modifier.weight(1f),
                    )
                    AtomicButton("Clear", onClick = { selected = emptySet() }, variant = AtomicButtonVariant.Text)
                    AtomicButton(
                        "Move to Trash",
                        onClick = { showConfirmDelete = true },
                        variant = AtomicButtonVariant.Destructive,
                        enabled = !isScanning && !fileTypesRunning,
                    )
                }
            }
        }
    }

    if (showConfirmDelete) {
        val targets = selectedNodes
        AtomicConfirmSheet(
            label = "Cleanup",
            headline = "Move ${targets.size} items to Trash?",
            body = CleanupText.confirmBody(StorageCleanupSelection.bytes(targets), retentionDays),
            confirmLabel = "Move ${targets.size} to Trash",
            destructive = true,
            onConfirm = {
                coroutineScope.launch {
                    onTrash(targets)
                    showConfirmDelete = false
                    selected = emptySet()
                    analyzer.invalidate(rootId)
                    analysisResult = StorageAnalysisResult()
                    scannedAt = null
                }
            },
            onDismiss = { showConfirmDelete = false },
        )
    }
}

private fun LazyListScope.categoryItems(
    result: StorageAnalysisResult,
    category: StorageAnalysisCategory,
    selected: Set<String>,
    onToggle: (FileNode) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onOpenFile: ((FileNode) -> Unit)?,
) {
    val nodes = StorageCleanupSelection.nodesIn(result, category)
    if (nodes.isEmpty()) {
        item { AtomicEmptyState(message = category.emptyMessage()) }
        return
    }
    if (category == StorageAnalysisCategory.DUPLICATE_FILES) {
        item {
            AtomicSectionLabel(
                "${result.duplicateGroups.size} groups",
                actionLabel = "Keep one of each",
                onAction = { onSelectionChange(StorageCleanupSelection.allButFirstCopy(result.duplicateGroups)) },
            )
        }
        result.duplicateGroups.forEach { group ->
            item(key = "group:${group.sha256}") {
                AtomicText(
                    "${FileUtils.formatBytes(group.sizeBytes)} each · ${group.items.size} copies",
                    AtomicTextRole.MonoLabel,
                    color = Atomic.colors.accentText,
                    modifier = Modifier.padding(top = AtomicSpacing.s8),
                )
            }
            items(group.items, key = { "dup:${group.sha256}:${it.id.raw}" }) { node ->
                val first = node == group.items.first()
                CleanupRow(
                    node,
                    selected,
                    onToggle,
                    onOpenFile,
                    note = if (first) "Original · kept by Keep one" else null,
                )
            }
        }
        return
    }
    item {
        AtomicSectionLabel(
            "${nodes.size} ${category.label.lowercase()}",
            actionLabel = if (nodes.all { it.id.raw in selected }) "Select none" else "Select all",
            onAction = { onSelectionChange(StorageCleanupSelection.toggleAll(selected, nodes)) },
        )
    }
    items(nodes, key = { it.id.raw }) { node -> CleanupRow(node, selected, onToggle, onOpenFile, note = null) }
}

@Composable
private fun CleanupRow(
    node: FileNode,
    selected: Set<String>,
    onToggle: (FileNode) -> Unit,
    onOpenFile: ((FileNode) -> Unit)?,
    note: String?,
) {
    val path = node.id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme)
    AtomicFileRow(
        name = node.name,
        meta =
            listOfNotNull(
                if (node.isDirectory) null else FileUtils.formatBytes(node.size),
                note,
                path,
            ).joinToString(" · "),
        icon = iconFor(node),
        leading = { FileThumbnail(node) },
        onClick = { onToggle(node) },
        selectionMode = true,
        selected = node.id.raw in selected,
        trailing =
            if (onOpenFile != null) {
                {
                    AtomicIconButton(
                        AtomicIcons.FolderOpen,
                        "Show ${node.name} in Files",
                        onClick = { onOpenFile(node) },
                    )
                }
            } else {
                null
            },
    )
}

/** "· scanned 4 min ago" under the total; pure so it is unit-tested. */
internal object AnalysisAge {
    private const val MINUTE_MILLIS = 60_000L

    fun suffix(
        scannedAt: Long?,
        now: Long,
    ): String {
        if (scannedAt == null) return ""
        val minutes = (now - scannedAt).coerceAtLeast(0) / MINUTE_MILLIS
        return if (minutes < 1) " · scanned just now" else " · scanned $minutes min ago"
    }
}
