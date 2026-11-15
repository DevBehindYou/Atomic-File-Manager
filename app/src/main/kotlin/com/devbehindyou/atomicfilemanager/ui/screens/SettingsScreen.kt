package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChipRow
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AccentSource
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicChoiceCard
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSettingsRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDangerAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDangerZone
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicInfoSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.DEFAULT_HIDDEN_FOLDER
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.TRASH_RETENTION_CHOICES
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.model.normalizeHiddenFolder
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import com.devbehindyou.atomicfilemanager.ui.security.authenticate
import com.devbehindyou.atomicfilemanager.ui.security.canAuthenticate
import com.devbehindyou.atomicfilemanager.ui.security.findFragmentActivity

private class HideModeChoice(val mode: HideMode?, val title: String, val description: String)

private fun hideModeChoices(hiddenFolder: String) =
    listOf(
        HideModeChoice(null, "Ask every time", "Choose a method each time you hide a file."),
        HideModeChoice(
            HideMode.FAST_OBSCURE,
            "Fast Obscure",
            "The file stays in its folder. It is renamed to .refract_obscured and its header is masked. " +
                "This is not encryption.",
        ),
        HideModeChoice(
            HideMode.GALLERY,
            "Hide from Gallery",
            "The file moves to $hiddenFolder on its own storage, and photo apps ignore it.",
        ),
        HideModeChoice(
            HideMode.PRIVATE_STORAGE,
            "Private Storage",
            "The file moves into the app's private folder. It is removed if Atomic File Manager is uninstalled.",
        ),
    )

private val themeChoices =
    listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark",
    )

/** Native navigation owned by the application shell. */
data class SettingsDestinations(
    val onOpenPrivateFiles: (() -> Unit)? = null,
    val onOpenFiles: (() -> Unit)? = null,
)

internal enum class SettingsSheet {
    APPEARANCE,
    BROWSING,
    PRIVACY,
    HIDDEN,
    ACCESS,
    BUBBLES,
    CLOUD,
    HIDE_METHOD,
    HIDDEN_FOLDER,
    PLUGINS,
    OPENING,
    ABOUT,
}

/** The three reference groups are navigation rows; preference controls live in secondary surfaces. */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    hasStorageAccess: Boolean,
    onRequestStorageAccess: () -> Unit,
    onClearScanCache: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
    storageAccessStatus: String? = null,
    destinations: SettingsDestinations = SettingsDestinations(),
) {
    val settings by settingsRepository.settings.collectAsState()
    var sheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    val context = LocalContext.current
    val version =
        remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
    val hideChoices = hideModeChoices(settings.hiddenFolder)

    // Existing guard: changing the lock requires successful authentication, not a visual toggle alone.
    fun setAuthRequired(enabled: Boolean) {
        val activity = context.findFragmentActivity()
        val available = canAuthenticate(context)
        when {
            activity == null -> Unit
            !available && enabled -> onNotify("Set up a screen lock, fingerprint or face unlock first.")
            !available -> settingsRepository.update { it.copy(requireAuthForHidden = false) }
            else ->
                authenticate(activity, if (enabled) "Turn on the lock" else "Turn off the lock") { ok ->
                    if (ok) settingsRepository.update { it.copy(requireAuthForHidden = enabled) }
                }
        }
    }

    var hideParent by rememberSaveable { mutableStateOf(SettingsSheet.HIDDEN) }
    SettingsRootContent(settings, modifier) { sheet = it }

    when (sheet) {
        SettingsSheet.APPEARANCE ->
            SettingsPreferencesDialog("Appearance", { sheet = null }) {
                AtomicChipRow {
                    themeChoices.forEach { (mode, label) ->
                        AtomicChip(label, settings.themeMode == mode, {
                            settingsRepository.update { it.copy(themeMode = mode) }
                        })
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val accent = Atomic.accent
                    SettingsPreferenceToggle(
                        "Wallpaper colours",
                        when {
                            !settings.dynamicColor -> "Use your wallpaper colour when it meets contrast requirements."
                            accent.source == AccentSource.WALLPAPER_REJECTED ->
                                "Atomic Orange is used because your wallpaper colour doesn't suit this theme."
                            else -> "Using your wallpaper colour as the accent."
                        },
                        settings.dynamicColor,
                    ) { on -> settingsRepository.update { it.copy(dynamicColor = on) } }
                }
            }
        SettingsSheet.BROWSING ->
            SettingsPreferencesDialog("File browsing", { sheet = null }) {
                AtomicText("View", AtomicTextRole.Name)
                AtomicChipRow {
                    FileViewMode.entries.forEach { mode ->
                        AtomicChip(
                            label = if (mode == FileViewMode.LIST) "List" else "Grid",
                            selected = settings.fileViewMode == mode,
                            onSelectedChange = { settingsRepository.update { it.copy(fileViewMode = mode) } },
                        )
                    }
                }
                SettingsPreferenceToggle(
                    "Show hidden files",
                    "Names that start with a dot appear faded.",
                    settings.showHiddenFiles,
                ) { on -> settingsRepository.update { it.copy(showHiddenFiles = on) } }
                SettingsPreferenceToggle(
                    "Use Trash",
                    if (settings.useTrash) {
                        "Deleted files can be restored for ${settings.trashRetentionDays} days."
                    } else {
                        "Deletes are permanent. Each delete still asks first."
                    },
                    settings.useTrash,
                ) { on -> settingsRepository.update { it.copy(useTrash = on) } }
                AtomicText("Keep deleted files for", AtomicTextRole.MonoMeta)
                AtomicChipRow {
                    TRASH_RETENTION_CHOICES.forEach { days ->
                        AtomicChip(
                            "$days days",
                            settings.trashRetentionDays == days,
                            { settingsRepository.update { it.copy(trashRetentionDays = days) } },
                            enabled = settings.useTrash,
                        )
                    }
                }
                AtomicSettingsRow("Hiding and privacy settings", { sheet = SettingsSheet.HIDDEN })
                AtomicDangerZone(
                    "These run immediately and can't be undone.",
                    listOf(
                        AtomicDangerAction("Clear scan cache", onExecute = {
                            onClearScanCache()
                            onNotify("File scan cache cleared.")
                        }),
                    ),
                )
            }
        SettingsSheet.PRIVACY, SettingsSheet.HIDDEN ->
            SettingsPreferencesDialog(
                if (sheet == SettingsSheet.HIDDEN) "Hidden files" else "Private files",
                { sheet = null },
            ) {
                AtomicText(
                    "Hidden is not encrypted. Visibility and authentication are separate.",
                    AtomicTextRole.BodySecondary,
                )
                SettingsPreferenceToggle(
                    "Show hidden files",
                    "Show dot-prefixed names in file lists.",
                    settings.showHiddenFiles,
                ) { on -> settingsRepository.update { it.copy(showHiddenFiles = on) } }
                AtomicSettingsRow(
                    "Default hiding method",
                    {
                        hideParent = sheet ?: SettingsSheet.HIDDEN
                        sheet = SettingsSheet.HIDE_METHOD
                    },
                    value = hideChoices.first { it.mode == settings.defaultHideMode }.title,
                )
                AtomicSettingsRow(
                    "Hidden folder",
                    {
                        hideParent = sheet ?: SettingsSheet.HIDDEN
                        sheet = SettingsSheet.HIDDEN_FOLDER
                    },
                    value = settings.hiddenFolder,
                )
                SettingsPreferenceToggle(
                    "Lock private files",
                    "Authenticate before hidden or private files open.",
                    settings.requireAuthForHidden,
                    ::setAuthRequired,
                )
                if (sheet == SettingsSheet.PRIVACY) {
                    destinations.onOpenPrivateFiles?.let { open ->
                        AtomicButton("Open Private files", onClick = {
                            sheet = null
                            open()
                        })
                    }
                }
            }
        SettingsSheet.ACCESS ->
            SettingsPreferencesDialog("Storage permissions", { sheet = null }) {
                AtomicText(
                    "Android controls shared-storage access. Only grant the access you need.",
                    AtomicTextRole.BodySecondary,
                )
                AtomicSettingsRow(
                    "All files access",
                    onRequestStorageAccess,
                    value = storageAccessStatus ?: if (hasStorageAccess) "Granted" else "Needed to browse shared files",
                )
            }
        SettingsSheet.BUBBLES ->
            SettingsPreferencesDialog("Transfer bubbles", { sheet = null }) {
                AtomicText("Keep selected files ready while you browse to a destination.", AtomicTextRole.BodySecondary)
                AtomicText("Up to ${TransferBubble.MAX_BUBBLES} transfer bubbles", AtomicTextRole.Name)
                AtomicText(
                    "Create a bubble from the + button in Files. Manage its contents from the bubble rail.",
                    AtomicTextRole.BodySecondary,
                )
                destinations.onOpenFiles?.let { open ->
                    AtomicButton("Open Files", onClick = {
                        sheet = null
                        open()
                    })
                }
            }
        SettingsSheet.CLOUD ->
            SettingsPreferencesDialog("Cloud Connect", { sheet = null }) {
                AtomicText("Planned · no live sign-in available", AtomicTextRole.Name)
                AtomicText(
                    "Cloud Connect is a separate bundled-plugin track. Provider accounts and live transfers " +
                        "are not available here yet. Linked folders and network storage remain in Connections.",
                    AtomicTextRole.BodySecondary,
                )
            }
        SettingsSheet.OPENING -> FileOpeningSettingsSheet(settingsRepository, onDismiss = { sheet = null })
        SettingsSheet.PLUGINS -> InAppPluginsSheet(onDismiss = { sheet = null })
        SettingsSheet.HIDE_METHOD ->
            AtomicSheet(label = "Default hiding method", onDismiss = { sheet = hideParent }) {
                hideChoices.forEach { choice ->
                    AtomicChoiceCard(
                        title = choice.title,
                        description = choice.description,
                        selected = settings.defaultHideMode == choice.mode,
                        onSelect = {
                            settingsRepository.update { it.copy(defaultHideMode = choice.mode) }
                            sheet = hideParent
                        },
                    )
                }
            }
        SettingsSheet.HIDDEN_FOLDER ->
            HiddenFolderSheet(
                current = settings.hiddenFolder,
                onDismiss = { sheet = hideParent },
                onSave = { folder ->
                    settingsRepository.update { it.copy(hiddenFolder = folder) }
                    sheet = hideParent
                },
            )
        SettingsSheet.ABOUT ->
            AtomicInfoSheet(
                label = "About & licences",
                headline = "Atomic File Manager",
                body =
                    "Version ${version ?: "unknown"}. Offline-first and private: no account, no ads, no tracking. " +
                        "Fonts: Bebas Neue, Hanken Grotesk and JetBrains Mono, under the SIL Open Font " +
                        "License 1.1. Icons: Tabler Icons, MIT (copyright 2020-2026 Paweł Kuna). " +
                        "Additional Material Icons, Apache License 2.0.",
                onDismiss = { sheet = null },
            )
        null -> Unit
    }
}

@Composable
private fun HiddenFolderSheet(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current) }
    val normalized = normalizeHiddenFolder(text)
    AtomicSheet(label = "Hidden folder", onDismiss = onDismiss) {
        AtomicText(
            "A folder inside each storage, for example $DEFAULT_HIDDEN_FOLDER. It is created when you hide a file.",
            AtomicTextRole.Body,
        )
        AtomicTextField(
            value = text,
            onValueChange = { text = it },
            label = "Folder",
            errorText = if (normalized == null) "Enter a folder name without \"..\" or a drive letter." else null,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            "Save",
            onClick = { normalized?.let(onSave) },
            enabled = normalized != null,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            "Reset to default",
            onClick = { text = DEFAULT_HIDDEN_FOLDER },
            variant = AtomicButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton("Cancel", onClick = onDismiss, variant = AtomicButtonVariant.Text)
    }
}
