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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicFileType
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicFileTypeColors
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicCategoryTile
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicWarningBox
import com.devbehindyou.atomicfilemanager.domain.model.FileCategory
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.startup.StorageReadinessContent
import com.devbehindyou.atomicfilemanager.ui.startup.StorageReadinessState
import com.devbehindyou.atomicfilemanager.ui.startup.StorageSnapshot

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
    onViewOpenedHistory: (() -> Unit)? = null,
    onManagePinnedLocations: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtomicSpacing.screenMargin, vertical = AtomicSpacing.s16)
                .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s24),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
            AtomicText("A place for everything.", AtomicTextRole.DisplayPushed)
            AtomicText("Your files, right where you need them.", AtomicTextRole.BodySecondary)
        }
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

        val primaryVolume = volumes.firstOrNull { it.type == StorageType.INTERNAL_SHARED } ?: volumes.firstOrNull()
        primaryVolume?.let { volume -> VolumeCard(volume, onBrowse = { onNavigateToVolume(volume) }) }

        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
            AtomicSectionLabel("Browse by type", actionLabel = "See storage", onAction = onOpenStorageDetails)
            CategoryGrid(
                tiles =
                    categoryTiles(
                        onCategory = onNavigateToCategory,
                        onPrivate = onOpenPrivateFiles,
                        privateLocked = privateLocked,
                    ),
            )
        }

        HomeShortcuts(
            onOpenFolder = onNavigateToFolder,
            onExploreStorage = onOpenStorageDetails,
            onViewOpenedHistory = onViewOpenedHistory,
            onManagePinnedLocations = onManagePinnedLocations,
        )

        val additionalVolumes = volumes.filter { it.id != primaryVolume?.id }
        if (additionalVolumes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
                AtomicSectionLabel("Connected storage")
                additionalVolumes.forEach { volume -> VolumeCard(volume, onBrowse = { onNavigateToVolume(volume) }) }
            }
        }

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
    HomeStorageCard(volume, onBrowse)
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
        HomeTile("Documents", "PDF · DOCX", AtomicIcons.Document, "category_document") {
            onCategory(FileCategory.DOCUMENT)
        },
        HomeTile("Archives", "ZIP · 7Z", AtomicIcons.Archive, "category_archive") {
            onCategory(FileCategory.ARCHIVE)
        },
        HomeTile("Downloads", "Folder", AtomicIcons.Download, "category_download") {
            onCategory(FileCategory.DOWNLOAD)
        },
        HomeTile("APKs", "Installers", AtomicIcons.Apk, "category_apk") { onCategory(FileCategory.APK) },
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
        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s10)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s10)) {
                    row.forEach { tile ->
                        AtomicCategoryTile(
                            label = tile.label,
                            meta = tile.meta,
                            icon = tile.icon,
                            onClick = tile.onClick,
                            inverted = tile.inverted,
                            tint = Color(AtomicFileTypeColors.icon(categoryColor(tile.testTag), Atomic.colors.isDark)),
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

private fun categoryColor(tag: String): AtomicFileType =
    when (tag) {
        "category_image", "category_download" -> AtomicFileType.IMAGE
        "category_video" -> AtomicFileType.VIDEO
        "category_audio" -> AtomicFileType.AUDIO
        "category_document" -> AtomicFileType.DOCUMENT
        "category_archive" -> AtomicFileType.ARCHIVE
        "category_apk" -> AtomicFileType.APK
        else -> AtomicFileType.GENERIC
    }
