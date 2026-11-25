package com.devbehindyou.atomicfilemanager

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.InspectArchiveUseCase
import com.devbehindyou.atomicfilemanager.ui.components.ArchiveContents
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class ArchiveInspectionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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

    @Test
    fun realTarFoldersNavigateAndEachFileShowsItsExactPath() {
        val context = compose.activity.applicationContext
        val source = File.createTempFile("archive-navigation-", ".tar", context.cacheDir)
        try {
            writeTar(source, listOf("first/readme.txt", "second/readme.txt", "top.txt"))
            val original = source.readBytes()
            val backend = FileSystemBackend(context)
            val inspected = runBlocking { InspectArchiveUseCase { backend }(FileNodeId.file(source.absolutePath)) }
            val entries = (inspected as FileResult.Success).value

            compose.setContent {
                AtomicTheme(darkTheme = true, wallpaperAccent = false) { ArchiveContents(entries) }
            }
            awaitEntry("first")
            compose.onNodeWithText("first").performClick()
            awaitEntry("readme.txt")
            compose.onNodeWithText("top.txt").assertDoesNotExist()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            awaitEntry("second")
            compose.onNodeWithText("second").performClick()
            awaitEntry("readme.txt")
            compose.onNodeWithText("readme.txt").performClick()
            compose.onNodeWithText("second/readme.txt").assertIsDisplayed()
            assertArrayEquals(original, source.readBytes())
        } finally {
            assertTrue(source.delete() || !source.exists())
        }
    }

    private fun awaitEntry(name: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
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
