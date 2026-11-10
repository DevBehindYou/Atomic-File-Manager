package com.devbehindyou.atomicfilemanager.data.volume

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StorageUsageLastResultTest {
    private val plans = AtomicInteger()
    private val listings = AtomicInteger()
    private val block = AtomicBoolean(false)
    private val unreadable = AtomicBoolean(false)
    private val blocked = CompletableDeferred<Unit>()
    private val volumes = listOf(volume("primary", requiresGrant = false))
    private val repository =
        StorageUsageRepository(
            object : StorageUsageSource {
                override suspend fun plan(volumes: List<StorageVolumeInfo>): StorageUsagePlan {
                    plans.incrementAndGet()
                    return StorageUsagePlan("primary", listOf(root), StorageCapacity(1_000, 100, 1), 0)
                }

                override fun list(directory: String) =
                    flow<FileResult<List<StorageUsageEntry>>> {
                        listings.incrementAndGet()
                        if (block.get()) {
                            blocked.complete(Unit)
                            awaitCancellation()
                        }
                        if (unreadable.get()) {
                            emit(FileResult.Failure(FileError.IoFailure(null)))
                        } else {
                            val entry = StorageUsageEntry(File(root, "a.pdf").path, "a.pdf", 600, false, false)
                            emit(FileResult.Success(listOf(entry)))
                        }
                    }
            },
            Dispatchers.IO,
        )

    @Test
    fun nothingIsKnownBeforeAnyScan() {
        assertNull(repository.lastResult(volumes))
        assertEquals(0, plans.get())
    }

    @Test
    fun aCompleteScanIsReturnedAsPreviousWithoutPlanningOrListing() =
        runBlocking {
            val final = repository.scan(volumes, refresh = true).toList().last()
            assertEquals(StorageUsagePhase.COMPLETE, final.phase)
            val restored = checkNotNull(repository.lastResult(volumes))
            assertEquals(StorageUsagePhase.IDLE, restored.phase)
            assertEquals(false, restored.interrupted)
            assertNull(restored.current)
            assertEquals(final.current, restored.lastGood)
            assertEquals(1, plans.get())
            assertEquals(1, listings.get())
        }

    @Test
    fun aPartialScanIsOnlyEvidenceAndNeverBecomesLastGood() =
        runBlocking {
            unreadable.set(true)
            val final = repository.scan(volumes, refresh = true).toList().last()
            assertEquals(StorageUsagePhase.PARTIAL, final.phase)
            val restored = checkNotNull(repository.lastResult(volumes))
            assertNull(restored.lastGood)
            assertEquals(final.current, restored.previousObserved)
            assertTrue(restored.previousObserved?.complete == false)
        }

    @Test
    fun aWalkCancelledBeforeItsResultIsReportedAsStoppedUntilALaterResult() =
        runBlocking {
            val complete = repository.scan(volumes, refresh = true).toList().last()
            block.set(true)
            val scan = launch(Dispatchers.IO) { repository.scan(volumes, refresh = true).collect {} }
            blocked.await()
            scan.cancelAndJoin()
            val stopped = checkNotNull(repository.lastResult(volumes))
            assertEquals(StorageUsagePhase.IDLE, stopped.phase)
            assertTrue(stopped.interrupted)
            assertEquals("The last complete result stays the evidence", complete.current, stopped.lastGood)
            block.set(false)
            repository.scan(volumes, refresh = true).toList()
            assertEquals(false, repository.lastResult(volumes)?.interrupted)
        }

    @Test
    fun otherStorageMountsOrGrantsShareNothing() =
        runBlocking {
            repository.scan(volumes, refresh = true).toList()
            assertNull(repository.lastResult(listOf(volume("primary", requiresGrant = true))))
            assertNull(repository.lastResult(listOf(volume("card", requiresGrant = false))))
            assertNull(repository.lastResult(volumes + volume("card", requiresGrant = false)))
            assertNull(repository.lastResult(emptyList()))
            assertEquals(1, plans.get())
        }

    private fun volume(
        id: String,
        requiresGrant: Boolean,
    ) = StorageVolumeInfo(
        id, id, StorageType.INTERNAL_SHARED, 1_000, 100, false, true, FileNodeId.file(File(root, id).path), requiresGrant,
    )

    private val root = File("storage-usage-last-result").absolutePath
}
