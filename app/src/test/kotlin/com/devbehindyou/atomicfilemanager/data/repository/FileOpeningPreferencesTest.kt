package com.devbehindyou.atomicfilemanager.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleKey
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleScope
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import com.devbehindyou.atomicfilemanager.domain.model.FileOpeningPreferences
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 34], application = Application::class)
class FileOpeningPreferencesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun realComponentAndRulesSurviveReloadWithoutChangingOtherSettings() {
        val key = FileOpenRuleKey(FileOpenRuleScope.EXTENSION, "pdf")
        val target = FileOpenTarget(FileOpenMode.EXTERNAL, "reader.app/reader.app.View", "Reader")
        val repository = SharedPreferencesSettingsRepository(context)
        repository.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                fileOpening = FileOpeningPreferences(FileOpenMode.ASK).withRule(key, target),
            )
        }
        val restored = SharedPreferencesSettingsRepository(context).settings.value
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertEquals(FileOpenMode.ASK, restored.fileOpening.defaultMode)
        assertEquals(target, restored.fileOpening.rules[key])
    }

    @Test
    fun corruptOversizedOrFutureVersionDataFallsBackWithoutCrashing() {
        for (raw in listOf("not json", "{\"version\":99}", " ".repeat(262_145))) {
            assertEquals(FileOpeningPreferences(), FileOpeningPreferencesCodec.decode(raw))
        }
    }

    @Test
    fun malformedRowsAreSkippedWhileValidRulesRemain() {
        val raw = """{"version":1,"default":"ASK","rules":[
          {"scope":"EXTENSION","key":"PDF","mode":"BUILT_IN"},
          {"scope":"MIME","key":"image/*","mode":"ASK"},
          {"scope":"FAMILY","key":"NOT_A_FAMILY","mode":"ASK"},
          {"scope":"EXTENSION","key":"txt","mode":"BUILT_IN","component":"bad/app"},
          {"scope":"EXTENSION","key":"kt","mode":"EXTERNAL","component":"invalid"}
        ]}"""
        val restored = FileOpeningPreferencesCodec.decode(raw)
        assertEquals(FileOpenMode.ASK, restored.defaultMode)
        assertEquals(
            mapOf(FileOpenRuleKey(FileOpenRuleScope.EXTENSION, "pdf") to FileOpenTarget(FileOpenMode.BUILT_IN)),
            restored.rules,
        )
    }

    @Test
    fun resettingOnlyOpeningPreferencesKeepsExistingFilesAndAppearanceOptions() {
        val repository = SharedPreferencesSettingsRepository(context)
        repository.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                showHiddenFiles = false,
                fileOpening = FileOpeningPreferences(FileOpenMode.EXTERNAL),
            )
        }
        repository.update { it.copy(fileOpening = FileOpeningPreferences()) }
        val restored = SharedPreferencesSettingsRepository(context).settings.value
        assertEquals(FileOpeningPreferences(), restored.fileOpening)
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertTrue(!restored.showHiddenFiles)
    }
}
