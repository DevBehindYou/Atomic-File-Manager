package com.devbehindyou.atomicfilemanager.data.operations

import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalDao
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import com.devbehindyou.atomicfilemanager.data.database.room.toJournalEntry
import com.devbehindyou.atomicfilemanager.domain.model.Conflict
import com.devbehindyou.atomicfilemanager.domain.model.ConflictDecision
import com.devbehindyou.atomicfilemanager.domain.model.ConflictResolver
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationProgress
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** A name clash the running operation is waiting on; answer it with [OperationQueue.resolveConflict]. */
class PendingConflict(val operation: FileOperation, val conflict: Conflict)

/**
 * One app-wide queue for file operations (ALL_IN_ONE_PLAN.md Phase 0.1). Operations run one at a
 * time in the order they were asked for, so starting a second operation no longer cancels the
 * first. Every operation is written to the journal before it runs and after every progress step,
 * so a crash leaves a record the next start can offer to resume.
 *
 * Operations that [ask][com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy.ASK] about
 * name clashes wait in [pendingConflict] until the user answers; the queue waits with them.
 */
class OperationQueue(
    private val engine: (FileOperation, ConflictResolver) -> Flow<OperationSnapshot>,
    private val journal: OperationJournalDao?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Runs before the first journal write or operation, e.g. start-up recovery of a dead process's work. */
    private val startup: (suspend () -> Unit)? = null,
) {
    private val ready = CompletableDeferred<Unit>()

    private class PendingOperation(val operation: FileOperation, val persisted: CompletableDeferred<Unit>)

    private val pending = Channel<PendingOperation>(Channel.UNLIMITED)

    // Journal writes run strictly in the order they were requested.
    private val journalWrites = Channel<suspend (OperationJournalDao) -> Unit>(Channel.UNLIMITED)
    private val cancelled: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<OperationSnapshot>>()

    private val _active = MutableStateFlow<OperationSnapshot?>(null)

    /** The running operation and its latest progress, or null when idle. */
    val active: StateFlow<OperationSnapshot?> = _active.asStateFlow()

    private val _queued = MutableStateFlow<List<FileOperation>>(emptyList())

    /** Operations waiting behind the active one, oldest first. */
    val queued: StateFlow<List<FileOperation>> = _queued.asStateFlow()

    private val _finished = MutableSharedFlow<OperationSnapshot>(extraBufferCapacity = FINISHED_BUFFER)

    /** The last snapshot of each operation as it ends (completed, partial, failed or cancelled). */
    val finished: SharedFlow<OperationSnapshot> = _finished.asSharedFlow()

    private val _journalError = MutableStateFlow<FileError?>(null)

    /** Persistence failed; recovery may see an older state even when the file operation completed. */
    val journalError: StateFlow<FileError?> = _journalError.asStateFlow()

    private val _pendingConflict = MutableStateFlow<PendingConflict?>(null)

    /** The conflict the running operation is waiting on, or null. */
    val pendingConflict: StateFlow<PendingConflict?> = _pendingConflict.asStateFlow()

    @Volatile
    private var conflictAnswer: CompletableDeferred<ConflictDecision>? = null

    private val conflictResolver =
        ConflictResolver { operation, conflict ->
            val answer = CompletableDeferred<ConflictDecision>()
            conflictAnswer = answer
            _pendingConflict.value = PendingConflict(operation, conflict)
            try {
                // No timeout: an unanswered conflict waits, it never guesses (screens/OPERATIONS.md §6).
                answer.await()
            } finally {
                conflictAnswer = null
                _pendingConflict.value = null
            }
        }

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeId: String? = null

    init {
        scope.launch {
            try {
                startup?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Recovery is best effort; new operations must still run.
            } finally {
                ready.complete(Unit)
            }
        }
        if (journal != null) {
            scope.launch {
                ready.await()
                for (write in journalWrites) {
                    try {
                        write(journal)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // A journal failure must never stop file operations; the next write retries the state.
                    }
                }
            }
        }
        scope.launch {
            ready.await()
            for (entry in pending) runOne(entry)
        }
    }

    /** Adds [operation] to the end of the queue and returns at once. */
    fun enqueue(operation: FileOperation): OperationId {
        _queued.update { it + operation }
        val now = clock()
        val persisted = scheduleJournalWrite { it.upsert(operation.toJournalEntry(OperationJournalState.QUEUED, now)) }
        pending.trySend(PendingOperation(operation, persisted))
        return operation.id
    }

    /** Enqueues [operation] and suspends until it has finished, returning its last snapshot. */
    suspend fun runAndAwait(operation: FileOperation): OperationSnapshot {
        val done = CompletableDeferred<OperationSnapshot>()
        waiters[operation.id.raw] = done
        enqueue(operation)
        return done.await()
    }

    /** Answers [pending] if it is still the open conflict; a stale answer is ignored. */
    fun resolveConflict(
        pending: PendingConflict,
        decision: ConflictDecision,
    ) {
        if (_pendingConflict.value === pending) conflictAnswer?.complete(decision)
    }

    /** Stops the operation if it is running, or drops it if it is still waiting. */
    fun cancel(id: OperationId) {
        cancelled += id.raw
        _queued.update { list -> list.filterNot { it.id == id } }
        if (activeId == id.raw) activeJob?.cancel()
    }

    private suspend fun runOne(entry: PendingOperation) {
        val operation = entry.operation
        try {
            // Admission is asynchronous to the caller, but mutation cannot outrun durable intent.
            entry.persisted.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _queued.update { list -> list.filterNot { it.id == operation.id } }
            finish(OperationSnapshot(operation, failedStatus(e)))
            return
        }
        _queued.update { list -> list.filterNot { it.id == operation.id } }
        if (operation.id.raw in cancelled) {
            finish(OperationSnapshot(operation, OperationStatus.Cancelled))
            return
        }
        var last = OperationSnapshot(operation, OperationStatus.Queued)
        coroutineScope {
            val job =
                launch(start = CoroutineStart.LAZY) {
                    try {
                        engine(operation, conflictResolver).collect { snapshot ->
                            last = snapshot
                            _active.value = snapshot
                            if (!snapshot.status.isTerminal()) record(snapshot)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        last = OperationSnapshot(operation, failedStatus(e))
                    }
                }
            activeId = operation.id.raw
            activeJob = job
            if (operation.id.raw in cancelled) job.cancel() else job.start()
            job.join()
            if (job.isCancelled && !last.status.isTerminal()) {
                last = OperationSnapshot(operation, OperationStatus.Cancelled)
            }
        }
        activeJob = null
        activeId = null
        _active.value = null
        finish(last)
    }

    private suspend fun finish(snapshot: OperationSnapshot) {
        cancelled -= snapshot.operation.id.raw
        record(snapshot)
        _finished.tryEmit(snapshot)
        waiters.remove(snapshot.operation.id.raw)?.complete(snapshot)
    }

    private suspend fun record(snapshot: OperationSnapshot) {
        val (state, progress, error) = journalStateOf(snapshot.status)
        val id = snapshot.operation.id.raw
        val now = clock()
        try {
            // Await each write: a fast copy cannot accumulate a closure per buffer in the journal.
            scheduleJournalWrite {
                it.updateProgress(
                    id = id,
                    state = state,
                    itemsDone = progress?.itemsDone ?: 0,
                    itemsTotal = progress?.itemsTotal ?: 0,
                    bytesDone = progress?.bytesDone ?: 0L,
                    bytesTotal = progress?.bytesTotal ?: 0L,
                    errorMessage = error,
                    updatedAt = now,
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Intent is already durable. Keep the actual operation outcome; recovery remains conservative.
            _journalError.value = FileError.Unknown(e.javaClass.simpleName)
        }
    }

    private fun scheduleJournalWrite(write: suspend (OperationJournalDao) -> Unit): CompletableDeferred<Unit> {
        val result = CompletableDeferred<Unit>()
        if (journal == null) {
            result.complete(Unit)
        } else {
            val sent =
                journalWrites.trySend { dao ->
                    try {
                        write(dao)
                        result.complete(Unit)
                    } catch (e: Exception) {
                        // A DAO-local timeout must fail this write, not kill both queue consumers.
                        // Whole-scope cancellation still propagates through ensureActive().
                        val failure =
                            if (e is CancellationException) {
                                coroutineContext.ensureActive()
                                IOException("Journal write cancelled", e)
                            } else {
                                e
                            }
                        result.completeExceptionally(failure)
                        _journalError.value = FileError.Unknown(failure.javaClass.simpleName)
                    }
                }
            if (sent.isFailure) result.completeExceptionally(IllegalStateException("Journal unavailable"))
        }
        return result
    }

    private companion object {
        const val FINISHED_BUFFER = 16

        val EMPTY_SUMMARY = OperationSummary(emptyList(), emptyList(), emptyList(), null)

        fun failedStatus(e: Exception): OperationStatus =
            // FileError.Unknown takes a short marker, never a message (ERROR_MODEL.md).
            OperationStatus.Failed(FileError.Unknown(e.javaClass.simpleName), EMPTY_SUMMARY)
    }
}

private fun OperationStatus.isTerminal(): Boolean =
    this is OperationStatus.Completed || this is OperationStatus.PartiallyCompleted ||
        this is OperationStatus.Failed || this is OperationStatus.Cancelled

/** Journal state, progress and error text for an [OperationStatus]. */
internal fun journalStateOf(status: OperationStatus): Triple<OperationJournalState, OperationProgress?, String?> =
    when (status) {
        OperationStatus.Queued, OperationStatus.Preparing, OperationStatus.Recovering ->
            Triple(OperationJournalState.QUEUED, null, null)
        is OperationStatus.Running -> Triple(OperationJournalState.RUNNING, status.progress, null)
        is OperationStatus.Paused -> Triple(OperationJournalState.PAUSED, status.progress, null)
        is OperationStatus.AwaitingInput -> Triple(OperationJournalState.PAUSED, null, "Waiting for a decision")
        is OperationStatus.Completed -> Triple(OperationJournalState.COMPLETED, null, null)
        is OperationStatus.PartiallyCompleted ->
            Triple(OperationJournalState.PARTIAL, null, "${status.summary.failed.size} items failed")
        is OperationStatus.Failed -> Triple(OperationJournalState.FAILED, null, status.error.toString())
        OperationStatus.Cancelled -> Triple(OperationJournalState.CANCELLED, null, null)
    }
