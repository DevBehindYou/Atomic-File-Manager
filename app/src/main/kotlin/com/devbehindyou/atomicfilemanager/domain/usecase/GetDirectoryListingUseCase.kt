package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetDirectoryListingUseCase
    @Inject
    constructor(
        private val io: CoroutineDispatcher = Dispatchers.IO,
        private val backendSelector: (FileNodeId) -> StorageBackend,
    ) {
        operator fun invoke(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
            flow {
                // Selection and flow creation can also touch platform storage; defer both to IO.
                emitAll(backendSelector(id).listChildren(id))
            }.flowOn(io)
    }
