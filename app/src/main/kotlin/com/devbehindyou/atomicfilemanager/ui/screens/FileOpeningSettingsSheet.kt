package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChipRow
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSettingsRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicCommandGrid
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.data.preview.ExternalFileApp
import com.devbehindyou.atomicfilemanager.data.preview.ExternalFileApps
import com.devbehindyou.atomicfilemanager.data.preview.mimeForOpeningRule
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenFamily
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleKey
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleScope
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import com.devbehindyou.atomicfilemanager.domain.model.FileOpeningPreferences
import com.devbehindyou.atomicfilemanager.domain.model.MAX_FILE_OPENING_RULES
import com.devbehindyou.atomicfilemanager.domain.model.parseFileOpenRuleKey
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FileOpeningSettingsSheet(repository: SettingsRepository, onDismiss: () -> Unit) {
    val settings by repository.settings.collectAsState()
    val policy = settings.fileOpening
    var selected by remember { mutableStateOf<FileOpenRuleKey?>(null) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(selected, reset) { scroll.scrollTo(0) }
    AtomicSheet(stringResource(R.string.file_open_settings), onDismiss, scrollState = scroll) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null || reset) {
                AtomicIconButton(AtomicIcons.Back, stringResource(R.string.file_open_back), onClick = { selected = null; reset = false })
            }
            AtomicText(
                selected?.let { ruleName(it) } ?: stringResource(R.string.file_open_settings),
                AtomicTextRole.Body,
                modifier = Modifier.weight(1f),
            )
            AtomicIconButton(AtomicIcons.Close, stringResource(R.string.action_close), onClick = onDismiss)
        }
        val key = selected
        when {
            reset -> {
                AtomicText(stringResource(R.string.file_open_reset_title), AtomicTextRole.Body)
                AtomicText(stringResource(R.string.file_open_reset_body), AtomicTextRole.BodySecondary)
                AtomicButton(
                    stringResource(R.string.file_open_reset_confirm),
                    onClick = { repository.update { it.copy(fileOpening = FileOpeningPreferences()) }; reset = false },
                    variant = AtomicButtonVariant.Destructive,
                )
            }
            key != null && policy.rules.size >= MAX_FILE_OPENING_RULES && key !in policy.rules ->
                AtomicText(stringResource(R.string.file_open_rule_limit), AtomicTextRole.BodySecondary)
            key != null -> FileOpeningRuleEditor(key, policy.rules[key], onChoose = { target ->
                repository.update { it.copy(fileOpening = it.fileOpening.withRule(key, target)) }
                selected = null
            }, onReset = {
                repository.update { it.copy(fileOpening = it.fileOpening.withoutRule(key)) }
                selected = null
            })
            else -> {
                AtomicText(stringResource(R.string.file_open_default), AtomicTextRole.MonoLabel)
                AtomicChipRow {
                    FileOpenMode.entries.forEach { mode ->
                        AtomicChip(stringResource(openingModeLabel(mode)), selected = policy.defaultMode == mode, onSelectedChange = {
                            repository.update { it.copy(fileOpening = it.fileOpening.copy(defaultMode = mode)) }
                        })
                    }
                }
                AtomicText(stringResource(R.string.file_open_policy_detail), AtomicTextRole.BodySecondary)
                AtomicText(stringResource(R.string.file_open_rules), AtomicTextRole.MonoLabel)
                AtomicText(stringResource(R.string.file_open_rule_priority), AtomicTextRole.BodySecondary)
                FileOpenFamily.entries.forEach { family ->
                    val familyKey = FileOpenRuleKey(FileOpenRuleScope.FAMILY, family.name)
                    AtomicSettingsRow(
                        title = stringResource(familyLabel(family)),
                        value = targetName(policy.rules[familyKey]),
                        onClick = { selected = familyKey },
                    )
                }
                policy.rules.filterKeys { it.scope != FileOpenRuleScope.FAMILY }.forEach { (rule, target) ->
                    AtomicSettingsRow(title = ruleName(rule), value = targetName(target), onClick = { selected = rule })
                }
                AddOpeningRule(policy) { selected = it }
                AtomicButton(stringResource(R.string.file_open_reset_all), onClick = { reset = true }, variant = AtomicButtonVariant.Ghost)
            }
        }
    }
}

@Composable
private fun FileOpeningRuleEditor(key: FileOpenRuleKey, current: FileOpenTarget?, onChoose: (FileOpenTarget) -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    var apps by remember(key) { mutableStateOf<List<ExternalFileApp>>(emptyList()) }
    var loading by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key) {
        try {
            apps = withContext(Dispatchers.IO) { ExternalFileApps(context.applicationContext).list(mimeForOpeningRule(key)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            apps = emptyList()
        } finally {
            loading = false
        }
    }
    AtomicText(stringResource(R.string.file_open_selected, targetName(current)), AtomicTextRole.Body)
    if (current?.component != null && !loading && apps.none { it.component == current.component }) {
        AtomicText(stringResource(R.string.file_open_missing_app), AtomicTextRole.BodySecondary)
    }
    AtomicCommandGrid(
        listOf(
            AtomicMenuAction(stringResource(R.string.file_open_prefer_built_in), AtomicIcons.Document, { onChoose(FileOpenTarget(FileOpenMode.BUILT_IN)) }),
            AtomicMenuAction(stringResource(R.string.file_open_ask), AtomicIcons.Info, { onChoose(FileOpenTarget(FileOpenMode.ASK)) }),
            AtomicMenuAction(stringResource(R.string.file_open_prefer_external), AtomicIcons.OpenExternally, { onChoose(FileOpenTarget(FileOpenMode.EXTERNAL)) }),
        ) + apps.map { app -> AtomicMenuAction(app.label, AtomicIcons.OpenExternally, { onChoose(app.target()) }) },
    )
    AtomicText(stringResource(if (loading) R.string.file_open_loading_apps else R.string.file_open_read_only), AtomicTextRole.BodySecondary)
    AtomicButton(stringResource(R.string.file_open_reset_rule), onClick = onReset, variant = AtomicButtonVariant.Ghost)
}

@Composable
private fun AddOpeningRule(policy: FileOpeningPreferences, onAdd: (FileOpenRuleKey) -> Unit) {
    var scope by rememberSaveable { mutableStateOf(FileOpenRuleScope.EXTENSION) }
    var text by rememberSaveable { mutableStateOf("") }
    val key = parseFileOpenRuleKey(scope, text)
    val full = policy.rules.size >= MAX_FILE_OPENING_RULES && key !in policy.rules
    AtomicText(stringResource(R.string.file_open_add_rule), AtomicTextRole.MonoLabel)
    AtomicChipRow {
        listOf(FileOpenRuleScope.EXTENSION, FileOpenRuleScope.MIME).forEach { candidate ->
            AtomicChip(
                stringResource(if (candidate == FileOpenRuleScope.EXTENSION) R.string.file_open_extension else R.string.file_open_mime),
                selected = scope == candidate,
                onSelectedChange = { scope = candidate; text = "" },
            )
        }
    }
    AtomicTextField(
        value = text,
        onValueChange = { text = it },
        label = stringResource(R.string.file_open_rule_value),
        placeholder = stringResource(R.string.file_open_rule_hint),
        errorText = when {
            full -> stringResource(R.string.file_open_rule_limit)
            text.isNotBlank() && key == null -> stringResource(R.string.file_open_invalid_rule)
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    AtomicButton(stringResource(R.string.file_open_edit_rule), onClick = { key?.let(onAdd) }, enabled = key != null && !full)
}

@Composable
private fun targetName(target: FileOpenTarget?): String =
    when {
        target == null -> stringResource(R.string.file_open_inherit)
        target.component != null -> stringResource(R.string.file_open_saved_app, target.appLabel ?: target.component)
        else -> stringResource(openingModeLabel(target.mode))
    }

@Composable
private fun ruleName(key: FileOpenRuleKey): String =
    when (key.scope) {
        FileOpenRuleScope.EXTENSION -> ".${key.value}"
        FileOpenRuleScope.MIME -> key.value
        FileOpenRuleScope.FAMILY -> stringResource(familyLabel(FileOpenFamily.valueOf(key.value)))
    }

@StringRes
internal fun openingModeLabel(mode: FileOpenMode): Int = when (mode) {
    FileOpenMode.BUILT_IN -> R.string.file_open_prefer_built_in
    FileOpenMode.ASK -> R.string.file_open_ask
    FileOpenMode.EXTERNAL -> R.string.file_open_prefer_external
}

@StringRes
private fun familyLabel(family: FileOpenFamily): Int = when (family) {
    FileOpenFamily.IMAGE -> R.string.file_open_family_image
    FileOpenFamily.VIDEO -> R.string.file_open_family_video
    FileOpenFamily.AUDIO -> R.string.file_open_family_audio
    FileOpenFamily.PDF -> R.string.file_open_family_pdf
    FileOpenFamily.DOCUMENT -> R.string.file_open_family_document
    FileOpenFamily.ARCHIVE -> R.string.file_open_family_archive
    FileOpenFamily.CODE -> R.string.file_open_family_code
    FileOpenFamily.TEXT -> R.string.file_open_family_text
    FileOpenFamily.OTHER -> R.string.file_open_family_other
}

