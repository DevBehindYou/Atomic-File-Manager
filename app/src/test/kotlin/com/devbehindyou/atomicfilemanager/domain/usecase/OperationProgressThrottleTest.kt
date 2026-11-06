package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperationProgressThrottleTest {
    @Test
    fun `fast multi-buffer copy emits boundaries and exact totals instead of per-buffer progress`() =
        runTest {
            val backend = InMemoryBackend()
            val destination = (backend.createDirectory(backend.rootId, "destination") as FileResult.Success).value
            val payload = ByteArray(FileOperationsEngine.BUFFER_SIZE * 64 + 123) { (it % 251).toByte() }
            val source = backend.putFile(backend.rootId, "fixture.bin", payload)
            val operation =
                FileOperation(
                    OperationId.random(),
                    OperationType.COPY,
                    listOf(source),
                    destination.id,
                    OperationOptions(),
                    0L,
                )
            val snapshots = FileOperationsEngine(clockMillis = { 0L }) { backend }.execute(operation).toList()
            val running = snapshots.mapNotNull { it.status as? OperationStatus.Running }.map { it.progress }
            assertEquals(2, running.size)
            assertEquals(0L, running.first().bytesDone)
            assertEquals(payload.size.toLong(), running.last().bytesDone)
            assertEquals(1, running.last().itemsDone)
            assertTrue(snapshots.last().status is OperationStatus.Completed)
        }

    @Test
    fun `progress is emitted only at interval boundaries between first and final`() =
        runTest {
            val backend = InMemoryBackend()
            val destination = (backend.createDirectory(backend.rootId, "destination") as FileResult.Success).value
            val source =
                backend.putFile(
                    backend.rootId,
                    "fixture.bin",
                    ByteArray(FileOperationsEngine.BUFFER_SIZE * 10),
                )
            val operation =
                FileOperation(
                    OperationId.random(),
                    OperationType.COPY,
                    listOf(source),
                    destination.id,
                    OperationOptions(),
                    0L,
                )
            var now = 0L
            val snapshots =
                FileOperationsEngine(clockMillis = { now.also { now += 50L } }) { backend }
                    .execute(operation).toList()
            val progress = snapshots.mapNotNull { it.status as? OperationStatus.Running }.map { it.progress }
            assertTrue(progress.size in 3..4, "ten 50ms chunks need at most two timed updates plus boundaries")
            assertTrue(progress.zipWithNext().all { (a, b) -> b.bytesDone >= a.bytesDone })
            assertEquals((FileOperationsEngine.BUFFER_SIZE * 10).toLong(), progress.last().bytesDone)
        }
}
