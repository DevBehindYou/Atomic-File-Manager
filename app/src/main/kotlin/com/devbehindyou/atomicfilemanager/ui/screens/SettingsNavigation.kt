package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicSwitch
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.ui.components.AtomicTopDialog
import com.devbehindyou.atomicfilemanager.ui.components.BalancedPopupHeader

private data class SettingsLink(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val destination: SettingsSheet,
)

/** Exact three-section, ten-row Settings root specified by screens.js. */
@Composable
internal fun SettingsRootContent(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onOpen: (SettingsSheet) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("settings_screen")) {
        val compact = maxWidth < 760.dp
        val sectionGap = if (compact) 26.dp else 28.dp
        val groupPadding = if (compact) AtomicSpacing.s16 else AtomicSpacing.screenMargin
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtomicSpacing.screenMargin)
                .padding(vertical = sectionGap),
            verticalArrangement = Arrangement.spacedBy(sectionGap),
        ) {
            SettingsLinkGroup(
                "Make it yours",
                listOf(
                    SettingsLink("Appearance", themeDetail(settings.themeMode), AtomicIcons.Sun, SettingsSheet.APPEARANCE),
                    SettingsLink("File browsing", "List, sorting and hidden files", AtomicIcons.Files, SettingsSheet.BROWSING),
                    SettingsLink("Opening files", "Built-in previews and external apps", AtomicIcons.Document, SettingsSheet.OPENING),
                    SettingsLink("In-app plugins", "Eight bundled tools · v0.2", AtomicIcons.Plug, SettingsSheet.PLUGINS),
                ),
                groupPadding,
                onOpen,
            )
            SettingsLinkGroup(
                "Access & privacy",
                listOf(
                    SettingsLink("Storage permissions", "Shared files and linked folders", AtomicIcons.Security, SettingsSheet.ACCESS),
                    SettingsLink("Private files", "Locked behind authentication", AtomicIcons.Lock, SettingsSheet.PRIVACY),
                    SettingsLink("Hidden files", "Visibility is separate from privacy", AtomicIcons.Visible, SettingsSheet.HIDDEN),
                    SettingsLink("Transfer bubbles", "Optional · up to 7 bubbles", AtomicIcons.Tasks, SettingsSheet.BUBBLES),
                ),
                groupPadding,
                onOpen,
            )
            SettingsLinkGroup(
                "About",
                listOf(
                    SettingsLink("About Atomic", "Version, licenses and help", AtomicIcons.Info, SettingsSheet.ABOUT),
                    SettingsLink("Cloud Connect", "Planned · no live sign-in available", AtomicIcons.Cloud, SettingsSheet.CLOUD),
                ),
                groupPadding,
                onOpen,
            )
        }
    }
}

private fun themeDetail(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.LIGHT -> "Light theme"
        ThemeMode.DARK -> "Dark theme"
        ThemeMode.SYSTEM -> "System theme"
    }

@Composable
private fun SettingsLinkGroup(
    title: String,
    links: List<SettingsLink>,
    horizontalPadding: Dp,
    onOpen: (SettingsSheet) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16)) {
        AtomicText(title, AtomicTextRole.DisplayCard, Modifier.semantics { heading() })
        Column(
            Modifier.fillMaxWidth()
                .background(Atomic.colors.surfaceCard, AtomicShape.lg)
                .border(1.dp, Atomic.colors.borderHair, AtomicShape.lg)
                .padding(horizontal = horizontalPadding, vertical = AtomicSpacing.s8),
        ) {
            links.forEachIndexed { index, link ->
                if (index > 0) AtomicDivider()
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = AtomicSize.touchTarget)
                        .clickable(role = Role.Button, onClick = { onOpen(link.destination) })
                        .testTag("setting_link_${link.destination.name.lowercase()}")
                        .semantics { contentDescription = link.title }
                        .padding(horizontal = AtomicSpacing.s4, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(link.icon, null, Modifier.size(AtomicSize.iconSmall), tint = Atomic.colors.accentText)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
                        AtomicText(link.title, AtomicTextRole.Name)
                        AtomicText(link.detail, AtomicTextRole.MonoMeta)
                    }
                    Icon(AtomicIcons.ChevronRight, null, Modifier.size(18.dp), tint = Atomic.colors.contentSecondary)
                }
            }
        }
    }
}

@Composable
internal fun SettingsPreferencesDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AtomicTopDialog(onDismiss, "settings_preferences") {
        BalancedPopupHeader(title, "Close settings dialog", onDismiss, "settings_preferences_header")
        AtomicDivider(modifier = Modifier.padding(top = AtomicSpacing.s12, bottom = AtomicSpacing.screenMargin))
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
            content = content,
        )
    }
}

/** Quiet preference row instead of the legacy outlined toggle card. */
@Composable
internal fun SettingsPreferenceToggle(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AtomicSize.touchTarget)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = title }
            .padding(vertical = AtomicSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
            AtomicText(title, AtomicTextRole.Body)
            AtomicText(description, AtomicTextRole.MonoMeta)
        }
        AtomicSwitch(checked, onCheckedChange = null)
    }
}
