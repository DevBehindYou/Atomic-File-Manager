package com.devbehindyou.atomicfilemanager.data.repository

import android.content.Context
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPair
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPairs

/** Saved folder syncs (ALL_IN_ONE_PLAN.md 4.1), kept on this phone only. */
class SyncPairStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("atomic_sync_pairs", Context.MODE_PRIVATE)

    fun all(): List<SyncPair> = SyncPairs.decode(prefs.getString(KEY, null))

    fun find(
        leftRaw: String,
        rightRaw: String,
    ): SyncPair? = SyncPairs.find(all(), leftRaw, rightRaw)

    fun save(pair: SyncPair) = synchronized(writerLock) { write(SyncPairs.add(all(), pair)) }

    fun remove(pair: SyncPair) = synchronized(writerLock) { write(SyncPairs.remove(all(), pair)) }

    private fun write(pairs: List<SyncPair>) {
        prefs.edit().putString(KEY, SyncPairs.encode(pairs)).apply()
    }

    private companion object {
        val writerLock = Any()
        const val KEY = "pairs"
    }
}
