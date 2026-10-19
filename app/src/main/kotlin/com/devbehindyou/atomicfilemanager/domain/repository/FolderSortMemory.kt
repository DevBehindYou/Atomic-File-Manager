package com.devbehindyou.atomicfilemanager.domain.repository

/** Sort order chosen per folder in Files (FR-3.3); values are sort option names, keyed by raw folder id. */
interface FolderSortMemory {
    suspend fun get(folderRaw: String): String?

    suspend fun set(
        folderRaw: String,
        value: String,
    )
}
