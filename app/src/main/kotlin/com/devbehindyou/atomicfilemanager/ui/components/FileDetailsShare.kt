package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ClipData
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.data.preview.ExternalPreviewFile
import com.devbehindyou.atomicfilemanager.data.preview.ExternalPreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Composable
internal fun FileDetailsShareButton(
    node: FileNode,
    onShared: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? AtomicApp ?: return
    val scope = rememberCoroutineScope()
    val source = node.previewSourceKey()
    val exporter =
        remember(app) {
            ExternalPreviewHelper(app) { app.container.storageBackendSelector.forNode(it) }
        }
    val request = remember(source, scope) { ExternalPreparationRequest(scope) }
    val state by request.state.collectAsState()
    var failed by remember(source) { mutableStateOf(false) }
    DisposableEffect(request) { onDispose { request.dispose() } }

    val label = stringResource(R.string.file_details_share)
    AtomicButton(
        label,
        onClick = {
            request.start { report ->
                failed = false
                val result =
                    exporter.open(node, onProgress = report) { prepared ->
                        context.startActivity(fileShareChooser(prepared, node.name, label))
                    }
                currentCoroutineContext().ensureActive()
                when (result) {
                    is FileResult.Success -> onShared()
                    is FileResult.Failure -> failed = true
                }
            }
        },
        enabled = !state.busy,
        variant = AtomicButtonVariant.Ghost,
        leadingIcon = AtomicIcons.Share,
        modifier = Modifier.testTag("file_details_share"),
    )
    if (state.busy) ExternalPreparationStatus(state, request::cancel)
    if (failed) AtomicText(stringResource(R.string.file_details_share_failed), AtomicTextRole.BodySecondary)
}

internal fun fileShareChooser(
    file: ExternalPreviewFile,
    name: String,
    chooserTitle: String,
): Intent {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(file.mimeType)
            .putExtra(Intent.EXTRA_STREAM, file.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(name, file.uri) }
    return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
