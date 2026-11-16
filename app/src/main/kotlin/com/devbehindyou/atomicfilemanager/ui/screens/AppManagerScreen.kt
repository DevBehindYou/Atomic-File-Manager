package com.devbehindyou.atomicfilemanager.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicEmptyState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicErrorState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicPushedHeader
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicCommandGrid
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicMenuAction
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.data.apps.AppManagerRules
import com.devbehindyou.atomicfilemanager.data.apps.AppSort
import com.devbehindyou.atomicfilemanager.data.apps.InstalledApp
import com.devbehindyou.atomicfilemanager.data.apps.InstalledAppsReader
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val APK_MIME = "application/vnd.android.package-archive"

/**
 * App manager (ALL_IN_ONE_PLAN.md 2.7): launchable apps with package names. Each opens a sheet to
 * open the app, see its system info, save or share its APK, or uninstall (the system asks first).
 * The list reloads when the screen comes back, so an uninstalled app disappears.
 */
@Composable
fun AppManagerScreen(
    onBack: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val reader = remember { InstalledAppsReader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<Result<List<InstalledApp>>?>(null) }
    var reloads by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(AppSort.NAME) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<InstalledApp?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(reloads) {
        apps = runCatching { reader.list() }.onFailure { if (it is CancellationException) throw it }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) reloads++
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val installed = apps?.getOrNull()
    val shown =
        remember(installed, query, showSystem, sort) {
            installed?.let { AppManagerRules.visible(it, query, showSystem, sort) }
        }

    Column(
        modifier
            .fillMaxSize()
            .background(Atomic.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("app_manager"),
    ) {
        AtomicPushedHeader(
            onBack = onBack,
            title = "App manager",
        )
        Column(
            Modifier.padding(horizontal = AtomicSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicTextField(
                value = query,
                onValueChange = { query = it },
                label = "Find an app",
                placeholder = "Name or package",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
            ) {
                AppSort.entries.forEach { option ->
                    AtomicChip(option.title, selected = sort == option, onSelectedChange = { sort = option })
                }
                AtomicChip("System apps", selected = showSystem, onSelectedChange = { showSystem = it })
            }
            if (shown != null) AtomicText(AppText.count(shown.size), AtomicTextRole.MonoMeta)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(AtomicSpacing.screenMargin),
        ) {
            when {
                apps?.isFailure == true ->
                    item {
                        AtomicErrorState(
                            title = "Apps unavailable",
                            message = "Couldn't read the installed apps.",
                            actionLabel = "Retry",
                            onAction = {
                                apps = null
                                reloads++
                            },
                        )
                    }
                shown == null -> item { AtomicLoading("Reading apps…") }
                shown.isEmpty() ->
                    item {
                        AtomicEmptyState(
                            message = if (query.isBlank()) "No apps to show" else "No apps match",
                            actionLabel =
                                when {
                                    query.isNotBlank() -> "Clear search"
                                    !showSystem -> "Show system apps"
                                    else -> null
                                },
                            onAction = {
                                if (query.isNotBlank()) query = "" else showSystem = true
                            },
                        )
                    }
                else ->
                    itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                        InstalledAppRow(app, first = index == 0, last = index == shown.lastIndex) { chosen = app }
                    }
            }
        }
    }

    chosen?.let { app ->
        val launchIntent by produceState<Intent?>(null, app.packageName, context) {
            value =
                withContext(Dispatchers.IO) {
                    context.packageManager.getLaunchIntentForPackage(app.packageName)
                }
        }
        AtomicSheet(label = app.label.ifBlank { app.packageName }, onDismiss = { chosen = null }) {
            AtomicFactSheet(AppText.facts(app))
            if (app.isSplit) {
                AtomicText(AppText.SPLIT_NOTE, AtomicTextRole.BodySecondary)
            }
            AtomicCommandGrid(
                buildList {
                    launchIntent?.let { intent ->
                        add(
                            AtomicMenuAction("Open app", AtomicIcons.OpenExternally, {
                                context.startSafely(intent, onNotify)
                            }),
                        )
                    }
                    add(
                        AtomicMenuAction("App info", AtomicIcons.Info, {
                            context.startSafely(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", app.packageName, null),
                                ),
                                onNotify,
                            )
                        }),
                    )
                    add(
                        AtomicMenuAction(
                            if (busy) "Saving…" else "Save APK to Download",
                            AtomicIcons.Download,
                            onClick = {
                                busy = true
                                scope.launch {
                                    val saved = reader.export(app)
                                    busy = false
                                    onNotify(
                                        saved.fold(
                                            { AppText.saved(it) },
                                            { "Couldn't save the APK. Check storage access." },
                                        ),
                                    )
                                }
                            },
                            enabled = !busy,
                            testTag = "app_export",
                        ),
                    )
                    add(
                        AtomicMenuAction(
                            "Share APK",
                            AtomicIcons.Share,
                            onClick = {
                                busy = true
                                scope.launch {
                                    reader.export(app).fold(
                                        { file -> context.shareApk(file, onNotify) },
                                        { onNotify("Couldn't prepare the APK to share.") },
                                    )
                                    busy = false
                                }
                            },
                            enabled = !busy,
                        ),
                    )
                    if (!app.isSystem) {
                        add(
                            AtomicMenuAction(
                                "Uninstall",
                                AtomicIcons.Trash,
                                onClick = {
                                    chosen = null
                                    context.startSafely(
                                        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)),
                                        onNotify,
                                    )
                                },
                                destructive = true,
                                testTag = "app_uninstall",
                            ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledApp,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val colors = Atomic.colors
    val top = if (first) AtomicShape.radiusLg else 0.dp
    val bottom = if (last) AtomicShape.radiusLg else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    AtomicFileRow(
        name = app.label.ifBlank { app.packageName },
        meta = app.packageName,
        icon = AtomicIcons.Apk,
        onClick = onClick,
        modifier = Modifier.clip(shape).background(colors.surfaceCard).padding(horizontal = AtomicSpacing.s16),
        leading = { InstalledAppIcon(app) },
        trailing = {
            Icon(
                AtomicIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(AtomicSize.iconSmall),
                tint = colors.contentSecondary,
            )
        },
    )
}

@Composable
private fun InstalledAppIcon(app: InstalledApp) {
    val packageManager = LocalContext.current.packageManager
    val pixels = with(LocalDensity.current) { AtomicSize.iconTile.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.packageName, app.updatedAt, pixels) {
        value =
            withContext(Dispatchers.IO) {
                try {
                    packageManager.getApplicationIcon(app.packageName).toBitmap(pixels, pixels).asImageBitmap()
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
    }
    val bitmap = icon
    if (bitmap == null) {
        AtomicIconTile(AtomicIcons.Apk)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(AtomicSize.iconTile).clip(AtomicShape.sm),
        )
    }
}

private fun Context.startSafely(
    intent: Intent?,
    onNotify: (String) -> Unit,
) {
    if (intent == null) {
        onNotify("This app can't be opened from here.")
        return
    }
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        onNotify("Nothing on this phone can do that.")
    }
}

private fun Context.shareApk(
    file: File,
    onNotify: (String) -> Unit,
) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(APK_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startSafely(Intent.createChooser(send, "Share ${file.name}"), onNotify)
}

/** Wording for [AppManagerScreen]; pure so it is unit-tested. */
internal object AppText {
    const val SPLIT_NOTE =
        "This app was installed in parts. It is saved as one .apks file, which needs an installer " +
            "that handles split APKs."

    fun count(n: Int): String = if (n == 1) "1 app" else "$n apps"

    fun facts(app: InstalledApp): List<AtomicFact> =
        listOfNotNull(
            AtomicFact("Package", app.packageName, monoValue = true),
            app.versionName?.let { AtomicFact("Version", it, monoValue = true) },
            AtomicFact("Size", FileUtils.formatBytes(app.sizeBytes), monoValue = true),
            AtomicFact("Installed", FileUtils.formatDate(app.installedAt), monoValue = true),
            AtomicFact("Updated", FileUtils.formatDate(app.updatedAt), monoValue = true),
            if (app.isSplit) AtomicFact("Parts", "${app.splitSourceDirs.size + 1} APKs", monoValue = true) else null,
        )

    fun saved(file: File): String = "Saved to Download/Atomic File Manager/Apps/${file.name}"
}
