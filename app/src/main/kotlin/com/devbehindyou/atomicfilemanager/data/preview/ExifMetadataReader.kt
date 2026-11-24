package com.devbehindyou.atomicfilemanager.data.preview

import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Reads EXIF from local images and writes clean copies (ALL_IN_ONE_PLAN.md 4.2). The original is
 * never changed: a clean copy is written, re-read and checked before it is handed back.
 */
class ExifMetadataReader(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun read(file: File): ImageMetadata? =
        withContext(io) {
            try {
                metadataOf(ExifInterface(file))
            } catch (_: IOException) {
                null
            } catch (_: RuntimeException) {
                // ExifInterface throws runtime errors on some corrupt files.
                null
            }
        }

    /**
     * Copies [source] to [target] without what [strip] removes; the copy is verified, else deleted.
     * A cancelled caller never receives the copy, so it is deleted too rather than left in the cache.
     */
    suspend fun cleanCopy(
        source: File,
        target: File,
        strip: MetadataStrip,
    ): Result<File> =
        try {
            withContext(io) {
                try {
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                    val exif = ExifInterface(target)
                    ExifTags.removedBy(strip).forEach { exif.setAttribute(it, null) }
                    exif.saveAttributes()
                    check(ExifTags.isClean(metadataOf(ExifInterface(target)), strip)) { "metadata still present" }
                    Result.success(target)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    target.delete()
                    Result.failure(failure)
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + io) { target.delete() }
            throw cancelled
        }

    private fun metadataOf(exif: ExifInterface): ImageMetadata {
        val latLong = exif.latLong
        return ImageMetadata(
            make = exif.text(ExifInterface.TAG_MAKE),
            model = exif.text(ExifInterface.TAG_MODEL),
            lens = exif.text(ExifInterface.TAG_LENS_MODEL),
            takenAt = exif.text(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.text(ExifInterface.TAG_DATETIME),
            iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 },
            exposureSeconds = exif.number(ExifInterface.TAG_EXPOSURE_TIME),
            fNumber = exif.number(ExifInterface.TAG_F_NUMBER),
            focalLengthMm = exif.number(ExifInterface.TAG_FOCAL_LENGTH),
            width =
                exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 },
            height =
                exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 },
            latitude = latLong?.getOrNull(0),
            longitude = latLong?.getOrNull(1),
            software = exif.text(ExifInterface.TAG_SOFTWARE),
            owner = exif.text(ExifInterface.TAG_CAMERA_OWNER_NAME) ?: exif.text(ExifInterface.TAG_ARTIST),
        )
    }

    private fun ExifInterface.text(tag: String): String? = getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }

    private fun ExifInterface.number(tag: String): Double? =
        getAttributeDouble(tag, Double.NaN).takeIf { !it.isNaN() && it > 0 }
}
