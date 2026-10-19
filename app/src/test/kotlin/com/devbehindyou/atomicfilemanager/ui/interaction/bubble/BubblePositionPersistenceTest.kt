package com.devbehindyou.atomicfilemanager.ui.interaction.bubble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BubblePositionPersistenceTest {
    @Test
    fun openingAndReadingTheStoreWaitForTheIoDispatcher() =
        runTest {
            var opened = false
            val store = RecordingStore(BubblePosition(0f, 0.75f))
            val persistence =
                BubblePositionPersistence(
                    openStore = {
                        opened = true
                        store
                    },
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            var loaded: BubblePosition? = null
            val load = launch(start = CoroutineStart.UNDISPATCHED) { persistence.loadInitial { loaded = it } }

            assertFalse(opened)
            assertEquals(0, store.reads)
            assertNull(loaded)
            runCurrent()

            load.join()
            assertTrue(opened)
            assertEquals(1, store.reads)
            assertEquals(BubblePosition(0f, 0.75f), loaded)
        }

    @Test
    fun aDragStartingBeforeTheReadCompletesKeepsItsImmediateCoordinates() =
        runTest {
            val store = RecordingStore(BubblePosition(0f, 0.75f))
            val persistence = BubblePositionPersistence({ store }, StandardTestDispatcher(testScheduler))
            var loaded: BubblePosition? = null
            val load = launch(start = CoroutineStart.UNDISPATCHED) { persistence.loadInitial { loaded = it } }

            persistence.markInteraction()
            runCurrent()
            load.join()

            assertEquals(1, store.reads)
            assertNull(loaded)
        }

    @Test
    fun quickSuccessiveSavesWriteOnlyTheLatestRequestedPosition() =
        runTest {
            var opened = false
            val store = RecordingStore()
            val persistence =
                BubblePositionPersistence(
                    openStore = {
                        opened = true
                        store
                    },
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            val first = persistence.save(this, BubblePosition(0f, 0.1f))
            val last = persistence.save(this, BubblePosition(1f, 0.9f))
            assertFalse(opened)
            assertTrue(store.written.isEmpty())
            runCurrent()
            first.join()
            last.join()

            assertEquals(listOf(BubblePosition(1f, 0.9f)), store.written)
        }

    @Test
    fun aSaveAlreadyInProgressFinishesBeforeTheNewerPositionIsWritten() =
        runTest {
            val store = RecordingStore()
            val persistence = BubblePositionPersistence({ store }, StandardTestDispatcher(testScheduler))
            store.onWrite = {
                if (store.written.size == 1) persistence.save(this, BubblePosition(1f, 0.9f))
            }

            persistence.save(this, BubblePosition(0f, 0.1f))
            runCurrent()

            assertEquals(listOf(BubblePosition(0f, 0.1f), BubblePosition(1f, 0.9f)), store.written)
        }

    @Test
    fun aReplacementRailCannotBeOverwrittenByAnOlderPendingSave() =
        runTest {
            val store = RecordingStore()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val writes = BubblePositionWrites()
            val oldRail = BubblePositionPersistence({ store }, dispatcher, writes)
            val replacementRail = BubblePositionPersistence({ store }, dispatcher, writes)

            oldRail.save(this, BubblePosition(0f, 0.1f))
            replacementRail.save(this, BubblePosition(1f, 0.9f))
            runCurrent()

            assertEquals(listOf(BubblePosition(1f, 0.9f)), store.written)
        }

    @Test
    fun aReplacementInitialReadOvertakingTheDispatchedSaveUsesItsPendingPosition() =
        runTest {
            val store = RecordingStore(BubblePosition(0f, 0.1f))
            val dispatcher = StandardTestDispatcher(testScheduler)
            val writes = BubblePositionWrites()
            val oldRail = BubblePositionPersistence({ store }, dispatcher, writes)
            val replacementRail = BubblePositionPersistence({ store }, dispatcher, writes)
            var loaded: BubblePosition? = null
            // Queue the replacement read first, then request a save before either IO job executes.
            val load = launch(start = CoroutineStart.UNDISPATCHED) { replacementRail.loadInitial { loaded = it } }
            val save = oldRail.save(this, BubblePosition(1f, 0.9f))
            runCurrent()
            load.join()
            save.join()

            assertEquals(BubblePosition(1f, 0.9f), loaded)
            assertEquals(0, store.reads)
            assertEquals(listOf(BubblePosition(1f, 0.9f)), store.written)
            assertNull(writes.pending.get().position)
        }

    @Test
    fun aFinishedFlushDoesNotCacheThePositionAcrossLaterDiskReads() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val writes = BubblePositionWrites()
            val first = BubblePositionPersistence({ RecordingStore() }, dispatcher, writes)
            first.save(this, BubblePosition(1f, 0.9f))
            runCurrent()
            val laterStore = RecordingStore(BubblePosition(0f, 0.25f))
            val replacement = BubblePositionPersistence({ laterStore }, dispatcher, writes)
            var loaded: BubblePosition? = null

            val load = launch { replacement.loadInitial { loaded = it } }
            runCurrent()
            load.join()

            assertEquals(BubblePosition(0f, 0.25f), loaded)
            assertEquals(1, laterStore.reads)
        }

    @Test
    fun aFailedPreferenceWriteIsReportedWithoutCancellingTheUiScopeOrCachingItsPosition() =
        runTest {
            val failure = IOException("preferences unavailable")
            val store = RecordingStore()
            store.onWrite = { throw failure }
            val writes = BubblePositionWrites()
            val persistence = BubblePositionPersistence({ store }, StandardTestDispatcher(testScheduler), writes)

            val save = persistence.save(this, BubblePosition(0f, 0.8f))
            runCurrent()

            assertSame(failure, save.await().exceptionOrNull())
            assertTrue(coroutineContext[Job]!!.isActive)
            assertNull(writes.pending.get().position)
        }

    @Test
    fun disposingTheUiScopeStillFinishesItsRequestedPreferenceWrite() =
        runTest {
            val store = RecordingStore()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val persistence = BubblePositionPersistence({ store }, dispatcher)
            val uiScope = CoroutineScope(Job() + dispatcher)
            val save = persistence.save(uiScope, BubblePosition(0f, 0.8f))

            uiScope.cancel()
            runCurrent()
            save.join()

            assertEquals(listOf(BubblePosition(0f, 0.8f)), store.written)
        }

    private class RecordingStore(private val initial: BubblePosition = BubblePosition()) : BubblePositionStore {
        var reads = 0
        val written = mutableListOf<BubblePosition>()
        var onWrite: () -> Unit = {}

        override fun read(): BubblePosition {
            reads++
            return initial
        }

        override fun write(position: BubblePosition) {
            written += position
            onWrite()
        }
    }
}
