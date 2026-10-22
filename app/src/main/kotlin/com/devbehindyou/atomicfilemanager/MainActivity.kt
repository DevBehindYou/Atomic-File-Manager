package com.devbehindyou.atomicfilemanager

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicHomeHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicBottomBar
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicDestination
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicInfoSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicNavRail
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSnackbarHost
import com.devbehindyou.atomicfilemanager.core.navigation.AtomicRoute
import com.devbehindyou.atomicfilemanager.core.navigation.RouteStack
import com.devbehindyou.atomicfilemanager.data.operations.PendingConflict
import com.devbehindyou.atomicfilemanager.data.volume.SharedStorageAccess
import com.devbehindyou.atomicfilemanager.data.volume.StorageVolumes
import com.devbehindyou.atomicfilemanager.domain.model.FileCategory
import com.devbehindyou.atomicfilemanager.domain.model.FileCollection
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.HideMode
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationSummary
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import com.devbehindyou.atomicfilemanager.domain.repository.SettingsRepository
import com.devbehindyou.atomicfilemanager.domain.usecase.FolderComparer
import com.devbehindyou.atomicfilemanager.ui.components.ConflictSheet
import com.devbehindyou.atomicfilemanager.ui.components.RecoverySheet
import com.devbehindyou.atomicfilemanager.ui.components.TasksButton
import com.devbehindyou.atomicfilemanager.ui.screens.AppManagerScreen
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseScreen
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseStorageAccess
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseTabStrip
import com.devbehindyou.atomicfilemanager.ui.screens.BrowseTabsState
import com.devbehindyou.atomicfilemanager.ui.screens.CategoryScreen
import com.devbehindyou.atomicfilemanager.ui.screens.CommandPaletteSheet
import com.devbehindyou.atomicfilemanager.ui.screens.HiddenFilesScreen
import com.devbehindyou.atomicfilemanager.ui.screens.HomeScreen
import com.devbehindyou.atomicfilemanager.ui.screens.OperationsScreen
import com.devbehindyou.atomicfilemanager.ui.screens.PaletteCommand
import com.devbehindyou.atomicfilemanager.ui.screens.SearchScreen
import com.devbehindyou.atomicfilemanager.ui.screens.SettingsScreen
import com.devbehindyou.atomicfilemanager.ui.screens.StorageIntelligenceScreen
import com.devbehindyou.atomicfilemanager.ui.screens.StorageScreen
import com.devbehindyou.atomicfilemanager.ui.screens.StoredFolderCompareScreen
import com.devbehindyou.atomicfilemanager.ui.screens.TrashScreen
import com.devbehindyou.atomicfilemanager.ui.screens.TrashText
import com.devbehindyou.atomicfilemanager.ui.screens.WifiShareScreen
import com.devbehindyou.atomicfilemanager.ui.security.AuthGate
import com.devbehindyou.atomicfilemanager.ui.shortcuts.AppShortcuts
import com.devbehindyou.atomicfilemanager.ui.shortcuts.OpenTarget
import com.devbehindyou.atomicfilemanager.ui.startup.SettingsStartupLoader
import com.devbehindyou.atomicfilemanager.ui.startup.SettingsStartupScreen
import com.devbehindyou.atomicfilemanager.ui.startup.SettingsStartupState
import com.devbehindyou.atomicfilemanager.ui.startup.StorageReadinessContent
import com.devbehindyou.atomicfilemanager.ui.startup.StorageReadinessLoader
import com.devbehindyou.atomicfilemanager.ui.startup.StorageReadinessState
import com.devbehindyou.atomicfilemanager.ui.startup.StorageSnapshot
import com.devbehindyou.atomicfilemanager.ui.util.DebugJank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class NavigationTab(val title: String) {
    HOME("Home"),
    BROWSE("Files"),
    STORAGE("Storage"),
    SETTINGS("Settings"),
}

// FragmentActivity (a ComponentActivity) because BiometricPrompt needs one.
class MainActivity : FragmentActivity() {
    /** Screens asked for from outside (a notification tap); consumed by [AtomicAppContent]. */
    private val externalOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // After recreation the route stack is already restored; don't push the same screen again.
        externalOpen.value =
            if (savedInstanceState == null) {
                intent?.getStringExtra(EXTRA_OPEN)
            } else {
                savedInstanceState.getString(STATE_PENDING_OPEN)
            }
        AppShortcuts.publish(this)

        enableEdgeToEdge()

        val app = application as AtomicApp
        val startup = SettingsStartupLoader(lifecycleScope) { app.container.settingsRepository }
        // Loading and failure UI must draw without waiting for a later Compose frame.
        startup.start()
        setContent {
            val startupState by startup.state.collectAsState()
            val ready = startupState as? SettingsStartupState.Ready
            if (ready == null) {
                SettingsStartupScreen(
                    loading = startupState is SettingsStartupState.Loading,
                    onRetry = startup::start,
                    onClose = ::finish,
                )
                return@setContent
            }
            val settings by ready.repository.settings.collectAsState()
            val darkTheme =
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            // Keep status and navigation bar icons readable when the chosen theme differs from the system's.
            DisposableEffect(darkTheme) {
                val lightNavScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
                val darkNavScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(lightNavScrim, darkNavScrim) { darkTheme },
                )
                onDispose {}
            }
            AtomicTheme(darkTheme = darkTheme, wallpaperAccent = settings.dynamicColor) {
                val openRequest by externalOpen.collectAsState()
                AtomicAppContent(
                    openRequest = openRequest,
                    onOpenRequestHandled = { handledRequest -> externalOpen.compareAndSet(handledRequest, null) },
                )
            }
        }
        DebugJank.track(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PENDING_OPEN, externalOpen.value)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { externalOpen.value = it }
    }

    /** Ctrl+K opens the command palette (FR-10.10) on tablets and Chromebooks with a keyboard. */
    override fun onKeyShortcut(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_K && event.isCtrlPressed) {
            externalOpen.value = OPEN_PALETTE
            return true
        }
        return super.onKeyShortcut(keyCode, event)
    }

    companion object {
        private const val STATE_PENDING_OPEN = "atomic.pending_open"
        const val EXTRA_OPEN = "com.devbehindyou.atomicfilemanager.extra.OPEN"
        const val OPEN_OPERATIONS = OpenTarget.OPERATIONS
        const val OPEN_PALETTE = OpenTarget.PALETTE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtomicAppContent(
    openRequest: String? = null,
    onOpenRequestHandled: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as AtomicApp
    val phoneIndex = app.container.phoneFileIndex
    val settingsRepository = app.container.settingsRepository
    val settings by settingsRepository.settings.collectAsState()
    var showMoreCategories by rememberSaveable { mutableStateOf(false) }

    // Screens pushed over the tabs (private files, categories, analysis), saved as strings.
    var routeEntries by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val routes = RouteStack.decode(routeEntries)

    // Read the saved entries, not [routes], so two changes in one callback both apply.
    fun push(route: AtomicRoute) {
        routeEntries = RouteStack.decode(routeEntries).push(route).encode()
    }

    fun pop() {
        routeEntries = RouteStack.decode(routeEntries).pop().encode()
    }
    var currentTab by rememberSaveable { mutableStateOf(NavigationTab.HOME) }
    val defaultPath = Environment.getExternalStorageDirectory()?.absolutePath ?: context.filesDir.absolutePath
    val defaultFolderRaw = FileNodeId.file(defaultPath).raw
    // Browse tabs (ALL_IN_ONE_PLAN.md 2.4), saved as one string so they survive rotation and process death.
    // Each tab counts its explicit opens from Home, Storage or analysis: Browse view models are cached per
    // start folder, so without that a second open of Downloads showed wherever the user had navigated to
    // last time. Tab switches and rotation leave it unchanged and keep the position.
    var browseTabsSaved by rememberSaveable { mutableStateOf(BrowseTabsState.single(defaultFolderRaw).encode()) }
    val browseTabs = remember(browseTabsSaved) { BrowseTabsState.decode(browseTabsSaved, defaultFolderRaw) }
    val updateBrowseTabs: ((BrowseTabsState) -> BrowseTabsState) -> Unit = { change ->
        browseTabsSaved = change(BrowseTabsState.decode(browseTabsSaved, defaultFolderRaw)).encode()
    }
    var tabHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun navigateTab(tab: NavigationTab) {
        if (tab != currentTab) {
            // Keep one entry per tab so repeated tab switching cannot grow the Back stack without bound.
            tabHistory = tabHistory.filterNot { it == tab.name || it == currentTab.name } + currentTab.name
            currentTab = tab
        }
    }

    fun openInBrowse(folderId: FileNodeId) {
        updateBrowseTabs { it.openInActive(folderId.raw) }
        navigateTab(NavigationTab.BROWSE)
    }

    fun navigateBack() {
        currentTab = tabHistory.lastOrNull()?.let(NavigationTab::valueOf) ?: NavigationTab.HOME
        tabHistory = tabHistory.dropLast(1)
    }
    var pendingVolumeId by rememberSaveable { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val storageLoader =
        remember(context, scope) {
            StorageReadinessLoader(scope) {
                val access = SharedStorageAccess.isGranted(context)
                StorageSnapshot(access, loadVolumes(context, access))
            }
        }
    val storageReadiness by storageLoader.state.collectAsState()
    val volumes = storageReadiness.snapshot?.volumes.orEmpty()
    val hasStorageAccess = storageReadiness.snapshot?.hasAccess ?: false
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPalette by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Access and volume metadata publish together after the IO read; unresolved is not denied.
    fun refreshVolumes() = storageLoader.refresh()

    LaunchedEffect(volumes) {
        val pending = volumes.firstOrNull { it.id == pendingVolumeId }
        if (pending?.rootNodeId != null && pending.isMounted) {
            openInBrowse(pending.rootNodeId)
            pendingVolumeId = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshVolumes()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // A USB drive or SD card plugged in or pulled out shows up at once, not on the next resume
    // (ALL_IN_ONE_PLAN.md 3.5).
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = refreshVolumes()
            }
        val filter =
            IntentFilter().apply {
                MEDIA_EVENTS.forEach(::addAction)
                addDataScheme("file")
            }
        // Exported, because only the system may send these protected broadcasts. Not-exported
        // would route through a signature permission on Android 12 and older that the system's
        // media broadcasts don't carry.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            refreshVolumes()
        }

    fun requestStorageAccess() {
        SharedStorageAccess.request(context) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    fun openVolume(volume: StorageVolumeInfo) {
        when {
            !volume.isMounted -> notify("This storage isn't mounted.")
            volume.rootNodeId != null -> {
                openInBrowse(volume.rootNodeId)
            }
            storageReadiness !is StorageReadinessState.Ready -> refreshVolumes()
            !hasStorageAccess -> {
                pendingVolumeId = volume.id
                requestStorageAccess()
            }
            else -> notify("This storage is unavailable. Reconnect it and try again.")
        }
    }

    BackHandler(enabled = tabHistory.isNotEmpty() && routes.isEmpty) {
        navigateBack()
    }

    LaunchedEffect(Unit) {
        refreshVolumes()
    }

    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 600

    fun openCategory(category: FileCategory) {
        if (category == FileCategory.OTHER) {
            showMoreCategories = true
        } else if (category == FileCategory.DOWNLOAD) {
            openInBrowse(
                FileNodeId.file(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                ),
            )
        } else {
            val collection =
                when (category) {
                    FileCategory.IMAGE -> FileCollection.IMAGES
                    FileCategory.VIDEO -> FileCollection.VIDEOS
                    FileCategory.AUDIO -> FileCollection.AUDIO
                    FileCategory.DOCUMENT -> FileCollection.DOCUMENTS
                    FileCategory.ARCHIVE -> FileCollection.ARCHIVES
                    FileCategory.APK -> FileCollection.APKS
                    else -> FileCollection.OTHER
                }
            push(AtomicRoute.Category(collection.name))
        }
    }

    // A name clash in a running operation can come up on any screen, so the sheet lives here.
    val operationQueue = app.container.operationQueue
    val pendingConflict by operationQueue.pendingConflict.collectAsState()
    var hiddenConflict by remember { mutableStateOf<PendingConflict?>(null) }
    pendingConflict?.takeIf { it !== hiddenConflict }?.let { pending ->
        ConflictSheet(
            pending = pending,
            onDecide = { operationQueue.resolveConflict(pending, it) },
            onCancelOperation = { operationQueue.cancel(pending.operation.id) },
            onDismiss = { hiddenConflict = pending },
        )
    }

    // Work the previous run left unfinished: offered once, right after start-up.
    val recovery = app.container.operationRecovery
    val recovered by recovery.pending.collectAsState()
    recovered?.let {
        RecoverySheet(
            recovered = it,
            onResume = recovery::resumeAll,
            onDiscard = recovery::discardAll,
            onLater = recovery::later,
        )
    }

    // Android 13+ hides the progress notification without this permission. Ask when the user
    // starts their first operation (it runs either way), at most once per app session.
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val operationRunning by remember(operationQueue) {
        operationQueue.active.map { it != null }.distinctUntilChanged()
    }.collectAsState(initial = false)
    LaunchedEffect(operationRunning) {
        if (operationRunning &&
            !askedForNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // After a move to Trash: say what happened and offer Undo for 10 s (FR-4.14). Restores report too.
    LaunchedEffect(operationQueue) {
        operationQueue.finished.collect { snapshot ->
            val summary = snapshot.status.summaryOrNull() ?: return@collect
            val done = summary.succeeded.size
            val failed = summary.failed.size
            when (snapshot.operation.type) {
                OperationType.TRASH ->
                    if (done > 0) {
                        scope.launch {
                            val result =
                                snackbarHostState.showSnackbar(
                                    TrashText.moved(done, failed),
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Long,
                                )
                            if (result == SnackbarResult.ActionPerformed) {
                                val entries = app.container.trashStore.byOperation(snapshot.operation.id.raw)
                                if (entries.isNotEmpty()) {
                                    operationQueue.enqueue(
                                        FileOperation(
                                            id = OperationId.random(),
                                            type = OperationType.RESTORE_FROM_TRASH,
                                            sources = entries.map { it.trashedId },
                                            destination = null,
                                            options = OperationOptions(),
                                            createdAt = System.currentTimeMillis(),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                OperationType.RESTORE_FROM_TRASH -> notify(TrashText.restored(done, failed))
                else -> Unit
            }
        }
    }

    fun runCommand(command: PaletteCommand) {
        showPalette = false
        // A command starts from the tabs, so Back from what it opens returns there.
        routeEntries = emptyList()
        when (command) {
            PaletteCommand.SEARCH -> push(AtomicRoute.Search)
            PaletteCommand.FILES -> navigateTab(NavigationTab.BROWSE)
            PaletteCommand.DOWNLOADS -> openCategory(FileCategory.DOWNLOAD)
            PaletteCommand.IMAGES -> openCategory(FileCategory.IMAGE)
            PaletteCommand.VIDEOS -> openCategory(FileCategory.VIDEO)
            PaletteCommand.AUDIO -> openCategory(FileCategory.AUDIO)
            PaletteCommand.DOCUMENTS -> openCategory(FileCategory.DOCUMENT)
            PaletteCommand.ARCHIVES -> openCategory(FileCategory.ARCHIVE)
            PaletteCommand.APKS -> openCategory(FileCategory.APK)
            PaletteCommand.STORAGE -> navigateTab(NavigationTab.STORAGE)
            PaletteCommand.ANALYSIS -> push(AtomicRoute.Analysis(defaultFolderRaw))
            PaletteCommand.TRASH -> push(AtomicRoute.Trash)
            PaletteCommand.OPERATIONS -> push(AtomicRoute.Operations)
            PaletteCommand.APPS -> push(AtomicRoute.Apps)
            PaletteCommand.PRIVATE -> push(AtomicRoute.PrivateFiles)
            PaletteCommand.WIFI_SHARE -> push(AtomicRoute.WifiShare)
            PaletteCommand.SETTINGS, PaletteCommand.THEME -> navigateTab(NavigationTab.SETTINGS)
            PaletteCommand.ABOUT -> showAboutDialog = true
        }
    }

    // Opens asked for from outside: the operation notification, a launcher shortcut, a pinned
    // folder, the storage widget or Ctrl+K.
    LaunchedEffect(openRequest) {
        when (val target = OpenTarget.parse(openRequest)) {
            OpenTarget.Operations -> if (routes.top != AtomicRoute.Operations) push(AtomicRoute.Operations)
            OpenTarget.Palette -> showPalette = true
            is OpenTarget.Command -> runCommand(target.command)
            is OpenTarget.Folder -> {
                routeEntries = emptyList()
                openInBrowse(FileNodeId(target.raw))
            }
            null -> Unit
        }
        if (openRequest != null) onOpenRequestHandled(openRequest)
    }
    if (showPalette) {
        CommandPaletteSheet(onRun = ::runCommand, onDismiss = { showPalette = false })
    }

    BackHandler(enabled = !routes.isEmpty) { pop() }
    when (val route = routes.top) {
        AtomicRoute.PrivateFiles -> {
            AuthGate(
                required = settings.requireAuthForHidden,
                title = "Unlock private files",
                onDenied = ::pop,
            ) {
                HiddenFilesScreen(
                    repository = app.container.hiddenFilesRepository,
                    onNavigateBack = ::pop,
                    initialMode = HideMode.PRIVATE_STORAGE,
                    privateOnly = true,
                )
            }
            return
        }
        AtomicRoute.Search -> {
            SearchScreen(
                index = app.container.searchIndex,
                roots = volumes.filter { it.isMounted }.mapNotNull { it.rootNodeId },
                getNode = app.container.getNodeUseCase,
                onBack = ::pop,
                onOpenFolder = { folder ->
                    pop()
                    openInBrowse(folder)
                },
            )
            return
        }
        AtomicRoute.Apps -> {
            AppManagerScreen(onBack = ::pop, onNotify = ::notify)
            return
        }
        is AtomicRoute.FolderCompare -> {
            val comparer = remember { FolderComparer { app.container.storageBackendSelector.forNode(it) } }
            StoredFolderCompareScreen(
                left = FileNodeId(route.leftRaw),
                right = FileNodeId(route.rightRaw),
                comparer = comparer,
                onRun = { operations ->
                    operations.forEach { operationQueue.enqueue(it) }
                    pop()
                    notify("Sync started. Follow it in Operations.")
                },
                onBack = ::pop,
            )
            return
        }
        AtomicRoute.WifiShare -> {
            WifiShareScreen(controller = app.container.wifiShare, onBack = ::pop)
            return
        }
        AtomicRoute.Trash -> {
            TrashScreen(
                store = app.container.trashStore,
                trashManager = app.container.trashManager,
                queue = operationQueue,
                retentionDays = settings.trashRetentionDays,
                onBack = ::pop,
                onNotify = ::notify,
            )
            return
        }
        AtomicRoute.Operations -> {
            OperationsScreen(
                queue = operationQueue,
                journal = app.container.operationJournal,
                onBack = ::pop,
                onShowConflict = { hiddenConflict = null },
            )
            return
        }
        is AtomicRoute.Category -> {
            val collection = FileCollection.entries.firstOrNull { it.name == route.collection }
            if (collection != null) {
                CategoryScreen(
                    collection = collection,
                    roots = volumes.filter { it.isMounted }.mapNotNull { it.rootNodeId },
                    index = phoneIndex,
                    onBack = ::pop,
                    onGrantAccess = { requestStorageAccess() },
                )
                return
            }
            // A collection removed in an update: drop the stale entry instead of crashing.
            LaunchedEffect(route) { pop() }
        }
        is AtomicRoute.Analysis -> {
            StorageIntelligenceScreen(
                rootId = FileNodeId(route.rootRaw),
                onNavigateBack = ::pop,
                onOpenFile = { file ->
                    pop()
                    openInBrowse(file.id)
                },
            )
            return
        }
        null -> Unit
    }
    if (showMoreCategories) {
        AtomicMenuSheet(
            label = "More categories",
            onDismiss = { showMoreCategories = false },
            actions =
                listOf(
                    FileCollection.PDFS,
                    FileCollection.TEXT,
                    FileCollection.EBOOKS,
                    FileCollection.FONTS,
                    FileCollection.OTHER,
                ).map { collection ->
                    AtomicMenuAction(
                        label = collection.title,
                        icon =
                            when (collection) {
                                FileCollection.PDFS -> AtomicIcons.Pdf
                                FileCollection.TEXT -> AtomicIcons.Code
                                FileCollection.EBOOKS -> AtomicIcons.Ebook
                                FileCollection.FONTS -> AtomicIcons.Font
                                else -> AtomicIcons.GenericFile
                            },
                        onClick = {
                            showMoreCategories = false
                            push(AtomicRoute.Category(collection.name))
                        },
                    )
                },
        )
    }

    val destinations =
        remember {
            listOf(
                AtomicDestination(NavigationTab.HOME.name, NavigationTab.HOME.title, AtomicIcons.Home, "tab_home"),
                AtomicDestination(
                    NavigationTab.BROWSE.name,
                    NavigationTab.BROWSE.title,
                    AtomicIcons.Files,
                    "tab_browse",
                ),
                AtomicDestination(
                    NavigationTab.STORAGE.name,
                    NavigationTab.STORAGE.title,
                    AtomicIcons.Storage,
                    "tab_storage",
                ),
                AtomicDestination(
                    NavigationTab.SETTINGS.name,
                    NavigationTab.SETTINGS.title,
                    AtomicIcons.Settings,
                    "tab_settings",
                ),
            )
        }
    val selectTab: (AtomicDestination) -> Unit = {
        pendingVolumeId = null
        navigateTab(NavigationTab.valueOf(it.key))
    }
    val screen: @Composable () -> Unit = {
        MainScreenContent(
            currentTab = currentTab,
            volumes = volumes,
            hasStorageAccess = hasStorageAccess,
            storageReadiness = storageReadiness,
            onRetryStorage = ::refreshVolumes,
            browseTabs = browseTabs,
            onBrowseTabsChange = updateBrowseTabs,
            settingsRepository = settingsRepository,
            privateLocked = settings.requireAuthForHidden,
            callbacks =
                ShellCallbacks(
                    onFolderSelected = ::openInBrowse,
                    onNavigateBack = ::navigateBack,
                    onRequestStorageAccess = {
                        pendingVolumeId = null
                        requestStorageAccess()
                    },
                    onBrowseVolume = ::openVolume,
                    onCategorySelected = ::openCategory,
                    onOpenPrivateFiles = { push(AtomicRoute.PrivateFiles) },
                    onOpenStorageIntelligence = { push(AtomicRoute.Analysis(it.raw)) },
                    onClearScanCache = phoneIndex::invalidate,
                    onNotify = ::notify,
                    onOpenStorageDetails = { navigateTab(NavigationTab.STORAGE) },
                    onOpenRoute = ::push,
                    onCompareFolders = { left, right -> push(AtomicRoute.FolderCompare(left.raw, right.raw)) },
                ),
        )
    }

    Scaffold(
        containerColor = Atomic.colors.background,
        snackbarHost = { AtomicSnackbarHost(snackbarHostState) },
        topBar = {
            if (currentTab != NavigationTab.BROWSE) {
                val home = currentTab == NavigationTab.HOME
                AtomicHomeHeader(
                    eyebrow = if (home) "Atomic" else "Atomic File Manager",
                    title = if (home) "File manager" else currentTab.title,
                    modifier = Modifier.background(Atomic.colors.background).statusBarsPadding(),
                    actions = {
                        if (home) {
                            AtomicIconButton(
                                icon = AtomicIcons.MoreHorizontal,
                                contentDescription = "Commands",
                                onClick = { showPalette = true },
                                modifier = Modifier.testTag("home_palette_button"),
                            )
                            AtomicIconButton(
                                icon = AtomicIcons.Search,
                                contentDescription = "Search all storage",
                                onClick = { push(AtomicRoute.Search) },
                                modifier = Modifier.testTag("home_search_button"),
                            )
                        }
                        TasksButton(
                            queue = operationQueue,
                            transfers = app.container.wifiShare.transfers,
                            onOpenOperations = { push(AtomicRoute.Operations) },
                        )
                    },
                )
            }
        },
        bottomBar = {
            if (!isExpanded) {
                AtomicBottomBar(
                    destinations = destinations,
                    selectedKey = currentTab.name,
                    onSelect = selectTab,
                    modifier = Modifier.testTag("bottom_nav_bar").navigationBarsPadding(),
                )
            }
        },
    ) { innerPadding ->
        if (isExpanded) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
            ) {
                AtomicNavRail(
                    destinations = destinations,
                    selectedKey = currentTab.name,
                    onSelect = selectTab,
                    modifier = Modifier.testTag("nav_rail"),
                )
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.background,
                ) { screen() }
            }
        } else {
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                color = MaterialTheme.colorScheme.background,
            ) { screen() }
        }
    }

    if (showAboutDialog) {
        AtomicInfoSheet(
            label = "About",
            headline = "Atomic File Manager",
            body =
                "An offline-first, private file manager in the DevBehindYou Atomic family. No account, no ads, " +
                    "no tracking. Nothing leaves your phone unless you connect a server yourself.",
            onDismiss = { showAboutDialog = false },
        )
    }
}

/** Navigation and feedback callbacks the tab screens share. */
private class ShellCallbacks(
    val onFolderSelected: (FileNodeId) -> Unit,
    val onNavigateBack: () -> Unit,
    val onRequestStorageAccess: () -> Unit,
    val onBrowseVolume: (StorageVolumeInfo) -> Unit,
    val onCategorySelected: (FileCategory) -> Unit,
    val onOpenPrivateFiles: () -> Unit,
    val onOpenStorageIntelligence: (FileNodeId) -> Unit,
    val onClearScanCache: () -> Unit,
    val onNotify: (String) -> Unit,
    val onOpenStorageDetails: () -> Unit,
    /** Opens a pushed screen: Operations, Trash, Apps, Wi-Fi share. */
    val onOpenRoute: (AtomicRoute) -> Unit,
    val onCompareFolders: (FileNodeId, FileNodeId) -> Unit,
)

@Composable
private fun MainScreenContent(
    currentTab: NavigationTab,
    volumes: List<StorageVolumeInfo>,
    hasStorageAccess: Boolean,
    storageReadiness: StorageReadinessState,
    onRetryStorage: () -> Unit,
    browseTabs: BrowseTabsState,
    onBrowseTabsChange: ((BrowseTabsState) -> BrowseTabsState) -> Unit,
    settingsRepository: SettingsRepository,
    privateLocked: Boolean,
    callbacks: ShellCallbacks,
) {
    if (storageReadiness.snapshot == null && currentTab in setOf(NavigationTab.HOME, NavigationTab.STORAGE)) {
        StorageReadinessContent(
            state = storageReadiness,
            onRetry = onRetryStorage,
            modifier = Modifier.fillMaxSize().padding(AtomicSpacing.s16),
        )
        return
    }
    when (currentTab) {
        NavigationTab.HOME -> {
            HomeScreen(
                volumes = volumes,
                hasStorageAccess = hasStorageAccess,
                storageReadiness = storageReadiness,
                onRetryStorage = onRetryStorage,
                onNavigateToVolume = callbacks.onBrowseVolume,
                onNavigateToCategory = callbacks.onCategorySelected,
                onOpenPrivateFiles = callbacks.onOpenPrivateFiles,
                onNavigateToFolder = callbacks.onFolderSelected,
                onRequestStorageAccess = callbacks.onRequestStorageAccess,
                onOpenStorageDetails = callbacks.onOpenStorageDetails,
                privateLocked = privateLocked,
            )
        }
        NavigationTab.BROWSE -> {
            val tab = browseTabs.active
            // A fresh composition per tab, so dialogs and scroll state don't leak between tabs.
            key(tab.id, tab.sessionId) {
                BrowseScreen(
                    initialFolderId = FileNodeId(tab.folderRaw),
                    storage =
                        BrowseStorageAccess(
                            volumes,
                            hasStorageAccess,
                            if (storageReadiness is StorageReadinessState.Ready) {
                                callbacks.onRequestStorageAccess
                            } else {
                                onRetryStorage
                            },
                        ),
                    onNavigateBack = callbacks.onNavigateBack,
                    openRequest = tab.openRequest,
                    onNotify = callbacks.onNotify,
                    onOpenOperations = { callbacks.onOpenRoute(AtomicRoute.Operations) },
                    tabKey = tab.stateKey,
                    tabStrip = {
                        BrowseTabStrip(
                            state = browseTabs,
                            onSelect = { id -> onBrowseTabsChange { it.select(id) } },
                            onClose = { id -> onBrowseTabsChange { it.close(id) } },
                        )
                    },
                    onLocationChange = { title -> onBrowseTabsChange { it.retitle(tab.id, title) } },
                    onOpenInNewTab =
                        if (browseTabs.canOpenMore) {
                            { folder: FileNode -> onBrowseTabsChange { it.open(folder.id.raw, folder.name) } }
                        } else {
                            null
                        },
                    onCompareFolders = callbacks.onCompareFolders,
                )
            }
        }
        NavigationTab.STORAGE -> {
            StorageScreen(
                volumes = volumes,
                onBrowseVolume = callbacks.onBrowseVolume,
                onBrowseFolder = callbacks.onFolderSelected,
                onCompareFolders = callbacks.onCompareFolders,
                onOpenStorageIntelligence = callbacks.onOpenStorageIntelligence,
                onOpenOperations = { callbacks.onOpenRoute(AtomicRoute.Operations) },
                onOpenTrash = { callbacks.onOpenRoute(AtomicRoute.Trash) },
                onOpenApps = { callbacks.onOpenRoute(AtomicRoute.Apps) },
                onOpenWifiShare = { callbacks.onOpenRoute(AtomicRoute.WifiShare) },
                onNotify = callbacks.onNotify,
            )
        }
        NavigationTab.SETTINGS -> {
            SettingsScreen(
                settingsRepository = settingsRepository,
                hasStorageAccess = hasStorageAccess,
                storageAccessStatus =
                    when (storageReadiness) {
                        is StorageReadinessState.Loading -> stringResource(R.string.storage_readiness_access_loading)
                        is StorageReadinessState.Failed -> stringResource(R.string.storage_readiness_access_failed)
                        is StorageReadinessState.Ready -> null
                    },
                onRequestStorageAccess =
                    if (storageReadiness is StorageReadinessState.Ready) {
                        callbacks.onRequestStorageAccess
                    } else {
                        onRetryStorage
                    },
                onClearScanCache = callbacks.onClearScanCache,
                onNotify = callbacks.onNotify,
            )
        }
    }
}

/** Mounted volumes, or a single internal entry built from the app's own folder when none are listed. */
private fun loadVolumes(
    context: Context,
    hasStorageAccess: Boolean,
): List<StorageVolumeInfo> {
    val enumerated = StorageVolumes.enumerate(context)
    if (enumerated.isNotEmpty()) return enumerated
    val totalBytes = runCatching { context.filesDir.totalSpace }.getOrDefault(0L).coerceAtLeast(1L)
    val freeBytes = runCatching { context.filesDir.freeSpace }.getOrDefault(0L)
    return listOf(
        StorageVolumeInfo(
            id = "primary",
            label = "Internal Shared Storage",
            type = StorageType.INTERNAL_SHARED,
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            isRemovable = false,
            isMounted = true,
            rootNodeId = null,
            requiresGrant = !hasStorageAccess,
        ),
    )
}

/** The summary of a finished operation, or null while it is still going or was cancelled. */
private fun OperationStatus.summaryOrNull(): OperationSummary? =
    when (this) {
        is OperationStatus.Completed -> summary
        is OperationStatus.PartiallyCompleted -> summary
        is OperationStatus.Failed -> summary
        else -> null
    }

/** Volume changes that refresh the Storage list. */
private val MEDIA_EVENTS =
    listOf(
        Intent.ACTION_MEDIA_MOUNTED,
        Intent.ACTION_MEDIA_UNMOUNTED,
        Intent.ACTION_MEDIA_REMOVED,
        Intent.ACTION_MEDIA_BAD_REMOVAL,
        Intent.ACTION_MEDIA_EJECT,
    )
