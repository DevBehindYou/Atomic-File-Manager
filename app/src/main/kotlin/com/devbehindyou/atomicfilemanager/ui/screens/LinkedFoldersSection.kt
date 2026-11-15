package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.data.volume.LinkedFolder
import com.devbehindyou.atomicfilemanager.data.volume.LinkedFolderText
import com.devbehindyou.atomicfilemanager.data.volume.LinkedFolders
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

/**
 * Folders cloud and other apps
 * share through the system picker, browsed with the SAF backend. No accounts in this app.
 */
@Composable
fun LinkedFoldersSection(
    onBrowseFolder: (FileNodeId) -> Unit,
    onNotify: (String) -> Unit,
    sectionTitle: String = "Linked folders",
) {
    val context = LocalContext.current
    val linked = remember { LinkedFolders(context.applicationContext) }
    var folders by remember { mutableStateOf(linked.list()) }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val folder = linked.link(uri)
            if (folder == null) {
                onNotify("That app didn't allow keeping access to the folder.")
            } else {
                folders = linked.list()
                onBrowseFolder(folder.rootId)
            }
        }

    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        AtomicSectionLabel(sectionTitle)
        AtomicText(LinkedFolderText.EXPLAINER, AtomicTextRole.BodySecondary)
        if (folders.isNotEmpty()) {
            Column {
                folders.forEach {
                        folder ->
                    LinkedFolderRow(folder, onBrowseFolder) {
                        linked.unlink(it)
                        folders = linked.list()
                    }
                }
            }
        }
        AtomicButton(
            "Link a folder",
            onClick = { pick.launch(null) },
            variant = AtomicButtonVariant.Ghost,
            leadingIcon = AtomicIcons.Add,
            modifier = Modifier.fillMaxWidth().testTag("link_folder_button"),
        )
    }
}

@Composable
private fun LinkedFolderRow(
    folder: LinkedFolder,
    onBrowseFolder: (FileNodeId) -> Unit,
    onUnlink: (LinkedFolder) -> Unit,
) {
    AtomicFileRow(
        name = folder.name,
        meta = LinkedFolderText.meta(folder.appLabel, folder.writable),
        icon = AtomicIcons.FolderOpen,
        onClick = { onBrowseFolder(folder.rootId) },
        trailing = {
            AtomicIconButton(
                AtomicIcons.Close,
                "Stop using ${folder.name}",
                onClick = { onUnlink(folder) },
                variant = AtomicIconButtonVariant.Destructive,
            )
        },
    )
}
