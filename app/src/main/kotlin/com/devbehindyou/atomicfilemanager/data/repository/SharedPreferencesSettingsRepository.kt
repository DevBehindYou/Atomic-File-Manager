package com.devbehindyou.atomicfilemanager.data.repository

import android.content.Context
import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.DEFAULT_HIDDEN_FOLDER
import com.devbehindyou.atomicfilemanager.domain.model.DEFAULT_TRASH_RETENTION_DAYS
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.TRASH_RETENTION_CHOICES
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.domain.model.normalizeHiddenFolder
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Settings live in a small SharedPreferences file. They are read once, synchronously, at
 * construction because the theme must be known before the first app frame is drawn.
 * Activity startup initializes this process-shared instance on IO while retaining the splash;
 * headless callers must also construct it on IO. No default snapshot is published before the read.
 */
class SharedPreferencesSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        write(state.updateAndGet(transform))
    }

    private fun read(): AppSettings =
        AppSettings(
            themeMode = prefs.getString(KEY_THEME, null).toEnumOrNull<ThemeMode>() ?: ThemeMode.SYSTEM,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
            defaultHideMode = prefs.getString(KEY_DEFAULT_HIDE_MODE, null).toEnumOrNull<HideMode>(),
            requireAuthForHidden = prefs.getBoolean(KEY_REQUIRE_AUTH, false),
            showHiddenFiles = prefs.getBoolean(KEY_SHOW_HIDDEN, true),
            fileViewMode = prefs.getString(KEY_FILE_VIEW_MODE, null).toEnumOrNull<FileViewMode>() ?: FileViewMode.LIST,
            hiddenFolder =
                prefs.getString(KEY_HIDDEN_FOLDER, null)?.let(::normalizeHiddenFolder) ?: DEFAULT_HIDDEN_FOLDER,
            useTrash = prefs.getBoolean(KEY_USE_TRASH, true),
            trashRetentionDays =
                prefs.getInt(KEY_TRASH_RETENTION, DEFAULT_TRASH_RETENTION_DAYS)
                    .takeIf { it in TRASH_RETENTION_CHOICES } ?: DEFAULT_TRASH_RETENTION_DAYS,
        )

    private fun write(settings: AppSettings) {
        val editor =
            prefs.edit()
                .putString(KEY_THEME, settings.themeMode.name)
                .putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
                .putBoolean(KEY_REQUIRE_AUTH, settings.requireAuthForHidden)
                .putBoolean(KEY_SHOW_HIDDEN, settings.showHiddenFiles)
                .putString(KEY_FILE_VIEW_MODE, settings.fileViewMode.name)
                .putString(KEY_HIDDEN_FOLDER, settings.hiddenFolder)
                .putBoolean(KEY_USE_TRASH, settings.useTrash)
                .putInt(KEY_TRASH_RETENTION, settings.trashRetentionDays)
        val hideMode = settings.defaultHideMode
        if (hideMode == null) {
            editor.remove(KEY_DEFAULT_HIDE_MODE)
        } else {
            editor.putString(KEY_DEFAULT_HIDE_MODE, hideMode.name)
        }
        editor.apply()
    }

    private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    private companion object {
        const val PREFS_NAME = "atomic_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_DEFAULT_HIDE_MODE = "default_hide_mode"
        const val KEY_REQUIRE_AUTH = "require_auth_for_hidden"
        const val KEY_SHOW_HIDDEN = "show_hidden_files"
        const val KEY_FILE_VIEW_MODE = "file_view_mode"
        const val KEY_HIDDEN_FOLDER = "hidden_folder"
        const val KEY_USE_TRASH = "use_trash"
        const val KEY_TRASH_RETENTION = "trash_retention_days"
    }
}
