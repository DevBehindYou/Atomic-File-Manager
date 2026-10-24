package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicErrorState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.data.preview.ApkInfo
import com.devbehindyou.atomicfilemanager.data.preview.ApkInfoReader
import com.devbehindyou.atomicfilemanager.data.preview.DocxPreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.ExternalPreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.androidVersionName
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.usecase.ArchiveEntryInfo
import com.devbehindyou.atomicfilemanager.domain.usecase.ArchivePasswords
import com.devbehindyou.atomicfilemanager.domain.usecase.ChecksumVerdict
import com.devbehindyou.atomicfilemanager.domain.usecase.FileChecksums
import com.devbehindyou.atomicfilemanager.domain.usecase.PASSWORD_PROTECTED
import com.devbehindyou.atomicfilemanager.domain.usecase.TextContent
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFileEditor
import com.devbehindyou.atomicfilemanager.domain.usecase.WRONG_PASSWORD
import com.devbehindyou.atomicfilemanager.domain.usecase.checkExpectedChecksum
import com.devbehindyou.atomicfilemanager.ui.components.preview.AudioPreviewContent
import com.devbehindyou.atomicfilemanager.ui.components.preview.DocxPreviewContent
import com.devbehindyou.atomicfilemanager.ui.components.preview.MarkdownPreviewContent
import com.devbehindyou.atomicfilemanager.ui.components.preview.PdfPreviewContent
import com.devbehindyou.atomicfilemanager.ui.components.preview.VideoPreviewContent
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * File preview (ATOMIC_UI_PLAN.md §7.5): a header with the name as stored and a mono size line,
 * then the viewer for the file type. Viewers keep their loading, error and paging states.
 */
@Composable
internal fun BuiltInFilePreviewPane(
    node: FileNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
    gallery: List<FileNode> = emptyList(),
    onOpenExternalForNode: ((FileNode) -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? AtomicApp
    // An image opened from a list swipes through that list's other images (ALL_IN_ONE_PLAN.md 1.5).
    val images =
        remember(node.id, gallery) {
            Gallery.of(gallery, node).takeIf { Gallery.isImage(node) && it.first.size > 1 }
        }
    var shown by remember(node.id) { mutableStateOf(node) }
    // Every preview counts as "opened" for Home > Recent (ALL_IN_ONE_PLAN.md 1.2).
    LaunchedEffect(shown.id) {
        try {
            withContext(Dispatchers.IO) { app?.container?.recentsRepository?.recordOpened(shown) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Recording a shortcut must not prevent viewing a readable file.
        }
    }
    val container = app?.container ?: return
    val externalHelper =
        remember(container, context) {
            ExternalPreviewHelper(context.applicationContext) { container.storageBackendSelector.forNode(it) }
        }
    val docxHelper = remember(container) { DocxPreviewHelper({ container.storageBackendSelector.forNode(it) }) }
    val openScope = rememberCoroutineScope()
    val preparationRequest =
        remember(node.id, node.modifiedAt, node.size, shown.id, shown.modifiedAt, shown.size, openScope) {
            ExternalPreparationRequest(openScope)
        }
    val preparation by key(preparationRequest) { preparationRequest.state.collectAsState() }
    val openingExternal = preparation.busy
    var externalFailed by remember(node.id, shown.id, shown.modifiedAt, shown.size) { mutableStateOf(false) }
    DisposableEffect(preparationRequest) {
        onDispose { preparationRequest.dispose() }
    }

    fun showSource(next: FileNode) {
        if (next.id != shown.id || next.modifiedAt != shown.modifiedAt || next.size != shown.size) {
            preparationRequest.dispose()
        }
        shown = next
    }
    val externalChooserTitle = stringResource(R.string.viewer_open_external)
    val openExternal: () -> Unit =
        onOpenExternal ?: {
            val requestedSource = shown
            preparationRequest.start { report ->
                externalFailed = false
                val result =
                    externalHelper.open(requestedSource, onProgress = report) { file ->
                        val view =
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(file.uri, file.mimeType)
                                clipData = ClipData.newRawUri(requestedSource.name, file.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(
                            Intent.createChooser(view, externalChooserTitle)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                currentCoroutineContext().ensureActive()
                externalFailed = result is FileResult.Failure
            }
        }
    val colors = Atomic.colors
    val previewType = remember(node) { resolvePreviewType(node) }
    // Share and open-with act on the file the caller opened, so they hide once another image is shown.
    val actsOnShown = shown.id == node.id
    val shownOpenExternal =
        resolvePreviewExternalAction(node, shown, onOpenExternal, onOpenExternalForNode, openExternal)

    Column(modifier.background(colors.background).testTag("file_preview_pane")) {
        Row(
            Modifier.fillMaxWidth().padding(start = AtomicSpacing.s10, end = AtomicSpacing.s16, top = AtomicSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
        ) {
            AtomicIconButton(
                AtomicIcons.Close,
                "Close preview",
                onClick = {
                    preparationRequest.dispose()
                    onClose()
                },
                variant = AtomicIconButtonVariant.Back,
                modifier = Modifier.testTag("preview_close"),
            )
            Column(Modifier.weight(1f)) {
                AtomicText(shown.name, AtomicTextRole.Name, maxLines = 1)
                val list = images?.first
                val position = list?.let { Gallery.position(it.indexOfFirst { n -> n.id == shown.id }, it.size) }
                val size = "${FileUtils.formatBytes(shown.size)} · ${FileUtils.formatDate(shown.modifiedAt)}"
                AtomicText(
                    if (position != null) "$position · $size" else size,
                    AtomicTextRole.MonoMeta,
                    maxLines = 1,
                )
            }
            if (onShare != null && actsOnShown) {
                AtomicIconButton(
                    AtomicIcons.Share,
                    "Share file",
                    onClick = onShare,
                    modifier = Modifier.testTag("preview_share"),
                )
            }
            PreviewExternalAction(
                onOpen = shownOpenExternal,
                busy = openingExternal,
            )
        }
        AtomicDivider(strong = true, modifier = Modifier.padding(top = AtomicSpacing.s4))
        if (openingExternal) {
            ExternalPreparationStatus(
                preparation,
                preparationRequest::cancel,
                Modifier.padding(AtomicSpacing.s12),
            )
        } else if (externalFailed) {
            AtomicText(
                stringResource(R.string.viewer_external_failed),
                AtomicTextRole.BodySecondary,
                modifier = Modifier.padding(AtomicSpacing.s12),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (images != null) {
                ImageGallery(images.first, images.second, container, onPageChange = ::showSource)
            } else {
                when (previewType) {
                    PreviewType.IMAGE -> ZoomableImage(node, container)
                    PreviewType.PDF -> PdfPreviewContent(node, container)
                    PreviewType.DOCX -> DocxPreviewContent(node, docxHelper::read)
                    PreviewType.TEXT -> TextPreviewContent(node, container, onSourceChanged = ::showSource)
                    PreviewType.MARKDOWN -> MarkdownPreviewContent(node, container)
                    PreviewType.ARCHIVE -> ArchivePreviewContent(node, container)
                    PreviewType.AUDIO -> AudioPreviewContent(node, container)
                    PreviewType.VIDEO -> VideoPreviewContent(node, container)
                    PreviewType.GENERIC -> GenericFileContent(node, container, shownOpenExternal, openingExternal)
                }
            }
        }
    }
}

/** A caller's original-file callback must never act on a different gallery image. */
internal fun resolvePreviewExternalAction(
    original: FileNode,
    shown: FileNode,
    onOpenOriginal: (() -> Unit)?,
    onOpenShown: ((FileNode) -> Unit)?,
    onOpenDefault: () -> Unit,
): (() -> Unit)? =
    when {
        onOpenOriginal != null -> onOpenOriginal.takeIf { original.id == shown.id }
        onOpenShown != null -> {
            { onOpenShown(shown) }
        }
        else -> onOpenDefault
    }

@Composable
internal fun PreviewExternalAction(
    onOpen: (() -> Unit)?,
    busy: Boolean,
) {
    if (onOpen != null) {
        AtomicIconButton(
            AtomicIcons.OpenExternally,
            stringResource(R.string.file_open_another_app),
            onClick = onOpen,
            enabled = !busy,
            modifier = Modifier.testTag("preview_open_external"),
        )
    }
}

@Composable
fun FilePreviewDialog(
    node: FileNode,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
    gallery: List<FileNode> = emptyList(),
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FilePreviewPane(
            node = node,
            onClose = onDismiss,
            modifier = modifier.fillMaxSize(),
            onShare = onShare,
            onOpenExternal = onOpenExternal,
            gallery = gallery,
        )
    }
}

/** Centred loading or error state shared by the viewers. */
@Composable
internal fun ViewerState(
    loading: Boolean,
    error: String?,
) {
    Box(Modifier.fillMaxSize().padding(AtomicSpacing.s16), contentAlignment = Alignment.Center) {
        when {
            loading -> AtomicLoading("Loading preview…")
            error != null -> AtomicErrorState(title = "Can't preview this file", message = error)
        }
    }
}

@Composable
private fun TextPreviewContent(
    node: FileNode,
    container: AppContainer,
    onSourceChanged: (FileNode) -> Unit,
) {
    var textContent by remember { mutableStateOf<TextContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wrapLines by remember { mutableStateOf(false) }
    var sourceRaw by rememberSaveable(node.id.raw) { mutableStateOf(node.id.raw) }
    var editorRaw by rememberSaveable(node.id.raw) { mutableStateOf<String?>(null) }
    val currentNode = remember(node, sourceRaw) { node.copy(id = FileNodeId(sourceRaw)) }
    // Bumped after each save in the editor, so the preview shows what was saved.
    var reloads by remember { mutableIntStateOf(0) }

    LaunchedEffect(sourceRaw, reloads) {
        isLoading = true
        errorMessage = null
        onSourceChanged(currentNode)
        when (val res = container.readFileContentUseCase.readText(currentNode.id)) {
            is FileResult.Success -> {
                textContent = res.value
                isLoading = false
            }
            is FileResult.Failure -> {
                errorMessage = "The text couldn't be read. The file may be binary or unreadable."
                isLoading = false
            }
        }
    }

    val content = textContent
    if (isLoading || errorMessage != null || content == null) {
        ViewerState(isLoading, errorMessage)
        return
    }
    val colors = Atomic.colors

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = AtomicSpacing.s16, end = AtomicSpacing.s8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtomicText(
                "${content.totalLinesCount} lines" + if (content.isTruncated) " · first part shown" else "",
                AtomicTextRole.MonoMeta,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
                if (node.access.writable && node.size <= TextFileEditor.MAX_EDIT_BYTES) {
                    AtomicChip(
                        label = "Edit",
                        selected = false,
                        onSelectedChange = { editorRaw = sourceRaw },
                        modifier = Modifier.testTag("preview_edit_text"),
                    )
                }
                AtomicChip(label = "Wrap", selected = wrapLines, onSelectedChange = { wrapLines = it })
            }
        }
        editorRaw?.let { opened ->
            TextEditorDialog(
                node = node.copy(id = FileNodeId(opened)),
                onDismiss = { editorRaw = null },
                onSaved = { saved ->
                    sourceRaw = saved.id.raw
                    onSourceChanged(saved)
                    reloads++
                },
            )
        }
        AtomicDivider()

        val scrollModifier =
            if (wrapLines) {
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
            }
        val mono = AtomicTypography.monoMeta

        Row(modifier = scrollModifier.padding(AtomicSpacing.s8)) {
            Column(
                modifier =
                    Modifier
                        .background(colors.surfaceInset, AtomicShape.sm)
                        .padding(horizontal = AtomicSpacing.s8, vertical = AtomicSpacing.s2),
            ) {
                content.lines.indices.forEach { index ->
                    Text(text = "${index + 1}", style = mono, color = colors.contentMuted)
                }
            }
            Spacer(modifier = Modifier.width(AtomicSpacing.s8))
            Column(modifier = Modifier.padding(vertical = AtomicSpacing.s2)) {
                content.lines.forEach { line ->
                    Text(text = line.ifEmpty { " " }, style = mono, color = colors.content)
                }
            }
        }
    }
}

@Composable
private fun ArchivePreviewContent(
    node: FileNode,
    container: AppContainer,
) {
    var entries by remember { mutableStateOf<List<ArchiveEntryInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // A password is asked for only when the archive needs one, and kept only on this screen.
    var needsPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var openedWith by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var extractQueued by remember { mutableStateOf(false) }

    LaunchedEffect(node.id, attempt) {
        isLoading = true
        val given = openedWith?.toCharArray()
        when (val res = container.inspectArchiveUseCase(node.id, given)) {
            is FileResult.Success -> {
                entries = res.value
                errorMessage = null
                needsPassword = false
            }
            is FileResult.Failure -> {
                val error = res.error
                needsPassword =
                    error is FileError.UnsupportedFormat &&
                    (error.mimeType == PASSWORD_PROTECTED || error.mimeType == WRONG_PASSWORD)
                errorMessage = archiveError(error)
                if (needsPassword) openedWith = null
            }
        }
        given?.fill(' ')
        isLoading = false
    }

    if (needsPassword) {
        Column(
            Modifier.fillMaxSize().padding(AtomicSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            AtomicText(errorMessage.orEmpty(), AtomicTextRole.Body)
            AtomicTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("archive_password"),
            )
            AtomicButton(
                if (isLoading) "Opening…" else "Open",
                onClick = {
                    openedWith = password
                    attempt++
                },
                enabled = password.isNotEmpty() && !isLoading,
                modifier = Modifier.fillMaxWidth().testTag("archive_password_open"),
            )
            AtomicText("The password isn't saved.", AtomicTextRole.BodySecondary)
        }
        return
    }
    if (isLoading || errorMessage != null) {
        ViewerState(isLoading, errorMessage)
        return
    }

    val parent = node.parentId
    if (openedWith != null && parent != null) {
        AtomicButton(
            if (extractQueued) "Extracting in the background" else "Extract here",
            onClick = {
                openedWith?.let { ArchivePasswords.put(node.id, it.toCharArray()) }
                container.operationQueue.enqueue(
                    FileOperation(
                        id = OperationId.random(),
                        type = OperationType.EXTRACT,
                        sources = listOf(node.id),
                        destination = parent,
                        options = OperationOptions(),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                extractQueued = true
            },
            enabled = !extractQueued,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16).testTag("archive_extract"),
        )
    }
    Column(modifier = Modifier.fillMaxSize()) {
        AtomicText(
            "${entries.size} items in archive",
            AtomicTextRole.MonoMeta,
            modifier = Modifier.padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8),
        )
        AtomicDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.path }) { entry ->
                AtomicFileRow(
                    name = entry.path,
                    meta =
                        if (entry.isDirectory) {
                            "Folder"
                        } else {
                            "${FileUtils.formatBytes(entry.uncompressedSize)} · " +
                                "${FileUtils.formatBytes(entry.compressedSize)} packed"
                        },
                    icon = if (entry.isDirectory) AtomicIcons.Files else AtomicIcons.Document,
                    onClick = {},
                    modifier = Modifier.padding(horizontal = AtomicSpacing.s16),
                )
            }
        }
    }
}

@Composable
private fun GenericFileContent(
    node: FileNode,
    container: AppContainer,
    onOpenExternal: (() -> Unit)?,
    openingExternal: Boolean,
) {
    var checksums by remember { mutableStateOf<FileChecksums?>(null) }
    var checksumError by remember { mutableStateOf<String?>(null) }
    var isCalculatingChecksums by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
    ) {
        if (node.name.endsWith(".apk", ignoreCase = true) && node.id.prefix == FileNodeId.Prefix.FILE) {
            ApkSummary(node)
        } else {
            AtomicText("No preview for this type. Details are below.", AtomicTextRole.BodySecondary)
        }
        val hashes = checksums
        AtomicFactSheet(
            buildList {
                add(AtomicFact("Size", FileUtils.formatBytes(node.size), monoValue = true))
                add(AtomicFact("Type", node.mimeType ?: "Unknown", monoValue = true))
                add(AtomicFact("Modified", FileUtils.formatDate(node.modifiedAt), monoValue = true))
                if (hashes != null) {
                    add(AtomicFact("MD5", hashes.md5, monoValue = true))
                    add(AtomicFact("SHA-256", hashes.sha256, monoValue = true))
                }
            },
        )
        checksumError?.let { AtomicText(it, AtomicTextRole.BodySecondary, color = Atomic.colors.error) }
        if (hashes != null) ExpectedChecksumField(hashes)
        if (hashes == null) {
            if (isCalculatingChecksums) {
                AtomicLoading("Calculating checksums…")
            } else {
                AtomicButton(
                    "Calculate checksums",
                    onClick = {
                        isCalculatingChecksums = true
                        checksumError = null
                        coroutineScope.launch {
                            when (val res = container.readFileContentUseCase.calculateChecksums(node.id)) {
                                is FileResult.Success -> checksums = res.value
                                is FileResult.Failure -> checksumError = "Checksums couldn't be calculated. Try again."
                            }
                            isCalculatingChecksums = false
                        }
                    },
                    variant = AtomicButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        GenericFileExternalAction(onOpenExternal, openingExternal)
    }
}

@Composable
internal fun GenericFileExternalAction(
    onOpen: (() -> Unit)?,
    busy: Boolean,
) {
    if (onOpen != null) {
        AtomicButton(
            stringResource(R.string.file_open_another_app),
            onClick = onOpen,
            leadingIcon = AtomicIcons.OpenExternally,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("generic_open_external"),
        )
    }
}

/**
 * App details read from an APK without installing it (FR-8.7). Says plainly when the file can't be
 * read as an app. Installing is left to "Open with" so Atomic File Manager needs no install permission.
 */
@Composable
private fun ApkSummary(node: FileNode) {
    val context = LocalContext.current
    val reader = remember { ApkInfoReader(context.applicationContext) }
    // First: finished reading; second: what was read (null when it isn't a readable app).
    val read by produceState<Pair<Boolean, ApkInfo?>>(false to null, node.id) {
        value = true to reader.read(node.id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme))
    }
    val (done, apk) = read
    if (apk == null) {
        AtomicText(
            if (done) "This APK can't be read as an app. It may be damaged or incomplete." else "Reading app details…",
            AtomicTextRole.BodySecondary,
            modifier = Modifier.testTag("apk_reading"),
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        modifier = Modifier.testTag("apk_summary"),
    ) {
        apk.icon?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(AtomicSize.touchTarget),
            )
        }
        Column(Modifier.weight(1f)) {
            AtomicText(apk.label, AtomicTextRole.NameLarge)
            AtomicText(apk.packageName, AtomicTextRole.MonoMeta)
        }
    }
    AtomicFactSheet(apkFacts(apk))
    AtomicText("Use Open with to install it with Android's installer.", AtomicTextRole.BodySecondary)
}

/** Why an archive can't be listed, in plain words. */
internal fun archiveError(error: FileError): String =
    when {
        error is FileError.UnsupportedFormat && error.mimeType == PASSWORD_PROTECTED ->
            "This archive is password-protected. Enter its password to see what's inside."
        error is FileError.UnsupportedFormat && error.mimeType == WRONG_PASSWORD ->
            "That password didn't open the archive. Try again."
        error is FileError.SuspiciousArchive -> "This archive was not opened: ${error.reason}."
        else -> "The archive couldn't be read. It may be damaged or in a format this app can't open."
    }

/** Facts shown for an APK; pure so the wording is unit-tested. */
internal fun apkFacts(apk: ApkInfo): List<AtomicFact> =
    listOfNotNull(
        AtomicFact(
            "Version",
            listOfNotNull(apk.versionName, "(${apk.versionCode})").joinToString(" "),
            monoValue = true,
        ),
        apk.minSdk?.let { AtomicFact("Needs", androidVersionName(it) + " or newer") },
        AtomicFact("Built for", androidVersionName(apk.targetSdk)),
        AtomicFact("Permissions", if (apk.permissionCount == 1) "1 requested" else "${apk.permissionCount} requested"),
    )

/** Paste a checksum from a download page and see whether this file matches (ALL_IN_ONE_PLAN.md 2.8). */
@Composable
private fun ExpectedChecksumField(hashes: FileChecksums) {
    var expected by rememberSaveable { mutableStateOf("") }
    val verdict = checkExpectedChecksum(expected, hashes)
    AtomicTextField(
        value = expected,
        onValueChange = { expected = it },
        label = "Expected checksum",
        placeholder = "Paste an MD5 or SHA-256",
        errorText = (verdict as? ChecksumVerdict.Mismatch)?.let { verdictText(it) },
        modifier = Modifier.fillMaxWidth().testTag("expected_checksum"),
    )
    if (verdict !is ChecksumVerdict.Mismatch && verdict !is ChecksumVerdict.Empty) {
        AtomicText(
            verdictText(verdict),
            AtomicTextRole.Body,
            color = if (verdict is ChecksumVerdict.Match) Atomic.colors.accentText else Atomic.colors.contentSecondary,
            modifier = Modifier.testTag("checksum_verdict"),
        )
    }
}

/** Wording for a [ChecksumVerdict]; pure so it is unit-tested. */
internal fun verdictText(verdict: ChecksumVerdict): String =
    when (verdict) {
        is ChecksumVerdict.Match -> "Matches (${verdict.algorithm}). This is the file that was published."
        is ChecksumVerdict.Mismatch -> "Doesn't match (${verdict.algorithm}). The file differs from the one published."
        is ChecksumVerdict.Unsupported -> "That looks like ${verdict.algorithm}. Paste the MD5 or SHA-256 instead."
        ChecksumVerdict.NotAHash -> "That isn't an MD5 or SHA-256 checksum."
        ChecksumVerdict.Empty -> ""
    }
