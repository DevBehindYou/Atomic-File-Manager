package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.ui.components.AtomicTopDialog
import com.devbehindyou.atomicfilemanager.ui.components.BalancedPopupHeader

private val pluginContentWidth = 960.dp
private val pluginTwoColumnMinWidth = 568.dp

/**
 * An inventory of shipped features, not an executable-plugin loader. Entries describe the
 * existing viewers/tools; they have no separate installation, permissions or update lifecycle.
 */
private enum class BuiltInPlugin(
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
internal fun InAppPluginsScreen(onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<BuiltInPlugin?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("plugins_screen"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = stringResource(R.string.plugins_title))
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = pluginContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s24),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s24),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
                    AtomicText(stringResource(R.string.plugins_included), AtomicTextRole.DisplayCard)
                    AtomicText(stringResource(R.string.plugins_intro), AtomicTextRole.BodySecondary)
                }
                PluginCardGrid { selected = it }
            }
        }
    }

    selected?.let { plugin ->
        val title = stringResource(plugin.title)
        AtomicTopDialog(onDismiss = { selected = null }, testTag = "plugins_about_dialog") {
            BalancedPopupHeader(
                title,
                stringResource(R.string.plugins_back),
                onClose = { selected = null },
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
}

@Composable
private fun PluginCardGrid(onDetails: (BuiltInPlugin) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= pluginTwoColumnMinWidth) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16)) {
            BuiltInPlugin.entries.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
                ) {
                    row.forEach { plugin ->
                        PluginCard(plugin, Modifier.weight(1f).fillMaxHeight()) { onDetails(plugin) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: BuiltInPlugin,
    modifier: Modifier,
    onDetails: () -> Unit,
) {
    val colors = Atomic.colors
    val title = stringResource(plugin.title)
    Column(
        modifier =
            modifier
                .background(colors.surfaceCard, AtomicShape.lg)
                .border(AtomicBorder.hair, colors.borderHair, AtomicShape.lg)
                .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtomicIconTile(plugin.icon, tint = colors.accentText, containerColor = colors.accent.copy(alpha = 0.12f))
            Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
                AtomicText(title, AtomicTextRole.NameLarge, Modifier.semantics { heading() })
                AtomicText(plugin.formats, AtomicTextRole.MonoMeta)
            }
        }
        Spacer(Modifier.weight(1f))
        AtomicDivider()
        AtomicButton(
            text = "About v${plugin.version}",
            onClick = onDetails,
            modifier =
                Modifier
                    .align(Alignment.End)
                    .semantics { contentDescription = "About $title" }
                    .testTag("plugins_details_${plugin.name.lowercase()}"),
            variant = AtomicButtonVariant.Text,
        )
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
