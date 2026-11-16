package com.devbehindyou.atomicfilemanager.data.database.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.devbehindyou.atomicfilemanager.domain.model.Favourite
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.RecentItem
import com.devbehindyou.atomicfilemanager.domain.model.RecentSource
import com.devbehindyou.atomicfilemanager.domain.repository.FavouritesRepository
import com.devbehindyou.atomicfilemanager.domain.repository.RecentsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Table `favourite` (ALL_IN_ONE_PLAN.md 1.2). [id] is the raw node id. */
@Entity(tableName = "favourite", indices = [Index("addedAt")])
data class FavouriteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDirectory: Boolean,
    val addedAt: Long,
)

/** Table `recent_item`: files opened in the app. Opening again moves a file to the top. */
@Entity(tableName = "recent_item", indices = [Index("openedAt")])
data class RecentItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val openedAt: Long,
)

@Dao
interface FavouriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavouriteEntity)

    @Query("DELETE FROM favourite WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM favourite ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteEntity>>
}

@Dao
interface RecentItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecentItemEntity)

    @Query("DELETE FROM recent_item WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM recent_item")
    suspend fun clear()

    @Query("SELECT * FROM recent_item ORDER BY openedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentItemEntity>>

    /** Keeps the newest [keep] rows. */
    @Query("DELETE FROM recent_item WHERE id NOT IN (SELECT id FROM recent_item ORDER BY openedAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}

class RoomFavouritesRepository(
    private val dao: FavouriteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : FavouritesRepository {
    override fun observe(): Flow<List<Favourite>> =
        dao.observeAll().map { rows ->
            rows.map { Favourite(FileNodeId(it.id), it.name, it.isDirectory, it.addedAt) }
        }

    override suspend fun add(node: FileNode) =
        dao.insert(
            FavouriteEntity(node.id.raw, node.name, node.isDirectory, clock()),
        )

    override suspend fun remove(id: FileNodeId) = dao.delete(id.raw)
}

class RoomRecentsRepository(
    private val dao: RecentItemDao,
    private val media: suspend (sinceMillis: Long, limit: Int) -> List<RecentItem>,
    private val clock: () -> Long = System::currentTimeMillis,
) : RecentsRepository {
    override fun observeOpened(limit: Int): Flow<List<RecentItem>> =
        dao.observeRecent(limit).map { rows ->
            rows.map { RecentItem(FileNodeId(it.id), it.name, it.mimeType, it.size, it.openedAt, RecentSource.OPENED) }
        }

    override suspend fun recordOpened(node: FileNode) {
        if (node.isDirectory) return
        dao.insert(RecentItemEntity(node.id.raw, node.name, node.mimeType, node.size, clock()))
        dao.trimTo(KEEP)
    }

    override suspend fun remove(id: FileNodeId) = dao.delete(id.raw)

    override suspend fun clearOpened() = dao.clear()

    override suspend fun recentMedia(
        sinceMillis: Long,
        limit: Int,
    ): List<RecentItem> = media(sinceMillis, limit)

    private companion object {
        const val KEEP = 100
    }
}
