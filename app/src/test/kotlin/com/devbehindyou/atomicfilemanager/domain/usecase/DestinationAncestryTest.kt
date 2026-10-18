package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DestinationAncestryTest {
    @Test
    fun `provider descendant rejection and failures prevent copy and move mutations`() =
        runTest {
            for (type in listOf(OperationType.COPY, OperationType.MOVE)) {
                for (answer in listOf(FileResult.Success(true), FileResult.Failure(FileError.AccessDenied(null)))) {
                    val delegate = InMemoryBackend()
                    val source = (delegate.createDirectory(delegate.rootId, "source") as FileResult.Success).value
                    val destination =
                        (
                            delegate.createDirectory(
                                delegate.rootId,
                                "destination",
                            ) as FileResult.Success
                        ).value
                    var checks = 0
                    var mutations = 0
                    val backend =
                        object : StorageBackend by delegate {
                            override suspend fun isSameOrDescendant(
                                ancestor: FileNodeId,
                                candidate: FileNodeId,
                            ): FileResult<Boolean> {
                                assertEquals(source.id, ancestor)
                                assertEquals(destination.id, candidate)
                                checks++
                                return answer
                            }

                            override suspend fun createDirectory(
                                parent: FileNodeId,
                                name: String,
                            ) = delegate.createDirectory(parent, name).also { mutations++ }

                            override suspend fun moveWithin(
                                id: FileNodeId,
                                newParent: FileNodeId,
                            ) = delegate.moveWithin(id, newParent).also { mutations++ }
                        }
                    val operation =
                        FileOperation(
                            OperationId.random(),
                            type,
                            listOf(source.id),
                            destination.id,
                            OperationOptions(),
                            0L,
                        )
                    val snapshots = FileOperationsEngine { backend }.execute(operation).toList()
                    assertEquals(1, checks)
                    assertEquals(0, mutations)
                    assertFalse(delegate.exists(destination.id, source.name))
                    assertTrue(snapshots.last().status is OperationStatus.Failed)
                }
            }
        }
}
