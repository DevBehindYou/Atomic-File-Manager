package com.devbehindyou.atomicfilemanager.data.search

import com.devbehindyou.atomicfilemanager.data.database.room.SearchIndexDao
import com.devbehindyou.atomicfilemanager.data.database.room.SearchIndexEntity
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.SearchFilter
import com.devbehindyou.atomicfilemanager.domain.model.SearchHit
import com.devbehindyou.atomicfilemanager.domain.model.SearchIndexState
import com.devbehindyou.atomicfilemanager.domain.model.likePatternFor
import com.devbehindyou.atomicfilemanager.domain.repository.SearchIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [SearchIndex] on the Room `search_index` table. A build walks the given volume roots breadth
 * first through [listChildren], skipping hidden (dot) entries the way the category scan does,
 * and never leaves a root ([canonicalPath] guards against links and loops). Rows are written in
 * batches, so searches may see mixed old/new evidence during an interrupted or partial build.
 * Only a fully readable walk may prune old rows or advance the last successful build time.
 */
class RoomSearchIndex(
    private val dao: SearchIndexDao,
    private val listChildren: (FileNodeId) -> Flow<FileResult<List<FileNode>>>,
    private val scope: CoroutineScope,
    /** Canonical path of a node, or null when it can't be resolved; identity for non-path storage. */
    private val canonicalPath: (FileNodeId) -> String?,
    private val builtAtStore: BuiltAtStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val freshForMillis: Long = DEFAULT_FRESH_MILLIS,
) : SearchIndex {
    /** Where the time of the last finished build is kept between runs. */
    interface BuiltAtStore {
        fun get(): Long?

        fun set(value: Long)
    }

    private val _state = MutableStateFlow(SearchIndexState(builtAt = builtAtStore.get()))
    override val state: StateFlow<SearchIndexState> = _state.asStateFlow()

    private val writerMutex = Mutex()

    @Volatile
    private var job: Job? = null

    init {
        scope.launch { _state.update { it.copy(indexedCount = dao.count()) } }
    }

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<SearchHit> = search(query, limit, SearchFilter.ALL)

    override suspend fun search(
        query: String,
        limit: Int,
        filter: SearchFilter,
    ): List<SearchHit> {
        val pattern = likePatternFor(query) ?: return emptyList()
        if (limit <= 0) return emptyList()
        val matches = ArrayList<SearchHit>()
        var offset = 0
        val directory =
            when (filter) {
                SearchFilter.ALL -> null
                SearchFilter.FOLDERS -> true
                else -> false
            }
        // Bounded pages preserve the domain's MIME/extension rules without loading the entire
        // index or duplicating classification in SQL. Concurrent index writers can still shift
        // offsets; a coherent revision/snapshot belongs to the separate index-consistency work.
        while (matches.size < limit) {
            currentCoroutineContext().ensureActive()
            val page = dao.search(pattern, SEARCH_PAGE_SIZE, offset, directory)
            for (row in page) {
                currentCoroutineContext().ensureActive()
                val hit = row.toHit()
                if (filter.matches(hit)) {
                    matches += hit
                    if (matches.size == limit) return matches
                }
            }
            if (page.size < SEARCH_PAGE_SIZE) break
            offset += page.size
        }
        return matches
    }

    override suspend fun files(roots: List<FileNodeId>): List<SearchHit> {
        if (_state.value.builtAt == null || roots.isEmpty()) return emptyList()
        val prefixes = roots.map { it.raw.trimEnd('/') }
        return dao
            .allFiles()
            .filter { row -> prefixes.any { row.id == it || row.id.startsWith("$it/") } }
            .map { it.toHit() }
    }

    override fun refreshIfStale(roots: List<FileNodeId>) {
        val snapshot = _state.value
        val builtAt = snapshot.builtAt
        if (snapshot.unreadable > 0 || builtAt == null || clock() - builtAt > freshForMillis) rebuild(roots)
    }

    @Synchronized
    override fun rebuild(roots: List<FileNodeId>) {
        if (job?.isActive == true || roots.isEmpty()) return
        _state.update { it.copy(building = true) }
        job =
            scope.launch {
                try {
                    build(roots)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Already flushed rows remain mixed evidence; failure never reaches pruning/freshness.
                } finally {
                    _state.update { it.copy(building = false) }
                }
            }
    }

    override suspend fun refreshFolders(folders: Collection<FileNodeId>) = writerMutex.withLock {
        if (_state.value.builtAt == null) return@withLock
        val generation = clock()
        for (folder in folders.distinct()) {
            currentCoroutineContext().ensureActive()
            val listed = mutableListOf<FileNode>()
            var emittedSuccess = false
            var failed = false
            listChildren(folder).collect { chunk ->
                when (chunk) {
                    is FileResult.Success -> {
                        emittedSuccess = true
                        listed += chunk.value.filterNot { it.isHidden }
                    }
                    is FileResult.Failure -> failed = true
                }
            }
            // Readable empty folders explicitly emit Success(emptyList()); silence is not deletion evidence.
            if (failed || !emittedSuccess) continue
            currentCoroutineContext().ensureActive()
            dao.reconcileFolder(folder.raw, listed.map { it.toEntity(generation) })
        }
        _state.update { it.copy(indexedCount = dao.count()) }
    }

    /** Walks [roots]; partial builds retain mixed evidence without pruning or claiming freshness. */
    internal suspend fun build(roots: List<FileNodeId>) = writerMutex.withLock {
        if (roots.isEmpty()) {
            _state.update { it.copy(unreadable = 1) }
            return@withLock
        }
        val generation = clock()
        val canonicalRoots = roots.mapNotNull(canonicalPath)
        val pending = ArrayDeque(roots)
        val visited = HashSet<String>()
        val batch = ArrayList<SearchIndexEntity>(BATCH_SIZE)
        var written = 0
        var unreadable = 0

        suspend fun flush() {
            if (batch.isEmpty()) return
            dao.upsert(batch.toList())
            written += batch.size
            batch.clear()
            _state.update { it.copy(indexedCount = maxOf(it.indexedCount, written)) }
        }

        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val folder = pending.removeFirst()
            val canonical = canonicalPath(folder)
            val inside = canonical != null && canonicalRoots.any { root -> isInside(canonical, root) }
            if (canonical == null) unreadable++
            if (!inside || !visited.add(canonical.orEmpty())) continue
            var emittedSuccess = false
            var failed = false
            listChildren(folder).collect { chunk ->
                when (chunk) {
                    is FileResult.Failure -> failed = true
                    is FileResult.Success -> {
                        emittedSuccess = true
                        for (node in chunk.value.filterNot { it.isHidden }) {
                            if (node.isDirectory) pending.add(node.id)
                            batch += node.toEntity(generation)
                            if (batch.size >= BATCH_SIZE) flush()
                        }
                    }
                }
            }
            if (failed || !emittedSuccess) unreadable++
        }
        flush()
        if (unreadable > 0) {
            _state.update { it.copy(indexedCount = dao.count(), unreadable = unreadable) }
            return@withLock
        }
        dao.deleteOlderThan(generation)
        val finished = clock()
        builtAtStore.set(finished)
        _state.update {
            SearchIndexState(indexedCount = dao.count(), building = true, builtAt = finished, unreadable = unreadable)
        }
    }

    private fun isInside(
        canonical: String,
        root: String,
    ): Boolean = canonical == root || canonical.startsWith(root.trimEnd('/') + "/")

    private fun FileNode.toEntity(generation: Long) =
        SearchIndexEntity(
            id = id.raw,
            name = name,
            nameLower = name.lowercase(),
            parentId = parentId?.raw,
            isDirectory = isDirectory,
            size = if (isDirectory) -1 else size,
            modifiedAt = modifiedAt,
            mimeType = mimeType,
            generation = generation,
        )

    private fun SearchIndexEntity.toHit() =
        SearchHit(
            id = FileNodeId(id),
            name = name,
            parentId = parentId?.let(::FileNodeId),
            isDirectory = isDirectory,
            size = size,
            modifiedAt = modifiedAt,
            mimeType = mimeType,
        )

    companion object {
        const val BATCH_SIZE = 500
        const val SEARCH_PAGE_SIZE = 500
        const val DEFAULT_FRESH_MILLIS = 6L * 60 * 60 * 1000
    }
}
