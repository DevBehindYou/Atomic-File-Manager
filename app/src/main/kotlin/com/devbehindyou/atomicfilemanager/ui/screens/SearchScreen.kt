package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.SearchFilter
import com.devbehindyou.atomicfilemanager.domain.model.SearchHit
import com.devbehindyou.atomicfilemanager.domain.model.SearchIndexState
import com.devbehindyou.atomicfilemanager.domain.model.toNode
import com.devbehindyou.atomicfilemanager.domain.repository.SearchIndex
import com.devbehindyou.atomicfilemanager.domain.usecase.GetNodeUseCase
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewDialog
import com.devbehindyou.atomicfilemanager.ui.components.FileThumbnail
import com.devbehindyou.atomicfilemanager.ui.components.iconFor
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val QUERY_LIMIT = 2_000
private const val SHOWN_LIMIT = 200
private const val TYPING_PAUSE_MS = 150L

private sealed interface SearchResults {
    data object Pending : SearchResults

    data object Failed : SearchResults

    data class Found(val hits: List<SearchHit>) : SearchResults
}

/** Search readable file and folder names using the existing index. */
@Composable
fun SearchScreen(
    index: SearchIndex,
    roots: List<FileNodeId>,
    getNode: GetNodeUseCase,
    onBack: () -> Unit,
    onOpenFolder: (FileNodeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val state by index.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    var results by remember(query, filter) { mutableStateOf<SearchResults>(SearchResults.Pending) }
    var showIndexStatus by rememberSaveable { mutableStateOf(false) }
    var showMoreTypes by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<FileNode?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    LaunchedEffect(roots) { index.refreshIfStale(roots) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Re-runs when the text, the chip or the index changes; the short pause skips keystrokes in between.
    LaunchedEffect(query, filter, state.indexedCount, state.builtAt) {
        if (query.isBlank()) return@LaunchedEffect
        delay(TYPING_PAUSE_MS)
        results =
            try {
                SearchResults.Found(index.search(query, QUERY_LIMIT, filter))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SearchResults.Failed
            }
    }
    val hits = (results as? SearchResults.Found)?.hits.orEmpty()
    val shown = remember(hits, filter) { hits.filter(filter::matches).take(SHOWN_LIMIT) }

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("search_screen"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = "Search")
        SearchNameField(
            value = query,
            onValueChange = { query = it },
            focus = focus,
            modifier = Modifier.padding(horizontal = AtomicSpacing.s16),
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(AtomicSpacing.s16),
        ) {
            item(key = "search_controls") {
                SearchControls(
                    filter = filter,
                    onFilter = { filter = it },
                    onMoreTypes = { showMoreTypes = true },
                    onIndexStatus = { showIndexStatus = true },
                )
            }
            when {
                query.isBlank() -> {
                    item {
                        AtomicEmptyState(
                            message = "What are you looking for?",
                            detail = "Search file names across accessible storage.",
                        )
                    }
                }
                results == SearchResults.Pending -> item { AtomicLoading("Searching…") }
                results == SearchResults.Failed -> {
                    item {
                        AtomicEmptyState(
                            message = "Search could not read the index.",
                            detail = "Check Index status, then try again.",
                        )
                    }
                }
                else -> {
                    val message = SearchText.emptyMessage(state, hits.size, shown.size)
                    if (message != null) {
                        item { AtomicEmptyState(message = message.first, detail = message.second) }
                    }
                }
            }
            items(shown, key = { it.id.raw }) { hit ->
                AtomicFileRow(
                    name = hit.name,
                    meta = SearchText.meta(hit),
                    icon = iconFor(hit.toNode()),
                    leading = { FileThumbnail(hit.toNode()) },
                    onClick = {
                        if (hit.isDirectory) {
                            onOpenFolder(hit.id)
                        } else {
                            scope.launch {
                                preview = (getNode(hit.id) as? FileResult.Success)?.value ?: hit.toNode()
                            }
                        }
                    },
                    trailing =
                        hit.parentId?.let { parent ->
                            @Composable {
                                AtomicIconButton(
                                    AtomicIcons.FolderOpen,
                                    "Open the folder holding ${hit.name}",
                                    onClick = { onOpenFolder(parent) },
                                )
                            }
                        },
                )
            }
        }
    }

    if (showIndexStatus) {
        SearchIndexDialog(
            state = state,
            hasRoots = roots.isNotEmpty(),
            onRebuild = { index.rebuild(roots) },
            onDismiss = { showIndexStatus = false },
        )
    }
    if (showMoreTypes) {
        SearchMoreTypesDialog(
            filter = filter,
            onFilter = {
                filter = it
                showMoreTypes = false
            },
            onDismiss = { showMoreTypes = false },
        )
    }
    preview?.let { FilePreviewDialog(node = it, onDismiss = { preview = null }) }
}

/** Wording for [SearchScreen]; pure so it is unit-tested. */
internal object SearchText {
    fun status(state: SearchIndexState): String =
        when {
            state.building -> "Indexing · ${count(state.indexedCount)} so far"
            state.builtAt == null -> "Not indexed yet"
            else -> "${count(state.indexedCount)} · indexed ${FileUtils.formatDate(state.builtAt)}"
        }

    /** Folder path, then size and date for files. */
    fun meta(hit: SearchHit): String {
        val folder = hit.parentId?.raw?.removePrefix("file:") ?: "/"
        return if (hit.isDirectory) folder else "$folder · ${FileUtils.formatBytes(hit.size)}"
    }

    /** Title and detail when there is nothing to list, or null when there are results. */
    fun emptyMessage(
        state: SearchIndexState,
        matches: Int,
        shown: Int,
    ): Pair<String, String?>? =
        when {
            shown > 0 -> null
            matches > 0 -> "Nothing of this type matches." to "Try All types."
            state.building -> "No match yet." to "Still indexing; results appear as folders are read."
            state.builtAt == null ->
                "Search needs an index." to "Check Index status to rebuild when storage is available."
            else -> "No matching files" to "Try a shorter name or choose a different file type."
        }

    private fun count(n: Int): String = if (n == 1) "1 item" else String.format(Locale.US, "%,d items", n)
}
