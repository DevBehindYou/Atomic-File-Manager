package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicToggleCard
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicCommandGrid
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuAction
import com.devbehindyou.atomicfilemanager.data.preview.ExternalFileApp
import com.devbehindyou.atomicfilemanager.data.preview.ExternalFileApps
import com.devbehindyou.atomicfilemanager.data.preview.ExternalPreviewHelper
import com.devbehindyou.atomicfilemanager.data.preview.externalMimeType
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleScope
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.MAX_FILE_OPENING_RULES
import com.devbehindyou.atomicfilemanager.domain.model.keyFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** All existing preview entry points share the same app-specific routing policy. */
@Composable
fun FilePreviewPane(
    node: FileNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
    gallery: List<FileNode> = emptyList(),
) {
    val context = LocalContext.current
    val app = context.applicationContext as? AtomicApp ?: return
    val repository = app.container.settingsRepository
    val settings by repository.settings.collectAsState()
    val preferred = remember(node.id) { settings.fileOpening.targetFor(node) }
    var sourceSelection by remember(node.id) { mutableStateOf(PreviewSourceSelection(node)) }
    val displayed = sourceSelection.forParent(node)
    val displayedKey = displayed.previewSourceKey()
    val displayedMime = externalMimeType(displayed)
    var useBuiltIn by rememberSaveable(node.id.raw) { mutableStateOf(preferred.mode == FileOpenMode.BUILT_IN) }
    var automaticAttempted by rememberSaveable(node.id.raw) { mutableStateOf(false) }
    var apps by remember(displayed.id, displayedMime) { mutableStateOf<List<ExternalFileApp>>(emptyList()) }
    var loading by remember(displayed.id, displayedMime) { mutableStateOf(true) }
    var problem by rememberSaveable(displayed.id.raw, displayedKey) { mutableStateOf<OpeningProblem?>(null) }
    val launcher = remember(context) { ExternalFileApps(context.applicationContext) }
    val exporter =
        remember(app) {
            ExternalPreviewHelper(context.applicationContext) { app.container.storageBackendSelector.forNode(it) }
        }
    val scope = rememberCoroutineScope()
    val preparationRequest =
        remember(node.id, displayedKey, useBuiltIn, scope) {
            ExternalPreparationRequest(scope)
        }
    val preparation by key(preparationRequest) { preparationRequest.state.collectAsState() }
    DisposableEffect(preparationRequest) {
        onDispose { preparationRequest.dispose() }
    }
    val chooserTitle = stringResource(R.string.viewer_open_external)
    val rule =
        remember(displayed) {
            keyFor(displayed, FileOpenRuleScope.EXTENSION) ?: keyFor(displayed, FileOpenRuleScope.MIME)
                ?: keyFor(displayed, FileOpenRuleScope.FAMILY)
        }
    val canSaveRule =
        rule != null &&
            (
                settings.fileOpening.rules.size < MAX_FILE_OPENING_RULES ||
                    rule in settings.fileOpening.rules
            )

    fun choose(
        target: FileOpenTarget,
        always: Boolean,
    ) {
        if (preparationRequest.state.value.busy) return
        if (target.mode == FileOpenMode.BUILT_IN) {
            if (always && rule != null) {
                repository.update { it.copy(fileOpening = it.fileOpening.withRule(rule, target)) }
            }
            useBuiltIn = true
            problem = null
            return
        }
        val requestedSource = displayed
        val requestedRule = rule
        preparationRequest.start { report ->
            problem = null
            val result =
                exporter.open(requestedSource, onProgress = report) { file ->
                    launcher.launch(file, requestedSource.name, target, chooserTitle)
                }
            currentCoroutineContext().ensureActive()
            if (result is FileResult.Success) {
                // Save an app-specific rule only after a real explicit component launches successfully.
                if (always && requestedRule != null) {
                    repository.update { it.copy(fileOpening = it.fileOpening.withRule(requestedRule, target)) }
                }
                try {
                    withContext(Dispatchers.IO) { app.container.recentsRepository.recordOpened(requestedSource) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A shortcut failure must not turn a successfully launched reader into an open failure.
                }
                currentCoroutineContext().ensureActive()
                onClose()
            } else {
                problem = OpeningProblem.FAILED
            }
        }
    }

    LaunchedEffect(displayed.id, displayedMime, useBuiltIn) {
        if (useBuiltIn) return@LaunchedEffect
        loading = true
        try {
            apps = withContext(Dispatchers.IO) { launcher.list(displayedMime) }
            if (!automaticAttempted && preferred.mode == FileOpenMode.EXTERNAL) {
                automaticAttempted = true
                when {
                    preferred.component != null && apps.none { it.component == preferred.component } ->
                        problem = OpeningProblem.MISSING_APP
                    apps.isEmpty() -> problem = OpeningProblem.NO_HANDLER
                    else -> choose(preferred, false)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            problem = OpeningProblem.FAILED
        } finally {
            loading = false
        }
    }

    if (useBuiltIn) {
        BuiltInFilePreviewPane(
            node = displayed,
            onClose = {
                preparationRequest.dispose()
                onClose()
            },
            modifier = modifier,
            onShare = onShare.takeIf { displayed.id == node.id },
            onOpenExternal = onOpenExternal.takeIf { displayed.id == node.id },
            gallery = gallery,
            onOpenExternalForNode = { shown ->
                preparationRequest.dispose()
                sourceSelection = PreviewSourceSelection(node, shown)
                useBuiltIn = false
            },
        )
    } else {
        FileOpeningChoicePanel(
            name = displayed.name,
            apps = apps,
            loading = loading,
            busy = preparation.busy,
            problem = problem,
            builtInAvailable = resolvePreviewType(displayed) != PreviewType.GENERIC,
            ruleLabel =
                rule?.takeIf { canSaveRule }?.let {
                    if (it.scope == FileOpenRuleScope.EXTENSION) ".${it.value}" else it.value
                },
            preparation = preparation,
            onCancelPreparation = preparationRequest::cancel,
            onChoose = ::choose,
            onClose = {
                preparationRequest.dispose()
                onClose()
            },
            modifier = modifier,
        )
    }
}

internal enum class OpeningProblem { MISSING_APP, NO_HANDLER, FAILED }

@Composable
internal fun FileOpeningChoicePanel(
    name: String,
    apps: List<ExternalFileApp>,
    loading: Boolean,
    busy: Boolean,
    problem: OpeningProblem?,
    builtInAvailable: Boolean,
    ruleLabel: String?,
    onChoose: (FileOpenTarget, Boolean) -> Unit,
    onClose: () -> Unit,
    preparation: ExternalPreparationState? = null,
    onCancelPreparation: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var always by rememberSaveable(name) { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().background(Atomic.colors.background)
            .verticalScroll(rememberScrollState()).padding(AtomicSpacing.s16).testTag("file_opening_choices"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AtomicText(name, AtomicTextRole.Name, modifier = Modifier.weight(1f))
            AtomicIconButton(AtomicIcons.Close, stringResource(R.string.action_close), onClick = onClose)
        }
        AtomicText(stringResource(R.string.file_open_title), AtomicTextRole.MonoLabel)
        val message =
            when {
                busy -> R.string.viewer_external_loading
                loading -> R.string.file_open_loading_apps
                problem == OpeningProblem.MISSING_APP -> R.string.file_open_missing_app
                problem == OpeningProblem.FAILED -> R.string.viewer_external_failed
                problem == OpeningProblem.NO_HANDLER || apps.isEmpty() -> R.string.file_open_no_apps
                else -> R.string.file_open_read_only
            }
        if (busy && preparation != null) {
            ExternalPreparationStatus(preparation, onCancelPreparation)
        } else {
            AtomicText(stringResource(message), AtomicTextRole.BodySecondary)
        }
        if (ruleLabel != null) {
            AtomicToggleCard(
                title = stringResource(R.string.file_open_always, ruleLabel),
                description = stringResource(R.string.file_open_app_only),
                checked = always,
                onCheckedChange = { always = it },
                enabled = !busy && !loading,
            )
        }
        val builtIn =
            AtomicMenuAction(
                stringResource(if (builtInAvailable) R.string.file_open_built_in else R.string.file_open_details),
                AtomicIcons.Document,
                { onChoose(FileOpenTarget(FileOpenMode.BUILT_IN), always) },
                enabled = !busy,
            )
        AtomicCommandGrid(
            listOf(builtIn) +
                apps.map { handler ->
                    AtomicMenuAction(
                        handler.label, AtomicIcons.OpenExternally,
                        { onChoose(handler.target(), always) }, enabled = !busy,
                    )
                },
        )
    }
}
