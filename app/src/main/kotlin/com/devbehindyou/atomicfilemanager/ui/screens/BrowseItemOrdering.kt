package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.FileNode

internal fun filterAndSortBrowseItems(
    items: List<FileNode>,
    query: String,
    sort: SortOption,
    showHidden: Boolean,
    fileFilter: BrowseFileFilter,
): List<FileNode> {
    val visible = items.filter { (showHidden || !it.isHidden) && fileFilter.matches(it) }
    val filtered =
        if (query.isBlank()) {
            visible
        } else {
            visible.filter { it.name.contains(query, ignoreCase = true) }
        }
    val comparator = comparatorFor(sort)
    return filtered.sortedWith(comparator)
}

private fun comparatorFor(sortOption: SortOption): Comparator<FileNode> {
    val byField: Comparator<FileNode> =
        when (sortOption) {
            SortOption.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOption.DATE_MODIFIED -> compareByDescending { it.modifiedAt }
            SortOption.SIZE -> compareByDescending { it.size }
        }
    return compareByDescending<FileNode> { it.isDirectory }.then(byField)
}
