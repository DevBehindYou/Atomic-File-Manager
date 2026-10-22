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
            SortOption.NAME_DESCENDING -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOption.DATE_MODIFIED_ASCENDING -> metadataComparator(false, 0) { it.modifiedAt }
            SortOption.DATE_MODIFIED -> metadataComparator(true, 0) { it.modifiedAt }
            SortOption.SIZE_ASCENDING -> metadataComparator(false, -1) { it.size }
            SortOption.SIZE -> metadataComparator(true, -1) { it.size }
        }
    // Never reverse the grouping or the unknown-metadata rank when reversing a field.
    return compareByDescending<FileNode> { it.isDirectory }.then(byField)
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.name }
        .thenBy { it.id.raw }
}

/** Unknown values follow known values within each folder/file group in either direction. */
private fun metadataComparator(
    descending: Boolean,
    unknown: Long,
    value: (FileNode) -> Long,
): Comparator<FileNode> =
    compareBy<FileNode> { value(it) == unknown }
        .then(if (descending) compareByDescending(value) else compareBy(value))
