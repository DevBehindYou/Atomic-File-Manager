package com.devbehindyou.atomicfilemanager.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubble
import com.devbehindyou.atomicfilemanager.domain.model.TransferBubbleItem
import java.util.UUID

class TransferBubbleDatabaseHelper(
    context: Context,
    dbName: String? = DATABASE_NAME,
) : SQLiteOpenHelper(context, dbName, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_BUBBLES (
                $COL_BUBBLE_ID TEXT PRIMARY KEY,
                $COL_DISPLAY_NAME TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_SORT_ORDER INTEGER NOT NULL,
                $COL_PREFERRED_OPERATION TEXT
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ITEMS (
                $COL_ITEM_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ITEM_BUBBLE_ID TEXT NOT NULL,
                $COL_ITEM_NODE_ID TEXT NOT NULL,
                $COL_ITEM_ORIGINAL_LOCATION TEXT NOT NULL,
                $COL_ITEM_DISPLAY_NAME TEXT NOT NULL,
                $COL_ITEM_SIZE INTEGER NOT NULL,
                $COL_ITEM_MIME TEXT,
                $COL_ITEM_ADDED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COL_ITEM_BUBBLE_ID) REFERENCES $TABLE_BUBBLES($COL_BUBBLE_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        db.execSQL(
            "CREATE INDEX idx_bubble_items_bubble_id ON $TABLE_ITEMS($COL_ITEM_BUBBLE_ID)",
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        // Schema migrations will be handled sequentially when bumping DATABASE_VERSION
    }

    fun getAllBubbles(): List<TransferBubble> {
        val db = readableDatabase
        val bubbles = mutableListOf<TransferBubble>()

        val bubbleCursor =
            db.query(
                TABLE_BUBBLES,
                null,
                null,
                null,
                null,
                null,
                "$COL_SORT_ORDER ASC, $COL_CREATED_AT ASC",
            )

        bubbleCursor.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(COL_BUBBLE_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(COL_DISPLAY_NAME)
            val createdIndex = cursor.getColumnIndexOrThrow(COL_CREATED_AT)
            val sortIndex = cursor.getColumnIndexOrThrow(COL_SORT_ORDER)
            val prefIndex = cursor.getColumnIndexOrThrow(COL_PREFERRED_OPERATION)

            while (cursor.moveToNext()) {
                val bubbleId = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex)
                val createdAt = cursor.getLong(createdIndex)
                val sortOrder = cursor.getInt(sortIndex)
                val preferredOp = if (cursor.isNull(prefIndex)) null else cursor.getString(prefIndex)

                val items = getItemsForBubble(db, bubbleId)

                bubbles.add(
                    TransferBubble(
                        id = bubbleId,
                        displayName = displayName,
                        createdAt = createdAt,
                        sortOrder = sortOrder,
                        preferredOperation = preferredOp,
                        items = items,
                    ),
                )
            }
        }
        return bubbles
    }

    private fun getItemsForBubble(
        db: SQLiteDatabase,
        bubbleId: String,
    ): List<TransferBubbleItem> {
        val items = mutableListOf<TransferBubbleItem>()
        val itemCursor =
            db.query(
                TABLE_ITEMS,
                null,
                "$COL_ITEM_BUBBLE_ID = ?",
                arrayOf(bubbleId),
                null,
                null,
                "$COL_ITEM_ADDED_AT ASC",
            )

        itemCursor.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COL_ITEM_ID)
            val nodeIdx = cursor.getColumnIndexOrThrow(COL_ITEM_NODE_ID)
            val locIdx = cursor.getColumnIndexOrThrow(COL_ITEM_ORIGINAL_LOCATION)
            val nameIdx = cursor.getColumnIndexOrThrow(COL_ITEM_DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(COL_ITEM_SIZE)
            val mimeIdx = cursor.getColumnIndexOrThrow(COL_ITEM_MIME)
            val addedIdx = cursor.getColumnIndexOrThrow(COL_ITEM_ADDED_AT)

            while (cursor.moveToNext()) {
                items.add(
                    TransferBubbleItem(
                        id = cursor.getLong(idIdx),
                        bubbleId = bubbleId,
                        fileNodeId = FileNodeId(cursor.getString(nodeIdx)),
                        originalLocation = cursor.getString(locIdx),
                        displayNameSnapshot = cursor.getString(nameIdx),
                        sizeSnapshot = cursor.getLong(sizeIdx),
                        mimeSnapshot = if (cursor.isNull(mimeIdx)) null else cursor.getString(mimeIdx),
                        addedAt = cursor.getLong(addedIdx),
                    ),
                )
            }
        }
        return items
    }

    /** Count and insert share a write transaction, including across helper instances. */
    fun createBubble(name: String?): Result<TransferBubble> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val currentCount = countBubbles()
            if (currentCount >= TransferBubble.MAX_BUBBLES) {
                return Result.failure(
                    IllegalStateException("Maximum of ${TransferBubble.MAX_BUBBLES} transfer bubbles allowed"),
                )
            }
            val bubble =
                TransferBubble(
                    id = UUID.randomUUID().toString(),
                    displayName = name ?: "Bubble ${currentCount + 1}",
                    sortOrder = currentCount,
                )
            if (!insertBubble(bubble)) {
                return Result.failure(IllegalStateException("Failed to persist transfer bubble"))
            }
            db.setTransactionSuccessful()
            return Result.success(bubble)
        } finally {
            db.endTransaction()
        }
    }

    fun insertBubble(bubble: TransferBubble): Boolean {
        val db = writableDatabase
        val values =
            ContentValues().apply {
                put(COL_BUBBLE_ID, bubble.id)
                put(COL_DISPLAY_NAME, bubble.displayName)
                put(COL_CREATED_AT, bubble.createdAt)
                put(COL_SORT_ORDER, bubble.sortOrder)
                put(COL_PREFERRED_OPERATION, bubble.preferredOperation)
            }
        return db.insert(TABLE_BUBBLES, null, values) != -1L
    }

    fun addItems(
        bubbleId: String,
        nodes: List<FileNode>,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (node in nodes) {
                val values =
                    ContentValues().apply {
                        put(COL_ITEM_BUBBLE_ID, bubbleId)
                        put(COL_ITEM_NODE_ID, node.id.raw)
                        put(COL_ITEM_ORIGINAL_LOCATION, node.parentId?.raw ?: "")
                        put(COL_ITEM_DISPLAY_NAME, node.displayName)
                        put(COL_ITEM_SIZE, node.size)
                        put(COL_ITEM_MIME, node.mimeType)
                        put(COL_ITEM_ADDED_AT, System.currentTimeMillis())
                    }
                db.insert(TABLE_ITEMS, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun removeItem(itemId: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_ITEMS, "$COL_ITEM_ID = ?", arrayOf(itemId.toString()))
    }

    fun clearBubble(bubbleId: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_ITEMS, "$COL_ITEM_BUBBLE_ID = ?", arrayOf(bubbleId))
    }

    fun deleteBubble(bubbleId: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_BUBBLES, "$COL_BUBBLE_ID = ?", arrayOf(bubbleId))
    }

    fun countBubbles(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_BUBBLES", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    companion object {
        const val DATABASE_NAME = "atomic_transfer_bubbles.db"
        const val DATABASE_VERSION = 1

        const val TABLE_BUBBLES = "transfer_bubbles"
        const val COL_BUBBLE_ID = "id"
        const val COL_DISPLAY_NAME = "display_name"
        const val COL_CREATED_AT = "created_at"
        const val COL_SORT_ORDER = "sort_order"
        const val COL_PREFERRED_OPERATION = "preferred_operation"

        const val TABLE_ITEMS = "transfer_bubble_items"
        const val COL_ITEM_ID = "id"
        const val COL_ITEM_BUBBLE_ID = "bubble_id"
        const val COL_ITEM_NODE_ID = "node_id"
        const val COL_ITEM_ORIGINAL_LOCATION = "original_location"
        const val COL_ITEM_DISPLAY_NAME = "display_name"
        const val COL_ITEM_SIZE = "size"
        const val COL_ITEM_MIME = "mime"
        const val COL_ITEM_ADDED_AT = "added_at"
    }
}
