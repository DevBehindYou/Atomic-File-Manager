package com.devbehindyou.atomicfilemanager.data.repository

import com.devbehindyou.atomicfilemanager.data.database.TransferBubbleDatabaseHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.repository.TransferBubbleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransferBubbleRepositoryImpl(
    private val dbHelper: TransferBubbleDatabaseHelper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * When set, the first database read runs on [ioDispatcher] in this scope instead of on the
     * constructing thread (the main thread at start-up, ALL_IN_ONE_PLAN.md §16.2 H2). Tests leave
     * it null and get the old synchronous load.
     */
    loadScope: CoroutineScope? = null,
) : TransferBubbleRepository {
    private val _bubbles = MutableStateFlow<List<TransferBubble>>(emptyList())
    override val bubbles: StateFlow<List<TransferBubble>> = _bubbles.asStateFlow()

    private val refreshLock = Any()

    init {
        if (loadScope == null) refresh() else loadScope.launch(ioDispatcher) { refresh() }
    }

    /** Reads and publishes under one lock, so whichever refresh runs last also read last. */
    private fun refresh() {
        synchronized(refreshLock) {
            _bubbles.value = dbHelper.getAllBubbles()
        }
    }

    override suspend fun createBubble(name: String?): Result<TransferBubble> =
        withContext(ioDispatcher) {
            val created = dbHelper.createBubble(name)
            if (created.isSuccess) refresh()
            created
        }

    override suspend fun addItemsToBubble(
        bubbleId: String,
        items: List<FileNode>,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            if (items.isEmpty()) return@withContext Result.success(Unit)
            dbHelper.addItems(bubbleId, items)
            refresh()
            Result.success(Unit)
        }

    override suspend fun removeItemFromBubble(
        bubbleId: String,
        itemId: Long,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            dbHelper.removeItem(itemId)
            refresh()
            Result.success(Unit)
        }

    override suspend fun clearBubble(bubbleId: String): Result<Unit> =
        withContext(ioDispatcher) {
            dbHelper.clearBubble(bubbleId)
            refresh()
            Result.success(Unit)
        }

    override suspend fun deleteBubble(bubbleId: String): Result<Unit> =
        withContext(ioDispatcher) {
            dbHelper.deleteBubble(bubbleId)
            refresh()
            Result.success(Unit)
        }

    override suspend fun getBubble(bubbleId: String): TransferBubble? =
        withContext(ioDispatcher) {
            _bubbles.value.find { it.id == bubbleId }
        }
}
