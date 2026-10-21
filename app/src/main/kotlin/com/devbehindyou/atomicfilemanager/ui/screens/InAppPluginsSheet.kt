package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicCommandGrid
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet

/**
 * An inventory of shipped features, not an executable-plugin loader. Entries describe the
 * existing viewers/tools; they have no separate installation, permissions or update lifecycle.
 */
private enum class BuiltInPlugin(
    @param:StringRes val title: Int,
    @param:StringRes val features: Int,
    @param:StringRes val limits: Int,
    val icon: ImageVector,
) {
    IMAGE(R.string.plugin_image, R.string.plugin_image_features, R.string.plugin_image_limits, AtomicIcons.Image),
    VIDEO(R.string.plugin_video, R.string.plugin_video_features, R.string.plugin_media_limits, AtomicIcons.Video),
    AUDIO(R.string.plugin_audio, R.string.plugin_audio_features, R.string.plugin_media_limits, AtomicIcons.Audio),
    PDF(R.string.plugin_pdf, R.string.plugin_pdf_features, R.string.plugin_pdf_limits, AtomicIcons.Pdf),
    DOCUMENT(
        R.string.plugin_document,
        R.string.plugin_document_features,
        R.string.plugin_document_limits,
        AtomicIcons.Document,
    ),
    CODE(R.string.plugin_code, R.string.plugin_code_features, R.string.plugin_code_limits, AtomicIcons.Code),
    MARKDOWN(
        R.string.plugin_markdown,
        R.string.plugin_markdown_features,
        R.string.plugin_markdown_limits,
        AtomicIcons.Text,
    ),
    ARCHIVE(
        R.string.plugin_archive,
        R.string.plugin_archive_features,
        R.string.plugin_archive_limits,
        AtomicIcons.Archive,
    ),
}

@Composable
internal fun InAppPluginsSheet(onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<BuiltInPlugin?>(null) }
    val plugin = selected
    val scroll = rememberScrollState()
    // Keep one modal window while changing pages, and reset a scrolled list before showing details.
    LaunchedEffect(plugin) { scroll.scrollTo(0) }
    AtomicSheet(
        label = stringResource(R.string.plugins_title),
        onDismiss = onDismiss,
        scrollState = scroll,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (plugin != null) {
                AtomicIconButton(
                    AtomicIcons.Back,
                    stringResource(R.string.plugins_back),
                    onClick = { selected = null },
                )
            }
            AtomicText(
                stringResource(plugin?.title ?: R.string.plugins_included),
                AtomicTextRole.Body,
                modifier = Modifier.weight(1f),
            )
            AtomicIconButton(AtomicIcons.Close, stringResource(R.string.action_close), onClick = onDismiss)
        }
        if (plugin == null) {
            AtomicText(stringResource(R.string.plugins_intro), AtomicTextRole.BodySecondary)
            AtomicCommandGrid(
                BuiltInPlugin.entries.map { entry ->
                    AtomicMenuAction(
                        label = stringResource(entry.title),
                        icon = entry.icon,
                        description = stringResource(R.string.plugins_built_in),
                        onClick = { selected = entry },
                    )
                },
            )
        } else {
            AtomicText(
                stringResource(R.string.plugins_built_in),
                AtomicTextRole.MonoMeta,
                color = Atomic.colors.accentText,
            )
            PluginDetail(R.string.plugins_features, plugin.features)
            PluginDetail(R.string.plugins_limits, plugin.limits)
            AtomicText(stringResource(R.string.plugins_usage), AtomicTextRole.BodySecondary)
        }
    }
}

@Composable
private fun PluginDetail(
    @StringRes label: Int,
    @StringRes body: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
        AtomicText(stringResource(label), AtomicTextRole.MonoLabel)
        AtomicText(stringResource(body), AtomicTextRole.Body)
    }
}
