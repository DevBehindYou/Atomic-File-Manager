package com.devbehindyou.atomicfilemanager.ui.screens

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicStatusPill
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicToggleCard
import com.devbehindyou.atomicfilemanager.data.share.WifiShareController
import com.devbehindyou.atomicfilemanager.data.share.WifiShareState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File

private val SHARE_CONTENT_WIDTH = 680.dp
private const val COUNTER_REFRESH_MS = 1_000L

/**
 * The guarded Wi-Fi share (ALL_IN_ONE_PLAN.md 3.4): one folder to a browser on the same Wi-Fi,
 * behind a one-time PIN, read-only unless uploads are switched on. It stops after 15 idle minutes.
 */
@Composable
fun WifiShareScreen(
    controller: WifiShareController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val state by controller.state.collectAsState()
    val folders = remember { WifiShareText.folders() }
    var folderPath by rememberSaveable { mutableStateOf(folders.firstOrNull()?.second?.path.orEmpty()) }
    var allowUpload by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current

    // Leaving the screen ends the share. Rotation rebuilds this screen within the same session.
    DisposableEffect(controller) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                controller.stop()
                controller.clear()
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("wifi_share"),
    ) {
        AtomicPushedHeader(onBack = onBack, title = "Wi-Fi share")
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = SHARE_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = AtomicSpacing.screenMargin, vertical = AtomicSpacing.s16),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
            ) {
                when (val session = state) {
                    is WifiShareState.Running -> RunningShare(session, controller)
                    else -> {
                        if (session is WifiShareState.Stopped) {
                            AtomicText(
                                session.message,
                                AtomicTextRole.Body,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        ShareSetup(
                            folders = folders,
                            folderPath = folderPath,
                            allowUpload = allowUpload,
                            onFolderChange = { folderPath = it },
                            onUploadsChange = { allowUpload = it },
                            onStart = { controller.start(File(folderPath), allowUpload) },
                        )
                    }
                }
                WifiShareCard {
                    AtomicText("When sharing ends", AtomicTextRole.Name)
                    AtomicText(WifiShareText.RUNNING_NOTE, AtomicTextRole.BodySecondary)
                }
            }
        }
    }
}

@Composable
private fun ShareSetup(
    folders: List<Pair<String, File>>,
    folderPath: String,
    allowUpload: Boolean,
    onFolderChange: (String) -> Unit,
    onUploadsChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    WifiShareCard {
        AtomicText("Share over your Wi-Fi", AtomicTextRole.DisplayCard)
        AtomicText(WifiShareText.EXPLAINER, AtomicTextRole.BodySecondary)
        AtomicText("Folder", AtomicTextRole.MonoLabel)
        if (folders.isEmpty()) {
            AtomicText("No shared folders are available.", AtomicTextRole.BodySecondary)
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            ) {
                folders.forEach { (label, dir) ->
                    AtomicChip(
                        label,
                        selected = folderPath == dir.path,
                        onSelectedChange = { onFolderChange(dir.path) },
                    )
                }
            }
        }
        AtomicToggleCard(
            title = "Allow uploads",
            description = "The browser can add files to this folder. Existing files stay intact.",
            checked = allowUpload,
            onCheckedChange = onUploadsChange,
        )
        AtomicButton(
            "Start sharing",
            onClick = onStart,
            enabled = folders.any { it.second.path == folderPath },
            variant = AtomicButtonVariant.Solid,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("start_wifi_share"),
        )
    }
}

@Composable
private fun RunningShare(
    running: WifiShareState.Running,
    controller: WifiShareController,
) {
    val activeCounts =
        remember(controller) {
            controller.transfers.active.map { it.size }.distinctUntilChanged()
        }
    val activeCount by activeCounts.collectAsState(initial = 0)
    var sent by remember(running) { mutableIntStateOf(0) }
    var received by remember(running) { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (true) {
            sent = running.server.downloads.get()
            received = running.server.uploads.get()
            delay(COUNTER_REFRESH_MS)
        }
    }
    WifiShareCard {
        AtomicStatusPill("Sharing")
        AtomicText("Share over your Wi-Fi", AtomicTextRole.DisplayCard)
        AtomicText("Folder", AtomicTextRole.MonoLabel)
        AtomicText(WifiShareText.scope(running.folder, running.allowUpload), AtomicTextRole.Body)
        AtomicDivider()
        AtomicText("Open this address in a browser on the same Wi-Fi.", AtomicTextRole.BodySecondary)
        SelectionContainer {
            AtomicText(running.url, AtomicTextRole.NameLarge, modifier = Modifier.testTag("wifi_share_url"))
        }
        AtomicText("Session PIN", AtomicTextRole.MonoLabel)
        AtomicText(running.pin, AtomicTextRole.DisplayTitle, modifier = Modifier.testTag("wifi_share_pin"))
        AtomicButton(
            "Stop sharing",
            onClick = controller::stop,
            variant = AtomicButtonVariant.Destructive,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("stop_wifi_share"),
        )
    }
    WifiShareCard {
        AtomicText("Transfers", AtomicTextRole.Name)
        AtomicText(WifiShareText.activeTransfers(activeCount), AtomicTextRole.BodySecondary)
        AtomicText(WifiShareText.completedTransfers(running.allowUpload, sent, received), AtomicTextRole.MonoMeta)
    }
}

@Composable
private fun WifiShareCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = Atomic.colors
    Column(
        Modifier.fillMaxWidth()
            .background(colors.surfaceCard, AtomicShape.lg)
            .border(AtomicBorder.hair, colors.borderHair, AtomicShape.lg)
            .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        content = content,
    )
}

/** Wording and local folder choices for [WifiShareScreen]. */
internal object WifiShareText {
    const val EXPLAINER =
        "Choose one folder to share with a browser on the same Wi-Fi. The browser needs the session PIN. " +
            "Uploads are off by default."
    const val RUNNING_NOTE =
        "Sharing stops when you leave this screen, tap Stop sharing, or after 15 minutes without use."

    fun scope(
        folder: File,
        allowUpload: Boolean,
    ): String = "${folder.name} · ${if (allowUpload) "Downloads and uploads" else "Downloads only"}"

    fun activeTransfers(count: Int): String =
        when (count) {
            0 -> "Waiting for a browser transfer"
            1 -> "1 Wi-Fi transfer in progress"
            else -> "$count Wi-Fi transfers in progress"
        }

    fun completedTransfers(
        allowUpload: Boolean,
        sent: Int,
        received: Int,
    ): String = if (allowUpload) "$sent downloads · $received uploads" else "$sent downloads"

    /** The common public folders that exist on this phone. */
    fun folders(): List<Pair<String, File>> =
        listOf(
            "Download" to Environment.DIRECTORY_DOWNLOADS,
            "Camera" to Environment.DIRECTORY_DCIM,
            "Pictures" to Environment.DIRECTORY_PICTURES,
            "Documents" to Environment.DIRECTORY_DOCUMENTS,
            "Music" to Environment.DIRECTORY_MUSIC,
            "Movies" to Environment.DIRECTORY_MOVIES,
        ).map { (label, type) -> label to Environment.getExternalStoragePublicDirectory(type) }
            .filter { it.second.isDirectory }
}
