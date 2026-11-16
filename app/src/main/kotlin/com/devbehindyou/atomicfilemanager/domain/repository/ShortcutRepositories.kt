package com.devbehindyou.atomicfilemanager.domain.repository

import com.devbehindyou.atomicfilemanager.domain.model.Favourite
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.RecentItem
import kotlinx.coroutines.flow.Flow

/** Starred files and folders (FR-6.4, FR-6.5). */
interface FavouritesRepository {
    /** Newest first. */
    fun observe(): Flow<List<Favourite>>

    suspend fun add(node: FileNode)

    suspend fun remove(id: FileNodeId)
}

/** Files opened in the app plus recent media (ALL_IN_ONE_PLAN.md 1.2). */
interface RecentsRepository {
    /** Files opened in the app, newest first. */
    fun observeOpened(limit: Int): Flow<List<RecentItem>>

    suspend fun recordOpened(node: FileNode)

    suspend fun remove(id: FileNodeId)

    /** Clears opened-file history metadata. Files and MediaStore are left intact. */
    suspend fun clearOpened()

    /** Photos, videos and audio added or changed since [sinceMillis], newest first; empty without access. */
    suspend fun recentMedia(
        sinceMillis: Long,
        limit: Int,
    ): List<RecentItem>
}
