package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

/** Keeps an explicit gallery/editor selection until its original parent source is actually replaced. */
internal data class PreviewSourceSelection(
    val parent: FileNode,
    val selected: FileNode = parent,
) {
    fun forParent(currentParent: FileNode): FileNode =
        when {
            currentParent.id != parent.id -> currentParent
            currentParent != parent && selected.id == parent.id -> currentParent
            else -> selected
        }
}

/** Export metadata that changes the selected resource or its handoff; lazy extras do not restart an export. */
internal data class PreviewSourceKey(
    val id: FileNodeId,
    val name: String,
    val mimeType: String?,
    val modifiedAt: Long,
    val size: Long,
)

internal fun FileNode.previewSourceKey() = PreviewSourceKey(id, name, mimeType, modifiedAt, size)
