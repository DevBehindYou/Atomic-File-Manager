package com.devbehindyou.atomicfilemanager.data.operations

import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalDao
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalEntity
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationProgress
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationSummary
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class JournalAdmissionTest {
    private fun operation(id: String) =
        FileOperation(
            OperationId(id),
            OperationType.DELETE,
            listOf(FileNodeId.file("/fixture/$id")),
            null,
            OperationOptions(),
            0L,
        )

    private val summary = OperationSummary(emptyList(), emptyList(), emptyList(), null)

    @Test
    fun `failed running or terminal persistence preserves actual outcome and queue availability`() =
        runTest {
            for (failedState in listOf(OperationJournalState.RUNNING, OperationJournalState.COMPLETED)) {
                val delegate = FakeOperationJournal()
                val journal =
                    object : OperationJournalDao by delegate {
                        override suspend fun updateProgress(
                            id: String,
                            state: OperationJournalState,
                            itemsDone: Int,
                            itemsTotal: Int,
                            bytesDone: Long,
                            bytesTotal: Long,
                            errorMessage: String?,
                            updatedAt: Long,
                        ) {
                            if (id == "first" && state == failedState) throw IOException("fixture update failure")
                            delegate.updateProgress(
                                id,
                                state,
                                itemsDone,
                                itemsTotal,
                                bytesDone,
                                bytesTotal,
                                errorMessage,
                                updatedAt,
                            )
                        }
                    }
                val mutations = mutableListOf<String>()
                val queue =
                    OperationQueue(
                        engine = { op, _ ->
                            flow {
                                emit(
                                    OperationSnapshot(
                                        op,
                                        OperationStatus.Running(OperationProgress(0, 1, 0, 1, null, 0, null)),
                                    ),
                                )
                                mutations += op.id.raw
                                emit(OperationSnapshot(op, OperationStatus.Completed(summary)))
                            }
                        },
                        journal = journal,
                        scope = backgroundScope,
                    )
                assertTrue(queue.runAndAwait(operation("first")).status is OperationStatus.Completed)
                assertNotNull(queue.journalError.value)
                assertTrue(queue.runAndAwait(operation("next")).status is OperationStatus.Completed)
                assertEquals(listOf("first", "next"), mutations)
                assertEquals(OperationJournalState.COMPLETED, delegate.rows.getValue("next").state)
                if (failedState == OperationJournalState.COMPLETED) {
                    assertEquals(OperationJournalState.RUNNING, delegate.rows.getValue("first").state)
                }
            }
        }

    @Test
    fun `journal-local cancellation fails admission without killing queue`() =
        runTest {
            val delegate = FakeOperationJournal()
            val journal =
                object : OperationJournalDao by delegate {
                    override suspend fun upsert(entry: OperationJournalEntity) {
                        if (entry.id == "timed-out") throw CancellationException("DAO timeout")
                        delegate.upsert(entry)
                    }
                }
            val started = mutableListOf<String>()
            val queue =
                OperationQueue(
                    engine = { op, _ ->
                        flow {
                            started += op.id.raw
                            emit(OperationSnapshot(op, OperationStatus.Completed(summary)))
                        }
                    },
                    journal = journal,
                    scope = backgroundScope,
                )
            assertTrue(queue.runAndAwait(operation("timed-out")).status is OperationStatus.Failed)
            assertTrue(queue.runAndAwait(operation("next")).status is OperationStatus.Completed)
            assertEquals(listOf("next"), started)
        }

    private class GatedTerminalJournal(
        private val delegate: FakeOperationJournal,
        val entered: CompletableDeferred<Unit> = CompletableDeferred(),
        val release: CompletableDeferred<Unit> = CompletableDeferred(),
    ) : OperationJournalDao by delegate {
        override suspend fun updateProgress(
            id: String,
            state: OperationJournalState,
            itemsDone: Int,
            itemsTotal: Int,
            bytesDone: Long,
            bytesTotal: Long,
            errorMessage: String?,
            updatedAt: Long,
        ) {
            if (state == OperationJournalState.COMPLETED) {
                entered.complete(Unit)
                release.await()
            }
            delegate.updateProgress(id, state, itemsDone, itemsTotal, bytesDone, bytesTotal, errorMessage, updatedAt)
        }
    }

    @Test
    fun `cancel during terminal persistence preserves completed mutation`() =
        runTest {
            val delegate = FakeOperationJournal()
            val journal = GatedTerminalJournal(delegate)
            val queue =
                OperationQueue(
                    engine = { op, _ -> flow { emit(OperationSnapshot(op, OperationStatus.Completed(summary))) } },
                    journal = journal,
                    scope = backgroundScope,
                )
            val op = operation("completed")
            val result = async { queue.runAndAwait(op) }
            journal.entered.await()
            queue.cancel(op.id)
            journal.release.complete(Unit)
            assertTrue(result.await().status is OperationStatus.Completed)
            assertEquals(OperationJournalState.COMPLETED, delegate.rows.getValue(op.id.raw).state)
        }

    @Test
    fun `engine waits until intent is persisted`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val delegate = FakeOperationJournal()
            val journal =
                object : OperationJournalDao by delegate {
                    override suspend fun upsert(entry: OperationJournalEntity) {
                        gate.await()
                        delegate.upsert(entry)
                    }
                }
            var started = false
            val queue =
                OperationQueue(
                    engine = { op, _ ->
                        flow {
                            assertNotNull(delegate.get(op.id.raw))
                            started = true
                            emit(OperationSnapshot(op, OperationStatus.Completed(summary)))
                        }
                    },
                    journal = journal,
                    scope = backgroundScope,
                )
            val result = async { queue.runAndAwait(operation("held")) }
            runCurrent()
            assertFalse(started)
            assertFalse(result.isCompleted)
            gate.complete(Unit)
            assertTrue(result.await().status is OperationStatus.Completed)
            assertTrue(started)
        }

    @Test
    fun `failed intent does not mutate and does not stop next operation`() =
        runTest {
            val delegate = FakeOperationJournal()
            val journal =
                object : OperationJournalDao by delegate {
                    override suspend fun upsert(entry: OperationJournalEntity) {
                        if (entry.id == "failed") throw IOException("fixture disk full")
                        delegate.upsert(entry)
                    }
                }
            val started = mutableListOf<String>()
            val queue =
                OperationQueue(
                    engine = { op, _ ->
                        flow {
                            started += op.id.raw
                            emit(OperationSnapshot(op, OperationStatus.Completed(summary)))
                        }
                    },
                    journal = journal,
                    scope = backgroundScope,
                )
            assertTrue(queue.runAndAwait(operation("failed")).status is OperationStatus.Failed)
            assertNotNull(queue.journalError.value)
            assertTrue(queue.runAndAwait(operation("next")).status is OperationStatus.Completed)
            assertEquals(listOf("next"), started)
        }

    @Test
    fun `cancel while admission is pending never starts engine`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val delegate = FakeOperationJournal()
            val journal =
                object : OperationJournalDao by delegate {
                    override suspend fun upsert(entry: OperationJournalEntity) {
                        gate.await()
                        delegate.upsert(entry)
                    }
                }
            var started = false
            val queue =
                OperationQueue(
                    engine = { op, _ ->
                        flow {
                            started = true
                            emit(OperationSnapshot(op, OperationStatus.Completed(summary)))
                        }
                    },
                    journal = journal,
                    scope = backgroundScope,
                )
            val op = operation("cancelled")
            val result = async { queue.runAndAwait(op) }
            runCurrent()
            queue.cancel(op.id)
            gate.complete(Unit)
            assertEquals(OperationStatus.Cancelled, result.await().status)
            assertFalse(started)
        }
}
