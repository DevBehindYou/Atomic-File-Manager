package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.focusRing
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.components.BreadcrumbItem

class BrowseStorageAccess(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val hasAccess: Boolean = true,
    val requestAccess: () -> Unit = {},
)

/** Resolve known storage identities without decoding provider IDs or probing filesystem paths. */
internal fun currentStorageVolume(
    volumes: List<StorageVolumeInfo>,
    currentId: FileNodeId?,
    breadcrumbs: List<BreadcrumbItem>,
): StorageVolumeInfo? {
    val mounted = volumes.filter { it.isMounted && it.rootNodeId != null }
    mounted.firstOrNull { it.rootNodeId == currentId }?.let { return it }
    if (currentId?.prefix == FileNodeId.Prefix.FILE) {
        mounted
            .filter { volume ->
                val root = volume.rootNodeId!!
                if (root.prefix != FileNodeId.Prefix.FILE) return@filter false
                val path = root.raw.trimEnd('/')
                currentId.raw == path || currentId.raw.startsWith("$path/")
            }
            .maxByOrNull { it.rootNodeId!!.raw.trimEnd('/').length }
            ?.let { return it }
    }
    return breadcrumbs.asReversed().firstNotNullOfOrNull { breadcrumb ->
        mounted.firstOrNull { it.rootNodeId?.raw == breadcrumb.path }
    }
}

/** Mounted volumes stay reachable while browsing; navigation and access grants belong to the caller. */
@Composable
internal fun BrowseStorageShortcuts(
    volumes: List<StorageVolumeInfo>,
    current: StorageVolumeInfo?,
    currentFolderId: FileNodeId,
    onOpen: (StorageVolumeInfo) -> Unit,
) {
    val available = volumes.filter { it.isMounted && (it.rootNodeId != null || it.requiresGrant) }
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8)
            .testTag("browse_storage_shortcuts"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val shortcutMaxWidth = maxWidth
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            ) {
                available.forEach { volume ->
                    key(volume.id) {
                        StorageShortcut(volume, current?.id == volume.id, Modifier.widthIn(max = shortcutMaxWidth)) {
                            onOpen(volume)
                        }
                    }
                }
            }
        }
        AtomicText(
            "Current storage: ${current?.label ?: otherStorageLabel(currentFolderId)}",
            AtomicTextRole.MonoMeta,
            modifier = Modifier.testTag("browse_current_storage"),
            maxLines = 1,
        )
    }
}

@Composable
private fun StorageShortcut(
    volume: StorageVolumeInfo,
    isCurrent: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val colors = Atomic.colors
    val foreground = if (isCurrent) colors.onAccent else colors.content
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .defaultMinSize(minHeight = AtomicSize.touchTarget)
            .focusRing(focused, colors.accentText, AtomicShape.radiusSm)
            .background(if (isCurrent) colors.accent else colors.surfaceInset, AtomicShape.sm)
            .border(AtomicBorder.control, if (isCurrent) colors.accent else colors.borderStrong, AtomicShape.sm)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = "Open storage ${volume.label}"
                selected = isCurrent
                stateDescription =
                    when {
                        volume.requiresGrant -> "Access required"
                        isCurrent -> "Current storage"
                        else -> "Available"
                    }
            }
            .padding(horizontal = AtomicSpacing.s12, vertical = AtomicSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (volume.requiresGrant) AtomicIcons.Lock else AtomicIcons.Storage,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(AtomicSize.iconSmall),
        )
        AtomicText(
            if (volume.type == StorageType.INTERNAL_SHARED) "Internal" else volume.label,
            AtomicTextRole.Body,
            color = foreground,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private fun otherStorageLabel(id: FileNodeId): String =
    when (id.prefix) {
        FileNodeId.Prefix.FILE -> "Local files"
        FileNodeId.Prefix.SAF -> "Document storage"
        FileNodeId.Prefix.MEDIA -> "Media library"
        FileNodeId.Prefix.USB -> "USB storage"
        FileNodeId.Prefix.SHIZUKU -> "Protected storage"
        FileNodeId.Prefix.SFTP, FileNodeId.Prefix.FTP, FileNodeId.Prefix.FTPS,
        FileNodeId.Prefix.SMB, FileNodeId.Prefix.WEBDAV,
        -> "Network storage"
        null -> "Other location"
    }
