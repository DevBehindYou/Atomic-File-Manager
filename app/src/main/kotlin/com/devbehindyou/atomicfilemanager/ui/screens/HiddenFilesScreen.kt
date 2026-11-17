package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicWarningBox
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicConfirmSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSnackbarHost
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.HiddenItem
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.isVaultEncrypted
import com.devbehindyou.atomicfilemanager.domain.model.originalParent
import com.devbehindyou.atomicfilemanager.domain.repository.HiddenFilesRepository
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

private val HideMode.tabLabel: String
    get() =
        when (this) {
            HideMode.FAST_OBSCURE -> "Obscured"
            HideMode.GALLERY -> "Gallery"
            HideMode.PRIVATE_STORAGE -> "Private storage"
        }

private val HideMode.explanation: String
    get() =
        when (this) {
            HideMode.FAST_OBSCURE ->
                "Files stay in their folder with a masked name and header, so other apps don't recognise them."
            HideMode.GALLERY -> "Files sit in your hidden folder, where photo apps don't look."
            HideMode.PRIVATE_STORAGE ->
                "Files live inside the app's own folder. Other apps can't see them. They are deleted if " +
                    "Atomic File Manager is uninstalled, so restore them first."
        }

/** Existing Files visibility preference, supplied by the hosting screen. */
class HiddenFilesVisibility(
    val showHiddenFiles: Boolean,
    val onChange: (Boolean) -> Unit,
)

/** Manage hidden entries after the host's access gate, or only private entries on the dedicated route. */
@Composable
fun HiddenFilesScreen(
    repository: HiddenFilesRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialMode: HideMode = HideMode.FAST_OBSCURE,
    privateOnly: Boolean = false,
    visibility: HiddenFilesVisibility? = null,
    onLockPrivateFiles: (() -> Unit)? = null,
) {
    val hiddenItems by repository.hiddenItems.collectAsStateWithLifecycle()
    var currentMode by rememberSaveable { mutableStateOf(initialMode) }
    var itemToDelete by remember { mutableStateOf<HiddenItem?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // One place for every restore path so a failure is reported instead of ignored or thrown.
    suspend fun restore(item: HiddenItem): Result<Unit> =
        try {
            when (item.mode) {
                HideMode.FAST_OBSCURE -> repository.restoreFastObscured(item)
                HideMode.GALLERY -> repository.unhideFromGallery(item)
                HideMode.PRIVATE_STORAGE -> repository.restoreFromPrivateStorage(item, item.originalParent())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun restoreAndReport(items: List<HiddenItem>) {
        val failures = items.mapNotNull { item -> restore(item).exceptionOrNull()?.let { item to it } }
        val first = failures.firstOrNull()
        val message =
            when {
                first == null && items.size == 1 -> "Restored ${items.first().originalName}."
                first == null -> "Restored ${items.size} files."
                failures.size == 1 ->
                    "Couldn't restore ${first.first.originalName}: ${first.second.message ?: "unknown error"}"
                else ->
                    "Couldn't restore ${failures.size} of ${items.size} files. First error: " +
                        (first.second.message ?: "unknown error")
            }
        snackbarHostState.showSnackbar(message)
    }

    val mode = if (privateOnly) HideMode.PRIVATE_STORAGE else currentMode
    val currentItems = remember(hiddenItems, mode) { hiddenItems.filter { it.mode == mode } }

    Box(
        modifier.fillMaxSize().background(Atomic.colors.background)
            .testTag(if (privateOnly) "private_files_screen" else "hidden_files_screen"),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            AtomicPushedHeader(
                onBack = onNavigateBack,
                title = if (privateOnly) "Private files" else "Hidden files",
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(AtomicSpacing.s16),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
            ) {
                if (privateOnly) {
                    item { AtomicWarningBox(title = HiddenText.PRIVATE_TITLE, body = HiddenText.PRIVATE_EXPLANATION) }
                    if (onLockPrivateFiles != null) {
                        item { AtomicButton("Lock now", onClick = onLockPrivateFiles, leadingIcon = AtomicIcons.Lock) }
                    }
                } else {
                    item {
                        AtomicWarningBox(
                            title = HiddenText.VISIBILITY_TITLE,
                            body = HiddenText.VISIBILITY_EXPLANATION,
                        )
                    }
                    if (visibility != null) item { HiddenVisibilityCard(visibility) }
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                        ) {
                            HideMode.entries.forEach { option ->
                                AtomicChip(
                                    label = option.tabLabel,
                                    selected = mode == option,
                                    onSelectedChange = { currentMode = option },
                                )
                            }
                        }
                    }
                }
                item { AtomicText(mode.explanation, AtomicTextRole.BodySecondary) }
                if (!privateOnly && mode == HideMode.PRIVATE_STORAGE) {
                    item { AtomicWarningBox(title = HiddenText.PRIVATE_TITLE, body = HiddenText.PRIVATE_EXPLANATION) }
                }
                if (currentItems.isEmpty()) {
                    item {
                        AtomicEmptyState(
                            message =
                                if (mode == HideMode.PRIVATE_STORAGE) {
                                    "No private files yet."
                                } else {
                                    "Nothing ${mode.tabLabel.lowercase()} yet."
                                },
                            detail = "Open a file's actions in Files and choose Hide.",
                        )
                    }
                } else {
                    item {
                        AtomicSectionLabel(
                            "${currentItems.size} files · ${FileUtils.formatBytes(currentItems.sumOf { it.size })}",
                            actionLabel = "Restore all",
                            onAction = { scope.launch { restoreAndReport(currentItems) } },
                        )
                    }
                    items(currentItems, key = { it.id }) { item ->
                        HiddenItemRow(
                            item = item,
                            onRestore = { scope.launch { restoreAndReport(listOf(item)) } },
                            onDelete = { itemToDelete = item },
                            onEncrypt =
                                if (item.mode == HideMode.PRIVATE_STORAGE && !item.isVaultEncrypted) {
                                    {
                                        scope.launch {
                                            val done = repository.encryptPrivateItem(item)
                                            snackbarHostState.showSnackbar(HiddenText.encrypted(item, done.isSuccess))
                                        }
                                    }
                                } else {
                                    null
                                },
                            onExport =
                                if (item.isVaultEncrypted) {
                                    {
                                        scope.launch {
                                            val saved = repository.exportCopy(item, HiddenText.exportFolder())
                                            snackbarHostState.showSnackbar(HiddenText.exported(item, saved.getOrNull()))
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
        }
        AtomicSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    itemToDelete?.let { item ->
        AtomicConfirmSheet(
            label = "Delete",
            headline = "Delete for good?",
            body = "\"${item.originalName}\" (${FileUtils.formatBytes(
                item.size,
            )}) is deleted, not restored. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                itemToDelete = null
                scope.launch {
                    val error = repository.deleteHiddenItem(item).exceptionOrNull()
                    snackbarHostState.showSnackbar(
                        if (error == null) {
                            "Deleted ${item.originalName}."
                        } else {
                            "Couldn't delete ${item.originalName}: ${error.message ?: "unknown error"}"
                        },
                    )
                }
            },
            onDismiss = { itemToDelete = null },
        )
    }
}

@Composable
private fun HiddenVisibilityCard(visibility: HiddenFilesVisibility) {
    Column(
        Modifier.fillMaxWidth().background(Atomic.colors.surfaceCard, AtomicShape.lg)
            .padding(horizontal = AtomicSpacing.s16),
    ) {
        SettingsPreferenceToggle(
            title = "Show hidden files",
            description = if (visibility.showHiddenFiles) "Visible in Files" else "Hidden from Files",
            checked = visibility.showHiddenFiles,
            onChange = visibility.onChange,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HiddenItemRow(
    item: HiddenItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onEncrypt: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    val from = item.originalLocation.substringBeforeLast('/').ifEmpty { "/" }
    val icon = if (item.mode == HideMode.PRIVATE_STORAGE) AtomicIcons.Lock else AtomicIcons.Hidden
    Column(
        modifier =
            Modifier.fillMaxWidth().background(Atomic.colors.surfaceCard, AtomicShape.lg)
                .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtomicIconTile(icon, tint = Atomic.colors.accentText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4)) {
                AtomicText(item.originalName, AtomicTextRole.NameLarge, Modifier.semantics { heading() })
                AtomicText(
                    "From $from · ${FileUtils.formatBytes(item.size)} · ${FileUtils.formatDate(item.hiddenAt)}",
                    AtomicTextRole.BodySecondary,
                )
                if (!item.isAvailable) AtomicText("File unavailable", AtomicTextRole.BodySecondary)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicButton("Restore", onClick = onRestore, variant = AtomicButtonVariant.Ghost)
            if (onEncrypt != null) AtomicButton("Encrypt", onClick = onEncrypt, variant = AtomicButtonVariant.Text)
            if (onExport != null) AtomicButton("Export", onClick = onExport, variant = AtomicButtonVariant.Text)
            AtomicIconButton(
                AtomicIcons.Trash,
                "Delete ${item.originalName} for good",
                onClick = onDelete,
                variant = AtomicIconButtonVariant.Destructive,
            )
        }
    }
}

/** Wording for [HiddenFilesScreen]; pure so it is unit-tested. */
internal object HiddenText {
    fun encrypted(
        item: HiddenItem,
        ok: Boolean,
    ): String = if (ok) "“${item.originalName}” is now encrypted" else "“${item.originalName}” couldn't be encrypted"

    fun exported(
        item: HiddenItem,
        savedName: String?,
    ): String =
        if (savedName != null) {
            "Saved a copy to $EXPORT_PATH/$savedName"
        } else {
            "“${item.originalName}” couldn't be exported"
        }

    const val VISIBILITY_TITLE = "Hidden is not encrypted"
    const val VISIBILITY_EXPLANATION =
        "Show hidden files changes which dot-prefixed names Files displays. Use Private files for protected storage."

    const val PRIVATE_TITLE = "Private storage"
    const val PRIVATE_EXPLANATION =
        "New private files are encrypted with a key kept in Android's keystore. Restore puts a file back; " +
            "Export saves a normal copy to Download and keeps the private one."

    const val EXPORT_PATH = "Download/Atomic File Manager/Exported"

    fun exportFolder(): FileNodeId =
        FileNodeId.file(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Atomic File Manager/Exported",
            ).absolutePath,
        )
}
