package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChipRow
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AccentSource
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicChoiceCard
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSettingsRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicToggleCard
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDangerAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDangerZone
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicInfoSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.DEFAULT_HIDDEN_FOLDER
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.TRASH_RETENTION_CHOICES
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
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

private enum class SettingsSheet { HIDE_METHOD, HIDDEN_FOLDER, PLUGINS, ABOUT }

/**
 * Settings (ATOMIC_UI_PLAN.md §7.9, canvas "Settings"): sections under mono labels, toggle cards
 * for on/off choices, rows with an accent arrow for anything that opens a sheet, and a danger
 * zone at the end. [onNotify] shows feedback in the app snackbar.
 */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    hasStorageAccess: Boolean,
    onRequestStorageAccess: () -> Unit,
    onClearScanCache: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by settingsRepository.settings.collectAsState()
    var sheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    val context = LocalContext.current
    val version =
        remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }
    val hideChoices = hideModeChoices(settings.hiddenFolder)

    // Changing the lock needs a successful unlock first, so someone holding an unlocked phone
    // cannot switch it off. Without any screen lock the lock cannot be enabled at all.
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

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s16)
                .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s24),
    ) {
        SettingsSection("Appearance") {
            AtomicChipRow {
                themeChoices.forEach { (mode, label) ->
                    AtomicChip(
                        label = label,
                        selected = settings.themeMode == mode,
                        onSelectedChange = { settingsRepository.update { it.copy(themeMode = mode) } },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val accent = Atomic.accent
                AtomicToggleCard(
                    title = "Wallpaper colours",
                    description =
                        when {
                            !settings.dynamicColor ->
                                "Use your wallpaper colour as the accent. Signal Blue is used if it's too pale to read."
                            accent.source == AccentSource.WALLPAPER_REJECTED ->
                                "Your wallpaper colour can't be read clearly here, so Signal Blue is used."
                            else -> "Using your wallpaper colour as the accent."
                        },
                    checked = settings.dynamicColor,
                    onCheckedChange = { on -> settingsRepository.update { it.copy(dynamicColor = on) } },
                )
            }
        }

        SettingsSection("Files") {
            AtomicToggleCard(
                title = "Show hidden files",
                description = "Names that start with a dot appear faded.",
                checked = settings.showHiddenFiles,
                onCheckedChange = { on -> settingsRepository.update { it.copy(showHiddenFiles = on) } },
            )
            AtomicSettingsRow(
                title = stringResource(R.string.plugins_title),
                value = stringResource(R.string.plugins_settings_summary),
                onClick = { sheet = SettingsSheet.PLUGINS },
                modifier = Modifier.testTag("setting_plugins"),
            )
        }

        SettingsSection("Trash") {
            AtomicToggleCard(
                title = "Use Trash",
                description =
                    if (settings.useTrash) {
                        "Deleted files go to Trash and can be restored for ${settings.trashRetentionDays} days."
                    } else {
                        "Deletes are permanent. Each delete still asks first."
                    },
                checked = settings.useTrash,
                onCheckedChange = { on -> settingsRepository.update { it.copy(useTrash = on) } },
                modifier = Modifier.testTag("setting_use_trash"),
            )
            AtomicText("Keep deleted files for", AtomicTextRole.MonoMeta)
            AtomicChipRow {
                TRASH_RETENTION_CHOICES.forEach { days ->
                    AtomicChip(
                        label = "$days days",
                        selected = settings.trashRetentionDays == days,
                        onSelectedChange = { settingsRepository.update { it.copy(trashRetentionDays = days) } },
                        enabled = settings.useTrash,
                    )
                }
            }
        }

        SettingsSection("Hiding & privacy") {
            AtomicSettingsRow(
                title = "Default hiding method",
                // Every HideMode plus "ask" (null) has a choice, so this always finds one.
                value = hideChoices.first { it.mode == settings.defaultHideMode }.title,
                onClick = { sheet = SettingsSheet.HIDE_METHOD },
            )
            AtomicSettingsRow(
                title = "Hidden folder",
                value = settings.hiddenFolder,
                onClick = { sheet = SettingsSheet.HIDDEN_FOLDER },
            )
            AtomicToggleCard(
                title = "Lock private files",
                description = "Ask for your fingerprint, face or screen lock before hidden or private files open.",
                checked = settings.requireAuthForHidden,
                onCheckedChange = ::setAuthRequired,
            )
        }

        SettingsSection("Storage access") {
            AtomicSettingsRow(
                title = "All files access",
                value =
                    if (hasStorageAccess) {
                        "Granted"
                    } else {
                        "Not granted. Shared storage can't be browsed without it."
                    },
                onClick = onRequestStorageAccess,
            )
            AtomicSettingsRow(
                title = "About & licences",
                value = "Version ${version ?: "unknown"}",
                onClick = { sheet = SettingsSheet.ABOUT },
            )
        }

        AtomicDangerZone(
            warning = "These run immediately and can't be undone.",
            actions =
                listOf(
                    AtomicDangerAction("Clear scan cache", onExecute = {
                        onClearScanCache()
                        onNotify("File scan cache cleared.")
                    }),
                ),
        )
    }

    when (sheet) {
        SettingsSheet.PLUGINS -> InAppPluginsSheet(onDismiss = { sheet = null })
        SettingsSheet.HIDE_METHOD ->
            AtomicSheet(label = "Default hiding method", onDismiss = { sheet = null }) {
                hideChoices.forEach { choice ->
                    AtomicChoiceCard(
                        title = choice.title,
                        description = choice.description,
                        selected = settings.defaultHideMode == choice.mode,
                        onSelect = {
                            settingsRepository.update { it.copy(defaultHideMode = choice.mode) }
                            sheet = null
                        },
                    )
                }
            }
        SettingsSheet.HIDDEN_FOLDER ->
            HiddenFolderSheet(
                current = settings.hiddenFolder,
                onDismiss = { sheet = null },
                onSave = { folder ->
                    settingsRepository.update { it.copy(hiddenFolder = folder) }
                    sheet = null
                },
            )
        SettingsSheet.ABOUT ->
            AtomicInfoSheet(
                label = "About & licences",
                headline = "Atomic File Manager",
                body =
                    "Version ${version ?: "unknown"}. Offline-first and private: no account, no ads, no tracking. " +
                        "Fonts: Bebas Neue, Hanken Grotesk and JetBrains Mono, under the SIL Open Font " +
                        "License 1.1. Icons: Material Icons, Apache License 2.0.",
                onDismiss = { sheet = null },
            )
        null -> Unit
    }
}

@Composable
private fun SettingsSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        AtomicSectionLabel(label)
        content()
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
