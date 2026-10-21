package com.devbehindyou.atomicfilemanager.ui.startup

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class StorageReadinessLoaderTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val owner = CoroutineScope(SupervisorJob() + dispatcher)
    private val granted = StorageSnapshot(true, listOf(volume()))
    private val denied = StorageSnapshot(false, listOf(volume().copy(rootNodeId = null, requiresGrant = true)))

    @Test
    fun `initial permission is unresolved until access and volumes publish together`() {
        val loader = StorageReadinessLoader(owner, dispatcher) { granted }
        try {
            assertNull(loader.state.value.snapshot)
            loader.refresh()
            assertInstanceOf(StorageReadinessState.Loading::class.java, loader.state.value)
            assertNull(loader.state.value.snapshot)
            scheduler.advanceUntilIdle()
            assertSame(granted, assertInstanceOf(StorageReadinessState.Ready::class.java, loader.state.value).snapshot)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun `failed check never becomes denial and retry can return actual denial`() {
        var attempts = 0
        val loader =
            StorageReadinessLoader(owner, dispatcher) {
                if (++attempts == 1) throw IOException("check unavailable")
                denied
            }
        try {
            loader.refresh()
            scheduler.advanceUntilIdle()
            assertInstanceOf(StorageReadinessState.Failed::class.java, loader.state.value)
            assertNull(loader.state.value.snapshot)
            loader.refresh()
            scheduler.advanceUntilIdle()
            assertSame(denied, assertInstanceOf(StorageReadinessState.Ready::class.java, loader.state.value).snapshot)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun `refresh and failure retain the last successful volumes without pretending readiness`() {
        var attempts = 0
        val loader =
            StorageReadinessLoader(owner, dispatcher) {
                if (++attempts > 1) throw IOException("drive unavailable")
                granted
            }
        try {
            loader.refresh()
            scheduler.advanceUntilIdle()
            loader.refresh()
            assertSame(
                granted,
                assertInstanceOf(StorageReadinessState.Loading::class.java, loader.state.value).snapshot,
            )
            scheduler.advanceUntilIdle()
            assertSame(granted, assertInstanceOf(StorageReadinessState.Failed::class.java, loader.state.value).snapshot)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun `new refresh cancels an older queued read and owner destruction cancels pending work`() {
        var calls = 0
        val loader =
            StorageReadinessLoader(owner, dispatcher) {
                calls++
                granted
            }
        loader.refresh()
        loader.refresh()
        scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        loader.refresh()
        owner.cancel()
        scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        assertInstanceOf(StorageReadinessState.Loading::class.java, loader.state.value)
    }

    @Test
    fun `a cancelled blocking read cannot publish over the latest result`() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        var attempts = 0
        val caller = Thread.currentThread()
        var readThread: Thread? = null
        Executors.newSingleThreadExecutor().use { worker ->
            worker.asCoroutineDispatcher().use { io ->
                val loader =
                    StorageReadinessLoader(owner, io) {
                        readThread = Thread.currentThread()
                        if (++attempts == 1) {
                            firstStarted.countDown()
                            check(releaseFirst.await(5, TimeUnit.SECONDS))
                            granted
                        } else {
                            denied
                        }
                    }
                try {
                    loader.refresh()
                    scheduler.runCurrent()
                    assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
                    assertTrue(readThread !== caller)
                    assertNull(loader.state.value.snapshot)
                    loader.refresh()
                    scheduler.runCurrent()
                    releaseFirst.countDown()
                    worker.submit {}.get(5, TimeUnit.SECONDS)
                    scheduler.runCurrent()
                    assertSame(
                        denied,
                        assertInstanceOf(StorageReadinessState.Ready::class.java, loader.state.value).snapshot,
                    )
                } finally {
                    releaseFirst.countDown()
                    owner.cancel()
                }
            }
        }
    }

    private fun volume() =
        StorageVolumeInfo(
            id = "test",
            label = "Test volume",
            type = StorageType.INTERNAL_SHARED,
            totalBytes = 100L,
            freeBytes = 50L,
            isRemovable = false,
            isMounted = true,
            rootNodeId = FileNodeId.file("/test"),
            requiresGrant = false,
        )
}
