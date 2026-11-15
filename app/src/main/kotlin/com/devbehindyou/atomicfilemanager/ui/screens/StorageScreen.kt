package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.navigation.StorageToolPage
import com.devbehindyou.atomicfilemanager.data.volume.storageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlin.math.roundToInt

/** The overview renders the supplied volume snapshot. Scans start only from Analysis. */
@Composable
fun StorageScreen(
    volumes: List<StorageVolumeInfo>,
    onBrowseVolume: (StorageVolumeInfo) -> Unit,
    onOpenStorageIntelligence: (FileNodeId) -> Unit,
    onOpenTool: (StorageToolPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val width = LocalConfiguration.current.screenWidthDp
    val horizontalMargin =
        when {
            width <= 380 -> 16.dp
            width < 760 -> 20.dp
            width < 1120 -> 24.dp
            width < 1400 -> 40.dp
            else -> 56.dp
        }
    val cardPadding = if (width < 760) 20.dp else 24.dp
    val capacity = remember(volumes) { storageCapacity(volumes) }
    val mountedNames = remember(volumes) { volumes.filter { it.isMounted }.map { it.label }.distinct() }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalMargin)
            .padding(bottom = AtomicSpacing.s24)
            .testTag("storage_screen"),
    ) {
        StorageCapacityCard(capacity, mountedNames, width, cardPadding)
        StorageSection("Your storage") {
            StorageVolumeCards(volumes, width, onBrowseVolume)
        }
        StorageSection("Understand your space") {
            StorageAnalysisCard(width, cardPadding) {
                val primaryRoot =
                    volumes.firstOrNull { it.type == StorageType.INTERNAL_SHARED }?.rootNodeId
                        ?: volumes.firstNotNullOfOrNull { it.rootNodeId }
                        ?: FileNodeId.file(
                            Environment.getExternalStorageDirectory()?.absolutePath ?: context.filesDir.absolutePath,
                        )
                onOpenStorageIntelligence(primaryRoot)
            }
        }
        StorageSection("More storage tools") {
            StorageToolList(width, onOpenTool)
        }
    }
}

@Composable
private fun StorageVolumeCards(
    volumes: List<StorageVolumeInfo>,
    width: Int,
    onBrowse: (StorageVolumeInfo) -> Unit,
) {
    if (volumes.isEmpty()) {
        AtomicText("No storage locations available.", AtomicTextRole.BodySecondary)
    } else if (width >= 1120) {
        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16)) {
            volumes.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s16)) {
                    pair.forEachIndexed { index, volume ->
                        Box(Modifier.weight(if (index == 0) 1.15f else 1f)) {
                            HomeStorageCard(volume, onBrowse = { onBrowse(volume) })
                        }
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16)) {
            volumes.forEach { volume -> HomeStorageCard(volume, onBrowse = { onBrowse(volume) }) }
        }
    }
}

@Composable
private fun StorageSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 26.dp),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
    ) {
        AtomicText(title, AtomicTextRole.DisplayCard, Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun StorageCapacityCard(
    capacity: StorageCapacity,
    mountedNames: List<String>,
    width: Int,
    cardPadding: Dp,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Atomic.colors.surfaceCard, AtomicShape.lg)
            .padding(cardPadding)
            .testTag("storage_capacity_summary"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
    ) {
        AtomicText("All connected storage", AtomicTextRole.DisplayCard, Modifier.semantics { heading() })
        if (capacity.totalBytes > 0) {
            val mobile = width < 760
            val wide = width >= 1120
            val ringSize =
                when {
                    wide -> 144.dp
                    mobile -> 100.dp
                    else -> 112.dp
                }
            val stroke =
                when {
                    wide -> 14.dp
                    mobile -> 10.dp
                    else -> 11.dp
                }
            val gap =
                when {
                    wide -> 32.dp
                    mobile -> 16.dp
                    else -> 20.dp
                }
            if (width <= 380) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    StorageCapacityRing(capacity.fraction, ringSize, stroke, mobile)
                    StorageCapacityValues(capacity, mountedNames, mobile)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    StorageCapacityRing(capacity.fraction, ringSize, stroke, mobile)
                    Box(Modifier.weight(1f)) { StorageCapacityValues(capacity, mountedNames, mobile) }
                }
            }
        } else {
            AtomicText(
                if (capacity.mountedVolumes == 0) "No mounted storage." else "Capacity unavailable.",
                AtomicTextRole.BodySecondary,
            )
        }
        if (capacity.unknownVolumes > 0) {
            AtomicText(
                if (capacity.unknownVolumes == 1) {
                    "Capacity unavailable for 1 mounted volume."
                } else {
                    "Capacity unavailable for ${capacity.unknownVolumes} mounted volumes."
                },
                AtomicTextRole.MonoMeta,
            )
        }
    }
}

@Composable
private fun StorageCapacityRing(
    fraction: Float,
    diameter: Dp,
    strokeWidth: Dp,
    mobile: Boolean,
) {
    val colors = Atomic.colors
    val percentage = (fraction * 100).roundToInt()
    Box(
        Modifier.size(diameter)
            .clearAndSetSemantics { contentDescription = "$percentage percent used" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = Offset(stroke / 2, stroke / 2)
            val diameterInside = Size(size.width - stroke, size.height - stroke)
            drawCircle(colors.surfaceInset, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            drawArc(
                colors.accent,
                startAngle = -90f,
                sweepAngle = fraction * 360,
                useCenter = false,
                topLeft = inset,
                size = diameterInside,
                style = Stroke(stroke),
            )
        }
        Text(
            "$percentage%",
            style = AtomicTypography.name.copy(fontSize = if (mobile) 20.sp else 24.sp),
            color = colors.content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageCapacityValues(
    capacity: StorageCapacity,
    mountedNames: List<String>,
    mobile: Boolean,
) {
    val colors = Atomic.colors
    val total = FileUtils.formatBytes(capacity.totalBytes)
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
        Text(
            text =
                buildAnnotatedString {
                    append(total.substringBeforeLast(' '))
                    append(" ")
                    withStyle(SpanStyle(fontSize = 16.sp)) {
                        append(total.substringAfterLast(' '))
                        append(" total")
                    }
                },
            modifier = Modifier.testTag("storage_capacity_total"),
            style =
                AtomicTypography.displayTitle.copy(
                    fontSize = if (mobile) 28.sp else 38.sp,
                    lineHeight = if (mobile) 34.sp else 46.sp,
                    letterSpacing = (-0.7).sp,
                ),
            color = colors.content,
        )
        AtomicText(mountedNames.joinToString(" + "), AtomicTextRole.BodySecondary)
        FlowRow(
            modifier = Modifier.padding(top = AtomicSpacing.s12),
            horizontalArrangement = Arrangement.spacedBy(if (mobile) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
        ) {
            StorageLegendItem("${FileUtils.formatBytes(capacity.usedBytes)} used", colors.accent)
            StorageLegendItem("${FileUtils.formatBytes(capacity.freeBytes)} available", colors.contentSecondary)
        }
    }
}

@Composable
private fun StorageLegendItem(
    label: String,
    dotColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        Box(Modifier.size(AtomicSpacing.s8).background(dotColor, CircleShape))
        AtomicText(label, AtomicTextRole.MonoMeta, color = Atomic.colors.content)
    }
}

@Composable
private fun StorageAnalysisCard(
    width: Int,
    cardPadding: Dp,
    onOpen: () -> Unit,
) {
    val surface = Modifier.fillMaxWidth().background(Atomic.colors.surfaceCard, AtomicShape.lg).padding(cardPadding)
    if (width < 760) {
        Column(surface, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            StorageAnalysisCopy()
            StorageAnalysisButton(onOpen, Modifier.align(Alignment.CenterHorizontally))
        }
    } else {
        Row(
            surface,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s24),
        ) {
            StorageAnalysisCopy(Modifier.weight(1f))
            StorageAnalysisButton(onOpen)
        }
    }
}

@Composable
private fun StorageAnalysisCopy(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
        AtomicText("Find what’s taking up space", AtomicTextRole.DisplayCard, Modifier.semantics { heading() })
        AtomicText(
            "See file types, large files and cleanup suggestions. Analysis starts only when you ask.",
            AtomicTextRole.BodySecondary,
        )
    }
}

@Composable
private fun StorageAnalysisButton(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtomicButton("Open Analysis", onClick = onOpen, modifier = modifier.testTag("open_storage_intelligence_button"))
}

@Composable
private fun StorageToolList(
    width: Int,
    onOpen: (StorageToolPage) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Atomic.colors.surfaceCard, AtomicShape.lg)
            .padding(horizontal = if (width < 760) 16.dp else 20.dp, vertical = AtomicSpacing.s8),
    ) {
        StorageToolRow(
            "Connections",
            "Linked folders and saved network locations",
            AtomicIcons.Plug,
        ) { onOpen(StorageToolPage.CONNECTIONS) }
        StorageToolDivider()
        StorageToolRow(
            "Folder compare",
            "Review differences before syncing",
            AtomicIcons.DualPane,
        ) { onOpen(StorageToolPage.FOLDER_COMPARE) }
        StorageToolDivider()
        StorageToolRow(
            "Storage access",
            "Manage access to shared and removable storage",
            AtomicIcons.Security,
        ) { onOpen(StorageToolPage.STORAGE_ACCESS) }
        StorageToolDivider()
        StorageToolRow(
            "Shizuku Labs",
            "Optional read-only tools; setup required",
            AtomicIcons.Info,
        ) { onOpen(StorageToolPage.SHIZUKU_LABS) }
    }
}

@Composable
internal fun StorageToolDivider() {
    HorizontalDivider(color = Atomic.colors.borderHair)
}

@Composable
internal fun StorageToolRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = Atomic.colors
    Row(
        Modifier.fillMaxWidth().clip(AtomicShape.sm).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AtomicSpacing.s4, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = colors.accentText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
            AtomicText(title, AtomicTextRole.Name)
            AtomicText(subtitle, AtomicTextRole.MonoMeta)
        }
        Icon(AtomicIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.content)
    }
}
