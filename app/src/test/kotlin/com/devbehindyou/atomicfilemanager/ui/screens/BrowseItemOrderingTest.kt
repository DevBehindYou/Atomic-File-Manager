package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class BrowseItemOrderingTest {
    @ParameterizedTest
    @EnumSource(SortOption::class)
    fun `each direction orders mixed entries without reversing folders first`(option: SortOption) {
        val items = listOf(
            node("Banana", size = 20, modified = 400),
            node("Zeta folder", directory = true, size = 10, modified = 100),
            node("Cherry", size = 60, modified = 50),
            node("Alpha folder", directory = true, size = 30, modified = 300),
            node("apple", size = 40, modified = 200),
        )
        val expected = when (option) {
            SortOption.NAME -> listOf("Alpha folder", "Zeta folder", "apple", "Banana", "Cherry")
            SortOption.NAME_DESCENDING -> listOf("Zeta folder", "Alpha folder", "Cherry", "Banana", "apple")
            SortOption.DATE_MODIFIED_ASCENDING -> listOf("Zeta folder", "Alpha folder", "Cherry", "apple", "Banana")
            SortOption.DATE_MODIFIED -> listOf("Alpha folder", "Zeta folder", "Banana", "apple", "Cherry")
            SortOption.SIZE_ASCENDING -> listOf("Zeta folder", "Alpha folder", "Banana", "apple", "Cherry")
            SortOption.SIZE -> listOf("Alpha folder", "Zeta folder", "Cherry", "apple", "Banana")
        }
        assertEquals(expected, ordered(items, option).map { it.name })
        assertEquals(expected, ordered(items.reversed(), option).map { it.name })
    }

    @ParameterizedTest
    @EnumSource(SortOption::class)
    fun `equal fields and case folded names have deterministic name and identity ties`(option: SortOption) {
        val first = node("same", id = "/a")
        val second = node("same", id = "/b")
        val upper = node("SAME", id = "/c")
        val folder = node("same", id = "/folder", directory = true)
        val items = listOf(second, first, upper, folder)
        val expected = listOf(folder.id, upper.id, first.id, second.id)
        assertEquals(expected, ordered(items, option).map { it.id })
        assertEquals(expected, ordered(items.reversed(), option).map { it.id })
    }

    @ParameterizedTest
    @EnumSource(
        value = SortOption::class,
        names = ["DATE_MODIFIED_ASCENDING", "DATE_MODIFIED", "SIZE_ASCENDING", "SIZE"],
    )
    fun `unknown metadata stays last within each group and zero bytes remains known`(option: SortOption) {
        // Missing metadata uses FileNode's sentinels; nullable MIME/count/extras also stay safe.
        val unknownFolder = node("A unknown folder", directory = true, size = -1, modified = 0)
        val knownFolder = node("Z known folder", directory = true, size = 0, modified = 1)
        val unknown = node("A unknown", size = -1, modified = 0)
        val zero = node("Z zero", size = 0, modified = 1)
        val largest = node("Middle", size = Long.MAX_VALUE, modified = Long.MAX_VALUE)
        val knownFiles = if (option in setOf(SortOption.DATE_MODIFIED, SortOption.SIZE)) {
            listOf(largest.id, zero.id)
        } else {
            listOf(zero.id, largest.id)
        }
        val items = listOf(unknown, unknownFolder, largest, zero, knownFolder)
        assertEquals(
            listOf(knownFolder.id, unknownFolder.id) + knownFiles + unknown.id,
            ordered(items, option).map { it.id },
        )
    }

    @Test
    fun `sort directions preserve search and hidden category filtering`() {
        val folder = node("keep folder", directory = true)
        val image = node("keep image.png").copy(mimeType = "image/png")
        val hidden = node("keep hidden.png").copy(mimeType = "image/png", isHidden = true)
        val wrongType = node("keep notes.txt").copy(mimeType = "text/plain")
        val wrongQuery = node("other.png").copy(mimeType = "image/png")
        val input = listOf(wrongType, image, hidden, wrongQuery, folder)
        for (option in SortOption.entries) {
            assertEquals(
                listOf(folder.id, image.id),
                filterAndSortBrowseItems(input, "keep", option, false, BrowseFileFilter.IMAGES).map { it.id },
            )
        }
        assertEquals(listOf(wrongType, image, hidden, wrongQuery, folder), input)
    }

    private fun ordered(items: List<FileNode>, option: SortOption) =
        filterAndSortBrowseItems(items, "", option, true, BrowseFileFilter.ALL)

    private fun node(
        name: String,
        id: String = "/root/$name",
        directory: Boolean = false,
        size: Long = 1,
        modified: Long = 1,
    ) = FileNode(
        id = FileNodeId.file(id),
        name = name,
        displayName = name,
        mimeType = null,
        size = size,
        modifiedAt = modified,
        isDirectory = directory,
        isHidden = false,
        parentId = null,
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL,
        childCount = null,
        extras = null,
    )
}
