package com.devbehindyou.atomicfilemanager.data.volume

import com.devbehindyou.atomicfilemanager.domain.model.CategoryUsage
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageSnapshot
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Each new [StorageUsageRepository] over the same [store] stands for a new process. */
class StorageUsagePersistenceTest {
    private val root = File("storage-usage-persistence").absolutePath
    private val store = MemoryStore()
    private val volumes = listOf(volume("primary", requiresGrant = false))

    @Test
    fun aNewProcessShowsTheLastCompleteResultWithoutPlanningOrListing() =
        runBlocking {
            val first = Process()
            val complete = first.repository.scan(volumes, refresh = true).toList().last()
            val next = Process()
            val restored = checkNotNull(next.repository.restore(volumes))
            assertEquals(StorageUsagePhase.IDLE, restored.phase)
            assertFalse(restored.interrupted)
            assertEquals(complete.current, restored.lastGood)
            assertEquals(0, next.plans.get())
            assertEquals(0, next.listings.get())
        }

    @Test
    fun aWalkEndedByProcessDeathIsReportedInTheNextProcess() =
        runBlocking {
            val first = Process()
            val complete = first.repository.scan(volumes, refresh = true).toList().last()
            first.block.set(true)
            val walk = launch(Dispatchers.IO) { first.repository.scan(volumes, refresh = true).collect {} }
            first.blocked.await()
            // The process dies here: no finally, no result. Only what is already durable survives.
            val stored = store.value
            walk.cancelAndJoin()
            store.value = stored
            val next = Process()
            val restored = checkNotNull(next.repository.restore(volumes))
            assertTrue(restored.interrupted)
            assertEquals("The last complete result is still the evidence", complete.current, restored.lastGood)
        }

    @Test
    fun restoredEvidenceIsNeverACachedHitButStaysVisibleWhileRescanning() =
        runBlocking {
            Process().repository.scan(volumes, refresh = true).toList()
            val next = Process()
            val restored = checkNotNull(next.repository.restore(volumes)).lastGood
            val updates = next.repository.scan(volumes, refresh = false).toList()
            assertEquals("A restored result must be walked again", 1, next.listings.get())
            assertTrue(updates.none { it.cached })
            assertEquals(restored, updates.first().lastGood)
            assertEquals(StorageUsagePhase.COMPLETE, updates.last().phase)
        }

    @Test
    fun writesAreBoundedToTheWalkMarkerAndItsResult() =
        runBlocking {
            val process = Process()
            process.repository.restore(volumes)
            process.repository.lastResult(volumes)
            assertEquals("Entry never writes", 0, store.writes.get())
            process.repository.scan(volumes, refresh = true).toList()
            assertEquals(2, store.writes.get())
            process.repository.scan(volumes, refresh = true).toList()
            assertEquals(4, store.writes.get())
        }

    @Test
    fun otherStorageMountsOrGrantsSeeNothingFromAnEarlierProcess() =
        runBlocking {
            Process().repository.scan(volumes, refresh = true).toList()
            val next = Process()
            assertNull(next.repository.restore(listOf(volume("primary", requiresGrant = true))))
            assertNull(next.repository.restore(listOf(volume("card", requiresGrant = false))))
            assertEquals(0, next.plans.get())
        }

    @Test
    fun anUnreadableOrFailingStoreOnlyLosesPreviousEvidence() =
        runBlocking {
            store.fail.set(true)
            val process = Process()
            assertNull(process.repository.restore(volumes))
            val final = process.repository.scan(volumes, refresh = true).toList().last()
            assertEquals(StorageUsagePhase.COMPLETE, final.phase)
            assertTrue(final.persistenceFailed)
            assertEquals(final.current, process.repository.lastResult(volumes)?.lastGood)
            assertTrue(checkNotNull(process.repository.lastResult(volumes)).persistenceFailed)
        }

    @Test
    fun aRejectedCommitKeepsTheResultInMemoryAndASuccessfulRefreshClearsTheWarning() =
        runBlocking {
            val process = Process()
            store.rejectWrites.set(true)
            val result = process.repository.scan(volumes, refresh = true).toList().last()
            assertEquals(StorageUsagePhase.COMPLETE, result.phase)
            assertTrue(result.persistenceFailed)
            assertNull(Process().repository.restore(volumes))

            val cached = process.repository.scan(volumes).toList().last()
            assertTrue(cached.cached)
            assertTrue(cached.persistenceFailed)

            store.rejectWrites.set(false)
            val retried = process.repository.scan(volumes, refresh = true).toList().last()
            assertFalse(retried.persistenceFailed)
            assertEquals(retried.current, Process().repository.restore(volumes)?.lastGood)
        }

    @Test
    fun codecRoundTripsAndRejectsCorruptOrForeignRecords() {
        val snapshot =
            StorageUsageSnapshot(
                StorageCapacity(1_000, 100, 2, 1),
                mapOf(
                    StorageUsageCategory.PDFS to CategoryUsage(600, 1),
                    StorageUsageCategory.OTHER to CategoryUsage(),
                ),
                1, 3, 1, 2, true, false, 1_234,
            )
        val value =
            StoredStorageUsage(
                StoredStorageUsageResult("id|true|file:///a|b\nc", "plan|key", snapshot),
                StoredStorageUsageResult("other", "plan", snapshot.copy(complete = true, limitReached = false)),
                "id|true|file:///a|b\nc",
            )
        val encoded = StorageUsageCodec.encode(value)
        assertEquals(value, StorageUsageCodec.decode(encoded))
        assertEquals(StoredStorageUsage(), StorageUsageCodec.decode(StorageUsageCodec.encode(StoredStorageUsage())))

        fun resealed(text: String) = text + "\n" + crcOf(text)
        val body = encoded.substringBeforeLast('\n')
        val corrupt =
            listOf(
                // Truncated or altered on disk: the checksum no longer matches.
                encoded.dropLast(5),
                body.dropLast(5) + "\n" + encoded.substringAfterLast('\n'),
                encoded.replace("PDFS:600", "PDFS:700"),
                // Well-formed checksum but invalid contents: the codec and model invariants still reject them.
                resealed("v2" + body.removePrefix("v1")),
                resealed(body.replace("PDFS:600", "PDFS:-600")),
                resealed(body.replace("PDFS:600:1", "PDFS:600:1;PDFS:1:1")),
                resealed(body.replace("PDFS", "MOVIES")),
                resealed(body.replace("1000,100,", "100,1000,")),
                resealed("v1\n!!!|plan|x\n\n"),
                "",
                encoded.substringBefore('\n'),
                "v1\n\n\n" + "A".repeat(20_000),
            )
        corrupt.forEach { assertNull("Must reject: ${it.take(60)}", StorageUsageCodec.decode(it)) }
    }

    private fun crcOf(body: String): String =
        java.util.zip.CRC32().apply { update(body.toByteArray(Charsets.UTF_8)) }.value.toString(16)

    private inner class Process {
        val plans = AtomicInteger()
        val listings = AtomicInteger()
        val block = AtomicBoolean(false)
        val blocked = CompletableDeferred<Unit>()
        val repository =
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
                            val entry = StorageUsageEntry(File(root, "a.pdf").path, "a.pdf", 600, false, false)
                            emit(FileResult.Success(listOf(entry)))
                        }
                },
                Dispatchers.IO,
                store = store,
            )
    }

    private class MemoryStore : StorageUsageStore {
        @Volatile var value: String? = null
        val writes = AtomicInteger()
        val fail = AtomicBoolean(false)
        val rejectWrites = AtomicBoolean(false)

        override fun read(): StoredStorageUsage? {
            if (fail.get()) error("Unreadable preferences")
            return value?.let(StorageUsageCodec::decode)
        }

        override fun write(value: StoredStorageUsage): Boolean {
            writes.incrementAndGet()
            if (fail.get()) error("Disk full")
            if (rejectWrites.get()) return false
            this.value = StorageUsageCodec.encode(value)
            return true
        }
    }

    private fun volume(
        id: String,
        requiresGrant: Boolean,
    ) = StorageVolumeInfo(
        id,
        id,
        StorageType.INTERNAL_SHARED,
        1_000,
        100,
        false,
        true,
        FileNodeId.file(File(root, id).path),
        requiresGrant,
    )
}
