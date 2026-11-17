package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

internal data class ViewerActions(
    val onClose: () -> Unit,
    val onShare: (() -> Unit)?,
    val onOpenExternal: (() -> Unit)?,
)

private enum class ViewerPanel { ABOUT, DETAILS }

@Composable
internal fun ViewerChrome(
    shown: FileNode,
    type: PreviewType,
    position: String?,
    actions: ViewerActions,
    openingExternal: Boolean,
) {
    val plugin = type.builtInPlugin()
    val source = remember(shown.id) { viewerSourceLabel(shown) }
    var panel by remember(shown.previewSourceKey()) { mutableStateOf<ViewerPanel?>(null) }

    Column(Modifier.fillMaxWidth().testTag("viewer_chrome")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s10, vertical = AtomicSpacing.s4),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtomicIconButton(
                AtomicIcons.Close,
                "Close preview",
                onClick = actions.onClose,
                variant = AtomicIconButtonVariant.Back,
                modifier = Modifier.testTag("preview_close"),
            )
            Column(Modifier.weight(1f)) {
                AtomicText(shown.name, AtomicTextRole.NameLarge, maxLines = 2)
                AtomicText(source, AtomicTextRole.BodySecondary, maxLines = 1)
            }
            if (plugin != null) {
                AtomicIconButton(
                    AtomicIcons.Info,
                    "About this plugin",
                    onClick = { panel = ViewerPanel.ABOUT },
                    modifier = Modifier.testTag("preview_about"),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s10),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val facts =
                listOfNotNull(
                    position,
                    shown.size.takeIf { it >= 0 }?.let(FileUtils::formatBytes),
                    shown.modifiedAt.takeIf { it > 0 }?.let { FileUtils.formatDate(it) },
                ).joinToString(" · ")
            AtomicText(facts, AtomicTextRole.MonoMeta, Modifier.weight(1f), maxLines = 1)
            PreviewExternalAction(onOpen = actions.onOpenExternal, busy = openingExternal)
            AtomicIconButton(
                AtomicIcons.Document,
                "File details",
                onClick = { panel = ViewerPanel.DETAILS },
                modifier = Modifier.testTag("preview_details"),
            )
            actions.onShare?.let { share ->
                AtomicIconButton(
                    AtomicIcons.Share,
                    "Share file",
                    onClick = share,
                    modifier = Modifier.testTag("preview_share"),
                )
            }
        }
        AtomicDivider(strong = true, modifier = Modifier.padding(top = AtomicSpacing.s4))
    }

    when (panel) {
        ViewerPanel.ABOUT -> plugin?.let {
            BuiltInPluginAboutDialog(it, "Close plugin information", onDismiss = { panel = null })
        }
        ViewerPanel.DETAILS -> FileDetailsDialog(shown, onDismiss = { panel = null })
        null -> Unit
    }
}

internal fun PreviewType.builtInPlugin(): BuiltInPlugin? =
    when (this) {
        PreviewType.IMAGE -> BuiltInPlugin.IMAGE
        PreviewType.PDF -> BuiltInPlugin.PDF
        PreviewType.DOCX -> BuiltInPlugin.DOCUMENT
        PreviewType.TEXT -> BuiltInPlugin.CODE
        PreviewType.MARKDOWN -> BuiltInPlugin.MARKDOWN
        PreviewType.ARCHIVE -> BuiltInPlugin.ARCHIVE
        PreviewType.AUDIO -> BuiltInPlugin.AUDIO
        PreviewType.VIDEO -> BuiltInPlugin.VIDEO
        PreviewType.GENERIC -> null
    }

internal fun viewerSourceLabel(node: FileNode): String =
    when (node.id.prefix) {
        FileNodeId.Prefix.FILE -> displayPath(node).substringBeforeLast('/', "").ifBlank { "Device storage" }
        FileNodeId.Prefix.SAF -> "Linked storage"
        FileNodeId.Prefix.MEDIA -> "Media storage"
        FileNodeId.Prefix.USB -> "USB storage"
        FileNodeId.Prefix.SFTP, FileNodeId.Prefix.FTP, FileNodeId.Prefix.FTPS,
        FileNodeId.Prefix.SMB, FileNodeId.Prefix.WEBDAV,
        -> "Network storage"
        FileNodeId.Prefix.SHIZUKU -> "Shizuku storage"
        null -> "File source"
    }
