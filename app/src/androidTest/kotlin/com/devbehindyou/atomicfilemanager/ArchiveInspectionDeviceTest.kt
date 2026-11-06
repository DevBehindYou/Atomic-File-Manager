package com.devbehindyou.atomicfilemanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.InspectArchiveUseCase
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class ArchiveInspectionDeviceTest {
    @Test
    fun distinctTarPathsOpenAndDuplicatePathsFailWithoutChangingFiles() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = Files.createTempDirectory(context.cacheDir.toPath(), "archive-inspection-").toFile()
            try {
                val paths = listOf("first/readme.txt", "second/readme.txt")
                val valid = File(directory, "valid.tar")
                val duplicate = File(directory, "duplicate.tar")
                val sentinel = File(directory, "sentinel.txt")
                writeTar(valid, paths)
                writeTar(duplicate, listOf(paths.first(), paths.first()))
                sentinel.writeText("untouched")
                val originalValid = valid.readBytes()
                val originalDuplicate = duplicate.readBytes()
                val originalSentinel = sentinel.readBytes()
                val backend = FileSystemBackend(context)
                val inspect = InspectArchiveUseCase { backend }

                val listed = inspect(FileNodeId.file(valid.absolutePath))
                assertTrue("Distinct paths should be listed: $listed", listed is FileResult.Success)
                assertEquals(paths, (listed as FileResult.Success).value.map { it.path })
                val rejected = inspect(FileNodeId.file(duplicate.absolutePath))
                assertTrue("Duplicate paths should be rejected: $rejected", rejected is FileResult.Failure)
                assertTrue((rejected as FileResult.Failure).error is FileError.SuspiciousArchive)

                assertArrayEquals(originalValid, valid.readBytes())
                assertArrayEquals(originalDuplicate, duplicate.readBytes())
                assertArrayEquals(originalSentinel, sentinel.readBytes())
                assertEquals(setOf(valid.name, duplicate.name, sentinel.name), directory.list()?.toSet())
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun writeTar(
        file: File,
        paths: List<String>,
    ) {
        val bytes = "fixture".toByteArray()
        TarArchiveOutputStream(file.outputStream()).use { tar ->
            for (path in paths) {
                val entry = TarArchiveEntry(path)
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
    }
}
