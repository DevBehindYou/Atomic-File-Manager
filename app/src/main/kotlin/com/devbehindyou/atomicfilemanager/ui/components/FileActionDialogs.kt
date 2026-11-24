package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.RemovableNames

private const val MAX_NAME_BYTES = 255

/**
 * Checks a file or folder name before it reaches the backend. Returns null when the name is
 * usable, otherwise one sentence that says how to fix it (spec §11: errors say what to do).
 */
fun fileNameError(
    name: String,
    removable: Boolean = false,
): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Enter a name."
        trimmed.contains('/') -> "A name can't contain \"/\". Try \"${trimmed.replace('/', ' ')}\"."
        trimmed.contains('\u0000') -> "A name can't contain hidden control characters."
        trimmed == "." || trimmed == ".." -> "\"$trimmed\" is reserved. Choose another name."
        trimmed.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> "That name is too long. Shorten it."
        removable -> RemovableNames.problem(trimmed)
        else -> null
    }
}

/** New folder sheet (canvas "Sheet · new folder"). [parentName] is shown as "In /Download". */
@Composable
fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
    parentName: String? = null,
    /** The folder is on a USB drive or SD card, so FAT/exFAT name rules apply. */
    removable: Boolean = false,
) {
    var folderName by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    val error = fileNameError(folderName, removable)

    AtomicSheet(label = if (parentName != null) "New folder in /$parentName" else "New folder", onDismiss = onDismiss) {
        AtomicTextField(
            value = folderName,
            onValueChange = {
                folderName = it
                touched = true
            },
            label = "Name",
            placeholder = "Tickets 2026",
            errorText = if (touched) error else null,
            modifier = Modifier.fillMaxWidth().testTag("new_folder_input"),
        )
        AtomicButton(
            "Create folder",
            onClick = { if (error == null) onConfirm(folderName.trim()) },
            enabled = error == null,
            modifier = Modifier.fillMaxWidth().testTag("confirm_create_folder_button"),
        )
        AtomicButton(
            "Cancel",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth().testTag("cancel_create_folder_button"),
        )
    }
}

@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit,
    /** The file is on a USB drive or SD card, so FAT/exFAT name rules apply. */
    removable: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val error = fileNameError(name, removable)
    val unchanged = name.trim() == initialName

    AtomicSheet(label = "Rename", onDismiss = onDismiss) {
        AtomicTextField(
            value = name,
            onValueChange = { name = it },
            label = "New name",
            errorText = error,
            modifier = Modifier.fillMaxWidth().testTag("rename_input"),
        )
        AtomicButton(
            "Rename",
            onClick = { if (error == null && !unchanged) onConfirm(name.trim()) },
            enabled = error == null && !unchanged,
            modifier = Modifier.fillMaxWidth().testTag("confirm_rename_button"),
        )
        AtomicButton(
            "Cancel",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth().testTag("cancel_rename_button"),
        )
    }
}
