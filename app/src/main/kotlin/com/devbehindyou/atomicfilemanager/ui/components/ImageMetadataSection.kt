package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.data.preview.ExifMetadataReader
import com.devbehindyou.atomicfilemanager.data.preview.ExifTags
import com.devbehindyou.atomicfilemanager.data.preview.ImageMetadata
import com.devbehindyou.atomicfilemanager.data.preview.MetadataStrip
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/** The local file behind [node] when it is an image on device storage, else null. */
internal fun localImageFile(node: FileNode): File? =
    node.takeIf { !it.isDirectory && it.mimeType?.startsWith("image/") == true && it.id.raw.startsWith("file:") }
        ?.let { File(it.id.raw.removePrefix("file:")) }

/**
 * Photo metadata in File info (ALL_IN_ONE_PLAN.md 4.2): camera, exposure and location when present,
 * and sharing a copy without location or without any identifying metadata. The original file is
 * never changed.
 */
@Composable
fun ImageMetadataSection(
    node: FileNode,
    file: File,
    reader: ExifMetadataReader = remember { ExifMetadataReader() },
) {
    // Each source revision owns its metadata and share state from its first frame. Restarting only
    // produceState's producer would show the previous image's camera and location under this name.
    key(node.id, node.modifiedAt, node.size, file) {
        ImageMetadataRevision(node, file, reader)
    }
}

@Composable
private fun ImageMetadataRevision(
    node: FileNode,
    file: File,
    reader: ExifMetadataReader,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta by produceState<ImageMetadata?>(null) { value = reader.read(file) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val shown = meta ?: return
    if (!shown.isEmpty) {
        AtomicText("Photo details", AtomicTextRole.MonoLabel)
        AtomicFactSheet(MetadataText.facts(shown))
        if (shown.hasLocation) {
            AtomicText(MetadataText.LOCATION_NOTE, AtomicTextRole.BodySecondary)
        }
    }
    if (!ExifTags.canStrip(node.mimeType)) return

    fun share(strip: MetadataStrip) {
        busy = true
        scope.launch {
            val target = File(File(context.cacheDir, "clean"), ExifTags.cleanName(node.name))
            reader.cleanCopy(file, target, strip).fold(
                { status = context.shareClean(it, node.mimeType) },
                { status = MetadataText.FAILED },
            )
            busy = false
        }
    }
    if (shown.hasLocation) {
        AtomicButton(
            "Share without location",
            onClick = { share(MetadataStrip.LOCATION) },
            enabled = !busy,
            variant = AtomicButtonVariant.Ghost,
            leadingIcon = AtomicIcons.Share,
            modifier = Modifier.fillMaxWidth().testTag("share_without_location"),
        )
    }
    AtomicButton(
        "Share without metadata",
        onClick = { share(MetadataStrip.ALL) },
        enabled = !busy,
        variant = AtomicButtonVariant.Ghost,
        leadingIcon = AtomicIcons.Share,
        modifier = Modifier.fillMaxWidth().testTag("share_without_metadata"),
    )
    status?.let { AtomicText(it, AtomicTextRole.BodySecondary) }
}

/** Starts the share sheet; returns a message when nothing can receive it. */
private fun Context.shareClean(
    file: File,
    mime: String?,
): String? {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(mime ?: "image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        startActivity(Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: ActivityNotFoundException) {
        "Nothing on this phone can share it."
    }
}

/** Wording for [ImageMetadataSection]; pure so it is unit-tested. */
internal object MetadataText {
    const val LOCATION_NOTE =
        "This file contains location data. Anyone you send the original to can see where it was taken."
    const val FAILED = "Couldn't make a clean copy of this image."

    fun facts(meta: ImageMetadata): List<AtomicFact> =
        listOfNotNull(
            camera(meta)?.let { AtomicFact("Camera", it) },
            meta.lens?.let { AtomicFact("Lens", it) },
            meta.takenAt?.let { AtomicFact("Taken", takenAt(it), monoValue = true) },
            exposure(meta)?.let { AtomicFact("Exposure", it, monoValue = true) },
            if (meta.width != null && meta.height != null) {
                AtomicFact("Pixels", "${meta.width} × ${meta.height}", monoValue = true)
            } else {
                null
            },
            if (meta.hasLocation) {
                AtomicFact("Location", coordinates(meta.latitude!!, meta.longitude!!), monoValue = true)
            } else {
                null
            },
            meta.software?.let { AtomicFact("Software", it) },
            meta.owner?.let { AtomicFact("Owner", it) },
        )

    /** "Google Pixel 8" rather than "Google Google Pixel 8" when the model repeats the make. */
    fun camera(meta: ImageMetadata): String? {
        val make = meta.make
        val model = meta.model
        return when {
            make == null -> model
            model == null -> make
            model.startsWith(make, ignoreCase = true) -> model
            else -> "$make $model"
        }
    }

    /** EXIF "2024:05:01 13:45:10" shown as "2024-05-01 13:45"; anything else as written. */
    fun takenAt(raw: String): String {
        val match = Regex("""^(\d{4}):(\d{2}):(\d{2}) (\d{2}:\d{2})""").find(raw) ?: return raw
        return match.groupValues.drop(1).let { "${it[0]}-${it[1]}-${it[2]} ${it[3]}" }
    }

    fun exposure(meta: ImageMetadata): String? =
        listOfNotNull(
            meta.exposureSeconds?.let { shutter(it) },
            meta.fNumber?.let { "f/" + trim(it) },
            meta.iso?.let { "ISO $it" },
            meta.focalLengthMm?.let { trim(it) + " mm" },
        ).joinToString(" · ").ifEmpty { null }

    /** 0.008 s reads as "1/125 s"; a second or longer as "2 s". */
    fun shutter(seconds: Double): String =
        if (seconds >= 1.0) {
            trim(seconds) + " s"
        } else {
            "1/${(1.0 / seconds).roundToLong()} s"
        }

    fun coordinates(
        lat: Double,
        lon: Double,
    ): String = String.format(Locale.US, "%.5f, %.5f", lat, lon)

    private fun trim(value: Double): String =
        if (value == floor(value)) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
}
