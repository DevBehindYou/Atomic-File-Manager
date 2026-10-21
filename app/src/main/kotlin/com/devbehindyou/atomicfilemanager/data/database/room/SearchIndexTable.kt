package com.devbehindyou.atomicfilemanager.data.database.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * Table `search_index` (ALL_IN_ONE_PLAN.md 1.4). [nameLower] is matched with LIKE for substring
 * search ("port" finds "report"), which FTS4 tokens would not. A build writes every row with its
 * [generation] and then deletes older generations, so searches keep working while it runs.
 */
@Entity(tableName = "search_index", indices = [Index("generation")])
data class SearchIndexEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameLower: String,
    val parentId: String?,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val mimeType: String?,
    val generation: Long,
)

@Dao
interface SearchIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<SearchIndexEntity>)

    @Query(
        "SELECT * FROM search_index WHERE nameLower LIKE :pattern ESCAPE '\\' " +
            "AND (:directory IS NULL OR isDirectory = :directory) " +
            "ORDER BY isDirectory DESC, nameLower, id LIMIT :limit OFFSET :offset",
    )
    suspend fun search(
        pattern: String,
        limit: Int,
        offset: Int = 0,
        directory: Boolean? = null,
    ): List<SearchIndexEntity>

    @Query("DELETE FROM search_index WHERE generation < :generation")
    suspend fun deleteOlderThan(generation: Long): Int

    @Query("SELECT * FROM search_index WHERE isDirectory = 0")
    suspend fun allFiles(): List<SearchIndexEntity>

    @Query("SELECT COUNT(*) FROM search_index")
    suspend fun count(): Int

    @Query("SELECT id FROM search_index WHERE parentId = :parentId")
    suspend fun idsIn(parentId: String): List<String>

    /** Commits one fully enumerated folder as one durable unit; callers enumerate before entering. */
    @Transaction
    suspend fun reconcileFolder(parentId: String, rows: List<SearchIndexEntity>) {
        require(rows.all { it.parentId == parentId }) { "Folder rows must belong to the listed parent" }
        val present = rows.mapTo(HashSet()) { it.id }
        upsert(rows)
        idsIn(parentId).filterNot { it in present }.forEach { gone ->
            val escaped = gone.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            deleteTree(gone, "$escaped/%")
        }
    }

    /** Removes [id] and, for a folder, everything below it (ids of local files are paths). */
    @Query("DELETE FROM search_index WHERE id = :id OR id LIKE :descendants ESCAPE '\\'")
    suspend fun deleteTree(
        id: String,
        descendants: String,
    )
}
