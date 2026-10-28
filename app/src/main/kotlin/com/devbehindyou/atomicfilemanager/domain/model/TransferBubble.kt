package com.devbehindyou.atomicfilemanager.domain.model

data class TransferBubble(
    val id: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val preferredOperation: String? = null,
    val items: List<TransferBubbleItem> = emptyList(),
) {
    val itemCount: Int get() = items.size
    val totalKnownSize: Long get() = items.sumOf { if (it.sizeSnapshot > 0) it.sizeSnapshot else 0L }

    companion object {
        const val MAX_BUBBLES = 7
    }
}

data class TransferBubbleItem(
    val id: Long = 0L,
    val bubbleId: String,
    val fileNodeId: FileNodeId,
    val originalLocation: String,
    val displayNameSnapshot: String,
    val sizeSnapshot: Long,
    val mimeSnapshot: String?,
    val addedAt: Long = System.currentTimeMillis(),
)
