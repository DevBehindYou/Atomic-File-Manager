package com.devbehindyou.atomicfilemanager.data.volume

import com.devbehindyou.atomicfilemanager.domain.model.CategoryUsage
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.domain.model.storageUsageAdd
import com.devbehindyou.atomicfilemanager.domain.model.storageUsageCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

enum class StorageUsagePhase { SCANNING, COMPLETE, PARTIAL, FAILED, NO_STORAGE }

data class StorageUsageUpdate(
    val phase: StorageUsagePhase = StorageUsagePhase.SCANNING,
    val current: StorageUsageSnapshot? = null,
    val lastGood: StorageUsageSnapshot? = null,
    val cached: Boolean = false,
    val changedDuringScan: Boolean = false,
    val previousObserved: StorageUsageSnapshot? = null,
)

data class StorageUsageLimits(
    val entries: Int = 250_000,
    val directories: Int = 25_000,
    val depth: Int = 64,
    val millis: Long = 30_000,
) {
    init {
        require(entries > 0 && directories > 0 && depth >= 0 && millis > 0)
    }
}

/** One bounded last-good cache; collection owns work and cancellation, never an application job. */
class StorageUsageRepository(
    private val source: StorageUsageSource,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val limits: StorageUsageLimits = StorageUsageLimits(),
    private val nanoTime: () -> Long = System::nanoTime,
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val ttlNanos: Long = 120_000_000_000,
) {
    private data class Cache(
        val inputKey: String,
        val planKey: String,
        val snapshot: StorageUsageSnapshot,
        val time: Long,
        val revision: Long,
    )

    private val writer = Mutex()
    private val revision = AtomicLong()

    @Volatile private var cache: Cache? = null
    private var observed: Cache? = null

    /** Mark stale without destroying the last successful evidence shown during refresh/failure. */
    fun invalidate() {
        revision.incrementAndGet()
    }

    fun scan(
        volumes: List<StorageVolumeInfo>,
        refresh: Boolean = false,
    ): Flow<StorageUsageUpdate> =
        flow {
            writer.withLock {
                if (refresh) invalidate()
                val inputKey =
                    volumes.map {
                        "${it.id}|${it.isMounted}|${it.rootNodeId?.raw}|${it.requiresGrant}"
                    }.sorted().joinToString("\n")
                if (cache?.let { it.inputKey != inputKey } == true) invalidate()
                val previous = cache?.takeIf { it.inputKey == inputKey }
                val priorObserved = observed?.takeIf { it.inputKey == inputKey }
                val plan =
                    try {
                        withTimeoutOrNull(limits.millis) { source.plan(volumes) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                if (plan == null) {
                    currentCoroutineContext().ensureActive()
                    invalidate()
                    emit(
                        StorageUsageUpdate(
                            StorageUsagePhase.FAILED,
                            lastGood = previous?.snapshot,
                            previousObserved = priorObserved?.snapshot,
                        ),
                    )
                    return@withLock
                }
                val hit = previous?.takeIf { it.planKey == plan.key }
                if (previous != null && hit == null) invalidate()
                val observedHit = priorObserved?.takeIf { it.planKey == plan.key }
                val requestedRevision = revision.get()
                if (!refresh && hit != null && hit.revision == requestedRevision &&
                    plan.unavailableRoots == 0 && plan.capacity.unknownVolumes == 0 &&
                    nanoTime() - hit.time in 0 until ttlNanos
                ) {
                    emit(
                        StorageUsageUpdate(
                            StorageUsagePhase.COMPLETE,
                            hit.snapshot.copy(capacity = plan.capacity),
                            hit.snapshot,
                            cached = true,
                        ),
                    )
                } else {
                    walk(plan, hit?.snapshot, requestedRevision) { update ->
                        currentCoroutineContext().ensureActive()
                        if (update.phase == StorageUsagePhase.COMPLETE && requestedRevision == revision.get()) {
                            cache =
                                Cache(
                                    inputKey, plan.key, checkNotNull(update.current), nanoTime(), requestedRevision,
                                )
                        }
                        if (update.phase == StorageUsagePhase.PARTIAL || update.phase == StorageUsagePhase.COMPLETE) {
                            observed =
                                Cache(
                                    inputKey, plan.key, checkNotNull(update.current), nanoTime(), requestedRevision,
                                )
                        }
                        emit(update.copy(previousObserved = observedHit?.snapshot))
                    }
                }
            }
        }.flowOn(io)

    private suspend fun walk(
        plan: StorageUsagePlan,
        lastGood: StorageUsageSnapshot?,
        requestedRevision: Long,
        publish: suspend (StorageUsageUpdate) -> Unit,
    ) {
        val started = nanoTime()
        val categories = StorageUsageCategory.entries.associateWith { CategoryUsage() }.toMutableMap()
        val pending = ArrayDeque<Pair<String, Int>>()
        plan.roots.forEach { pending += it to 0 }
        val visited = HashSet<String>()
        val seenFiles = HashSet<String>()
        var entries = 0
        var files = 0L
        var directories = 0
        var unreadable = plan.unavailableRoots
        var links = 0
        var bounded = false
        var lastEmission = started

        fun snapshot(complete: Boolean = false) =
            StorageUsageSnapshot(
                plan.capacity, categories.toMap(), files, directories, unreadable, links, bounded, complete, wallTime(),
            )
        publish(StorageUsageUpdate(StorageUsagePhase.SCANNING, snapshot(), lastGood))
        if (plan.capacity.mountedVolumes == 0) {
            publish(StorageUsageUpdate(StorageUsagePhase.NO_STORAGE, snapshot()))
            return
        }
        val finishedWithinBudget =
            withTimeoutOrNull(limits.millis) {
                while (pending.isNotEmpty() && !bounded) {
                    currentCoroutineContext().ensureActive()
                    if (directories >= limits.directories || nanoTime() - started >= limits.millis * 1_000_000) {
                        bounded = true
                        break
                    }
                    val (folder, depth) = pending.removeFirst()
                    if (visited.add(folder)) {
                        directories++
                        var success = false
                        var failed = false
                        try {
                            flow { emitAll(source.list(folder)) }.catch { error ->
                                if (error is CancellationException) throw error
                                emit(FileResult.Failure(FileError.IoFailure(null)))
                            }.collect { result ->
                                currentCoroutineContext().ensureActive()
                                when (result) {
                                    is FileResult.Failure -> failed = true
                                    is FileResult.Success -> {
                                        success = true
                                        for (entry in result.value) {
                                            currentCoroutineContext().ensureActive()
                                            if (
                                                entries >= limits.entries ||
                                                nanoTime() - started >= limits.millis * 1_000_000
                                            ) {
                                                bounded = true
                                                break
                                            }
                                            entries++
                                            if (entry.link || plan.roots.none { isUnderStorageRoot(entry.path, it) }) {
                                                links++
                                            } else if (entry.directory) {
                                                if (depth >= limits.depth || pending.size >= limits.directories) {
                                                    bounded = true
                                                } else {
                                                    pending += entry.path to depth + 1
                                                }
                                            } else if (seenFiles.add(entry.path)) {
                                                val category = storageUsageCategory(entry.name)
                                                val before = categories.getValue(category)
                                                categories[category] =
                                                    CategoryUsage(
                                                        storageUsageAdd(before.bytes, entry.size),
                                                        storageUsageAdd(before.files, 1),
                                                    )
                                                files = storageUsageAdd(files, 1)
                                            }
                                        }
                                    }
                                }
                                if (nanoTime() - lastEmission >= 250_000_000) {
                                    publish(StorageUsageUpdate(StorageUsagePhase.SCANNING, snapshot(), lastGood))
                                    lastEmission = nanoTime()
                                }
                                if (bounded) throw ScanLimitReached()
                            }
                        } catch (_: ScanLimitReached) {
                            bounded = true
                        }
                        if (!success || failed) unreadable++
                    }
                }
                true
            } ?: false
        if (!finishedWithinBudget) bounded = true
        currentCoroutineContext().ensureActive()
        val changed = revision.get() != requestedRevision
        val complete = unreadable == 0 && links == 0 && !bounded && !changed && plan.capacity.unknownVolumes == 0
        publish(
            StorageUsageUpdate(
                if (complete) StorageUsagePhase.COMPLETE else StorageUsagePhase.PARTIAL,
                snapshot(complete),
                lastGood,
                changedDuringScan = changed,
            ),
        )
    }

    private class ScanLimitReached : RuntimeException()
}
