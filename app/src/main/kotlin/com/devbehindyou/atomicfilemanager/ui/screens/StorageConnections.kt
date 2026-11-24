package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicErrorState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSettingsRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicToggleCard
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicConfirmSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.data.backend.network.DiscoveredServer
import com.devbehindyou.atomicfilemanager.data.backend.network.NetworkCredentialsStore
import com.devbehindyou.atomicfilemanager.data.backend.network.NsdServerDiscovery
import com.devbehindyou.atomicfilemanager.data.backend.network.ServerDiscovery
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

@Composable
internal fun StorageConnections(
    onBrowseFolder: (FileNodeId) -> Unit,
    onNotify: (String) -> Unit,
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
    var protocolToAdd by rememberSaveable { mutableStateOf<NetworkProtocol?>(null) }
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

    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s24)) {
        LinkedFoldersSection(onBrowseFolder = onBrowseFolder, onNotify = onNotify)

        NetworkLocationsSection(
            servers = savedServers,
            loadingFailed = serversLoadingFailed,
            busy = serversBusy,
            onRetry = { serversLoadRound++ },
            onBrowse = { onBrowseFolder(it.toRootNodeId()) },
            onRemove = { serverToDelete = it },
            onAdd = { protocolToAdd = it },
        )
    }

    protocolToAdd?.let { protocol ->
        AddNetworkServerDialog(
            initialProtocol = protocol,
            saving = serversBusy,
            ready = savedServers != null,
            onDismiss = { if (!serversBusy) protocolToAdd = null },
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
                            protocolToAdd = null
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

@Composable
internal fun NetworkLocationsSection(
    servers: List<NetworkServerConfig>?,
    loadingFailed: Boolean,
    busy: Boolean,
    onRetry: () -> Unit,
    onBrowse: (NetworkServerConfig) -> Unit,
    onRemove: (NetworkServerConfig) -> Unit,
    onAdd: (NetworkProtocol) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        AtomicSectionLabel("Network locations")
        when {
            loadingFailed ->
                AtomicErrorState(
                    title = stringResource(R.string.storage_servers_load_error),
                    message = "Retry to open or add a saved connection.",
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                )
            servers == null -> AtomicLoading(stringResource(R.string.storage_servers_loading))
            servers.isEmpty() ->
                AtomicEmptyState(
                    message = "No saved servers",
                    detail = "Choose a protocol to set up a connection.",
                )
            else ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(Atomic.colors.surfaceCard, AtomicShape.lg)
                        .padding(horizontal = AtomicSpacing.s12),
                ) {
                    servers.forEach { server ->
                        AtomicFileRow(
                            name = server.name,
                            meta = savedServerSummary(server),
                            icon = AtomicIcons.Network,
                            onClick = { onBrowse(server) },
                            trailing = {
                                AtomicIconButton(
                                    AtomicIcons.Trash,
                                    "Remove " + server.name,
                                    onClick = { onRemove(server) },
                                    variant = AtomicIconButtonVariant.Destructive,
                                    enabled = !busy,
                                )
                            },
                        )
                    }
                }
        }
        if (servers != null && !loadingFailed && !busy) {
            NetworkProtocolChoices(onAdd)
        }
    }
}

@Composable
private fun NetworkProtocolChoices(onAdd: (NetworkProtocol) -> Unit) {
    AtomicSectionLabel("Add server")
    Column(
        Modifier.fillMaxWidth()
            .background(Atomic.colors.surfaceCard, AtomicShape.lg)
            .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8),
    ) {
        val protocols =
            listOf(
                NetworkProtocol.SMB,
                NetworkProtocol.SFTP,
                NetworkProtocol.FTP,
                NetworkProtocol.FTPS,
                NetworkProtocol.WEBDAV,
            )
        protocols.forEachIndexed { index, protocol ->
            if (index > 0) StorageToolDivider()
            StorageToolRow(
                title = protocol.displayName,
                subtitle =
                    when (protocol) {
                        NetworkProtocol.SMB -> "Windows share or NAS"
                        NetworkProtocol.SFTP -> "SSH file transfer"
                        NetworkProtocol.FTP -> "Unencrypted connection"
                        NetworkProtocol.FTPS -> "FTP with explicit TLS"
                        NetworkProtocol.WEBDAV -> "Server address and remote path"
                    },
                icon = AtomicIcons.Network,
                onClick = { onAdd(protocol) },
            )
        }
    }
}

private const val SCAN_MS = 15_000L

private fun savedServerSummary(server: NetworkServerConfig): String {
    val user =
        when {
            server.anonymous -> "anonymous@"
            server.username.isNotEmpty() -> "${server.username}@"
            else -> ""
        }
    return "${server.protocol.name} · $user${server.host}:${server.port}${server.remotePath}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddNetworkServerDialog(
    initialProtocol: NetworkProtocol,
    onDismiss: () -> Unit,
    onSave: (NetworkServerConfig, String?) -> Unit,
    saving: Boolean = false,
    ready: Boolean = true,
) {
    var selectedProtocol by rememberSaveable(initialProtocol) { mutableStateOf(initialProtocol) }
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
