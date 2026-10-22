package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.DuplicateGroup
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisProgress
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** One streaming traversal shared by progress UI and structured-result callers. */
@Singleton
class StorageAnalyzerUseCase
    @Inject
    constructor(
        private val backendSelector: (FileNodeId) -> StorageBackend,
        private val readFileContentUseCase: ReadFileContentUseCase,
        /** True when an .apk is for an app already installed; the platform check lives outside the domain. */
        private val isInstalledApk: suspend (FileNode) -> Boolean = { false },
        private val clock: () -> Long = System::currentTimeMillis,
    ) {
        /** A finished [analyze] run and when it finished. */
        class LastAnalysis(
            val result: StorageAnalysisResult,
            val scannedAt: Long,
        )

        private val lastRuns = ConcurrentHashMap<FileNodeId, LastAnalysis>()

        /**
         * The last finished [analyze] of [rootId] if it is younger than [maxAgeMillis], so the screen
         * can show it at once instead of walking the storage again (ALL_IN_ONE_PLAN.md §16.2 H6).
         */
        fun lastAnalysis(
            rootId: FileNodeId,
            maxAgeMillis: Long = RECENT_MILLIS,
        ): LastAnalysis? = lastRuns[rootId]?.takeIf { clock() - it.scannedAt in 0..maxAgeMillis }

        /** Cleanup changed the scanned files; do not offer stale cached suggestions on return. */
        fun invalidate(rootId: FileNodeId) {
            lastRuns.remove(rootId)
        }

        fun analyze(
            rootId: FileNodeId,
            largeFileThresholdBytes: Long = 50 * 1024 * 1024L,
            maxScanDepth: Int = 12,
        ): Flow<StorageAnalysisProgress> =
            flow {
                val result = scan(rootId, largeFileThresholdBytes, maxScanDepth) { emit(it) }
                lastRuns[rootId] = LastAnalysis(result, clock())
                emit(
                    StorageAnalysisProgress(
                        category = StorageAnalysisCategory.DUPLICATE_FILES,
                        scannedFilesCount = result.scannedFilesCount,
                        foundCount = result.duplicateGroups.size,
                        reclaimedBytesEstimate = result.totalPotentialSavingsBytes,
                        isComplete = true,
                        result = result,
                    ),
                )
            }.flowOn(Dispatchers.IO)

        suspend fun getFullAnalysis(
            rootId: FileNodeId,
            largeFileThresholdBytes: Long = 50 * 1024 * 1024L,
            maxScanDepth: Int = 12,
        ): StorageAnalysisResult =
            withContext(Dispatchers.IO) {
                scan(rootId, largeFileThresholdBytes, maxScanDepth) {}
            }

        private suspend fun scan(
            rootId: FileNodeId,
            threshold: Long,
            maxDepth: Int,
            progress: suspend (StorageAnalysisProgress) -> Unit,
        ): StorageAnalysisResult {
            require(threshold >= 0 && maxDepth >= 0)
            val files = linkedMapOf<FileNodeId, FileNode>()
            val emptyFolders = mutableListOf<FileNode>()
            val visited = mutableSetOf<FileNodeId>()
            val queue = ArrayDeque<Pair<FileNodeId, Int>>()
            queue.add(rootId to 0)
            var partial = false
            while (queue.isNotEmpty()) {
                coroutineContext.ensureActive()
                val (folder, depth) = queue.removeFirst()
                if (!visited.add(folder)) continue
                val backend = backendSelector(folder)
                var hadChildren = false
                var listingFailed = false
                var hadEmission = false
                backend.listChildren(folder).collect { chunk ->
                    coroutineContext.ensureActive()
                    hadEmission = true
                    when (chunk) {
                        is FileResult.Failure -> {
                            listingFailed = true
                            partial = true
                        }
                        is FileResult.Success -> for (child in chunk.value) {
                            coroutineContext.ensureActive()
                            hadChildren = true
                            if (child.isDirectory) {
                                // The Trash and other app data are never cleanup suggestions (ALL_IN_ONE_PLAN.md 1.1).
                                val appData = child.name == TrashManager.APP_FOLDER
                                if (!appData && depth < maxDepth) queue.add(child.id to depth + 1)
                                if (!appData && depth >= maxDepth) partial = true
                            } else if (files.putIfAbsent(child.id, child) == null && files.size % 50 == 0) {
                                progress(
                                    StorageAnalysisProgress(
                                        StorageAnalysisCategory.LARGE_FILES,
                                        files.size,
                                        0,
                                        0,
                                        false,
                                    ),
                                )
                            }
                        }
                    }
                }
                if (!hadEmission) partial = true
                if (hadEmission && !listingFailed && !hadChildren && folder != rootId) {
                    when (val node = backend.getNode(folder)) {
                        is FileResult.Success -> emptyFolders.add(node.value)
                        is FileResult.Failure -> partial = true
                    }
                }
            }
            progress(StorageAnalysisProgress(StorageAnalysisCategory.DUPLICATE_FILES, files.size, 0, 0, false))
            val duplicates = mutableListOf<DuplicateGroup>()
            val candidates = files.values.filter { it.size > 0 }.groupBy { it.size }.filterValues { it.size > 1 }
            for ((size, group) in candidates) {
                val hashes = mutableMapOf<String, MutableList<FileNode>>()
                for (file in group) {
                    coroutineContext.ensureActive()
                    when (val checksum = readFileContentUseCase.calculateChecksums(file.id)) {
                        is FileResult.Success -> hashes.getOrPut(checksum.value.sha256) { mutableListOf() }.add(file)
                        is FileResult.Failure -> partial = true
                    }
                }
                for ((hash, matches) in hashes) {
                    if (matches.size > 1) duplicates.add(DuplicateGroup(size, hash, matches))
                }
            }
            val now = clock()
            val installedApks =
                files.values.filter(CleanupRules::isApk).filter { apk ->
                    coroutineContext.ensureActive()
                    runCatching { isInstalledApk(apk) }.getOrDefault(false)
                }
            return StorageAnalysisResult(
                oldScreenshots =
                    files.values.filter {
                        CleanupRules.isOldScreenshot(
                            it,
                            now,
                        )
                    }.sortedBy { it.modifiedAt },
                oldDownloads =
                    files.values.filter {
                        CleanupRules.isOldDownload(
                            it,
                            now,
                        )
                    }.sortedByDescending { it.size },
                installedApks = installedApks.sortedByDescending { it.size },
                largeFiles = files.values.filter { it.size >= threshold }.sortedByDescending { it.size },
                duplicateGroups = duplicates.sortedByDescending { it.potentialSavingsBytes },
                emptyFolders = emptyFolders,
                tempCacheFiles = files.values.filter(::isTempOrCacheFile).sortedByDescending { it.size },
                scannedFilesCount = files.size,
                isPartial = partial,
            )
        }

        private fun isTempOrCacheFile(node: FileNode): Boolean {
            val lower = node.name.lowercase()
            return lower.endsWith(".tmp") || lower.endsWith(".temp") || lower.endsWith(".log") ||
                lower.endsWith(".bak") || lower.endsWith("~") || lower.startsWith("thumb_") ||
                node.id.raw.contains("/.thumbnails/") || node.id.raw.contains("/cache/")
        }

        companion object {
            /** How long a finished analysis is shown again without a rescan. */
            const val RECENT_MILLIS = 10L * 60 * 1000
        }
    }
