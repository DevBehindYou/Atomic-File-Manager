package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.navigation.StorageToolPage
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo

@Composable
fun StorageToolScreen(
    page: StorageToolPage,
    onBack: () -> Unit,
    volumes: List<StorageVolumeInfo>,
    hasStorageAccess: Boolean,
    onRequestStorageAccess: () -> Unit,
    onBrowseVolume: (StorageVolumeInfo) -> Unit,
    onBrowseFolder: (FileNodeId) -> Unit,
    onCompareFolders: (FileNodeId, FileNodeId) -> Unit,
    onOpenFolderCompare: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTool: (StorageToolPage) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
    accessStatus: String? = null,
) {
    val title =
        when (page) {
            StorageToolPage.CONNECTIONS -> "Connections"
            StorageToolPage.FOLDER_COMPARE -> "Folder compare"
            StorageToolPage.STORAGE_ACCESS -> "Storage access"
            StorageToolPage.SHIZUKU_LABS -> "Shizuku Labs"
        }
    Column(modifier.fillMaxSize().testTag("storage_tool_screen")) {
        AtomicPushedHeader(onBack, title = title)
        key(page) {
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AtomicSpacing.screenMargin, vertical = AtomicSpacing.s16),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s24),
            ) {
                when (page) {
                    StorageToolPage.CONNECTIONS -> StorageConnections(onBrowseFolder, onNotify)
                    StorageToolPage.FOLDER_COMPARE -> StorageCompareTools(onOpenFolderCompare, onCompareFolders)
                    StorageToolPage.STORAGE_ACCESS ->
                        StorageAccessTools(
                            volumes = volumes,
                            hasStorageAccess = hasStorageAccess,
                            accessStatus = accessStatus,
                            onRequestStorageAccess = onRequestStorageAccess,
                            onBrowseVolume = onBrowseVolume,
                            onBrowseFolder = onBrowseFolder,
                            onOpenShizuku = { onOpenTool(StorageToolPage.SHIZUKU_LABS) },
                            onOpenFiles = onOpenFiles,
                            onNotify = onNotify,
                        )
                    StorageToolPage.SHIZUKU_LABS -> ShizukuLabsSection(onBrowseFolder)
                }
            }
        }
    }
}

@Composable
private fun StorageCompareTools(
    onOpenFolderCompare: () -> Unit,
    onCompareFolders: (FileNodeId, FileNodeId) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Atomic.colors.surfaceCard, AtomicShape.lg)
            .padding(AtomicSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
    ) {
        AtomicText("Compare first", AtomicTextRole.DisplayCard)
        AtomicText(
            "In Files, select two folders and choose Compare. Review the differences before syncing.",
            AtomicTextRole.BodySecondary,
        )
        AtomicButton(
            "Choose folders in Files",
            onClick = onOpenFolderCompare,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    SyncPairsSection(onCompareFolders)
}

@Composable
private fun StorageAccessTools(
    volumes: List<StorageVolumeInfo>,
    hasStorageAccess: Boolean,
    accessStatus: String?,
    onRequestStorageAccess: () -> Unit,
    onBrowseVolume: (StorageVolumeInfo) -> Unit,
    onBrowseFolder: (FileNodeId) -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenFiles: () -> Unit,
    onNotify: (String) -> Unit,
) {
    StorageAccessNotice()
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        AtomicSectionLabel("Device storage")
        Column(
            Modifier.fillMaxWidth()
                .background(Atomic.colors.surfaceCard, AtomicShape.lg)
                .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8),
        ) {
            StorageToolRow(
                "Shared storage",
                accessStatus ?: if (hasStorageAccess) "Allowed by Android" else "Needed to browse shared files",
                AtomicIcons.Storage,
                onClick = onRequestStorageAccess,
            )
            volumes.filter { it.isRemovable }.forEach { volume ->
                StorageToolDivider()
                StorageToolRow(
                    volume.label,
                    when {
                        !volume.isMounted -> "Not mounted. Reconnect this storage."
                        volume.rootNodeId != null -> "Available to browse"
                        accessStatus != null -> accessStatus
                        !hasStorageAccess -> "Access required"
                        else -> "Unavailable. Reconnect and try again."
                    },
                    AtomicIcons.Plug,
                    onClick = { onBrowseVolume(volume) },
                )
            }
        }
    }
    LinkedFoldersSection(onBrowseFolder, onNotify, sectionTitle = "Removable or linked folder")
    Column(
        Modifier.fillMaxWidth()
            .background(Atomic.colors.surfaceCard, AtomicShape.lg)
            .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8),
    ) {
        StorageToolRow(
            "Optional Shizuku",
            "Read-only tools. Setup required.",
            AtomicIcons.Security,
            onClick = onOpenShizuku,
        )
    }
    AtomicButton("Continue to Files", onClick = onOpenFiles)
}

@Composable
private fun StorageAccessNotice() {
    Row(
        Modifier.fillMaxWidth()
            .background(Atomic.colors.surfaceCard, AtomicShape.lg)
            .padding(AtomicSpacing.s16),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        AtomicIconTile(AtomicIcons.Security)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
        ) {
            AtomicText("Choose the access you need", AtomicTextRole.DisplayCard)
            AtomicText(
                "Android manages shared storage access. Use the folder picker to grant access to one folder.",
                AtomicTextRole.BodySecondary,
            )
        }
    }
}
