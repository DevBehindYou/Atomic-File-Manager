package com.devbehindyou.atomicfilemanager.data.preview

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenFamily
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleKey
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleScope
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import java.util.Locale

internal data class ExternalFileApp(val component: String, val label: String) {
    fun target() = FileOpenTarget(FileOpenMode.EXTERNAL, component, label)
}

/** Representative MIME for a settings rule, not a claim that every format in a family shares handlers. */
internal fun mimeForOpeningRule(key: FileOpenRuleKey): String = when (key.scope) {
    FileOpenRuleScope.MIME -> key.value
    FileOpenRuleScope.EXTENSION -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(key.value) ?: "application/octet-stream"
    FileOpenRuleScope.FAMILY -> when (FileOpenFamily.valueOf(key.value)) {
        FileOpenFamily.IMAGE -> "image/jpeg"
        FileOpenFamily.VIDEO -> "video/mp4"
        FileOpenFamily.AUDIO -> "audio/mpeg"
        FileOpenFamily.PDF -> "application/pdf"
        FileOpenFamily.DOCUMENT -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        FileOpenFamily.ARCHIVE -> "application/zip"
        FileOpenFamily.CODE, FileOpenFamily.TEXT -> "text/plain"
        FileOpenFamily.OTHER -> "application/octet-stream"
    }
}

/** Metadata-only app discovery; actual bytes are exported only after an explicit/saved open action. */
internal class ExternalFileApps(private val context: Context) {
    fun list(mimeType: String): List<ExternalFileApp> {
        val probe = Uri.Builder().scheme("content").authority("${context.packageName}.fileprovider").appendPath("open").build()
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(probe, mimeType)
        // The flags overload remains available on API27; ResolveInfoFlags requires API33.
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                if (!activity.exported || !activity.enabled || activity.applicationInfo?.enabled == false ||
                    activity.packageName == context.packageName
                ) return@mapNotNull null
                ExternalFileApp(
                    ComponentName(activity.packageName, activity.name).flattenToString(),
                    resolved.loadLabel(context.packageManager).toString(),
                )
            }.distinctBy { it.component }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun launch(file: ExternalPreviewFile, name: String, target: FileOpenTarget, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, file.mimeType)
            clipData = ClipData.newRawUri(name, file.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            target.component?.let { component = requireNotNull(ComponentName.unflattenFromString(it)) }
        }
        context.startActivity(
            (if (target.component == null) Intent.createChooser(intent, chooserTitle) else intent)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
