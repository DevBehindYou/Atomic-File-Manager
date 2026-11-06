package com.devbehindyou.atomicfilemanager.data.repository

import android.content.Context
import com.devbehindyou.atomicfilemanager.domain.repository.FolderSortMemory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Remembers the sort order chosen in each folder (FR-3.3) in a small SharedPreferences file keyed
 * by the folder's node id. Capped so it can't grow without bound: past [MAX_FOLDERS] entries the
 * file is cleared and starts again, which only means some folders fall back to the default order.
 */
class FolderSortPreferences(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FolderSortMemory {
    private val prefs by lazy { context.getSharedPreferences("atomic_folder_sort", Context.MODE_PRIVATE) }
    private val access = Mutex()

    override suspend fun get(folderRaw: String): String? =
        access.withLock {
            withContext(io) { prefs.getString(folderRaw, null) }
        }

    override suspend fun set(
        folderRaw: String,
        value: String,
    ) = access.withLock {
        withContext(io) {
            val editor = prefs.edit()
            if (prefs.all.size >= MAX_FOLDERS && !prefs.contains(folderRaw)) editor.clear()
            editor.putString(folderRaw, value).apply()
        }
    }

    private companion object {
        const val MAX_FOLDERS = 500
    }
}
