package com.devbehindyou.atomicfilemanager.data.volume

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageCapacity
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StorageUsageRepositoryTest {
    @Test
    fun `explicit empty completion is cached but manual refresh bypasses the cache`() = runTest {
        val source = FakeSource()
        val repository = repository(source)
        val completed = repository.scan(volumes).last()
        val snapshot = checkNotNull(completed.current)
        assertEquals(StorageUsagePhase.COMPLETE, completed.phase)
        assertEquals(0L, snapshot.readableBytes)
        assertTrue(snapshot.complete)
        assertFalse(completed.cached)
        assertTrue(repository.scan(volumes).last().cached)
        assertEquals(1, source.listCalls.size)
        assertFalse(repository.scan(volumes, refresh = true).last().cached)
        assertEquals(2, source.listCalls.size)
    }

    @Test
    fun `silence denied and mixed failed listings remain partial and retry within TTL`() = runTest {
        val denied = FileResult.Failure(FileError.AccessDenied(null))
        val incomplete = listOf<Flow<FileResult<List<StorageUsageEntry>>>>(
            emptyFlow(), flowOf(denied), flowOf(FileResult.Success(emptyList()), denied),
        )
        for (listing in incomplete) {
            val source = FakeSource().apply { entries = { listing } }
            val repository = repository(source)
            repeat(2) {
                val update = repository.scan(volumes).last()
                val snapshot = checkNotNull(update.current)
                assertEquals(StorageUsagePhase.PARTIAL, update.phase)
                assertEquals(1, snapshot.unreadable)
                assertFalse(snapshot.complete)
                assertFalse(update.cached)
            }
            assertEquals(2, source.listCalls.size)
        }
    }

    @Test
    fun `failed refresh retains last good evidence without making it a fresh complete scan`() = runTest {
        val source = FakeSource().apply { entries = { success(file("old.pdf", 10)) } }
        val repository = repository(source)
        val previous = repository.scan(volumes).last().current
        val failures = listOf<Flow<FileResult<List<StorageUsageEntry>>>>(
            emptyFlow(), flow { throw IOException("listing temporarily unavailable") },
        )
        for (failure in failures) {
            source.entries = { failure }
            val partial = repository.scan(volumes, refresh = true).last()
            assertEquals(StorageUsagePhase.PARTIAL, partial.phase)
            assertEquals(previous, partial.lastGood)
            assertEquals(0L, partial.current!!.readableBytes)
        }
        source.entries = { success(file("new.mp3", 20)) }
        val recovered = repository.scan(volumes).last()
        assertFalse(recovered.cached)
        assertEquals(StorageUsagePhase.COMPLETE, recovered.phase)
        assertEquals(20L, recovered.current!!.categories.getValue(StorageUsageCategory.AUDIO).bytes)
        assertEquals(4, source.listCalls.size)
    }

    @Test
    fun `exceptional plan failure retains last good and recovers before TTL`() = runTest {
        val source = FakeSource().apply { entries = { success(file("old.pdf", 10)) } }
        val repository = repository(source)
        val previous = repository.scan(volumes).last().current
        source.failPlan = true
        val failed = repository.scan(volumes, refresh = true).last()
        assertEquals(StorageUsagePhase.FAILED, failed.phase)
        assertEquals(previous, failed.lastGood)
        assertNull(failed.current)
        source.failPlan = false
        source.entries = { success(file("new.pdf", 30)) }
        val recovered = repository.scan(volumes).last()
        assertFalse(recovered.cached)
        assertEquals(30L, recovered.current!!.readableBytes)
        assertEquals(2, source.listCalls.size)
    }

    @Test
    fun `a suspended plan times out retains last good and forces recovery before TTL`() = runTest {
        val source = FakeSource().apply { entries = { success(file("old.pdf", 10)) } }
        val repository = repository(source, StorageUsageLimits(millis = 25))
        val previous = repository.scan(volumes).last().current
        val cancelled = CompletableDeferred<Unit>()
        source.beforePlan = { try { awaitCancellation() } finally { cancelled.complete(Unit) } }
        val failed = repository.scan(volumes).last()
        assertEquals(25L, testScheduler.currentTime)
        assertTrue(cancelled.isCompleted)
        assertEquals(StorageUsagePhase.FAILED, failed.phase)
        assertEquals(previous, failed.lastGood)
        assertNull(failed.current)
        source.beforePlan = {}
        source.entries = { success(file("new.pdf", 20)) }
        val recovered = repository.scan(volumes).last()
        assertEquals(StorageUsagePhase.COMPLETE, recovered.phase)
        assertFalse(recovered.cached)
        assertEquals(20L, recovered.current!!.readableBytes)
        assertEquals(2, source.listCalls.size)
    }

    @Test
    fun `cancelling a live listing publishes no completion and the next collection rescans`() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val source = FakeSource().apply {
            entries = {
                flow {
                    emit(FileResult.Success(listOf(file("partial.jpg", 4))))
                    started.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
            }
        }
        val repository = repository(source)
        val updates = mutableListOf<StorageUsageUpdate>()
        val active = launch { repository.scan(volumes).toList(updates) }
        started.await()
        active.cancelAndJoin()
        assertTrue(cancelled.isCompleted)
        assertFalse(updates.any { it.phase == StorageUsagePhase.COMPLETE || it.cached })
        source.entries = { success(file("complete.jpg", 8)) }
        val recovered = repository.scan(volumes).last()
        assertEquals(StorageUsagePhase.COMPLETE, recovered.phase)
        assertEquals(8L, recovered.current!!.readableBytes)
        assertEquals(2, source.listCalls.size)
    }

    @Test
    fun `entry budget stops enumeration and never validates partial totals as fresh`() = runTest {
        val source = FakeSource().apply { entries = { success(*(1..5).map { file("$it.bin", 2) }.toTypedArray()) } }
        val repository = repository(source, StorageUsageLimits(entries = 2))
        repeat(2) {
            val update = repository.scan(volumes).last()
            val snapshot = checkNotNull(update.current)
            assertEquals(StorageUsagePhase.PARTIAL, update.phase)
            assertTrue(snapshot.limitReached)
            assertEquals(2L, snapshot.scannedFiles)
            assertEquals(4L, snapshot.readableBytes)
            assertFalse(update.cached)
        }
        assertEquals(2, source.listCalls.size)
    }

    @Test
    fun `a silent suspended listing has a virtual time deadline rather than hanging indefinitely`() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        val source = FakeSource().apply {
            entries = { flow { try { awaitCancellation() } finally { cancelled.complete(Unit) } } }
        }
        val repository = repository(source, StorageUsageLimits(millis = 25))
        val update = repository.scan(volumes).last()
        val snapshot = checkNotNull(update.current)
        assertEquals(25L, testScheduler.currentTime)
        assertTrue(cancelled.isCompleted)
        assertEquals(StorageUsagePhase.PARTIAL, update.phase)
        assertTrue(snapshot.limitReached)
        assertFalse(snapshot.complete)
    }

    @Test
    fun `nested and duplicate roots or repeated file entries never duplicate byte totals`() = runTest {
        val source = FakeSource().apply {
            planValue = planValue.copy(roots = listOf(rootPath, nestedPath, rootPath))
            entries = { folder ->
                if (folder == rootPath) success(
                    StorageUsageEntry(nestedPath, "nested", 0, directory = true, link = false),
                    file("one.pdf", 10), file("one.pdf", 10),
                ) else success(file("nested/two.pdf", 20))
            }
        }
        val update = repository(source).scan(volumes).last()
        val snapshot = checkNotNull(update.current)
        assertEquals(StorageUsagePhase.COMPLETE, update.phase)
        assertEquals(30L, snapshot.readableBytes)
        assertEquals(2L, snapshot.scannedFiles)
        assertEquals(listOf(rootPath, nestedPath), source.listCalls)
    }

    @Test
    fun `invalidation during an active walk prevents cache publication and forces a new walk`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val source = FakeSource().apply {
            entries = {
                flow {
                    emit(FileResult.Success(listOf(file("one.pdf", 10))))
                    entered.complete(Unit)
                    release.await()
                }
            }
        }
        val repository = repository(source)
        val updates = mutableListOf<StorageUsageUpdate>()
        val active = launch { repository.scan(volumes).toList(updates) }
        entered.await()
        repository.invalidate()
        release.complete(Unit)
        active.join()
        assertEquals(StorageUsagePhase.PARTIAL, updates.last().phase)
        assertTrue(updates.last().changedDuringScan)
        assertFalse(updates.last().current!!.complete)
        source.entries = { success(file("one.pdf", 10)) }
        assertFalse(repository.scan(volumes).last().cached)
        assertEquals(2, source.listCalls.size)
    }

    @Test
    fun `root and permission changes do not reuse the previous scope's last good snapshot`() = runTest {
        val source = FakeSource().apply { entries = { success(file("one.pdf", 10)) } }
        val repository = repository(source)
        repository.scan(volumes).last()
        source.planValue = source.planValue.copy(key = "denied", roots = emptyList(), unavailableRoots = 1)
        val denied = repository.scan(listOf(volume.copy(requiresGrant = true))).last()
        assertEquals(StorageUsagePhase.PARTIAL, denied.phase)
        assertNull(denied.lastGood)
        assertFalse(denied.cached)
        source.planValue = source.planValue.copy(key = "other", roots = listOf(otherPath), unavailableRoots = 0)
        source.entries = { success(StorageUsageEntry(File(otherPath, "new.bin").path, "new.bin", 50, false, false)) }
        val changed = repository.scan(listOf(volume.copy(rootNodeId = FileNodeId.file(otherPath)))).last()
        assertEquals(StorageUsagePhase.COMPLETE, changed.phase)
        assertNull(changed.lastGood)
        assertEquals(50L, changed.current!!.readableBytes)
    }

    @Test
    fun `logical file bytes larger than physical used bytes have no attributable remainder`() = runTest {
        val source = FakeSource().apply { entries = { success(file("sparse.bin", 900)) } }
        val snapshot = repository(source).scan(volumes).last().current!!
        assertEquals(600L, snapshot.capacity.usedBytes)
        assertEquals(900L, snapshot.readableBytes)
        assertNull(snapshot.unattributedUsedEstimate)
        assertNotNull(snapshot.categories[StorageUsageCategory.OTHER])
    }

    @Test
    fun `no mounted storage and unknown denied storage never masquerade as complete zero usage`() = runTest {
        val source = FakeSource().apply {
            planValue = StorageUsagePlan("none", emptyList(), StorageCapacity(0, 0, 0), 0)
        }
        val repository = repository(source)
        val absent = repository.scan(emptyList()).last()
        assertEquals(StorageUsagePhase.NO_STORAGE, absent.phase)
        assertFalse(absent.current!!.complete)
        source.planValue = StorageUsagePlan("unknown", emptyList(), StorageCapacity(0, 0, 1, 1), 1)
        repeat(2) {
            val unknown = repository.scan(volumes).last()
            val snapshot = checkNotNull(unknown.current)
            assertEquals(StorageUsagePhase.PARTIAL, unknown.phase)
            assertEquals(1, snapshot.capacity.unknownVolumes)
            assertEquals(1, snapshot.unreadable)
            assertFalse(snapshot.complete)
            assertFalse(unknown.cached)
        }
    }

    private fun TestScope.repository(source: StorageUsageSource, limits: StorageUsageLimits = StorageUsageLimits()) =
        StorageUsageRepository(
            source, StandardTestDispatcher(testScheduler), limits,
            nanoTime = { testScheduler.currentTime * 1_000_000 }, wallTime = { testScheduler.currentTime },
        )

    private class FakeSource : StorageUsageSource {
        var planValue = StorageUsagePlan("plan", listOf(rootPath), StorageCapacity(1_000, 400, 1), 0)
        var failPlan = false
        var beforePlan: suspend () -> Unit = {}
        val listCalls = mutableListOf<String>()
        var entries: (String) -> Flow<FileResult<List<StorageUsageEntry>>> = { flowOf(FileResult.Success(emptyList())) }

        override suspend fun plan(volumes: List<StorageVolumeInfo>): StorageUsagePlan {
            beforePlan()
            if (failPlan) throw IOException("capacity temporarily unavailable")
            return planValue
        }

        override fun list(directory: String): Flow<FileResult<List<StorageUsageEntry>>> {
            listCalls += directory
            return entries(directory)
        }
    }

    private companion object {
        val rootPath = File("storage-usage-test-root").absolutePath
        val nestedPath = File(rootPath, "nested").path
        val otherPath = File("other-storage-usage-test-root").absolutePath
        val volume = StorageVolumeInfo("primary", "Internal", StorageType.INTERNAL_SHARED, 1_000, 400,
            false, true, FileNodeId.file(rootPath), false)
        val volumes = listOf(volume)
        fun file(name: String, size: Long) = StorageUsageEntry(File(rootPath, name).path, name.substringAfterLast('/'), size, false, false)
        fun success(vararg entries: StorageUsageEntry): Flow<FileResult<List<StorageUsageEntry>>> =
            flowOf(FileResult.Success(entries.toList()))
    }
}
