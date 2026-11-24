package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicStatusPill
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.ui.components.AtomicTopDialog
import com.devbehindyou.atomicfilemanager.ui.components.BalancedPopupHeader

private val aboutContentWidth = 720.dp

private enum class AboutDetails(val title: String, val tag: String) {
    HELP("Everyday file operations", "about_help"),
    NOTICES("Fonts & icons", "about_notices"),
}

@Composable
internal fun AboutAtomicScreen(
    version: String?,
    onBack: () -> Unit,
    onOpenBundledTools: () -> Unit,
) {
    var details by rememberSaveable { mutableStateOf<AboutDetails?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("about_screen"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = "About")
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = aboutContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s24),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s24),
            ) {
                AboutSummary(version, onOpenBundledTools)
                Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
                    AtomicSectionLabel("Help & notices")
                    Column(
                        Modifier.fillMaxWidth().clip(AtomicShape.lg).background(Atomic.colors.surfaceCard),
                    ) {
                        AboutDetailRow(
                            detail = AboutDetails.HELP,
                            description = "Browse, long-press, select and choose a destination",
                            icon = AtomicIcons.Info,
                            onClick = { details = AboutDetails.HELP },
                        )
                        AtomicDivider(Modifier.padding(horizontal = AtomicSpacing.s16))
                        AboutDetailRow(
                            detail = AboutDetails.NOTICES,
                            description = "Font and icon credits",
                            icon = AtomicIcons.Document,
                            onClick = { details = AboutDetails.NOTICES },
                        )
                    }
                }
            }
        }
    }

    details?.let { AboutDetailsDialog(it, onDismiss = { details = null }) }
}

@Composable
private fun AboutSummary(
    version: String?,
    onOpenBundledTools: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Atomic.colors.surfaceCard, AtomicShape.lg)
                .padding(AtomicSpacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        AtomicText(
            "Atomic File Manager",
            AtomicTextRole.DisplayCard,
            Modifier.semantics { heading() },
        )
        AtomicStatusPill(if (version.isNullOrBlank()) "Version unavailable" else "Version $version")
        AtomicText("A local-first file manager for Android.", AtomicTextRole.Body)
        AtomicText("Android 9+ (API 28) · Kotlin / Compose", AtomicTextRole.BodySecondary)
        AtomicButton("Bundled tools", onClick = onOpenBundledTools)
    }
}

@Composable
private fun AboutDetailRow(
    detail: AboutDetails,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = AtomicSize.touchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(detail.tag)
                .padding(AtomicSpacing.s16),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtomicIconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s2)) {
            AtomicText(detail.title, AtomicTextRole.Name)
            AtomicText(description, AtomicTextRole.BodySecondary)
        }
        Icon(
            AtomicIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(AtomicSize.iconSmall),
            tint = Atomic.colors.contentSecondary,
        )
    }
}

@Composable
private fun AboutDetailsDialog(
    detail: AboutDetails,
    onDismiss: () -> Unit,
) {
    AtomicTopDialog(onDismiss, "${detail.tag}_dialog") {
        BalancedPopupHeader(
            detail.title,
            "Close ${if (detail == AboutDetails.HELP) "help" else "font and icon credits"}",
            onDismiss,
            detail.tag,
            buttonVariant = AtomicIconButtonVariant.Inset,
        )
        AtomicDivider(Modifier.padding(vertical = AtomicSpacing.s8))
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            when (detail) {
                AboutDetails.HELP -> {
                    AtomicText("1. Open Files and choose a storage location.", AtomicTextRole.Body)
                    AtomicText(
                        "2. Long-press an item to open its actions, or select multiple items.",
                        AtomicTextRole.Body,
                    )
                    AtomicText(
                        "3. Choose Copy or Move, then open the destination and tap Paste here.",
                        AtomicTextRole.Body,
                    )
                    AtomicText("4. Resolve any conflicts and check Tasks for progress.", AtomicTextRole.Body)
                }
                AboutDetails.NOTICES -> {
                    AtomicText(
                        "Bebas Neue, Hanken Grotesk and JetBrains Mono use the SIL Open Font License 1.1.",
                        AtomicTextRole.Body,
                    )
                    AtomicText("Tabler Icons · MIT License · © 2020–2026 Paweł Kuna", AtomicTextRole.Body)
                    AtomicText("Additional Material Icons · Apache License 2.0", AtomicTextRole.Body)
                }
            }
        }
    }
}
