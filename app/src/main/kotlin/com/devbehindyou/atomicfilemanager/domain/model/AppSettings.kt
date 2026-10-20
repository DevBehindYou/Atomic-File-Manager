package com.devbehindyou.atomicfilemanager.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Presentation of files and folders in the browser. */
enum class FileViewMode { LIST, GRID }

/** Where "Hide from Gallery" puts files, relative to the root of the storage the file is on. */
const val DEFAULT_HIDDEN_FOLDER = "Atomic File Manager/Hidden"

/**
 * Cleans a folder typed by the user (relative to a storage root). Returns `null` when it cannot be
 * used, for example when it is empty or tries to leave the storage root with `..`.
 */
fun normalizeHiddenFolder(raw: String): String? {
    val parts = raw.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }
    val usable =
        parts.isNotEmpty() &&
            parts.none { part -> part == "." || part == ".." || part.any { it == ':' || it.isISOControl() } }
    return if (usable) parts.joinToString("/") else null
}

/**
 * Preferences that outlive a session.
 *
 * [defaultHideMode] is `null` when the user wants to be asked how to hide each file. When it is set,
 * hiding a file uses that method straight away.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** WALLPAPER COLOURS: swaps only the accent, with guard rails (ATOMIC_UI_PLAN.md §4.5). */
    val dynamicColor: Boolean = false,
    val defaultHideMode: HideMode? = null,
    val requireAuthForHidden: Boolean = false,
    val showHiddenFiles: Boolean = true,
    val fileViewMode: FileViewMode = FileViewMode.LIST,
    val hiddenFolder: String = DEFAULT_HIDDEN_FOLDER,
    /** Delete moves to the Trash where the storage allows it; off means deletes are permanent. */
    val useTrash: Boolean = true,
    /** Days an item stays in the Trash before it is deleted for good. */
    val trashRetentionDays: Int = DEFAULT_TRASH_RETENTION_DAYS,
)

const val DEFAULT_TRASH_RETENTION_DAYS = 30

/** The choices offered in Settings (ALL_IN_ONE_PLAN.md 1.1). */
val TRASH_RETENTION_CHOICES = listOf(7, 30, 60)
