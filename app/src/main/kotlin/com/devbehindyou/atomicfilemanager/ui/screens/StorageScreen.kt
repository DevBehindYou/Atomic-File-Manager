package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSettingsRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicToggleCard
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicConfirmSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicStorageCard
import com.devbehindyou.atomicfilemanager.data.backend.network.DiscoveredServer
import com.devbehindyou.atomicfilemanager.data.backend.network.NetworkCredentialsStore
import com.devbehindyou.atomicfilemanager.data.backend.network.NsdServerDiscovery
import com.devbehindyou.atomicfilemanager.data.backend.network.ServerDiscovery
import com.devbehindyou.atomicfilemanager.data.volume.StorageVolumes
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import com.devbehindyou.atomicfilemanager.domain.model.StorageVolumeInfo
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Storage (ATOMIC_UI_PLAN.md §7.4, canvas "Storage"): free space on the main volume, a card with
 * a meter per volume, storage analysis, app cache, and network servers.
 */
@Composable
fun StorageScreen(
    volumes: List<StorageVolumeInfo>,
    onBrowseVolume: (StorageVolumeInfo) -> Unit,
    onBrowseFolder: (FileNodeId) -> Unit = {},
    /** Opens a saved folder sync (ALL_IN_ONE_PLAN.md 4.1). */
    onCompareFolders: (FileNodeId, FileNodeId) -> Unit = { _, _ -> },
    onOpenStorageIntelligence: ((FileNodeId) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpenOperations: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    onOpenWifiShare: () -> Unit = {},
    onNotify: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as? AtomicApp
    val saveServerError = stringResource(R.string.storage_server_save_error)
    val removeServerError = stringResource(R.string.storage_server_remove_error)
    val passwordUnavailable = stringResource(R.string.storage_server_password_unavailable)
    val credentials =
        remember(app, context) {
            StoragePreferenceAccess(mutex = StoragePreferenceLocks.savedServers) {
                app?.container?.networkCredentialsStore ?: NetworkCredentialsStore(context.applicationContext)
            }
        }
    val scope = rememberCoroutineScope()
    var savedServers by remember(credentials) { mutableStateOf<List<NetworkServerConfig>?>(null) }
    var serversLoadingFailed by remember(credentials) { mutableStateOf(false) }
    var serversBusy by remember(credentials) { mutableStateOf(false) }
    var serversLoadRound by remember(credentials) { mutableIntStateOf(0) }
    var showAddServerDialog by rememberSaveable { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<NetworkServerConfig?>(null) }

    LaunchedEffect(credentials, serversLoadRound) {
        serversLoadingFailed = false
        try {
            savedServers = credentials.access { it.getAllServers() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            serversLoadingFailed = true
        }
    }

    val cacheSize by produceState(initialValue = 0L) {
        value = withContext(Dispatchers.IO) { StorageVolumes.getAppCacheSize(context) }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AtomicSpacing.s16)
                .testTag("storage_screen"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s24),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16)) {
            AtomicSectionLabel("Storage usage")
            if (app != null) StorageUsageDashboard(volumes, app.container.storageUsageRepository, app.container.operationQueue.finished)
            AtomicSectionLabel("Volumes", trailingText = "${volumes.size} volumes")
            volumes.forEach { volume -> StorageVolumeCard(volume, onBrowse = { onBrowseVolume(volume) }) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
            AtomicSectionLabel("Analysis")
            AtomicText(
                "Find duplicates, large files and empty folders. Nothing is deleted without asking.",
                AtomicTextRole.BodySecondary,
            )
            AtomicButton(
                "Analyze storage",
                onClick = {
                    val primaryRoot =
                        volumes.firstOrNull()?.rootNodeId ?: FileNodeId.file(
                            Environment.getExternalStorageDirectory()?.absolutePath ?: context.filesDir.absolutePath,
                        )
                    onOpenStorageIntelligence?.invoke(primaryRoot)
                },
                leadingIcon = AtomicIcons.Cleanup,
                modifier = Modifier.fillMaxWidth().testTag("open_storage_intelligence_button"),
            )
            AtomicText("App cache · ${FileUtils.formatBytes(cacheSize)}", AtomicTextRole.MonoMeta)
        }

        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
            AtomicSectionLabel("Tools")
            val running = app?.container?.operationQueue?.active?.collectAsState()?.value
            AtomicSettingsRow(
                title = "Operations",
                value = if (running == null) "History, resume and retry" else "1 running",
                onClick = onOpenOperations,
                modifier = Modifier.testTag("open_operations"),
            )
            val trashFlow = remember(app) { app?.container?.trashStore?.observeAll() }
            val trash = trashFlow?.collectAsState(initial = emptyList())?.value.orEmpty()
            AtomicSettingsRow(
                title = "Trash",
                value = TrashText.summary(trash),
                onClick = onOpenTrash,
                modifier = Modifier.testTag("open_trash"),
            )
            AtomicSettingsRow(
                title = "Wi-Fi share",
                value = "Send a folder to a computer's browser",
                onClick = onOpenWifiShare,
                modifier = Modifier.testTag("open_wifi_share"),
            )
            AtomicSettingsRow(
                title = "Apps",
                value = "Save APKs, uninstall, app info",
                onClick = onOpenApps,
                modifier = Modifier.testTag("open_apps"),
            )
        }

        LinkedFoldersSection(onBrowseFolder = onBrowseFolder, onNotify = onNotify)

        SyncPairsSection(onCompareFolders = onCompareFolders)

        Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
            AtomicSectionLabel("Network")
            val servers = savedServers
            if (servers == null) {
                AtomicText(
                    stringResource(
                        if (serversLoadingFailed) {
                            R.string.storage_servers_load_error
                        } else {
                            R.string.storage_servers_loading
                        },
                    ),
                    AtomicTextRole.BodySecondary,
                )
                if (serversLoadingFailed) {
                    AtomicButton(stringResource(R.string.action_retry), onClick = {
                        serversLoadRound++
                    }, variant = AtomicButtonVariant.Text)
                }
            } else if (servers.isEmpty()) {
                AtomicEmptyState(
                    message = "No servers yet.",
                    detail = "Connect to FTP, FTPS or WebDAV to browse and move files on another machine.",
                )
            } else {
                Column {
                    servers.forEach { server ->
                        AtomicFileRow(
                            name = server.name,
                            meta = "${server.protocol.name} · ${serverAddress(server)}",
                            icon = AtomicIcons.Network,
                            onClick = { onBrowseFolder(server.toRootNodeId()) },
                            trailing = {
                                AtomicIconButton(
                                    AtomicIcons.Trash,
                                    "Remove ${server.name}",
                                    onClick = { serverToDelete = server },
                                    variant = AtomicIconButtonVariant.Destructive,
                                    enabled = !serversBusy,
                                )
                            },
                        )
                    }
                }
            }
            AtomicButton(
                "Add server",
                onClick = { showAddServerDialog = true },
                variant = AtomicButtonVariant.Ghost,
                leadingIcon = AtomicIcons.Add,
                enabled = servers != null && !serversBusy,
                modifier = Modifier.fillMaxWidth().testTag("add_network_server_button"),
            )
            AtomicText(
                "SFTP, SMB, FTP, FTPS and WebDAV. For SMB, put the share in the path, like /Photos.",
                AtomicTextRole.MonoMeta,
            )
        }

        ShizukuLabsSection(onBrowseFolder = onBrowseFolder)

        AtomicText(
            "Capacity covers mounted filesystems. The file breakdown covers readable shared folders; Android keeps some system and app data private.",
            AtomicTextRole.BodySecondary,
        )
    }

    if (showAddServerDialog) {
        AddNetworkServerDialog(
            saving = serversBusy,
            ready = savedServers != null,
            onDismiss = { if (!serversBusy) showAddServerDialog = false },
            onSave = { config, password ->
                if (!serversBusy && savedServers != null) {
                    serversBusy = true
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            val (passwordSaved, updated) =
                                credentials.update { store ->
                                    store.saveServer(config, password) to store.getAllServers()
                                }
                            savedServers = updated
                            showAddServerDialog = false
                            if (!passwordSaved) {
                                onNotify(passwordUnavailable)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            onNotify(saveServerError)
                        } finally {
                            serversBusy = false
                        }
                    }
                }
            },
        )
    }

    serverToDelete?.let { server ->
        AtomicConfirmSheet(
            label = "Network",
            headline = "Remove server?",
            body = "\"${server.name}\" and its saved password are removed from this phone. Files on the server stay.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                if (!serversBusy) {
                    serversBusy = true
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            savedServers =
                                credentials.update { store ->
                                    store.deleteServer(server.id)
                                    store.getAllServers()
                                }
                            serverToDelete = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            onNotify(removeServerError)
                        } finally {
                            serversBusy = false
                        }
                    }
                }
            },
            onDismiss = { if (!serversBusy) serverToDelete = null },
        )
    }
}

private const val SCAN_MS = 15_000L

private fun serverAddress(server: NetworkServerConfig): String {
    val user =
        when {
            server.anonymous -> "anonymous@"
            server.username.isNotEmpty() -> "${server.username}@"
            else -> ""
        }
    return "$user${server.host}:${server.port}${server.remotePath}"
}

@Composable
private fun StorageVolumeCard(
    volume: StorageVolumeInfo,
    onBrowse: () -> Unit,
) {
    val total = volume.totalBytes
    val used = (total - volume.freeBytes).coerceAtLeast(0L)
    val fraction = if (total > 0L) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    AtomicStorageCard(
        name = volume.label,
        usage = "${FileUtils.formatBytes(used)} / ${FileUtils.formatBytes(total)}",
        fraction = fraction,
        meterDescription = "${volume.label}, ${(fraction * 100).toInt()} percent used",
        onBrowse = onBrowse,
        browseLabel =
            when {
                !volume.isMounted -> "Unavailable"
                volume.requiresGrant -> "Grant access →"
                else -> "Browse →"
            },
        nearlyFullLabel = "Nearly full · ${FileUtils.formatBytes(volume.freeBytes)} left",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddNetworkServerDialog(
    onDismiss: () -> Unit,
    onSave: (NetworkServerConfig, String?) -> Unit,
    saving: Boolean = false,
    ready: Boolean = true,
) {
    var selectedProtocol by rememberSaveable { mutableStateOf(NetworkProtocol.FTP) }
    var name by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf(selectedProtocol.defaultPort.toString()) }
    var remotePath by rememberSaveable { mutableStateOf("/") }
    var username by rememberSaveable { mutableStateOf("") }
    // Kept in memory only: a password must not end up in the saved-state bundle.
    var password by remember { mutableStateOf("") }
    var anonymous by rememberSaveable { mutableStateOf(false) }
    val portError = if (portText.toIntOrNull()?.let { it in 1..65535 } == false) "Use a port from 1 to 65535." else null

    // Servers that announce themselves on this Wi-Fi (ALL_IN_ONE_PLAN.md 3.2); browsing stops
    // after a short while, or when the sheet closes.
    val context = LocalContext.current
    var found by remember { mutableStateOf(emptyList<DiscoveredServer>()) }
    var scanning by remember { mutableStateOf(true) }
    var scanRound by remember { mutableIntStateOf(0) }
    LaunchedEffect(scanRound) {
        scanning = true
        withTimeoutOrNull(SCAN_MS) {
            NsdServerDiscovery(
                context.applicationContext,
            ).discover().collect { found = ServerDiscovery.merge(found, it) }
        }
        scanning = false
    }

    AtomicSheet(label = "Add server", onDismiss = onDismiss) {
        AtomicText("On this network", AtomicTextRole.MonoLabel)
        if (found.isEmpty()) {
            AtomicText(
                if (scanning) "Looking for file servers…" else "No server announced itself. Enter one below.",
                AtomicTextRole.BodySecondary,
            )
        }
        found.forEach { server ->
            AtomicSettingsRow(
                title = server.name,
                value = "${server.protocol.name} · ${server.host}",
                onClick = {
                    selectedProtocol = server.protocol
                    host = server.host
                    portText = server.port.toString()
                    remotePath = server.path
                    if (name.isBlank()) name = server.name
                },
                modifier = Modifier.testTag("discovered_server"),
            )
        }
        if (!scanning) {
            AtomicButton("Look again", onClick = { scanRound++ }, variant = AtomicButtonVariant.Text)
        }
        AtomicText("Protocol", AtomicTextRole.MonoLabel)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            NetworkProtocol.entries.forEach { proto ->
                AtomicChip(
                    label = proto.name,
                    selected = selectedProtocol == proto,
                    onSelectedChange = {
                        selectedProtocol = proto
                        portText = proto.defaultPort.toString()
                    },
                )
            }
        }
        AtomicTextField(
            name,
            { name = it },
            label = "Name",
            placeholder = "Office NAS",
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicTextField(
            host,
            { host = it },
            label = "Host or address",
            placeholder = "192.168.1.100",
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicTextField(
            portText,
            { portText = it },
            label = "Port",
            errorText = portError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicTextField(
            remotePath,
            { remotePath = it },
            label = "Remote path or share",
            placeholder = "/",
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicToggleCard(
            title = "Anonymous",
            description = "Connect without a user name or password.",
            checked = anonymous,
            onCheckedChange = { anonymous = it },
        )
        if (!anonymous) {
            AtomicTextField(username, { username = it }, label = "User name", modifier = Modifier.fillMaxWidth())
            AtomicTextField(
                password,
                { password = it },
                label = "Password",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AtomicButton(
            if (saving) stringResource(R.string.action_saving) else "Save server",
            onClick = {
                val port = portText.toIntOrNull() ?: selectedProtocol.defaultPort
                val effectiveName = name.ifBlank { "${selectedProtocol.name} - $host" }
                val config =
                    NetworkServerConfig(
                        id = UUID.randomUUID().toString(),
                        name = effectiveName,
                        protocol = selectedProtocol,
                        host = host.trim(),
                        port = port,
                        username = username.trim(),
                        remotePath = if (remotePath.startsWith("/")) remotePath else "/$remotePath",
                        anonymous = anonymous,
                    )
                onSave(config, if (anonymous) null else password)
            },
            enabled = ready && !saving && host.isNotBlank() && portError == null,
            modifier = Modifier.fillMaxWidth(),
        )
        AtomicButton(
            "Cancel",
            onClick = onDismiss,
            variant = AtomicButtonVariant.Ghost,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
