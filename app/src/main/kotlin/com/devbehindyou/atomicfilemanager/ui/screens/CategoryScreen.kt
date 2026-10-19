package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicTitleRow
import com.devbehindyou.atomicfilemanager.data.volume.PhoneFileIndex
import com.devbehindyou.atomicfilemanager.data.volume.PhoneIndexSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.FileCollection
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.ui.components.FilePreviewDialog
import com.devbehindyou.atomicfilemanager.ui.components.FileThumbnail
import com.devbehindyou.atomicfilemanager.ui.components.iconFor
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files of one collection plus the snapshot they were computed from. */
private class CollectionResult(val source: PhoneIndexSnapshot?, val files: List<FileNode>)

@Composable
fun CategoryScreen(
    collection: FileCollection,
    roots: List<FileNodeId>,
    index: PhoneFileIndex,
    onBack: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var snapshot by remember { mutableStateOf(PhoneIndexSnapshot()) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by rememberSaveable(collection) { mutableStateOf("") }
    var preview by remember { mutableStateOf<FileNode?>(null) }
    LaunchedEffect(roots, refresh) {
        snapshot = PhoneIndexSnapshot()
        index.scan(roots, refresh > 0).collect { snapshot = it }
    }
    // Filtering and sorting every file on the phone must not run on the main thread. The producer restarts
    // (and cancels the previous run) whenever a newer scan snapshot, collection or query arrives.
    val result by produceState(CollectionResult(null, emptyList()), snapshot, collection, query) {
        val source = snapshot
        val files =
            withContext(Dispatchers.Default) {
                source.files
                    .filter { collection.matches(it) && it.name.contains(query, ignoreCase = true) }
                    .sortedByDescending { it.modifiedAt }
            }
        value = CollectionResult(source, files)
    }
    val files = result.files
    val settled = result.source === snapshot
    Column(
        Modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AtomicPushedHeader(
            onBack = onBack,
            eyebrow = "Category",
            actions = {
                AtomicIconButton(AtomicIcons.Refresh, "Refresh category", onClick = { refresh++ })
            },
        )
        AtomicTitleRow(
            title = collection.title,
            counter = if (snapshot.complete) "${files.size} files" else "${files.size} found so far",
            modifier = Modifier.padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8),
        )
        if (roots.isEmpty()) {
            AtomicEmptyState(
                message = "Storage access is off.",
                detail = "Allow it to find ${collection.title.lowercase()} on your phone and SD card.",
                actionLabel = "Grant access",
                onAction = onGrantAccess,
                modifier = Modifier.padding(AtomicSpacing.s16),
            )
        } else {
            Column(
                Modifier.padding(horizontal = AtomicSpacing.s16),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            ) {
                AtomicTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search ${collection.title.lowercase()}",
                    placeholder = "Name contains…",
                    modifier = Modifier.fillMaxWidth(),
                )
                AtomicText(
                    "Internal storage and SD card" + if (snapshot.complete) "" else " · Scanning",
                    AtomicTextRole.MonoMeta,
                )
                if (!snapshot.complete) AtomicLoading("Scanning · ${files.size} found so far")
                if (snapshot.unreadable > 0) {
                    AtomicText(
                        "${snapshot.unreadable} protected or unavailable folders couldn't be read.",
                        AtomicTextRole.MonoMeta,
                        color = Atomic.colors.error,
                    )
                }
            }
            if (snapshot.complete && settled && files.isEmpty()) {
                AtomicEmptyState(
                    message =
                        if (query.isEmpty()) {
                            "No ${collection.title.lowercase()} found on this phone."
                        } else {
                            "Nothing matches \"$query\"."
                        },
                    modifier = Modifier.padding(AtomicSpacing.s16),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(files, key = { it.id.raw }) { file ->
                    val folder = file.parentId?.raw?.removePrefix(FileNodeId.Prefix.FILE.scheme).orEmpty()
                    AtomicFileRow(
                        name = file.name,
                        meta = "${FileUtils.formatBytes(file.size)} · $folder",
                        icon = iconFor(file),
                        leading = { FileThumbnail(file) },
                        onClick = { preview = file },
                        hidden = file.isHidden,
                        modifier = Modifier.padding(horizontal = AtomicSpacing.s16),
                    )
                }
            }
        }
    }
    preview?.let { FilePreviewDialog(node = it, onDismiss = { preview = null }, gallery = result.files) }
}
