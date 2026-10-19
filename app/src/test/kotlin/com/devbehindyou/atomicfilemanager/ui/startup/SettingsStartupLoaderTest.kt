package com.devbehindyou.atomicfilemanager.ui.startup

import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStartupLoaderTest {
    @Test
    fun `construction and initial access wait for IO rather than using the caller thread`() {
        val scheduler = TestCoroutineScheduler()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val caller = Thread.currentThread()
        val settings = SavedSettings()
        var factoryThread: Thread? = null
        Executors.newSingleThreadExecutor().use { worker ->
            worker.asCoroutineDispatcher().use { io ->
                val loader =
                    SettingsStartupLoader(owner, io) {
                        factoryThread = Thread.currentThread()
                        settings
                    }
                try {
                    loader.start()
                    assertSame(SettingsStartupState.Loading, loader.state.value)
                    assertEquals(null, factoryThread)
                    scheduler.runCurrent()
                    worker.submit {}.get(5, TimeUnit.SECONDS)
                    assertTrue(factoryThread !== caller)
                    assertSame(SettingsStartupState.Loading, loader.state.value)
                    scheduler.runCurrent()
                    assertSame(
                        settings,
                        assertInstanceOf(SettingsStartupState.Ready::class.java, loader.state.value).repository,
                    )
                    assertEquals(0, settings.writes)
                } finally {
                    owner.cancel()
                }
            }
        }
    }

    @Test
    fun `duplicate starts and replacement activities share the fully loaded repository`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val owner = CoroutineScope(SupervisorJob() + dispatcher)
        var constructions = 0
        val shared by lazy { SavedSettings().also { constructions++ } }
        val first = SettingsStartupLoader(owner, dispatcher) { shared }
        val replacement = SettingsStartupLoader(owner, dispatcher) { shared }
        try {
            first.start()
            first.start()
            replacement.start()
            scheduler.advanceUntilIdle()
            val firstReady = assertInstanceOf(SettingsStartupState.Ready::class.java, first.state.value)
            val replacementReady = assertInstanceOf(SettingsStartupState.Ready::class.java, replacement.state.value)
            assertEquals(1, constructions)
            assertSame(firstReady.repository, replacementReady.repository)
            assertEquals(ThemeMode.DARK, firstReady.repository.settings.value.themeMode)
            assertTrue(firstReady.repository.settings.value.dynamicColor)
            assertTrue(firstReady.repository.settings.value.requireAuthForHidden)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun `an edit before first composition remains current instead of being replaced by a startup snapshot`() {
        val scheduler = TestCoroutineScheduler()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val settings = SavedSettings()
        Executors.newSingleThreadExecutor().use { worker ->
            worker.asCoroutineDispatcher().use { io ->
                val loader = SettingsStartupLoader(owner, io) { settings }
                try {
                    loader.start()
                    scheduler.runCurrent()
                    worker.submit {}.get(5, TimeUnit.SECONDS)
                    settings.update { it.copy(themeMode = ThemeMode.LIGHT, showHiddenFiles = false) }
                    scheduler.runCurrent()
                    val ready = assertInstanceOf(SettingsStartupState.Ready::class.java, loader.state.value)
                    assertEquals(ThemeMode.LIGHT, ready.repository.settings.value.themeMode)
                    assertEquals(false, ready.repository.settings.value.showHiddenFiles)
                    assertEquals(1, settings.writes)
                } finally {
                    owner.cancel()
                }
            }
        }
    }

    @Test
    fun `a failed read offers retry without publishing defaults or persisting anything`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val owner = CoroutineScope(SupervisorJob() + dispatcher)
        val settings = SavedSettings()
        var attempts = 0
        val loader =
            SettingsStartupLoader(owner, dispatcher) {
                attempts++
                if (attempts == 1) throw IOException("settings unavailable")
                settings
            }
        try {
            loader.start()
            scheduler.advanceUntilIdle()
            assertInstanceOf(SettingsStartupState.Failed::class.java, loader.state.value)
            assertEquals(0, settings.writes)
            loader.start()
            loader.start()
            assertSame(SettingsStartupState.Loading, loader.state.value)
            scheduler.advanceUntilIdle()
            assertEquals(2, attempts)
            assertSame(
                settings,
                assertInstanceOf(SettingsStartupState.Ready::class.java, loader.state.value).repository,
            )
            assertEquals(0, settings.writes)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun `destroying the activity before dispatch prevents repository construction`() {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val owner = CoroutineScope(SupervisorJob() + dispatcher)
        var constructed = false
        val loader =
            SettingsStartupLoader(owner, dispatcher) {
                constructed = true
                SavedSettings()
            }
        loader.start()
        owner.cancel()
        scheduler.advanceUntilIdle()
        assertEquals(false, constructed)
        assertSame(SettingsStartupState.Loading, loader.state.value)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `a destroyed activity never receives late IO success or failure`(fail: Boolean) {
        val scheduler = TestCoroutineScheduler()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newSingleThreadExecutor().use { worker ->
            worker.asCoroutineDispatcher().use { io ->
                val loader =
                    SettingsStartupLoader(owner, io) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        if (fail) throw IOException("late failure")
                        SavedSettings()
                    }
                try {
                    loader.start()
                    scheduler.runCurrent()
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    owner.cancel()
                    release.countDown()
                    worker.submit {}.get(5, TimeUnit.SECONDS)
                    scheduler.advanceUntilIdle()
                    assertSame(SettingsStartupState.Loading, loader.state.value)
                } finally {
                    release.countDown()
                    owner.cancel()
                }
            }
        }
    }

    private class SavedSettings : SettingsRepository {
        override val settings =
            MutableStateFlow(AppSettings(themeMode = ThemeMode.DARK, dynamicColor = true, requireAuthForHidden = true))
        var writes = 0
            private set

        override fun update(transform: (AppSettings) -> AppSettings) {
            writes++
            settings.value = transform(settings.value)
        }
    }
}
