package com.devbehindyou.atomicfilemanager.data.operations

import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalDao
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalEntity
import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import com.devbehindyou.atomicfilemanager.data.database.room.toJournalEntry
import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.repository.OutputTarget
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.FileOperationsEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * Process-kill tests (ALL_IN_ONE_PLAN.md 0.1 and §20): the app dies at a step of a copy, then
 * starts again. A real kill runs no cleanup, so from the kill point on the storage ignores every
 * change and the journal stops being written; what is left is exactly what a dead process leaves.
 * After the restart nothing may be lost or half-written under a real name, and Resume must finish
 * the copy byte for byte.
 */
class ProcessKillTest {
    private val disk = InMemoryBackend()
    private val journal = FakeOperationJournal()
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()

    // Several copy buffers long, so the mid-write kill lands before the complete payload is written.
    // The fixture's journal handshake, not payload size or thread timing, establishes RUNNING first.
    private val payload = ByteArray(FileOperationsEngine.BUFFER_SIZE * 4 + 1_234) { (it * 31 % 251).toByte() }

    @AfterEach
    fun tearDown() = executor.shutdownNow().let { }

    private enum class KillAt { MID_WRITE, BEFORE_PUBLISH, AFTER_PUBLISH }

    private fun names(folder: FileNodeId): Map<String, FileNode> =
        runBlocking { disk.listChildren(folder).first() as FileResult.Success }.value.associateBy { it.name }

    private fun bytes(node: FileNode): ByteArray =
        runBlocking {
            val out = ByteArrayOutputStream()
            (disk.openInput(node.id) as FileResult.Success).value.stream().use { it.copyTo(out) }
            out.toByteArray()
        }

    private fun copyOf(
        source: FileNodeId,
        target: FileNodeId,
        policy: CollisionPolicy = CollisionPolicy.ASK,
    ) = FileOperation(
        id = OperationId("copy-1"),
        type = OperationType.COPY,
        sources = listOf(source),
        destination = target,
        options = OperationOptions(collisionPolicy = policy),
        createdAt = 0L,
    )

    /** Runs [operation] until [killAt], then kills the "process". */
    private fun runUntilKilled(
        operation: FileOperation,
        killAt: KillAt,
    ) = runBlocking {
        val dead = DeadProcess(killAt)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val queue =
            OperationQueue(
                engine = { op, resolver -> FileOperationsEngine { dead.backend }.execute(op, resolver) },
                journal = dead.journal,
                scope = scope,
            )
        queue.enqueue(operation)
        withTimeout(TIMEOUT_MS) { dead.killed.await() }
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
    }

    /** Starts the app again on what the dead one left: recovery runs, then Resume. */
    private fun restartAndResume(): RecoveredOperations =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            // The queue starts its start-up sweep on another thread at once; wait until recovery exists.
            val recoveryReady = CompletableDeferred<OperationRecovery>()
            val queue =
                OperationQueue(
                    engine = { op, resolver -> FileOperationsEngine { disk }.execute(op, resolver) },
                    journal = journal,
                    scope = scope,
                    startup = { recoveryReady.await().sweep() },
                )
            val recovery = OperationRecovery(journal, queue, { disk }, scope)
            recoveryReady.complete(recovery)
            val recovered = withTimeout(TIMEOUT_MS) { recovery.pending.first { it != null } }!!
            recovery.resumeAll()
            withTimeout(TIMEOUT_MS) {
                while (journal.rows["copy-1"]?.state != OperationJournalState.COMPLETED) delay(POLL_MS)
            }
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            recovered
        }

    private fun setUpFolders(): Pair<FileNodeId, FileNodeId> =
        runBlocking {
            val from = (disk.createDirectory(disk.rootId, "from") as FileResult.Success).value.id
            val to = (disk.createDirectory(disk.rootId, "to") as FileResult.Success).value.id
            disk.putFile(from, "movie.mp4", payload)
            from to to
        }

    @Test
    fun `killed mid-write leaves no partial under the real name and resume finishes`() {
        val (from, to) = setUpFolders()
        val source = names(from).getValue("movie.mp4")
        runUntilKilled(copyOf(source.id, to), KillAt.MID_WRITE)

        assertTrue(names(to).keys.none { it == "movie.mp4" }, "nothing half-written under the real name")
        assertEquals(OperationJournalState.RUNNING, journal.rows.getValue("copy-1").state)

        val recovered = restartAndResume()

        assertEquals(1, recovered.cleanup.removedPartials)
        assertArrayEquals(payload, bytes(names(to).getValue("movie.mp4")))
        assertEquals(setOf("movie.mp4"), names(to).keys)
        assertArrayEquals(payload, bytes(names(from).getValue("movie.mp4")), "source untouched")
    }

    @Test
    fun `killed after staging but before publishing keeps nothing stray`() {
        val (from, to) = setUpFolders()
        runUntilKilled(copyOf(names(from).getValue("movie.mp4").id, to), KillAt.BEFORE_PUBLISH)

        assertTrue(names(to).keys.none { it == "movie.mp4" })

        restartAndResume()

        assertEquals(setOf("movie.mp4"), names(to).keys)
        assertArrayEquals(payload, bytes(names(to).getValue("movie.mp4")))
    }

    @Test
    fun `killed while replacing a file puts the old one back before anything else`() {
        val (from, to) = setUpFolders()
        val old = "the version that was there before".toByteArray()
        runBlocking { disk.putFile(to, "movie.mp4", old) }
        runUntilKilled(
            copyOf(names(from).getValue("movie.mp4").id, to, CollisionPolicy.OVERWRITE),
            KillAt.BEFORE_PUBLISH,
        )

        // The old file was moved aside for the swap when the process died.
        assertTrue(names(to).keys.none { it == "movie.mp4" })

        val recovered = restartAndResume()

        assertNotNull(recovered)
        assertEquals(setOf("movie.mp4"), names(to).keys, "no backup or staging file left over")
        assertArrayEquals(payload, bytes(names(to).getValue("movie.mp4")))
    }

    @Test
    fun `killed after publishing but before the journal caught up still ends with one complete copy`() {
        val (from, to) = setUpFolders()
        runUntilKilled(
            copyOf(names(from).getValue("movie.mp4").id, to, CollisionPolicy.OVERWRITE),
            KillAt.AFTER_PUBLISH,
        )

        assertArrayEquals(payload, bytes(names(to).getValue("movie.mp4")))
        assertEquals(OperationJournalState.RUNNING, journal.rows.getValue("copy-1").state)

        restartAndResume()

        assertEquals(setOf("movie.mp4"), names(to).keys)
        assertArrayEquals(payload, bytes(names(to).getValue("movie.mp4")))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `delayed running journal delivery gates the first output write`() =
        runTest {
            val (from, to) = setUpFolders()
            val operation = copyOf(names(from).getValue("movie.mp4").id, to)
            journal.upsert(operation.toJournalEntry())
            val allowRunningWrite = CompletableDeferred<Unit>()
            val dead = DeadProcess(KillAt.MID_WRITE) { allowRunningWrite.await() }
            val stagingName = ".atomic-journal-barrier.partial"
            val runningWrite =
                launch {
                    dead.journal.updateProgress(
                        id = operation.id.raw,
                        state = OperationJournalState.RUNNING,
                        itemsDone = 0,
                        itemsTotal = 1,
                        bytesDone = 0,
                        bytesTotal = payload.size.toLong(),
                        errorMessage = null,
                        updatedAt = 0,
                    )
                }
            val outputWrite =
                async {
                    val output = (dead.backend.openOutput(to, stagingName, null) as FileResult.Success).value
                    output.stream().use { it.write(payload, 0, payload.size / 2) }
                }

            runCurrent()

            assertTrue(runningWrite.isActive, "RUNNING delivery is suspended at the controlled gate")
            assertFalse(outputWrite.isCompleted, "the first storage write must wait for persisted RUNNING")
            assertFalse(dead.killed.isCompleted)
            assertTrue(names(to).isEmpty())
            assertEquals(OperationJournalState.QUEUED, journal.rows.getValue(operation.id.raw).state)

            allowRunningWrite.complete(Unit)
            advanceUntilIdle()
            runningWrite.join()
            outputWrite.await()

            assertTrue(dead.killed.isCompleted, "the original mid-write kill point still fires")
            assertEquals(OperationJournalState.RUNNING, journal.rows.getValue(operation.id.raw).state)
            assertArrayEquals(payload.copyOfRange(0, payload.size / 2), bytes(names(to).getValue(stagingName)))
        }

    /**
     * The storage and journal as a dying process sees them: normal until [killAt], then every
     * change is silently lost.
     */
    private inner class DeadProcess(
        private val killAt: KillAt,
        private val beforeRunningWrite: suspend () -> Unit = {},
    ) {
        val killed = CompletableDeferred<Unit>()
        private val runningPersisted = CompletableDeferred<Unit>()
        private val isDead get() = killed.isCompleted

        private fun kill() {
            killed.complete(Unit)
        }

        val journal: OperationJournalDao =
            object : OperationJournalDao by this@ProcessKillTest.journal {
                override suspend fun upsert(entry: OperationJournalEntity) {
                    if (!isDead) this@ProcessKillTest.journal.upsert(entry)
                }

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
                    if (!isDead) {
                        if (state == OperationJournalState.RUNNING) beforeRunningWrite()
                        if (isDead) return
                        this@ProcessKillTest.journal.updateProgress(
                            id,
                            state,
                            itemsDone,
                            itemsTotal,
                            bytesDone,
                            bytesTotal,
                            errorMessage,
                            updatedAt,
                        )
                        if (state == OperationJournalState.RUNNING) runningPersisted.complete(Unit)
                    }
                }
            }

        val backend: StorageBackend =
            object : StorageBackend by disk {
                override suspend fun openOutput(
                    parent: FileNodeId,
                    name: String,
                    mime: String?,
                ): FileResult<OutputTarget> {
                    if (isDead) return lost()
                    // flowOn buffers progress: receipt by the collector need not mean durable delivery.
                    // These scenarios intentionally kill a running operation after its journal write.
                    runningPersisted.await()
                    if (isDead) return lost()
                    val real = (disk.openOutput(parent, name, mime) as FileResult.Success).value
                    return FileResult.Success(DyingTarget(real, parent, name))
                }

                override suspend fun rename(
                    id: FileNodeId,
                    newName: String,
                ): FileResult<FileNode> {
                    if (isDead) return lost()
                    val publishing = !newName.startsWith(".atomic-")
                    if (publishing && killAt == KillAt.BEFORE_PUBLISH) {
                        kill()
                        return lost()
                    }
                    val result = disk.rename(id, newName)
                    if (publishing && killAt == KillAt.AFTER_PUBLISH) kill()
                    return result
                }

                override suspend fun delete(id: FileNodeId): FileResult<Unit> = if (isDead) lost() else disk.delete(id)

                override suspend fun moveWithin(
                    id: FileNodeId,
                    newParent: FileNodeId,
                ): FileResult<FileNode> = if (isDead) lost() else disk.moveWithin(id, newParent)

                override suspend fun createDirectory(
                    parent: FileNodeId,
                    name: String,
                ): FileResult<FileNode> = if (isDead) lost() else disk.createDirectory(parent, name)
            }

        private fun <T> lost(): FileResult<T> = FileResult.Failure(FileError.IoFailure("process is dead"))

        /** Writes reach the disk as they would; at the kill, the bytes so far are what the disk holds. */
        private inner class DyingTarget(
            private val real: OutputTarget,
            private val parent: FileNodeId,
            private val name: String,
        ) : OutputTarget by real {
            private val written = ByteArrayOutputStream()

            override fun stream(): OutputStream {
                val inner = real.stream()
                return object : OutputStream() {
                    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

                    override fun write(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ) {
                        if (isDead) return
                        inner.write(b, off, len)
                        written.write(b, off, len)
                        if (killAt == KillAt.MID_WRITE && written.size() >= payload.size / 2) {
                            // What a real disk holds at this moment: a partial file under the staging name.
                            runBlocking { disk.putFile(parent, name, written.toByteArray()) }
                            kill()
                        }
                    }

                    override fun close() {
                        if (!isDead) inner.close()
                    }
                }
            }

            override fun discard() {
                if (!isDead) real.discard()
            }

            override suspend fun toNode(): FileResult<FileNode> = if (isDead) lost() else real.toNode()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val POLL_MS = 20L
    }
}
