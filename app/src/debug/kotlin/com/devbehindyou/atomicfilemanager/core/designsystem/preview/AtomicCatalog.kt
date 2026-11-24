package com.devbehindyou.atomicfilemanager.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicCheckbox
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicMeter
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicProgressBar
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicSwitch
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicFileType
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicFileTypeColors
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicErrorState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuSheet
import com.devbehindyou.atomicfilemanager.ui.components.AtomicTopDialog

@Composable
internal fun AtomicComponentCatalog(darkTheme: Boolean) {
    AtomicTheme(darkTheme = darkTheme, wallpaperAccent = false) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Atomic.colors.background)
                    .verticalScroll(rememberScrollState())
                    .padding(AtomicSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
        ) {
            AtomicText("Component catalogue", AtomicTextRole.DisplayTitle)
            CatalogSection("Actions") { CatalogActions() }
            CatalogSection("Controls") { CatalogControls() }
            CatalogSection("Semantic colors") { CatalogColors() }
            CatalogSection("File families") { CatalogFileFamilies() }
            CatalogSection("Feedback") { CatalogFeedback() }
        }
    }
}

@Composable
private fun CatalogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Atomic.colors.surfaceCard, AtomicShape.lg)
                .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        AtomicText(title, AtomicTextRole.DisplayCard)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogActions() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicButton("Primary", onClick = {})
        AtomicButton("Secondary", onClick = {}, variant = AtomicButtonVariant.Ghost)
        AtomicButton("Quiet action", onClick = {}, variant = AtomicButtonVariant.Text)
        AtomicButton("Move to trash", onClick = {}, variant = AtomicButtonVariant.Destructive)
        AtomicButton("Unavailable", onClick = {}, enabled = false)
        AtomicIconButton(AtomicIcons.Search, "Search sample", onClick = {})
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogControls() {
    var selected by rememberSaveable { mutableStateOf(true) }
    var checked by rememberSaveable { mutableStateOf(false) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var range by rememberSaveable { mutableStateOf(0.5f) }
    var name by rememberSaveable { mutableStateOf("Tickets/2026") }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicChip("Filter", selected = selected, onSelectedChange = { selected = it })
        AtomicCheckbox(
            checked = checked,
            onCheckedChange = { checked = it },
            modifier = Modifier.semantics { contentDescription = "Sample checkbox" },
        )
        AtomicSwitch(
            checked = enabled,
            onCheckedChange = { enabled = it },
            modifier = Modifier.semantics { contentDescription = "Sample switch" },
        )
    }
    AtomicTextField(
        value = name,
        onValueChange = { name = it },
        label = "Folder name",
        errorText = if ('/' in name) "A folder name cannot contain a slash." else null,
    )
    Slider(
        value = range,
        onValueChange = { range = it },
        modifier = Modifier.semantics { contentDescription = "Sample range" },
    )
    AtomicMeter(fraction = 0.32f, contentDescription = "Phone, 32 percent used")
    AtomicMeter(fraction = 0.90f, contentDescription = "SD card, 90 percent used, nearly full")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogColors() {
    val colors = Atomic.colors
    val samples =
        listOf(
            "Accent" to colors.accent,
            "Live" to colors.live,
            "High usage" to colors.meterHigh,
            "Error" to colors.error,
            "Surface" to colors.surfaceInset,
        )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        samples.forEach { (label, color) ->
            Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
                Box(Modifier.size(36.dp).background(color, AtomicShape.sm))
                AtomicText(label, AtomicTextRole.MonoMeta)
            }
        }
    }
}

@Composable
private fun CatalogFileFamilies() {
    var selectedName by rememberSaveable { mutableStateOf("Field notes.pdf") }
    val dark = Atomic.colors.isDark
    catalogFiles.forEach { (name, icon, family) ->
        AtomicFileRow(
            name = name,
            meta = family.name.lowercase().replaceFirstChar(Char::uppercase),
            icon = icon,
            selectionMode = true,
            selected = selectedName == name,
            onClick = { selectedName = name },
            leading = {
                AtomicIconTile(icon, tint = Color(AtomicFileTypeColors.icon(family, dark)))
            },
        )
    }
}

private data class CatalogFile(
    val name: String,
    val icon: ImageVector,
    val type: AtomicFileType,
)

private val catalogFiles =
    listOf(
        CatalogFile("Field notes.pdf", AtomicIcons.Pdf, AtomicFileType.PDF),
        CatalogFile("Weekend.jpg", AtomicIcons.Image, AtomicFileType.IMAGE),
        CatalogFile("Sample recording.mp3", AtomicIcons.Audio, AtomicFileType.AUDIO),
        CatalogFile("Sample clip.mp4", AtomicIcons.Video, AtomicFileType.VIDEO),
        CatalogFile("Project archive.zip", AtomicIcons.Archive, AtomicFileType.ARCHIVE),
        CatalogFile("Sample installer.apk", AtomicIcons.Apk, AtomicFileType.APK),
        CatalogFile("Checklist.txt", AtomicIcons.Text, AtomicFileType.TEXT),
        CatalogFile("Release notes.docx", AtomicIcons.Document, AtomicFileType.DOCUMENT),
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogFeedback() {
    var showCommands by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    AtomicEmptyState("No matching files", detail = "Try a different file type.")
    AtomicErrorState("Source disconnected", "Reconnect the source, then retry.")
    AtomicProgressBar(fraction = 0.42f, contentDescription = "Sample progress, 42 percent")
    AtomicLoading("Loading folder…")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicButton("Open command sheet", onClick = { showCommands = true })
        AtomicButton("Open compact popup", onClick = { showOptions = true })
    }
    if (showCommands) {
        AtomicMenuSheet(
            label = "Commands",
            actions = listOf(AtomicMenuAction("Done", AtomicIcons.Close, onClick = { showCommands = false })),
            onDismiss = { showCommands = false },
        )
    }
    if (showOptions) {
        AtomicTopDialog(onDismiss = { showOptions = false }, testTag = "catalog_options") {
            AtomicText("File options", AtomicTextRole.DisplayCard)
            AtomicButton("Close", onClick = { showOptions = false })
        }
    }
}

@Preview(name = "Catalogue light", widthDp = 400, heightDp = 1000)
@Composable
internal fun CatalogLightPreview() = AtomicComponentCatalog(darkTheme = false)

@Preview(name = "Catalogue dark", widthDp = 400, heightDp = 1000)
@Composable
internal fun CatalogDarkPreview() = AtomicComponentCatalog(darkTheme = true)

@Preview(name = "Catalogue narrow with large text", widthDp = 320, heightDp = 1000, fontScale = 2f)
@Composable
internal fun CatalogLargeFontPreview() = AtomicComponentCatalog(darkTheme = false)
