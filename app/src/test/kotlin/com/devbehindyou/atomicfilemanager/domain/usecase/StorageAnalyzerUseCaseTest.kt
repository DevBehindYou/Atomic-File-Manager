package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StorageAnalyzerUseCaseTest {
    private lateinit var backend: InMemoryBackend
    private lateinit var readContent: ReadFileContentUseCase
    private lateinit var analyzer: StorageAnalyzerUseCase

    @BeforeEach
    fun setUp() {
        backend = InMemoryBackend()
        readContent = ReadFileContentUseCase { backend }
        analyzer =
            StorageAnalyzerUseCase(
                backendSelector = { backend },
                readFileContentUseCase = readContent,
            )
    }

    private suspend fun writeBytes(
        parentId: FileNodeId,
        name: String,
        bytes: ByteArray,
    ): FileNodeId {
        val target = (backend.openOutput(parentId, name, "application/octet-stream") as FileResult.Success).value
        target.stream().use { it.write(bytes) }
        target.sync()
        return (target.toNode() as FileResult.Success).value.id
    }

    @Test
    fun `keeps the last finished analysis for a while`() =
        runTest {
            var now = 1_000L
            val timed =
                StorageAnalyzerUseCase(
                    backendSelector = { backend },
                    readFileContentUseCase = readContent,
                    clock = { now },
                )
            writeBytes(backend.rootId, "a.dat", ByteArray(10))
            assertNull(timed.lastAnalysis(backend.rootId))
            timed.analyze(backend.rootId).toList()
            val last = timed.lastAnalysis(backend.rootId)
            assertEquals(1, last?.result?.scannedFilesCount)
            assertEquals(1_000L, last?.scannedAt)
            now += StorageAnalyzerUseCase.RECENT_MILLIS + 1
            assertNull(timed.lastAnalysis(backend.rootId))
        }

    @Test
    fun `cleanup invalidation stops cached suggestions being offered without a new request`() =
        runTest {
            analyzer.analyze(backend.rootId).toList()
            assertTrue(analyzer.lastAnalysis(backend.rootId) != null)
            analyzer.invalidate(backend.rootId)
            assertNull(analyzer.lastAnalysis(backend.rootId))
        }

    @Test
    fun `detects large files exceeding specified threshold`() =
        runTest {
            val smallData = ByteArray(1024) { 1 }
            val largeData = ByteArray(5000) { 2 }

            writeBytes(backend.rootId, "small.dat", smallData)
            val largeId = writeBytes(backend.rootId, "large.dat", largeData)

            val result = analyzer.getFullAnalysis(backend.rootId, largeFileThresholdBytes = 4000)

            assertEquals(1, result.largeFiles.size)
            assertEquals(largeId, result.largeFiles.first().id)
            assertEquals(5000L, result.largeFiles.first().size)
        }

    @Test
    fun `detects duplicate files by matching byte size and sha256`() =
        runTest {
            val uniqueData = "Unique content here".toByteArray()
            val duplicateData = "Identical bytes across two files!".toByteArray()

            writeBytes(backend.rootId, "unique.txt", uniqueData)
            writeBytes(backend.rootId, "file_copy_1.bin", duplicateData)

            val subDir = (backend.createDirectory(backend.rootId, "subfolder") as FileResult.Success).value
            writeBytes(subDir.id, "file_copy_2.bin", duplicateData)

            val result = analyzer.getFullAnalysis(backend.rootId)

            assertEquals(1, result.duplicateGroups.size)
            val group = result.duplicateGroups.first()
            assertEquals(2, group.items.size)
            assertEquals(duplicateData.size.toLong(), group.sizeBytes)
            assertEquals(duplicateData.size.toLong(), group.potentialSavingsBytes)
            assertTrue(result.totalPotentialSavingsBytes >= duplicateData.size)
        }

    @Test
    fun `detects empty folders and temporary cache files`() =
        runTest {
            val emptyDir = (backend.createDirectory(backend.rootId, "empty_dir") as FileResult.Success).value
            val nonEmptyDir = (backend.createDirectory(backend.rootId, "normal_dir") as FileResult.Success).value
            writeBytes(nonEmptyDir.id, "normal.txt", "some text".toByteArray())

            writeBytes(backend.rootId, "app_crash.log", "stack trace log".toByteArray())
            writeBytes(backend.rootId, "temp_data.tmp", "transient data".toByteArray())

            val result = analyzer.getFullAnalysis(backend.rootId)

            assertEquals(1, result.emptyFolders.size)
            assertEquals(emptyDir.id, result.emptyFolders.first().id)

            assertEquals(2, result.tempCacheFiles.size)
            val names = result.tempCacheFiles.map { it.name }.toSet()
            assertTrue("app_crash.log" in names)
            assertTrue("temp_data.tmp" in names)
        }

    @Test
    fun `analyze flow emits intermediate and final progress`() =
        runTest {
            writeBytes(backend.rootId, "file1.bin", "duplicate bytes".toByteArray())
            writeBytes(backend.rootId, "file2.bin", "duplicate bytes".toByteArray())

            val progressList = analyzer.analyze(backend.rootId, largeFileThresholdBytes = 10).toList()

            assertTrue(progressList.isNotEmpty())
            val finalProgress = progressList.last()
            assertTrue(finalProgress.isComplete)
            assertEquals(1, finalProgress.foundCount)
        }
}
