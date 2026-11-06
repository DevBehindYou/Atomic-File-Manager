package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.FileCollection
import com.devbehindyou.atomicfilemanager.domain.model.FileNode

/** Category filters keep folders visible so navigating into another folder remains possible. */
enum class BrowseFileFilter(val label: String, private val collection: FileCollection? = null) {
    ALL("All files"),
    FOLDERS("Folders only"),
    FILES("Files only"),
    IMAGES("Images", FileCollection.IMAGES),
    VIDEOS("Videos", FileCollection.VIDEOS),
    AUDIO("Audio", FileCollection.AUDIO),
    DOCUMENTS("Documents", FileCollection.DOCUMENTS),
    ARCHIVES("Archives", FileCollection.ARCHIVES),
    ;

    fun matches(node: FileNode): Boolean =
        when (this) {
            ALL -> true
            FOLDERS -> node.isDirectory
            FILES -> !node.isDirectory
            else -> node.isDirectory || collection?.matches(node) == true
        }
}
