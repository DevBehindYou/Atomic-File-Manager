package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicMeter
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.METER_HIGH_THRESHOLD
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

/** Prototype volume hierarchy with real mounted volume data and no implicit storage scan. */
@Composable
internal fun HomeStorageCard(
    volume: StorageVolumeInfo,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    val featured = volume.type == StorageType.INTERNAL_SHARED && volume.isMounted
    val foreground = if (featured) colors.onAccent else colors.content
    val secondary = if (featured) foreground else colors.contentSecondary
    val used = (volume.totalBytes - volume.freeBytes).coerceIn(0L, volume.totalBytes.coerceAtLeast(0L))
    val fraction = if (volume.isMounted) used.toFloat() / volume.totalBytes.coerceAtLeast(1L) else 0f
    Column(
        modifier.fillMaxWidth().testTag("volume_item_${volume.id}")
            .clip(AtomicShape.lg)
            .background(if (featured) colors.accent else colors.surfaceCard)
            .clickable(role = Role.Button, onClick = onBrowse)
            .padding(AtomicSpacing.s22),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            Box(
                Modifier.size(AtomicSize.touchTarget).background(foreground.copy(alpha = 0.08f), AtomicShape.md),
                contentAlignment = Alignment.Center,
            ) { Icon(AtomicIcons.Storage, null, tint = foreground, modifier = Modifier.size(AtomicSize.icon)) }
            AtomicText(volume.label, AtomicTextRole.Name, Modifier.weight(1f), color = foreground)
            Icon(AtomicIcons.Forward, null, tint = foreground, modifier = Modifier.size(AtomicSize.iconSmall))
        }
        if (volume.isMounted && volume.totalBytes > 0L) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
                AtomicText(FileUtils.formatBytes(used), AtomicTextRole.DisplayTitle, color = foreground)
                AtomicText(
                    "of ${FileUtils.formatBytes(volume.totalBytes)} used",
                    AtomicTextRole.MonoMeta,
                    modifier = Modifier.weight(1f).padding(bottom = AtomicSpacing.s4),
                    color = secondary,
                )
            }
            AtomicMeter(
                fraction,
                "${volume.label}, ${(fraction * 100).toInt()} percent used",
                onAccentSurface = featured,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
                AtomicText(
                    "${FileUtils.formatBytes(volume.freeBytes)} available",
                    AtomicTextRole.MonoMeta,
                    modifier = Modifier.weight(1f),
                    color = secondary,
                )
                AtomicText(
                    if (fraction >= METER_HIGH_THRESHOLD) "Nearly full" else "Room to create",
                    AtomicTextRole.MonoMeta,
                    color = secondary,
                )
            }
        } else {
            AtomicText(
                if (volume.isMounted) "Capacity unavailable" else "Not mounted",
                AtomicTextRole.BodySecondary,
                color = secondary,
            )
        }
    }
}
