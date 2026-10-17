package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class StorageReadDispatchTest {
    @Test
    fun `metadata and cold listing run on injected dispatcher`() =
        runTest {
            Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "storage-read-test") }
                .asCoroutineDispatcher().use { io ->
                    val delegate = InMemoryBackend()
                    val calls = mutableListOf<String>()
                    val backend =
                        object : StorageBackend by delegate {
                            override suspend fun getNode(id: FileNodeId) =
                                delegate.getNode(id).also {
                                    calls += "get:${Thread.currentThread().name}"
                                }

                            override fun listChildren(id: FileNodeId) =
                                flow {
                                    calls += "list:${Thread.currentThread().name}"
                                    delegate.listChildren(id).collect { emit(it) }
                                }
                        }
                    val listing =
                        GetDirectoryListingUseCase(io) {
                            calls += "select:${Thread.currentThread().name}"
                            backend
                        }(delegate.rootId)
                    assertTrue(calls.isEmpty(), "listing must stay cold")
                    listing.toList()
                    GetNodeUseCase(io) { backend }(delegate.rootId)
                    assertEquals(
                        listOf("select:storage-read-test", "list:storage-read-test", "get:storage-read-test"),
                        calls.map { it.substringBefore(" @coroutine#") },
                    )
                }
        }

    @Test
    fun `listing cancellation is not translated into success`() =
        runTest {
            val delegate = InMemoryBackend()
            val backend =
                object : StorageBackend by delegate {
                    override fun listChildren(id: FileNodeId) =
                        flow {
                            emit(FileResult.Success(emptyList<FileNode>()))
                            throw CancellationException("fixture cancellation")
                        }
                }
            var completed = false
            try {
                GetDirectoryListingUseCase { backend }(delegate.rootId).toList()
                completed = true
            } catch (_: CancellationException) {
                // Expected across the flowOn boundary.
            }
            assertFalse(completed)
        }
}
