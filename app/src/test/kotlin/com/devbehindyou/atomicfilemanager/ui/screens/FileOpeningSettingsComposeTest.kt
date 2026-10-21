package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenFamily
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleKey
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleScope
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileOpeningSettingsComposeTest {
    @get:Rule val compose = createComposeRule()
    private val repository = object : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings(themeMode = ThemeMode.DARK))
        override fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
    }

    @Test
    fun familyRuleCanBeChangedAndResetWithoutChangingAppearance() {
        compose.setContent { AtomicTheme(darkTheme = false, wallpaperAccent = false) { FileOpeningSettingsSheet(repository, {}) } }
        val key = FileOpenRuleKey(FileOpenRuleScope.FAMILY, FileOpenFamily.PDF.name)
        compose.onNodeWithContentDescription("PDFs").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ask every time").performClick()
        assertEquals(FileOpenTarget(FileOpenMode.ASK), repository.settings.value.fileOpening.rules[key])
        compose.onNodeWithContentDescription("PDFs").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Reset this rule").performScrollTo().performClick()
        assertEquals(emptyMap<FileOpenRuleKey, FileOpenTarget>(), repository.settings.value.fileOpening.rules)
        assertEquals(ThemeMode.DARK, repository.settings.value.themeMode)
    }

    @Test
    fun resetAllRequiresConfirmation() {
        repository.update { it.copy(fileOpening = it.fileOpening.copy(defaultMode = FileOpenMode.ASK)) }
        compose.setContent { AtomicTheme(darkTheme = false, wallpaperAccent = false) { FileOpeningSettingsSheet(repository, {}) } }
        compose.onNodeWithContentDescription("Reset all file-opening rules").performScrollTo().performClick()
        assertEquals(FileOpenMode.ASK, repository.settings.value.fileOpening.defaultMode)
        compose.onNodeWithContentDescription("Reset rules").performScrollTo().performClick()
        assertEquals(FileOpenMode.BUILT_IN, repository.settings.value.fileOpening.defaultMode)
    }
}
