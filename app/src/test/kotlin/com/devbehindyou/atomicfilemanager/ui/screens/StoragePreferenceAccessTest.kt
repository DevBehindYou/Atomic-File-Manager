package com.devbehindyou.atomicfilemanager.ui.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StoragePreferenceAccessTest {
    @Test
    fun `constructs preferences and reads only on the supplied IO worker`() =
        runBlocking {
            val caller = Thread.currentThread()
            val accessThreads = mutableListOf<Thread>()
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
                val preferences =
                    StoragePreferenceAccess(io) {
                        accessThreads += Thread.currentThread()
                        listOf("saved server")
                    }
                assertTrue(accessThreads.isEmpty())
                val saved =
                    preferences.access {
                        accessThreads += Thread.currentThread()
                        it.single()
                    }
                assertEquals("saved server", saved)
                assertEquals(2, accessThreads.size)
                assertTrue(accessThreads.all { it !== caller })
            }
        }

    @Test
    fun `serializes mutation and refresh so a second action sees the saved result`() =
        runBlocking {
            val firstEntered = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val secondEntered = CountDownLatch(1)
            val factoryCalls = AtomicInteger()
            Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { io ->
                val preferences =
                    StoragePreferenceAccess(io) {
                        factoryCalls.incrementAndGet()
                        mutableListOf("existing")
                    }
                val first =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        preferences.access { saved ->
                            firstEntered.countDown()
                            check(releaseFirst.await(5, TimeUnit.SECONDS))
                            saved += "added"
                            saved.toList()
                        }
                    }
                try {
                    assertTrue(firstEntered.await(5, TimeUnit.SECONDS))
                    val second =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            preferences.access { saved ->
                                secondEntered.countDown()
                                saved.remove("existing")
                                saved.toList()
                            }
                        }
                    assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))
                    releaseFirst.countDown()
                    assertEquals(listOf("existing", "added"), first.await())
                    assertEquals(listOf("added"), second.await())
                    assertEquals(1, factoryCalls.get())
                } finally {
                    releaseFirst.countDown()
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancellation before IO starts does not construct or mutate preferences`() =
        runTest {
            var constructed = false
            var mutated = false
            val preferences =
                StoragePreferenceAccess(StandardTestDispatcher(testScheduler)) {
                    constructed = true
                    mutableListOf<String>()
                }
            val write =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    preferences.access {
                        mutated = true
                        it += "new server"
                    }
                }
            write.cancel()
            advanceUntilIdle()
            assertTrue(write.isCancelled)
            assertFalse(constructed)
            assertFalse(mutated)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `accepted update finishes after disposal without publishing its snapshot`() =
        runTest {
            var constructed = false
            var snapshotRead = false
            var published = false
            val saved = mutableListOf<String>()
            val preferences =
                StoragePreferenceAccess(StandardTestDispatcher(testScheduler)) {
                    constructed = true
                    saved
                }
            val write =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    preferences.update {
                        it += "new server"
                        snapshotRead = true
                        it.toList()
                    }
                    published = true
                }
            assertFalse(constructed)
            write.cancel()
            advanceUntilIdle()
            assertTrue(write.isCancelled)
            assertTrue(constructed)
            assertTrue(snapshotRead)
            assertEquals(listOf("new server"), saved)
            assertFalse(published)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `failed accepted update does not notify a disposed screen`() =
        runTest {
            var attempted = false
            var notified = false
            val preferences = StoragePreferenceAccess(StandardTestDispatcher(testScheduler)) { Unit }
            val write =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        preferences.update {
                            attempted = true
                            error("preference failure")
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        notified = true
                    }
                }
            write.cancel()
            advanceUntilIdle()
            assertTrue(write.isCancelled)
            assertTrue(attempted)
            assertFalse(notified)
        }

    @Test
    fun `failed accepted update still reports its error to an active screen`() =
        runTest {
            val failure = IllegalStateException("preference failure")
            val preferences = StoragePreferenceAccess(StandardTestDispatcher(testScheduler)) { Unit }
            val result = runCatching { preferences.update<Unit> { throw failure } }
            assertSame(failure, result.exceptionOrNull())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `replacement screen cannot read before an accepted update reaches IO`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val mutex = Mutex()
            val saved = mutableListOf("existing")
            val oldScreen = StoragePreferenceAccess(dispatcher, mutex) { saved }
            val replacement = StoragePreferenceAccess(UnconfinedTestDispatcher(testScheduler), mutex) { saved }
            val write =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    oldScreen.update { it += "added" }
                }
            write.cancel()
            val refreshed =
                async(start = CoroutineStart.UNDISPATCHED) {
                    replacement.access { it.toList() }
                }
            assertFalse(refreshed.isCompleted)
            advanceUntilIdle()
            assertTrue(write.isCancelled)
            assertEquals(listOf("existing", "added"), refreshed.await())
        }
}
