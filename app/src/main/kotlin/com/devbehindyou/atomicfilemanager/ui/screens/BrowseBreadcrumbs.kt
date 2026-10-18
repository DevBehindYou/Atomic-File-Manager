package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.components.BreadcrumbItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A history entry keeps the visited ancestry, including providers that cannot return parents. */
internal data class BrowseLocation(val id: FileNodeId, val breadcrumbs: List<BreadcrumbItem>)

/** Only filesystem IDs encode paths. All other ancestry comes from resolved nodes or visited folders. */
internal suspend fun resolveBrowseBreadcrumbs(
    folderId: FileNodeId,
    folder: FileNode?,
    knownBreadcrumbs: List<BreadcrumbItem>,
    getNode: suspend (FileNodeId) -> FileResult<FileNode>,
): List<BreadcrumbItem> {
    val name = folder?.displayName?.ifEmpty { "Folder" } ?: "Folder"
    if (folderId.prefix == FileNodeId.Prefix.FILE || folderId.prefix == FileNodeId.Prefix.SHIZUKU) {
        return pathBreadcrumbs(folderId, name)
    }
    val knownIndex = knownBreadcrumbs.indexOfLast { it.path == folderId.raw }
    if (knownIndex >= 0) {
        val current = knownBreadcrumbs[knownIndex]
        return knownBreadcrumbs.take(knownIndex) + current.copy(name = folder?.displayName ?: current.name)
    }

    val crumbs = mutableListOf(BreadcrumbItem(name, folderId.raw))
    val visited = mutableSetOf(folderId)
    var parentId = folder?.parentId
    // Cyclic or arbitrarily deep provider responses must not keep navigation loading forever.
    while (parentId != null && crumbs.size < 128 && visited.add(parentId)) {
        currentCoroutineContext().ensureActive()
        val parent = when (val result = getNode(parentId)) {
            is FileResult.Success -> result.value
            is FileResult.Failure -> break
        }
        crumbs.add(BreadcrumbItem(parent.displayName, parent.id.raw))
        parentId = parent.parentId
    }
    return crumbs.asReversed().toList()
}

private fun pathBreadcrumbs(folderId: FileNodeId, folderName: String): List<BreadcrumbItem> {
    val prefix = requireNotNull(folderId.prefix).scheme
    val segments = folderId.raw.removePrefix(prefix).split('/').filter { it.isNotEmpty() }
    var runningPath = ""
    return segments.map { segment ->
        runningPath += "/$segment"
        BreadcrumbItem(segment, "$prefix$runningPath")
    }.ifEmpty { listOf(BreadcrumbItem(folderName, folderId.raw)) }
}
