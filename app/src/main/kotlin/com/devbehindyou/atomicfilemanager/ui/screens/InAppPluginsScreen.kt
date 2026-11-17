package com.devbehindyou.atomicfilemanager.ui.screens

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
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.ui.components.BuiltInPlugin
import com.devbehindyou.atomicfilemanager.ui.components.BuiltInPluginAboutDialog

private val pluginContentWidth = 960.dp
private val pluginTwoColumnMinWidth = 568.dp

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
        BuiltInPluginAboutDialog(plugin, stringResource(R.string.plugins_back), onDismiss = { selected = null })
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
