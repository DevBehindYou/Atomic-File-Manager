package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.usecase.SyncMode
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FolderComparePreferencesTest {
    @Test
    fun `construct read and save execute on the injected worker`() =
        runTest {
            val caller = Thread.currentThread()
            val threads = mutableListOf<Thread>()
            val store = TestPairStore(SyncMode.MIRROR, onAccess = { threads += Thread.currentThread() })
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
                val access =
                    StoragePreferenceAccess<ComparePairStore>(io) {
                        threads += Thread.currentThread()
                        store
                    }
                val model = FolderComparePreferences("left", "right", this, access)
                assertTrue(threads.isEmpty())
                model.load()
                assertTrue(model.state.value.loading)
                assertEquals(SyncMode.MIRROR, model.state.first { !it.loading }.mode)
                model.save(SyncMode.COPY_NEW)
                model.state.first { !it.save.saving }
                assertTrue(threads.size >= 3)
                assertTrue(threads.all { it !== caller })
                assertEquals(listOf(SyncPair("left", "right", SyncMode.COPY_NEW)), store.writes)
            }
        }

    @Test
    fun `failed load blocks saving until a successful retry restores the saved mode`() =
        runTest {
            val store = TestPairStore(SyncMode.MIRROR).apply { failRead = true }
            val model = model(store)
            model.load()
            advanceUntilIdle()
            assertTrue(model.state.value.loadFailed)
            model.save(SyncMode.COPY_NEW)
            advanceUntilIdle()
            assertTrue(store.writes.isEmpty())
            store.failRead = false
            model.load()
            advanceUntilIdle()
            assertFalse(model.state.value.loadFailed)
            assertFalse(model.state.value.loading)
            assertEquals(SyncMode.MIRROR, model.state.value.mode)
            assertEquals(SyncMode.MIRROR, model.state.value.save.savedMode)
        }

    @Test
    fun `saved acknowledgement follows success and rapid duplicate saves are rejected`() =
        runTest {
            val store = TestPairStore(SyncMode.COPY_NEW)
            val model = model(store)
            model.load()
            advanceUntilIdle()
            model.save(SyncMode.MIRROR)
            model.save(SyncMode.COPY_NEW)
            assertTrue(model.state.value.save.saving)
            assertEquals(SyncMode.COPY_NEW, model.state.value.save.savedMode)
            assertTrue(store.writes.isEmpty())
            advanceUntilIdle()
            assertEquals(listOf(SyncPair("left", "right", SyncMode.MIRROR)), store.writes)
            assertEquals(FolderCompareSaveState(SyncMode.MIRROR), model.state.value.save)
        }

    @Test
    fun `failed save leaves the old acknowledgement and can be retried`() =
        runTest {
            val store = TestPairStore(SyncMode.COPY_NEW)
            val model = model(store)
            model.load()
            advanceUntilIdle()
            store.failWrite = true
            model.save(SyncMode.MIRROR)
            advanceUntilIdle()
            assertEquals(FolderCompareSaveState(SyncMode.COPY_NEW, failed = true), model.state.value.save)
            store.failWrite = false
            model.save(SyncMode.MIRROR)
            advanceUntilIdle()
            assertEquals(FolderCompareSaveState(SyncMode.MIRROR), model.state.value.save)
        }

    @Test
    fun `accepted save survives disposal without updating the closed screen`() =
        runTest {
            val store = TestPairStore(SyncMode.COPY_NEW)
            val dispatcher = StandardTestDispatcher(testScheduler)
            val screen = CoroutineScope(SupervisorJob() + dispatcher)
            val access = StoragePreferenceAccess<ComparePairStore>(dispatcher) { store }
            val model = FolderComparePreferences("captured-left", "captured-right", screen, access)
            try {
                model.load()
                advanceUntilIdle()
                model.save(SyncMode.MIRROR)
                val closedState = model.state.value
                screen.cancel()
                advanceUntilIdle()
                assertEquals(listOf(SyncPair("captured-left", "captured-right", SyncMode.MIRROR)), store.writes)
                assertEquals(closedState, model.state.value)
            } finally {
                screen.cancel()
            }
        }

    @Test
    fun `disposal before a read starts does not open preferences`() =
        runTest {
            var constructed = false
            val dispatcher = StandardTestDispatcher(testScheduler)
            val screen = CoroutineScope(SupervisorJob() + dispatcher)
            val access =
                StoragePreferenceAccess<ComparePairStore>(dispatcher) {
                    constructed = true
                    TestPairStore(null)
                }
            val model = FolderComparePreferences("left", "right", screen, access)
            model.load()
            screen.cancel()
            advanceUntilIdle()
            assertFalse(constructed)
            assertTrue(model.state.value.loading)
        }

    @Test
    fun `delayed preference read cannot overwrite a newer mode choice`() =
        runTest {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val store =
                TestPairStore(SyncMode.MIRROR, onAccess = {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                })
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
                val model = FolderComparePreferences("left", "right", this, StoragePreferenceAccess(io) { store })
                try {
                    model.load()
                    runCurrent()
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    model.select(SyncMode.COPY_NEW)
                    release.countDown()
                    val loaded = model.state.first { !it.loading }
                    assertEquals(SyncMode.COPY_NEW, loaded.mode)
                    assertEquals(SyncMode.MIRROR, loaded.save.savedMode)
                } finally {
                    release.countDown()
                }
            }
        }

    private fun kotlinx.coroutines.test.TestScope.model(store: TestPairStore): FolderComparePreferences =
        FolderComparePreferences(
            "left",
            "right",
            this,
            StoragePreferenceAccess(StandardTestDispatcher(testScheduler)) { store },
        )

    private class TestPairStore(
        var mode: SyncMode?,
        private val onAccess: () -> Unit = {},
    ) : ComparePairStore {
        var failRead = false
        var failWrite = false
        val writes = mutableListOf<SyncPair>()

        override fun findMode(
            left: String,
            right: String,
        ): SyncMode? {
            onAccess()
            check(!failRead) { "read failed" }
            return mode
        }

        override fun save(pair: SyncPair) {
            onAccess()
            check(!failWrite) { "save failed" }
            writes += pair
            mode = pair.mode
        }
    }
}
