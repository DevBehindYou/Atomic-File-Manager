package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicCommandGrid
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet

/** Everything the palette can do (ALL_IN_ONE_PLAN.md 4.4). [keywords] are other words people type. */
enum class PaletteCommand(
    val title: String,
    val group: String,
    val keywords: List<String> = emptyList(),
) {
    SEARCH("Search all storage", "Go to", listOf("find", "lookup")),
    FILES("Files", "Go to", listOf("browse", "folders", "explorer")),
    DOWNLOADS("Downloads", "Go to", listOf("download")),
    IMAGES("Images", "Go to", listOf("photos", "pictures", "gallery")),
    VIDEOS("Videos", "Go to", listOf("movies", "clips")),
    AUDIO("Audio", "Go to", listOf("music", "songs", "recordings")),
    DOCUMENTS("Documents", "Go to", listOf("docs", "pdf", "office")),
    ARCHIVES("Archives", "Go to", listOf("zip", "compress", "extract", "rar", "7z")),
    APKS("APK files", "Go to", listOf("installers", "packages")),
    STORAGE("Storage", "Go to", listOf("space", "volumes", "sd card", "usb")),
    ANALYSIS("Storage analysis", "Tools", listOf("large files", "duplicates", "cleanup", "free space")),
    TRASH("Trash", "Tools", listOf("bin", "recycle", "deleted", "restore")),
    OPERATIONS("Operations", "Tools", listOf("copy", "move", "progress", "transfers", "queue")),
    APPS("Apps", "Tools", listOf("app manager", "uninstall", "backup apk")),
    PRIVATE("Private files", "Tools", listOf("hidden", "vault", "secure", "encrypt")),
    WIFI_SHARE("Wi-Fi share", "Tools", listOf("send to computer", "pc", "browser", "transfer", "wifi")),
    SETTINGS("Settings", "Settings", listOf("preferences", "options")),
    THEME("Theme", "Settings", listOf("dark mode", "light mode", "colours", "colors", "appearance")),
    ABOUT("About Atomic File Manager", "Settings", listOf("version", "privacy")),
}

/** Matching for the palette; pure so it is unit-tested. */
object CommandPalette {
    /**
     * Commands for [query], best first. Every word typed must start a word of the title or a
     * keyword. Title prefixes rank above title words, which rank above keywords; ties keep the
     * listed order. A blank query lists everything.
     */
    fun match(query: String): List<PaletteCommand> {
        val words = tokens(query)
        if (words.isEmpty()) return PaletteCommand.entries
        return PaletteCommand.entries
            .mapNotNull { command -> score(command, query.trim().lowercase(), words)?.let { command to it } }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun score(
        command: PaletteCommand,
        whole: String,
        words: List<String>,
    ): Int? {
        val title = command.title.lowercase()
        val titleWords = tokens(title)
        val keywordWords = command.keywords.flatMap(::tokens)
        if (words.any { w -> titleWords.none { it.startsWith(w) } && keywordWords.none { it.startsWith(w) } }) {
            return null
        }
        return when {
            title.startsWith(whole) -> 0
            words.all { w -> titleWords.any { it.startsWith(w) } } -> 1
            else -> 2
        }
    }

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
}

/**
 * The command palette: type to filter, tap or press Enter to run the first match. Opened from the
 * header on Home and with Ctrl+K on a keyboard.
 */
@Composable
fun CommandPaletteSheet(
    onRun: (PaletteCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = CommandPalette.match(query)
    AtomicSheet(label = "Commands", onDismiss = onDismiss) {
        AtomicTextField(
            value = query,
            onValueChange = { query = it },
            label = "Type a command",
            placeholder = "Trash, theme, downloads…",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { matches.firstOrNull()?.let(onRun) }),
            modifier = Modifier.fillMaxWidth().testTag("palette_query"),
        )
        if (matches.isEmpty()) {
            AtomicText("No command matches “$query”.", AtomicTextRole.BodySecondary)
        }
        AtomicCommandGrid(
            matches.map { command ->
                AtomicMenuAction(
                    label = command.title,
                    icon = command.icon,
                    description = command.group,
                    onClick = { onRun(command) },
                    testTag = "palette_${command.name.lowercase()}",
                )
            },
        )
    }
}

private val PaletteCommand.icon
    get() =
        when (this) {
            PaletteCommand.SEARCH -> AtomicIcons.Search
            PaletteCommand.FILES -> AtomicIcons.Files
            PaletteCommand.DOWNLOADS -> AtomicIcons.Download
            PaletteCommand.IMAGES -> AtomicIcons.Image
            PaletteCommand.VIDEOS -> AtomicIcons.Video
            PaletteCommand.AUDIO -> AtomicIcons.Audio
            PaletteCommand.DOCUMENTS -> AtomicIcons.Document
            PaletteCommand.ARCHIVES -> AtomicIcons.Archive
            PaletteCommand.APKS, PaletteCommand.APPS -> AtomicIcons.Apk
            PaletteCommand.STORAGE -> AtomicIcons.Storage
            PaletteCommand.ANALYSIS -> AtomicIcons.Cleanup
            PaletteCommand.TRASH -> AtomicIcons.Trash
            PaletteCommand.OPERATIONS -> AtomicIcons.Tasks
            PaletteCommand.PRIVATE -> AtomicIcons.Lock
            PaletteCommand.WIFI_SHARE -> AtomicIcons.Network
            PaletteCommand.SETTINGS -> AtomicIcons.Settings
            PaletteCommand.THEME -> AtomicIcons.Theme
            PaletteCommand.ABOUT -> AtomicIcons.Info
        }
