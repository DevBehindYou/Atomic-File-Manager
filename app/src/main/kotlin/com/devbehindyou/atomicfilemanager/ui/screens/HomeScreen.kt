package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicCategoryTile
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicWarningBox
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicStorageCard
import com.devbehindyou.atomicfilemanager.domain.model.FileCategory
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.startup.StorageReadinessContent
import com.devbehindyou.atomicfilemanager.ui.startup.StorageReadinessState
import com.devbehindyou.atomicfilemanager.ui.startup.StorageSnapshot
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

private const val LARGE_FONT_SCALE = 1.5f
private val TWO_COLUMN_MAX = 340.dp
private val THREE_COLUMN_MAX = 560.dp
private const val WIDE_COLUMNS = 5

private class HomeTile(
    val label: String,
    val meta: String,
    val icon: ImageVector,
    val testTag: String,
    val inverted: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Home (ATOMIC_UI_PLAN.md §7.1, canvas "Home"): storage cards with a meter per volume, a category
 * grid that also holds Downloads, Private and More, and the common public folders. Search,
 * favourites and recents join in Phase 1.
 */
@Composable
fun HomeScreen(
    volumes: List<StorageVolumeInfo>,
    hasStorageAccess: Boolean,
    onNavigateToVolume: (StorageVolumeInfo) -> Unit,
    onNavigateToCategory: (FileCategory) -> Unit,
    onNavigateToFolder: (FileNodeId) -> Unit,
    onRequestStorageAccess: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPrivateFiles: () -> Unit = {},
    onOpenStorageDetails: () -> Unit = {},
    privateLocked: Boolean = false,
    storageReadiness: StorageReadinessState = StorageReadinessState.Ready(StorageSnapshot(hasStorageAccess, volumes)),
    onRetryStorage: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s16)
                .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s24),
    ) {
        StorageReadinessContent(storageReadiness, onRetryStorage)
        if (storageReadiness is StorageReadinessState.Ready && !hasStorageAccess) {
            Column(
                Modifier.testTag("permission_warning_card"),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
            ) {
                AtomicWarningBox(
                    title = "All files access is off",
                    body =
                        "Atomic File Manager needs it to browse photos, downloads and other shared files. " +
                            "Nothing leaves your phone.",
                )
                AtomicButton("Grant access", onClick = onRequestStorageAccess, modifier = Modifier.fillMaxWidth())
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16)) {
            AtomicSectionLabel("Storage", actionLabel = "Details →", onAction = onOpenStorageDetails)
            volumes.forEach { volume -> VolumeCard(volume, onBrowse = { onNavigateToVolume(volume) }) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
            AtomicSectionLabel("Categories", trailingText = "All storage")
            CategoryGrid(
                tiles =
                    categoryTiles(
                        onCategory = onNavigateToCategory,
                        onPrivate = onOpenPrivateFiles,
                        privateLocked = privateLocked,
                    ),
            )
        }

        HomeShortcuts(onOpenFolder = onNavigateToFolder)

        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
            AtomicSectionLabel("Folders")
            QuickAccessFolders(onFolderClick = onNavigateToFolder)
        }
    }
}

@Composable
private fun VolumeCard(
    volume: StorageVolumeInfo,
    onBrowse: () -> Unit,
) {
    val total = volume.totalBytes.coerceAtLeast(1L)
    val used = (volume.totalBytes - volume.freeBytes).coerceIn(0L, total)
    val fraction = used.toFloat() / total
    val percent = (fraction * 100).toInt()
    AtomicStorageCard(
        name = volume.label,
        usage =
            if (volume.isMounted) {
                "${FileUtils.formatBytes(volume.freeBytes)} free · " +
                    "${FileUtils.formatBytes(used)} / ${FileUtils.formatBytes(volume.totalBytes)}"
            } else {
                "Not mounted"
            },
        fraction = if (volume.isMounted) fraction else 0f,
        meterDescription = "${volume.label}, $percent percent used",
        onBrowse = onBrowse,
        nearlyFullLabel = "Nearly full",
        modifier = Modifier.testTag("volume_item_${volume.id}"),
    )
}

private fun categoryTiles(
    onCategory: (FileCategory) -> Unit,
    onPrivate: () -> Unit,
    privateLocked: Boolean,
): List<HomeTile> =
    listOf(
        HomeTile("Images", "JPG · PNG", AtomicIcons.Image, "category_image") { onCategory(FileCategory.IMAGE) },
        HomeTile("Videos", "MP4 · MKV", AtomicIcons.Video, "category_video") { onCategory(FileCategory.VIDEO) },
        HomeTile("Audio", "MP3 · FLAC", AtomicIcons.Audio, "category_audio") { onCategory(FileCategory.AUDIO) },
        HomeTile("Docs", "PDF · DOCX", AtomicIcons.Document, "category_document") {
            onCategory(FileCategory.DOCUMENT)
        },
        HomeTile("Archives", "ZIP · 7Z", AtomicIcons.Archive, "category_archive") {
            onCategory(FileCategory.ARCHIVE)
        },
        HomeTile("APKs", "Installers", AtomicIcons.Apk, "category_apk") { onCategory(FileCategory.APK) },
        HomeTile("Downloads", "Folder", AtomicIcons.Files, "category_download") {
            onCategory(FileCategory.DOWNLOAD)
        },
        HomeTile(
            label = "Private",
            meta = if (privateLocked) "Locked" else "App storage",
            icon = AtomicIcons.Lock,
            testTag = "app_private_storage_item",
            inverted = true,
            onClick = onPrivate,
        ),
        HomeTile("More", "PDF · Fonts", AtomicIcons.MoreHorizontal, "category_more") {
            onCategory(FileCategory.OTHER)
        },
    )

@Composable
private fun CategoryGrid(tiles: List<HomeTile>) {
    val largeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns =
            when {
                largeFont || maxWidth < TWO_COLUMN_MAX -> 2
                maxWidth < THREE_COLUMN_MAX -> 3
                else -> WIDE_COLUMNS
            }
        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
                    row.forEach { tile ->
                        AtomicCategoryTile(
                            label = tile.label,
                            meta = tile.meta,
                            icon = tile.icon,
                            onClick = tile.onClick,
                            inverted = tile.inverted,
                            modifier = Modifier.weight(1f).testTag(tile.testTag),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f).height(AtomicSpacing.s2)) }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessFolders(onFolderClick: (FileNodeId) -> Unit) {
    val context = LocalContext.current.applicationContext
    val folders by produceState(emptyList<Pair<String, FileNodeId>>(), context) {
        value = loadHomeQuickAccessFolders(context)
    }
    Column {
        folders.forEach { (name, id) ->
            AtomicFileRow(
                name = name,
                meta = "Shared storage",
                icon = AtomicIcons.Files,
                onClick = { onFolderClick(id) },
            )
        }
    }
}
