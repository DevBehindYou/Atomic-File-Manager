package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetNodeUseCase
    @Inject
    constructor(
        private val io: CoroutineDispatcher = Dispatchers.IO,
        private val backendSelector: (FileNodeId) -> StorageBackend,
    ) {
        suspend operator fun invoke(id: FileNodeId): FileResult<FileNode> =
            withContext(io) {
                val backend = backendSelector(id)
                backend.getNode(id)
            }
    }
