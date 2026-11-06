package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicCheckbox
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailKind
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.domain.usecase.ArchiveFormats
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

private const val HIDDEN_ALPHA = 0.62f

/** Star state for accessible row actions; null omits the action. */
class FavouriteToggle(
    val isFavourite: Boolean,
    val onToggle: () -> Unit,
)

data class FileItemPresentation(
    val viewMode: FileViewMode = FileViewMode.LIST,
    val favourite: FavouriteToggle? = null,
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
    presentation: FileItemPresentation = FileItemPresentation(),
) {
    val favourite = presentation.favourite
    val viewMode = presentation.viewMode
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

    val itemModifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = AtomicSize.touchTarget)
            .then(frame)
            .combinedClickable(
                // The caller toggles selection on click; long press can select a range.
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
            }.alpha(if (node.isHidden) HIDDEN_ALPHA else 1f)
            .testTag("file_item_${node.id.raw}")

    if (viewMode == FileViewMode.GRID) {
        Column(
            modifier =
                itemModifier
                    .heightIn(min = AtomicSize.fileGridCellHeight)
                    .background(colors.surfaceCard, AtomicShape.sm)
                    .border(
                        if (isSelected) AtomicBorder.selected else AtomicBorder.hair,
                        if (isSelected) colors.accent else colors.borderHair,
                        AtomicShape.sm,
                    ).padding(AtomicSpacing.s12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FileThumbnail(node, size = AtomicSize.gridThumbnail)
                if (isSelectionMode) {
                    AtomicCheckbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            AtomicText(node.name, AtomicTextRole.Name, maxLines = 2, textAlign = TextAlign.Center)
            val summary =
                if (node.isDirectory) {
                    node.childCount?.let { "$it item${if (it == 1) "" else "s"}" } ?: "Folder"
                } else {
                    FileUtils.formatBytes(node.size)
                }
            AtomicText(summary, AtomicTextRole.MonoMeta, maxLines = 1, textAlign = TextAlign.Center)
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
                modifier = itemModifier.padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s10),
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
}
