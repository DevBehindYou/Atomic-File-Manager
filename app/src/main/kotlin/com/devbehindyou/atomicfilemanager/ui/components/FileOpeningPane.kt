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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
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
import com.devbehindyou.atomicfilemanager.data.preview.SelectedContentMimeResolver
import com.devbehindyou.atomicfilemanager.data.preview.SelectedContentType
import com.devbehindyou.atomicfilemanager.data.preview.externalMimeType
import com.devbehindyou.atomicfilemanager.data.preview.openingMetadataType
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleScope
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import com.devbehindyou.atomicfilemanager.domain.model.FileOpeningPreferences
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
    val policySnapshot = remember(node.id) { settings.fileOpening }
    val initialTarget =
        remember(node.id) {
            openingMetadataType(
                node,
            )?.let { policySnapshot.targetFor(it.node.copy(mimeType = externalMimeType(it.node))) }
        }
    var preferredMode by rememberSaveable(node.id.raw) { mutableStateOf(initialTarget?.mode?.name) }
    var preferredComponent by rememberSaveable(node.id.raw) { mutableStateOf(initialTarget?.component) }
    var preferredLabel by rememberSaveable(node.id.raw) { mutableStateOf(initialTarget?.appLabel) }
    val preferred = preferredMode?.let { FileOpenTarget(FileOpenMode.valueOf(it), preferredComponent, preferredLabel) }
    var sourceSelection by remember(node.id) { mutableStateOf(PreviewSourceSelection(node)) }
    val rawDisplayed = sourceSelection.forParent(node)
    val rawKey = rawDisplayed.previewSourceKey()
    var useBuiltIn by rememberSaveable(node.id.raw) { mutableStateOf(initialTarget?.mode == FileOpenMode.BUILT_IN) }
    var automaticAttempted by rememberSaveable(node.id.raw) { mutableStateOf(false) }
    var dismissed by rememberSaveable(node.id.raw) { mutableStateOf(false) }
    var acknowledgedName by rememberSaveable(node.id.raw) { mutableStateOf<String?>(null) }
    var engineId by rememberSaveable(node.id.raw) { mutableStateOf(node.id.raw.takeIf { useBuiltIn }) }
    var engineMime by rememberSaveable(node.id.raw) { mutableStateOf(externalMimeType(node).takeIf { useBuiltIn }) }
    val launcher = remember(context) { ExternalFileApps(context.applicationContext) }
    val exporter =
        remember(app) {
            ExternalPreviewHelper(context.applicationContext) { app.container.storageBackendSelector.forNode(it) }
        }
    val typeResolver =
        remember(app) {
            SelectedContentMimeResolver { app.container.storageBackendSelector.forNode(it) }
        }
    val scope = rememberCoroutineScope()
    var resolutionGeneration by remember { mutableIntStateOf(0) }
    val typeRequest =
        remember(scope, resolutionGeneration, typeResolver) {
            OpeningTypeResolution(scope) { typeResolver.resolve(it) }
        }
    val typeState by key(typeRequest) { typeRequest.state.collectAsState() }
    val currentSelection by rememberUpdatedState(rawKey to dismissed)
    val currentRequest by rememberUpdatedState(typeRequest)
    DisposableEffect(typeRequest) {
        onDispose { typeRequest.dispose() }
    }
    LaunchedEffect(typeRequest, typeState.disposed, typeState.resolving, dismissed) {
        // A reused slot waits for the dismissed owner's cleanup before creating a new owner.
        if (!dismissed && typeState.disposed && !typeState.resolving) resolutionGeneration++
    }
    val metadataType = openingMetadataType(rawDisplayed)
    val type = typeState.result.takeIf { typeState.key == rawKey && !typeState.resolving } ?: metadataType
    val checkingType = type == null || typeState.resolving
    val displayed = type?.node ?: rawDisplayed
    val displayedKey = displayed.previewSourceKey()
    val displayedMime = externalMimeType(displayed)
    var apps by remember(displayed.id, displayedMime) { mutableStateOf<List<ExternalFileApp>>(emptyList()) }
    var loading by remember(displayed.id, displayedMime) { mutableStateOf(true) }
    var problem by rememberSaveable(displayed.id.raw, displayedKey) { mutableStateOf<OpeningProblem?>(null) }
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
            (settings.fileOpening.rules.size < MAX_FILE_OPENING_RULES || rule in settings.fileOpening.rules)

    fun currentTypeIsReady(): Boolean {
        val current = typeRequest.state.value
        return !currentSelection.second && currentSelection.first == rawKey && currentRequest === typeRequest &&
            !current.disposed && !current.resolving && current.key == rawKey && current.result != null
    }

    fun dismiss() {
        dismissed = true
        typeRequest.dispose()
        preparationRequest.dispose()
        onClose()
    }

    fun choose(
        target: FileOpenTarget,
        always: Boolean,
    ) {
        if (!currentTypeIsReady() || preparationRequest.state.value.busy) return
        automaticAttempted = true
        if (target.mode == FileOpenMode.BUILT_IN) {
            if (always && rule != null) {
                repository.update {
                    it.copy(
                        fileOpening = it.fileOpening.withRule(rule, target),
                    )
                }
            }
            engineId = displayed.id.raw
            engineMime = displayedMime
            useBuiltIn = true
            problem = null
            return
        }
        val requestedSource = displayed
        val requestedMetadata = rawDisplayed
        val requestedRule = rule
        preparationRequest.start { report ->
            problem = null
            val result =
                exporter.open(requestedSource, onProgress = report) { file ->
                    launcher.launch(file, requestedSource.name, target, chooserTitle)
                }
            currentCoroutineContext().ensureActive()
            if (result is FileResult.Success) {
                if (always && requestedRule != null) {
                    repository.update { it.copy(fileOpening = it.fileOpening.withRule(requestedRule, target)) }
                }
                try {
                    withContext(Dispatchers.IO) { app.container.recentsRepository.recordOpened(requestedMetadata) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A shortcut failure cannot turn a launched reader into an open failure.
                }
                currentCoroutineContext().ensureActive()
                acknowledgedName = requestedMetadata.name
                dismiss()
            } else {
                problem = OpeningProblem.FAILED
            }
        }
    }

    val needsChoiceResolution = preferred == null || !useBuiltIn
    LaunchedEffect(rawKey, needsChoiceResolution, typeRequest, dismissed) {
        if (dismissed || typeRequest.state.value.disposed) return@LaunchedEffect
        if (!needsChoiceResolution) {
            val known = openingMetadataType(rawDisplayed)
            if (known != null) {
                typeRequest.select(rawDisplayed) { known }
            } else if (typeRequest.state.value.key?.id != rawDisplayed.id) {
                typeRequest.invalidateSelection()
            }
            return@LaunchedEffect
        }
        typeRequest.select(rawDisplayed) { source ->
            val known = openingMetadataType(source)
            if (known != null) {
                known
            } else {
                val genericChoice =
                    prefersMetadataChoices(source, policySnapshot) &&
                        withContext(Dispatchers.IO) { launcher.list(externalMimeType(source)).isNotEmpty() }
                typeResolver.resolve(source, allowProbe = !genericChoice)
            }
        }
    }

    LaunchedEffect(node.id, rawKey, typeState, preferred, dismissed) {
        if (dismissed || checkingType) return@LaunchedEffect
        if (preferred == null) {
            val target = policySnapshot.targetFor(type.node.copy(mimeType = externalMimeType(type.node)))
            preferredComponent = target.component
            preferredLabel = target.appLabel
            preferredMode = target.mode.name
            useBuiltIn = target.mode == FileOpenMode.BUILT_IN && type !is SelectedContentType.Failure
            if (useBuiltIn) {
                engineId = displayed.id.raw
                engineMime = displayedMime
            }
        }
        if (type is SelectedContentType.Failure) {
            automaticAttempted = true
            useBuiltIn = false
            problem = OpeningProblem.FAILED
        }
    }

    LaunchedEffect(
        rawKey,
        displayed.id,
        displayedMime,
        useBuiltIn,
        checkingType,
        preferred,
        typeState,
        typeRequest,
        dismissed,
    ) {
        if (useBuiltIn || checkingType || preferred == null || !currentTypeIsReady()) return@LaunchedEffect
        loading = true
        try {
            val found = withContext(Dispatchers.IO) { launcher.list(displayedMime) }
            currentCoroutineContext().ensureActive()
            if (!currentTypeIsReady()) return@LaunchedEffect
            apps = found
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

    if (dismissed) {
        // A caller may retain the pane after acknowledgement; keep its immutable name, without live controls.
        acknowledgedName?.let { name ->
            AtomicText(
                name,
                AtomicTextRole.Name,
                modifier.fillMaxSize().background(Atomic.colors.background).padding(AtomicSpacing.s16),
            )
        }
        return
    }
    if (useBuiltIn) {
        // A committed engine hint keeps the mounted gallery/editor stable. It is never authoritative on handoff.
        val engineNode =
            if (metadataType == null && engineId == rawDisplayed.id.raw && engineMime != null) {
                rawDisplayed.copy(mimeType = engineMime)
            } else {
                rawDisplayed
            }
        BuiltInFilePreviewPane(
            node = engineNode,
            authoritativeMetadata = rawDisplayed,
            onClose = ::dismiss,
            modifier = modifier,
            onShare = onShare.takeIf { displayed.id == node.id },
            onOpenExternal = onOpenExternal.takeIf { displayed.id == node.id },
            gallery = gallery,
            onOpenExternalForNode = { shown ->
                preparationRequest.dispose()
                // Strip only our exact presentation hint, preserving a different image or editor result.
                val requested = if (shown.previewSourceKey() == engineNode.previewSourceKey()) rawDisplayed else shown
                sourceSelection = PreviewSourceSelection(node, requested)
                automaticAttempted = true
                useBuiltIn = false
            },
        )
    } else if (checkingType || preferred == null) {
        FileOpeningTypeCheck(rawDisplayed.name, ::dismiss, modifier)
    } else {
        FileOpeningChoicePanel(
            name = displayed.name,
            apps = apps,
            loading = loading,
            busy = preparation.busy,
            problem = problem,
            builtInAvailable = resolvePreviewType(displayed) != PreviewType.GENERIC,
            ruleLabel =
                rule?.takeIf {
                    canSaveRule
                }?.let { if (it.scope == FileOpenRuleScope.EXTENSION) ".${it.value}" else it.value },
            preparation = preparation,
            onCancelPreparation = preparationRequest::cancel,
            onChoose = ::choose,
            onClose = ::dismiss,
            onIdentifyType =
                if (type is SelectedContentType.Unresolved && type.canIdentify) {
                    {
                        if (currentTypeIsReady() && !preparationRequest.state.value.busy) {
                            automaticAttempted = true
                            problem = null
                            typeRequest.select(rawDisplayed, refresh = true) { typeResolver.resolve(it) }
                        }
                    }
                } else {
                    null
                },
            modifier = modifier,
        )
    }
}

/** ASK can use existing generic readers, except when a typed rule still needs disambiguation. */
private fun prefersMetadataChoices(
    node: FileNode,
    preferences: FileOpeningPreferences,
): Boolean {
    val extension = keyFor(node, FileOpenRuleScope.EXTENSION)?.let(preferences.rules::get)
    return if (extension != null) {
        extension.mode == FileOpenMode.ASK
    } else {
        preferences.defaultMode == FileOpenMode.ASK &&
            preferences.rules.keys.none { it.scope == FileOpenRuleScope.MIME || it.scope == FileOpenRuleScope.FAMILY }
    }
}

@Composable
private fun FileOpeningTypeCheck(
    name: String,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize().background(Atomic.colors.background).padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AtomicText(name, AtomicTextRole.Name, modifier = Modifier.weight(1f))
            AtomicIconButton(AtomicIcons.Close, stringResource(R.string.action_close), onClick = onClose)
        }
        AtomicLoading(stringResource(R.string.selected_type_checking))
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
    onIdentifyType: (() -> Unit)? = null,
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
        if (onIdentifyType != null) {
            AtomicButton(
                stringResource(R.string.selected_type_identify),
                onClick = onIdentifyType,
                variant = AtomicButtonVariant.Ghost,
                enabled = !busy && !loading,
                leadingIcon = AtomicIcons.Search,
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
