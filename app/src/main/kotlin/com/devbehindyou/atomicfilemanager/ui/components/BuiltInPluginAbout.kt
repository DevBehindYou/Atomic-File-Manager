package com.devbehindyou.atomicfilemanager.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons

/**
 * An inventory of shipped features, not an executable-plugin loader. Entries describe the
 * existing viewers/tools; they have no separate installation, permissions or update lifecycle.
 */
internal enum class BuiltInPlugin(
    @param:StringRes val title: Int,
    @param:StringRes val features: Int,
    @param:StringRes val limits: Int,
    val icon: ImageVector,
    val version: String,
    val formats: String,
) {
    IMAGE(
        R.string.plugin_image,
        R.string.plugin_image_features,
        R.string.plugin_image_limits,
        AtomicIcons.Image,
        "0.2",
        "JPG · PNG · GIF",
    ),
    VIDEO(
        R.string.plugin_video,
        R.string.plugin_video_features,
        R.string.plugin_media_limits,
        AtomicIcons.Video,
        "0.2",
        "MP4 · WEBM",
    ),
    AUDIO(
        R.string.plugin_audio,
        R.string.plugin_audio_features,
        R.string.plugin_media_limits,
        AtomicIcons.Audio,
        "0.2",
        "WAV · MP3",
    ),
    PDF(
        R.string.plugin_pdf,
        R.string.plugin_pdf_features,
        R.string.plugin_pdf_limits,
        AtomicIcons.Pdf,
        "0.2",
        "PDF",
    ),
    DOCUMENT(
        R.string.plugin_document,
        R.string.plugin_document_features,
        R.string.plugin_document_limits,
        AtomicIcons.Document,
        "0.2",
        "DOCX",
    ),
    CODE(
        R.string.plugin_code,
        R.string.plugin_code_features,
        R.string.plugin_code_limits,
        AtomicIcons.Code,
        "0.2",
        "TXT · JSON",
    ),
    MARKDOWN(
        R.string.plugin_markdown,
        R.string.plugin_markdown_features,
        R.string.plugin_markdown_limits,
        AtomicIcons.Text,
        "0.2",
        "MD",
    ),
    ARCHIVE(
        R.string.plugin_archive,
        R.string.plugin_archive_features,
        R.string.plugin_archive_limits,
        AtomicIcons.Archive,
        "0.2",
        "ZIP · TAR · 7Z",
    ),
}

@Composable
internal fun BuiltInPluginAboutDialog(
    plugin: BuiltInPlugin,
    closeDescription: String,
    onDismiss: () -> Unit,
) {
    AtomicTopDialog(onDismiss = onDismiss, testTag = "plugins_about_dialog") {
        BalancedPopupHeader(
            stringResource(plugin.title),
            closeDescription,
            onClose = onDismiss,
            testTagPrefix = "plugins_about",
            buttonVariant = AtomicIconButtonVariant.Inset,
        )
        AtomicDivider(Modifier.padding(vertical = AtomicSpacing.s8))
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
        ) {
            PluginAboutContent(plugin)
        }
    }
}

@Composable
private fun PluginAboutContent(plugin: BuiltInPlugin) {
    AtomicText(
        stringResource(R.string.plugins_built_in),
        AtomicTextRole.MonoMeta,
        color = Atomic.colors.accentText,
    )
    AtomicText(
        stringResource(R.string.plugins_version, plugin.version),
        AtomicTextRole.MonoMeta,
    )
    PluginDetail(R.string.plugins_features, plugin.features)
    PluginDetail(R.string.plugins_limits, plugin.limits)
    AtomicText(stringResource(R.string.plugins_usage), AtomicTextRole.BodySecondary)
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
