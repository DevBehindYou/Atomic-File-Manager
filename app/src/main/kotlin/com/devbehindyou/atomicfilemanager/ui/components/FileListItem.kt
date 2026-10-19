package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicCheckbox
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.data.preview.ThumbnailKind
import com.devbehindyou.atomicfilemanager.data.preview.genericThumbnailMime
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailKind
import com.devbehindyou.atomicfilemanager.domain.model.FileCollection
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.usecase.ArchiveFormats
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

private const val HIDDEN_ALPHA = 0.62f

/** Outlined type icon for a node (spec §10: one icon family, no per-type colours). */
fun iconFor(node: FileNode): ImageVector {
    val mime = node.mimeType.orEmpty().lowercase().substringBefore(';').trim()
    val visualKind = thumbnailKind(node)
    return when {
        node.isDirectory -> AtomicIcons.Files
        visualKind == ThumbnailKind.IMAGE -> AtomicIcons.Image
        visualKind == ThumbnailKind.VIDEO -> AtomicIcons.Video
        mime.startsWith("audio/") -> AtomicIcons.AudioFile
        mime == "application/vnd.android.package-archive" -> AtomicIcons.Apk
        mime.contains("zip") || mime.contains("compressed") || mime.contains("archive") -> AtomicIcons.Archive
        genericThumbnailMime(mime) ->
            when {
                FileCollection.AUDIO.matches(node) -> AtomicIcons.AudioFile
                FileCollection.APKS.matches(node) -> AtomicIcons.Apk
                FileCollection.ARCHIVES.matches(node) -> AtomicIcons.Archive
                else -> AtomicIcons.Document
            }
        else -> AtomicIcons.Document
    }
}

/** Star state for accessible row actions; null omits the action. */
class FavouriteToggle(
    val isFavourite: Boolean,
    val onToggle: () -> Unit,
)

/** Mono meta line: "42 items · 2026-10-02 21:44" for folders, "214.0 KB · 2026-10-04 08:11" for files. */
fun metaFor(node: FileNode): String {
    val date = FileUtils.formatDate(node.modifiedAt)
    return if (node.isDirectory) {
        val items = node.childCount?.let { "$it item${if (it == 1) "" else "s"}" } ?: "Folder"
        "$items · $date"
    } else {
        val size = FileUtils.formatBytes(node.size)
        if (node.isHidden) "$size · Hidden" else "$size · $date"
    }
}

/**
 * File or folder row (ATOMIC_UI_PLAN.md §7.2): icon tile, name as stored, mono meta line, and
 * "→" for folders. Long press selects; the selection toolbar and accessibility actions expose file actions. Selected rows get a
 * 2 dp accent border and a checkbox; hidden files are faded. TalkBack gets the same actions as
 * custom actions. [isSelectionMode] is derived by the caller from `selectedIds.isNotEmpty()`.
 */
@Composable
fun FileListItem(
    node: FileNode,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onShowDetails: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExtract: (() -> Unit)? = null,
    onCompress: (() -> Unit)? = null,
    onQuickPeek: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    favourite: FavouriteToggle? = null,
) {
    val colors = Atomic.colors
    val isArchive = ArchiveFormats.kindOf(node.name) != null || node.mimeType?.contains("zip") == true
    val isMedia = thumbnailKind(node) != null
    val actions =
        listOfNotNull(
            if (isArchive && onExtract != null) "Extract" to onExtract else null,
            if (isMedia && onQuickPeek != null) "Quick preview" to onQuickPeek else null,
            if (onCompress != null) "Compress to ZIP" to onCompress else null,
            favourite?.let {
                (if (it.isFavourite) "Remove from favourites" else "Add to favourites") to it.onToggle
            },
            if (onHide != null) "Hide" to onHide else null,
            "Details" to onShowDetails,
            "Rename" to onRename,
            "Delete" to onDelete,
        )
    val frame =
        if (isSelected) {
            Modifier
                .background(colors.surfaceCard, AtomicShape.sm)
                .border(AtomicBorder.selected, colors.accent, AtomicShape.sm)
        } else {
            Modifier
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(min = AtomicSize.touchTarget)
                    .then(frame)
                    .combinedClickable(
                        // The caller's onClick toggles in selection mode; long-press may select a range.
                        onClick = onClick,
                        onLongClick = onToggleSelect,
                    ).semantics {
                        contentDescription =
                            buildString {
                                append(if (node.isDirectory) "Folder " else "File ")
                                append(node.name)
                                if (!node.isDirectory) {
                                    append(", ")
                                    append(FileUtils.formatBytes(node.size))
                                }
                            }
                        if (isSelectionMode) {
                            selected = isSelected
                            role = Role.Checkbox
                        }
                        customActions =
                            actions.map { (label, action) ->
                                CustomAccessibilityAction(label) {
                                    action()
                                    true
                                }
                            }
                    }.padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s10)
                    .alpha(if (node.isHidden) HIDDEN_ALPHA else 1f)
                    .testTag("file_item_${node.id.raw}"),
        ) {
            FileThumbnail(node)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s2)) {
                AtomicText(node.name, AtomicTextRole.Name, maxLines = 2)
                AtomicText(metaFor(node), AtomicTextRole.MonoMeta, maxLines = 1)
            }
            when {
                isSelectionMode -> AtomicCheckbox(checked = isSelected, onCheckedChange = null)
                node.isDirectory -> AtomicText("→", AtomicTextRole.MonoMeta, color = colors.accentText)
            }
        }
        if (!isSelected) AtomicDivider(modifier = Modifier.padding(horizontal = AtomicSpacing.s16))
    }
}
