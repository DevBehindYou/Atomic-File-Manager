package com.devbehindyou.atomicfilemanager.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.FileViewMode
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesSettingsRepositoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `defaults ask for the hide method, follow the system theme and show hidden files`() {
        val settings = SharedPreferencesSettingsRepository(context).settings.value

        assertEquals(AppSettings(), settings)
        assertNull(settings.defaultHideMode)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
    }

    @Test
    fun `changes survive creating the repository again`() {
        SharedPreferencesSettingsRepository(context).update {
            it.copy(
                themeMode = ThemeMode.DARK,
                dynamicColor = false,
                defaultHideMode = HideMode.PRIVATE_STORAGE,
                requireAuthForHidden = true,
                showHiddenFiles = false,
                fileViewMode = FileViewMode.GRID,
                hiddenFolder = "Vault/Photos",
            )
        }

        val reloaded = SharedPreferencesSettingsRepository(context).settings.value

        assertEquals(ThemeMode.DARK, reloaded.themeMode)
        assertEquals(false, reloaded.dynamicColor)
        assertEquals(HideMode.PRIVATE_STORAGE, reloaded.defaultHideMode)
        assertEquals(true, reloaded.requireAuthForHidden)
        assertEquals(false, reloaded.showHiddenFiles)
        assertEquals(FileViewMode.GRID, reloaded.fileViewMode)
        assertEquals("Vault/Photos", reloaded.hiddenFolder)
    }

    @Test
    fun `choosing ask every time clears a stored default hide method`() {
        val repository = SharedPreferencesSettingsRepository(context)
        repository.update { it.copy(defaultHideMode = HideMode.GALLERY) }
        repository.update { it.copy(defaultHideMode = null) }

        assertNull(SharedPreferencesSettingsRepository(context).settings.value.defaultHideMode)
    }

    @Test
    fun `unknown stored values fall back to the defaults instead of crashing`() {
        context.getSharedPreferences("atomic_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "SOLARIZED")
            .putString("default_hide_mode", "TELEPORT")
            .putString("file_view_mode", "CAROUSEL")
            .putString("hidden_folder", "../escape")
            .commit()

        val settings = SharedPreferencesSettingsRepository(context).settings.value

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(FileViewMode.LIST, settings.fileViewMode)
        assertNull(settings.defaultHideMode)
        assertEquals("Atomic File Manager/Hidden", settings.hiddenFolder)
    }
}
