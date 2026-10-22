package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import java.util.UUID

/** One Browse tab: where it was opened, how often it was re-opened there, and what it shows now. */
data class BrowseTab(
    val id: Int,
    val folderRaw: String,
    val openRequest: Int = 0,
    val title: String = "",
    /** Empty for legacy saved tabs; newly opened tabs receive a persisted unique session. */
    val sessionId: String = "",
) {
    /** Preserve legacy model keys while keeping a reopened numeric tab ID in a fresh session. */
    val stateKey: String get() =
        if (sessionId.isEmpty()) {
            if (id == 0) "" else "tab$id:"
        } else {
            "tab$id:$sessionId:"
        }
}

internal fun primaryBrowseViewModelKey(tabKey: String, initialFolderId: FileNodeId): String =
    "browse:$tabKey${initialFolderId.raw}"

/**
 * Browse tabs (ALL_IN_ONE_PLAN.md 2.4): up to [MAX] tabs, each with its own view model. Pure so
 * it is unit-tested, and saved as a string so tabs survive rotation and process death.
 */
data class BrowseTabsState(
    val tabs: List<BrowseTab>,
    val activeId: Int,
) {
    val active: BrowseTab get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    val canOpenMore: Boolean get() = tabs.size < MAX

    /** Adds a tab at [folderRaw] after the active one and switches to it; unchanged when full. */
    fun open(
        folderRaw: String,
        title: String = "",
    ): BrowseTabsState {
        if (!canOpenMore) return this
        val id = (tabs.maxOf { it.id }) + 1
        val at = tabs.indexOfFirst { it.id == activeId } + 1
        val next = tabs.toMutableList().apply {
            add(at, BrowseTab(id, folderRaw, title = title, sessionId = UUID.randomUUID().toString()))
        }
        return BrowseTabsState(next, id)
    }

    /** Shows [folderRaw] in the active tab (an open from Home, Storage or a shortcut). */
    fun openInActive(folderRaw: String): BrowseTabsState =
        copy(
            tabs =
                tabs.map {
                    if (it.id == active.id) it.copy(folderRaw = folderRaw, openRequest = it.openRequest + 1) else it
                },
        )

    /** Closes a tab; the last one never closes. The neighbour on the left becomes active. */
    fun close(id: Int): BrowseTabsState {
        if (tabs.size == 1 || tabs.none { it.id == id }) return this
        val index = tabs.indexOfFirst { it.id == id }
        val rest = tabs.filterNot { it.id == id }
        val nextActive = if (id == activeId) rest[(index - 1).coerceAtLeast(0)].id else activeId
        return BrowseTabsState(rest, nextActive)
    }

    fun select(id: Int): BrowseTabsState = if (tabs.any { it.id == id }) copy(activeId = id) else this

    fun retitle(
        id: Int,
        title: String,
    ): BrowseTabsState = copy(tabs = tabs.map { if (it.id == id) it.copy(title = title) else it })

    fun encode(): String =
        listOf(activeId.toString()).plus(
            tabs.map {
                listOf(it.id, it.openRequest, it.title.escape(), it.folderRaw.escape(), it.sessionId.escape())
                    .joinToString(FIELD)
            },
        ).joinToString(TAB)

    companion object {
        const val MAX = 4
        private const val TAB = "\u001E"
        private const val FIELD = "\u001F"

        fun single(folderRaw: String): BrowseTabsState = BrowseTabsState(listOf(BrowseTab(0, folderRaw)), 0)

        /** The saved tabs, or one tab at [fallbackRaw] when nothing usable was saved. */
        fun decode(
            saved: String?,
            fallbackRaw: String,
        ): BrowseTabsState {
            val parts = saved?.split(TAB).orEmpty()
            val active = parts.firstOrNull()?.toIntOrNull()
            val tabs =
                parts.drop(1).mapNotNull { entry ->
                    val f = entry.split(FIELD)
                    val id = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    val request = f.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                    val folder = f.getOrNull(3)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val session = f.getOrNull(4).orEmpty().takeIf(::isSessionId).orEmpty()
                    BrowseTab(id, folder, request, f.getOrNull(2).orEmpty(), session)
                }.distinctBy { it.id }.take(MAX)
            if (active == null || tabs.isEmpty()) return single(fallbackRaw)
            return BrowseTabsState(tabs, if (tabs.any { it.id == active }) active else tabs.first().id)
        }

        /** Separators can't appear in a saved field; folder paths never contain them. */
        private fun String.escape(): String = replace(TAB, " ").replace(FIELD, " ")

        private fun isSessionId(value: String): Boolean =
            value.isEmpty() || runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    }
}

/** The tab strip; shown only while more than one tab is open, so a single tab looks as before. */
@Composable
fun BrowseTabStrip(
    state: BrowseTabsState,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
) {
    if (state.tabs.size < 2) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s4)
            .testTag("browse_tabs"),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.tabs.forEach { tab ->
            AtomicChip(
                label = BrowseTabText.label(tab),
                selected = tab.id == state.activeId,
                onSelectedChange = { onSelect(tab.id) },
            )
            if (tab.id == state.activeId) {
                AtomicIconButton(
                    AtomicIcons.Close,
                    "Close tab ${BrowseTabText.label(tab)}",
                    onClick = { onClose(tab.id) },
                )
            }
        }
    }
}

internal object BrowseTabText {
    /** The folder the tab shows, or the last part of where it was opened before it reports one. */
    fun label(tab: BrowseTab): String =
        tab.title.ifBlank {
            tab.folderRaw.substringAfter(':').trimEnd('/').substringAfterLast('/').ifBlank { "Storage" }
        }
}
