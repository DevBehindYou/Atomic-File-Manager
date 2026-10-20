package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationProgress
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TaskTextTest {
    private val copy =
        FileOperation(
            OperationId("job"),
            OperationType.COPY,
            listOf(FileNodeId.file("/photo.jpg")),
            FileNodeId.file("/backup"),
            OperationOptions(),
            0,
        )

    @Test
    fun `network labels follow transfer direction and retain move semantics`() {
        assertEquals("Copy", TaskText.title(copy))
        for (prefix in listOf("sftp", "ftp", "ftps", "smb", "webdav")) {
            val remote = FileNodeId("$prefix:server:/backup")
            assertEquals("Upload", TaskText.title(copy.copy(destination = remote)))
            assertEquals(
                "Download · Move",
                TaskText.title(copy.copy(type = OperationType.MOVE, sources = listOf(remote))),
            )
            assertEquals("Copy", TaskText.title(copy.copy(sources = listOf(remote), destination = remote)))
            assertEquals("Delete", TaskText.title(copy.copy(type = OperationType.DELETE, sources = listOf(remote))))
        }
    }

    @Test
    fun `unknown totals remain indeterminate and byte progress takes precedence`() {
        val progress = OperationProgress(2, 4, 25, 100, null, 0, null)
        assertEquals(0.25f, TaskText.fraction(progress))
        assertEquals(0.5f, TaskText.fraction(progress.copy(bytesTotal = 0)))
        assertNull(TaskText.fraction(progress.copy(bytesTotal = 0, itemsTotal = 0)))
        assertEquals(1f, TaskText.fraction(progress.copy(bytesDone = 200)))
    }
}
