package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicFileTypeColors
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.ui.shortcuts.AppShortcuts
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import java.util.Locale

/** The exact local path, or the complete opaque identifier for any other provider. */
fun displayPath(node: FileNode): String = node.id.raw.removePrefix("file:")

fun fileFacts(node: FileNode): List<AtomicFact> =
    buildList {
        val size =
            when {
                node.size >= 0 ->
                    "${FileUtils.formatBytes(node.size)} (${String.format(Locale.US, "%,d", node.size)} bytes)"
                node.isDirectory -> "Not calculated"
                else -> "Unknown"
            }
        val type = if (node.isDirectory) "Folder" else node.mimeType?.takeIf { it.isNotBlank() } ?: "Unknown"
        add(AtomicFact("Type", type))
        add(AtomicFact("Size", size, monoValue = true))
        val location = if (node.id.prefix == FileNodeId.Prefix.FILE) displayPath(node) else "Unavailable"
        add(AtomicFact("Location", location, monoValue = true))
        add(
            AtomicFact(
                "Modified",
                if (node.modifiedAt == 0L) "Unknown" else FileUtils.formatDate(node.modifiedAt),
                monoValue = true,
            ),
        )
        add(
            AtomicFact(
                "Access",
                listOfNotNull(
                    "Read".takeIf { node.access.readable },
                    "Write".takeIf { node.access.writable },
                    "Rename".takeIf { node.access.renamable },
                    "Delete".takeIf { node.access.deletable },
                ).joinToString(" · ").ifEmpty { "None" },
                monoValue = true,
            ),
        )
        if (node.isDirectory) {
            add(AtomicFact("Items", node.childCount?.toString() ?: "Not counted", monoValue = true))
        }
    }

internal data class FileDetailsCommands(
    val onOpen: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val onRename: (() -> Unit)? = null,
    val offerShare: Boolean = false,
    @StringRes val openLabel: Int = R.string.file_details_open,
)

@Composable
internal fun FileDetailsDialog(
    node: FileNode,
    commands: FileDetailsCommands = FileDetailsCommands(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val facts = remember(node) { fileFacts(node) }
    val type = remember(node) { fileTypeFor(node) }
    val icon = remember(node) { iconFor(node) }
    val typeColor = Color(AtomicFileTypeColors.icon(type, Atomic.colors.isDark))
    AtomicSheet(label = if (node.isDirectory) "Folder details" else "File details", onDismiss = onDismiss) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtomicIconTile(icon, tint = typeColor, containerColor = typeColor.copy(alpha = 0.12f))
            AtomicText(
                node.name,
                AtomicTextRole.NameLarge,
                Modifier.weight(1f).semantics { heading() }.testTag("file_details_name"),
            )
        }
        AtomicFactSheet(facts, Modifier.testTag("file_details_metadata"))
        localImageFile(node)?.let { ImageMetadataSection(node, it) }
        FileDetailsPrimaryActions(node, commands, onDismiss)
        FileDetailsActions(node, context, onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileDetailsPrimaryActions(
    node: FileNode,
    commands: FileDetailsCommands,
    onDismiss: () -> Unit,
) {
    val open = commands.onOpen.takeIf { node.access.readable }
    val share = commands.onShare.takeIf { node.access.readable && !node.isDirectory }
    val rename = commands.onRename.takeIf { node.access.renamable }
    val offerShare = commands.offerShare && share == null && node.access.readable && !node.isDirectory
    if (open == null && share == null && rename == null && !offerShare) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        open?.let {
            AtomicButton(
                stringResource(commands.openLabel),
                onClick = {
                    onDismiss()
                    it()
                },
                leadingIcon = if (node.isDirectory) AtomicIcons.FolderOpen else AtomicIcons.OpenExternally,
                modifier = Modifier.testTag("file_details_open"),
            )
        }
        share?.let {
            AtomicButton(
                stringResource(R.string.file_details_share),
                onClick = {
                    onDismiss()
                    it()
                },
                variant = AtomicButtonVariant.Ghost,
                leadingIcon = AtomicIcons.Share,
                modifier = Modifier.testTag("file_details_share"),
            )
        }
        if (offerShare) FileDetailsShareButton(node, onShared = onDismiss)
        rename?.let {
            AtomicButton(
                stringResource(R.string.selection_rename),
                onClick = {
                    onDismiss()
                    it()
                },
                variant = AtomicButtonVariant.Ghost,
                leadingIcon = AtomicIcons.Rename,
                modifier = Modifier.testTag("file_details_rename"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileDetailsActions(
    node: FileNode,
    context: Context,
    onDismiss: () -> Unit,
) {
    val reference = if (node.id.prefix == FileNodeId.Prefix.FILE) "Path" else "Identifier"
    var copied by remember(node.id) { mutableStateOf(false) }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicButton(
            if (copied) "$reference copied" else "Copy ${reference.lowercase(Locale.ROOT)}",
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(reference, displayPath(node)))
                copied = clipboard != null
            },
            variant = AtomicButtonVariant.Ghost,
            leadingIcon = AtomicIcons.Copy,
            modifier = Modifier.testTag("copy_path_button"),
        )
        if (node.isDirectory && AppShortcuts.canPinFolder(context)) {
            AtomicButton(
                "Add to home screen",
                onClick = { AppShortcuts.pinFolder(context, node.id.raw, node.name) },
                variant = AtomicButtonVariant.Ghost,
                leadingIcon = AtomicIcons.Add,
                modifier = Modifier.testTag("pin_folder_button"),
            )
        }
        AtomicButton("Close", onClick = onDismiss, variant = AtomicButtonVariant.Solid)
    }
}
