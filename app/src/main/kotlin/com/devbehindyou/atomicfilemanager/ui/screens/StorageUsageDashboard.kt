package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicProgressBar
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicFileType
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicFileTypeColors
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsagePhase
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsageRepository
import com.devbehindyou.atomicfilemanager.data.volume.StorageUsageUpdate
import com.devbehindyou.atomicfilemanager.domain.model.CategoryUsage
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

/** Recursive work requires an explicit request and ends when the visible analysis is stopped. */
@Composable
internal fun StorageUsageDashboard(
    volumes: List<StorageVolumeInfo>,
    repository: StorageUsageRepository,
    finished: Flow<OperationSnapshot>,
    allowScan: Boolean = true,
    onRunningChange: (Boolean) -> Unit = {},
) {
    val controls = rememberStorageUsageAnalysis(volumes, repository, finished, allowScan, onRunningChange)
    StorageUsagePanel(controls.update, onRefresh = controls.start, allowRefresh = controls.enabled)
}

internal data class StorageUsageControls(
    val update: StorageUsageUpdate,
    val enabled: Boolean,
    val start: () -> Unit,
)

/** Hoisted above lazy items so scrolling does not dispose the requested analysis or its result. */
@Composable
internal fun rememberStorageUsageAnalysis(
    volumes: List<StorageVolumeInfo>,
    repository: StorageUsageRepository,
    finished: Flow<OperationSnapshot>,
    allowScan: Boolean,
    onRunningChange: (Boolean) -> Unit,
    onRequest: () -> Boolean = { true },
    analysisKey: Any? = null,
): StorageUsageControls {
    val owner = LocalLifecycleOwner.current
    val scopeKey = volumes.map { "${it.id}|${it.isMounted}|${it.rootNodeId?.raw}|${it.requiresGrant}" }.sorted()
    var update by remember(repository, scopeKey, analysisKey) {
        mutableStateOf(StorageUsageUpdate(phase = StorageUsagePhase.IDLE))
    }
    var requested by remember(repository, scopeKey, analysisKey) { mutableStateOf(false) }
    // Keep an old session's cancellation callback tied to its own owner after a root change.
    val notifyRunning = remember(repository, scopeKey, analysisKey) { mutableStateOf(onRunningChange) }
    notifyRunning.value = onRunningChange
    LaunchedEffect(repository, scopeKey, owner, requested, analysisKey) {
        if (!requested) return@LaunchedEffect
        try {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    coroutineScope {
                        val invalidation = launch { finished.collect { repository.invalidate() } }
                        try {
                            repository.scan(volumes, refresh = true).collect { update = it }
                        } finally {
                            invalidation.cancelAndJoin()
                        }
                    }
                } finally {
                    requested = false
                }
            }
        } finally {
            repository.invalidate()
            if (update.phase == StorageUsagePhase.SCANNING) update = update.copy(phase = StorageUsagePhase.CANCELLED)
            notifyRunning.value(false)
        }
    }
    return StorageUsageControls(update, enabled = allowScan && !requested, start = {
        if (allowScan && !requested && onRequest()) {
            val previous = update.current?.takeIf { it.complete } ?: update.lastGood
            val observed = update.current ?: update.previousObserved
            update = StorageUsageUpdate(lastGood = previous, previousObserved = observed)
            requested = true
            notifyRunning.value(true)
        }
    })
}

@Composable
internal fun StorageUsagePanel(
    update: StorageUsageUpdate,
    onRefresh: () -> Unit,
    allowRefresh: Boolean = true,
) {
    val current = update.current
    val previous =
        update.lastGood.takeIf { update.phase != StorageUsagePhase.COMPLETE }
            ?: update.previousObserved.takeIf {
                update.phase == StorageUsagePhase.SCANNING || update.phase == StorageUsagePhase.FAILED
            }
    val files = previous ?: current
    val capacity = current?.capacity ?: previous?.capacity
    Column(
        Modifier.fillMaxWidth().testTag("storage_usage_dashboard"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
    ) {
        if (capacity == null && update.phase != StorageUsagePhase.IDLE) {
            AtomicText(
                if (update.phase == StorageUsagePhase.FAILED) {
                    "Mounted storage capacity could not be read."
                } else {
                    "Reading mounted storage capacity…"
                },
                AtomicTextRole.BodySecondary,
            )
        } else if (capacity != null) {
            StorageCapacitySummary(capacity)
        }
        AtomicSectionLabel("Readable shared files")
        AtomicText(usageStatus(update), AtomicTextRole.BodySecondary, Modifier.testTag("storage_usage_status"))
        if (previous != null) {
            val coverage = if (previous.complete) "complete" else "partial"
            val scannedAt = FileUtils.formatDate(previous.scannedAtMillis)
            AtomicText(
                "Showing the previous $coverage file breakdown from $scannedAt. It may be stale.",
                AtomicTextRole.BodySecondary,
            )
        } else if (update.phase == StorageUsagePhase.COMPLETE && files != null) {
            val cacheLabel = if (update.cached) " · cached" else ""
            AtomicText(
                "Last scanned ${FileUtils.formatDate(files.scannedAtMillis)}$cacheLabel",
                AtomicTextRole.MonoMeta,
            )
        }
        AtomicText(
            "Includes accessible files, including hidden files. Percentages use their combined size.",
            AtomicTextRole.BodySecondary,
        )
        if (files != null) {
            AtomicText(
                "${FileUtils.formatBytes(files.readableBytes)} · ${files.scannedFiles} files found",
                AtomicTextRole.Name,
                Modifier.testTag("storage_readable_total"),
            )
            StorageUsageCategory.entries.forEach { category -> UsageCategoryRow(category, files) }
            val estimate = files.copy(capacity = capacity ?: files.capacity).unattributedUsedEstimate
            AtomicText(
                if (estimate != null) {
                    "Other used space (estimate) · ${FileUtils.formatBytes(estimate)}. " +
                        "Includes apps, system data and inaccessible files; file sizes can differ from disk allocation."
                } else {
                    "Other used-space estimate unavailable: " +
                        "some capacity is unknown or file sizes exceed disk allocation."
                },
                AtomicTextRole.BodySecondary,
                Modifier.testTag("storage_unattributed_estimate"),
            )
        }
        AtomicButton(
            when (update.phase) {
                StorageUsagePhase.SCANNING -> "Scanning files…"
                StorageUsagePhase.IDLE -> "Analyze file types"
                else -> "Refresh file types"
            },
            onClick = onRefresh,
            enabled = allowRefresh && update.phase != StorageUsagePhase.SCANNING,
            variant = AtomicButtonVariant.Ghost,
            leadingIcon = AtomicIcons.Refresh,
            modifier = Modifier.fillMaxWidth().testTag("refresh_storage_usage"),
        )
    }
}

@Composable
internal fun StorageCapacitySummary(capacity: StorageCapacity) {
    val colors = Atomic.colors
    Column(
        Modifier.fillMaxWidth().background(colors.surfaceCard, AtomicShape.md)
            .border(AtomicBorder.structure, colors.borderStrong, AtomicShape.md).padding(AtomicSpacing.s16)
            .testTag("storage_capacity_summary"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicText("Internal + external storage", AtomicTextRole.MonoLabel)
        val unavailable = if (capacity.unknownVolumes > 0) " · ${capacity.unknownVolumes} capacity unavailable" else ""
        AtomicText("${capacity.mountedVolumes} mounted volumes$unavailable", AtomicTextRole.MonoMeta)
        when {
            capacity.mountedVolumes == 0 -> AtomicText("No mounted local storage.", AtomicTextRole.BodySecondary)
            capacity.totalBytes == 0L ->
                AtomicText(
                    "Capacity unavailable. Grant access or reconnect storage, then refresh.",
                    AtomicTextRole.BodySecondary,
                )
            else -> {
                val known = if (capacity.unknownVolumes > 0) " (known volumes)" else ""
                CapacityValue("Total$known", capacity.totalBytes)
                CapacityValue("Used$known", capacity.usedBytes)
                CapacityValue("Free$known", capacity.freeBytes)
                AtomicProgressBar(capacity.fraction, "${percent(capacity.fraction)} of known mounted capacity used")
                AtomicText(
                    "Filesystem usage includes areas outside the readable file breakdown.",
                    AtomicTextRole.BodySecondary,
                )
            }
        }
    }
}

@Composable
private fun CapacityValue(
    label: String,
    bytes: Long,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        AtomicText(label, AtomicTextRole.Body, Modifier.weight(1f))
        AtomicText(FileUtils.formatBytes(bytes), AtomicTextRole.MonoMeta)
    }
}

@Composable
private fun UsageCategoryRow(
    category: StorageUsageCategory,
    snapshot: StorageUsageSnapshot,
) {
    val (icon, type) =
        when (category) {
            StorageUsageCategory.IMAGES -> AtomicIcons.Image to AtomicFileType.IMAGE
            StorageUsageCategory.VIDEOS -> AtomicIcons.Video to AtomicFileType.VIDEO
            StorageUsageCategory.AUDIO -> AtomicIcons.AudioFile to AtomicFileType.AUDIO
            StorageUsageCategory.PDFS -> AtomicIcons.Pdf to AtomicFileType.PDF
            StorageUsageCategory.DOCUMENTS -> AtomicIcons.Document to AtomicFileType.DOCUMENT
            StorageUsageCategory.ARCHIVES -> AtomicIcons.Archive to AtomicFileType.ARCHIVE
            StorageUsageCategory.APKS -> AtomicIcons.Apk to AtomicFileType.APK
            StorageUsageCategory.TEXT -> AtomicIcons.Code to AtomicFileType.CODE
            StorageUsageCategory.EBOOKS -> AtomicIcons.Ebook to AtomicFileType.EBOOK
            StorageUsageCategory.FONTS -> AtomicIcons.Font to AtomicFileType.FONT
            StorageUsageCategory.OTHER -> AtomicIcons.UnknownFile to AtomicFileType.GENERIC
        }
    val usage = snapshot.categories[category] ?: CategoryUsage()
    val fraction = snapshot.fraction(category)
    Column(
        Modifier.fillMaxWidth().testTag("storage_usage_${category.name}"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            AtomicIconTile(icon)
            Column(Modifier.weight(1f)) {
                AtomicText(
                    category.label,
                    AtomicTextRole.Name,
                    color = Color(AtomicFileTypeColors.icon(type, Atomic.colors.isDark)),
                )
                AtomicText(
                    "${FileUtils.formatBytes(usage.bytes)} · ${usage.files} files · ${percent(fraction)}",
                    AtomicTextRole.MonoMeta,
                )
            }
        }
        AtomicProgressBar(fraction, "${category.label}, ${percent(fraction)} of observed shared-file bytes")
    }
}

internal fun usageStatus(update: StorageUsageUpdate): String {
    val current = update.current
    return when (update.phase) {
        StorageUsagePhase.IDLE -> "Analyze file types when you need a breakdown."
        StorageUsagePhase.SCANNING -> "Scanning files · ${current?.scannedFiles ?: 0} files found."
        StorageUsagePhase.COMPLETE ->
            "Readable-folder scan complete. System and private app data are outside this breakdown."
        StorageUsagePhase.PARTIAL ->
            buildString {
                append(
                    "Partial results · ${current?.unreadable ?: 0} unreadable/unavailable folders · " +
                        "${current?.skippedLinks ?: 0} skipped links.",
                )
                if (current?.limitReached == true) {
                    append(
                        " Scan budget reached; totals cover only the entries observed.",
                    )
                }
                if (update.changedDuringScan) append(" Files changed while scanning. Refresh to update.")
                if ((current?.capacity?.unknownVolumes ?: 0) > 0) append(" Some mounted capacity is unknown.")
            }
        StorageUsagePhase.FAILED -> "Storage usage could not be read. Refresh to retry; no empty result is assumed."
        StorageUsagePhase.NO_STORAGE -> "No mounted local storage is available. Reconnect storage, then refresh."
        StorageUsagePhase.CANCELLED -> "Scan stopped. Analyze file types again when ready."
    }
}

private fun percent(fraction: Float): String = String.format(Locale.US, "%.1f%%", fraction * 100)
