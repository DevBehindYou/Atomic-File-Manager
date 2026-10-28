package com.devbehindyou.atomicfilemanager.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.ui.startup.SettingsStartupLoader
import com.devbehindyou.atomicfilemanager.ui.startup.SettingsStartupState
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class SettingsStartupPreferencesTest {
    @Test
    fun `first ready value has saved theme color and authentication after IO-only preference reads`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("atomic_settings", Context.MODE_PRIVATE).edit()
                .putString("theme_mode", "DARK")
                .putBoolean("dynamic_color", true)
                .putBoolean("require_auth_for_hidden", true)
                .putBoolean("show_hidden_files", false)
                .commit()
            val caller = Thread.currentThread()
            val accesses = mutableListOf<Thread>()
            var writes = 0
            val recordingContext =
                object : ContextWrapper(context) {
                    override fun getSharedPreferences(
                        name: String,
                        mode: Int,
                    ): SharedPreferences {
                        accesses += Thread.currentThread()
                        val prefs = super.getSharedPreferences(name, mode)
                        return object : SharedPreferences by prefs {
                            override fun getString(
                                key: String?,
                                defValue: String?,
                            ): String? {
                                accesses += Thread.currentThread()
                                return prefs.getString(key, defValue)
                            }

                            override fun getBoolean(
                                key: String?,
                                defValue: Boolean,
                            ): Boolean {
                                accesses += Thread.currentThread()
                                return prefs.getBoolean(key, defValue)
                            }

                            override fun getInt(
                                key: String?,
                                defValue: Int,
                            ): Int {
                                accesses += Thread.currentThread()
                                return prefs.getInt(key, defValue)
                            }

                            override fun edit(): SharedPreferences.Editor {
                                writes++
                                return prefs.edit()
                            }
                        }
                    }
                }
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
                val shared by lazy { SharedPreferencesSettingsRepository(recordingContext) }
                val loader = SettingsStartupLoader(this, io) { shared }
                assertTrue(accesses.isEmpty())
                loader.start()
                val ready = withTimeout(5_000) { loader.state.filterIsInstance<SettingsStartupState.Ready>().first() }
                assertTrue(accesses.isNotEmpty())
                assertTrue(accesses.all { it !== caller })
                assertEquals(ThemeMode.DARK, ready.repository.settings.value.themeMode)
                assertTrue(ready.repository.settings.value.dynamicColor)
                assertTrue(ready.repository.settings.value.requireAuthForHidden)
                assertEquals(false, ready.repository.settings.value.showHiddenFiles)
                assertEquals(0, writes)
                ready.repository.update { it.copy(themeMode = ThemeMode.LIGHT) }
                val replacement = SettingsStartupLoader(this, io) { shared }
                replacement.start()
                val reloaded =
                    withTimeout(5_000) { replacement.state.filterIsInstance<SettingsStartupState.Ready>().first() }
                assertSame(ready.repository, reloaded.repository)
                assertEquals(ThemeMode.LIGHT, reloaded.repository.settings.value.themeMode)
                assertEquals(1, writes)
            }
        }
}
