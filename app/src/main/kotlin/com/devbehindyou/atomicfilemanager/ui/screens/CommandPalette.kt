package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.runtime.Composable
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons

/** Everything the palette can do (ALL_IN_ONE_PLAN.md 4.4). [keywords] are other words people type. */
enum class PaletteCommand(
    val title: String,
    val group: String,
    val keywords: List<String> = emptyList(),
) {
    SEARCH("Search", "Go to", listOf("all storage", "find", "lookup")),
    FILES("Files", "Go to", listOf("browse", "folders", "explorer")),
    NEW_FOLDER("New folder", "Tools", listOf("create directory", "mkdir")),
    DOWNLOADS("Downloads", "Go to", listOf("download")),
    IMAGES("Images", "Go to", listOf("photos", "pictures", "gallery")),
    VIDEOS("Videos", "Go to", listOf("movies", "clips")),
    AUDIO("Audio", "Go to", listOf("music", "songs", "recordings")),
    DOCUMENTS("Documents", "Go to", listOf("docs", "pdf", "office")),
    ARCHIVES("Archives", "Go to", listOf("zip", "compress", "extract", "rar", "7z")),
    APKS("APK files", "Go to", listOf("installers", "packages")),
    STORAGE("Storage", "Go to", listOf("space", "volumes", "sd card", "usb")),
    ANALYSIS("Analysis", "Tools", listOf("storage analysis", "large files", "duplicates", "cleanup", "free space")),
    TRASH("Trash", "Tools", listOf("bin", "recycle", "deleted", "restore")),
    OPERATIONS("Tasks", "Tools", listOf("operations", "copy", "move", "progress", "transfers", "queue")),
    APPS("App manager", "Tools", listOf("apps", "uninstall", "backup apk")),
    PRIVATE("Private files", "Tools", listOf("hidden", "vault", "secure", "encrypt")),
    FOLDER_COMPARE("Folder compare", "Tools", listOf("sync", "differences")),
    WIFI_SHARE("Wi-Fi Share", "Tools", listOf("send to computer", "pc", "browser", "transfer", "wifi")),
    PLUGINS("In-app plugins", "Tools", listOf("built-in viewers", "bundled tools")),
    CONNECTIONS("Connections", "Tools", listOf("linked folders", "network locations")),
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

/** Opens the searchable command sheet from a tab header or Ctrl+K. */
@Composable
fun CommandPaletteSheet(
    onRun: (PaletteCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    CommandPaletteDialog(onRun = onRun, onDismiss = onDismiss)
}

private val palettePrimaryCommands =
    listOf(
        PaletteCommand.FILES,
        PaletteCommand.NEW_FOLDER,
        PaletteCommand.SEARCH,
        PaletteCommand.OPERATIONS,
        PaletteCommand.ANALYSIS,
        PaletteCommand.TRASH,
        PaletteCommand.PRIVATE,
        PaletteCommand.FOLDER_COMPARE,
        PaletteCommand.APPS,
        PaletteCommand.WIFI_SHARE,
        PaletteCommand.PLUGINS,
        PaletteCommand.CONNECTIONS,
    )

internal val paletteBrowseOrder = palettePrimaryCommands + (PaletteCommand.entries - palettePrimaryCommands.toSet())

internal val PaletteCommand.icon
    get() =
        when (this) {
            PaletteCommand.SEARCH -> AtomicIcons.Search
            PaletteCommand.FILES -> AtomicIcons.Files
            PaletteCommand.NEW_FOLDER -> AtomicIcons.NewFolder
            PaletteCommand.DOWNLOADS -> AtomicIcons.Download
            PaletteCommand.IMAGES -> AtomicIcons.Image
            PaletteCommand.VIDEOS -> AtomicIcons.Video
            PaletteCommand.AUDIO -> AtomicIcons.Audio
            PaletteCommand.DOCUMENTS -> AtomicIcons.Document
            PaletteCommand.ARCHIVES -> AtomicIcons.Archive
            PaletteCommand.APKS, PaletteCommand.APPS -> AtomicIcons.Apk
            PaletteCommand.STORAGE -> AtomicIcons.Storage
            PaletteCommand.ANALYSIS -> AtomicIcons.Storage
            PaletteCommand.TRASH -> AtomicIcons.Trash
            PaletteCommand.OPERATIONS -> AtomicIcons.Tasks
            PaletteCommand.PRIVATE -> AtomicIcons.Lock
            PaletteCommand.FOLDER_COMPARE -> AtomicIcons.DualPane
            PaletteCommand.WIFI_SHARE -> AtomicIcons.Network
            PaletteCommand.PLUGINS -> AtomicIcons.Plug
            PaletteCommand.CONNECTIONS -> AtomicIcons.Cloud
            PaletteCommand.SETTINGS -> AtomicIcons.Settings
            PaletteCommand.THEME -> AtomicIcons.Theme
            PaletteCommand.ABOUT -> AtomicIcons.Info
        }
