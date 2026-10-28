package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Application
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicFileType
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailNode
import com.devbehindyou.atomicfilemanager.data.preview.thumbnailSourceOf
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class FileTypePresentationTest {
    @Test
    fun genericProvidersStillRecognizeDistinctFileFamilies() {
        val examples =
            mapOf(
                "PHOTO.JPG" to AtomicFileType.IMAGE,
                "movie.mkv" to AtomicFileType.VIDEO,
                "song.mp3" to AtomicFileType.AUDIO,
                "report.PDF" to AtomicFileType.PDF,
                "install.apk" to AtomicFileType.APK,
                "backup.tar.gz" to AtomicFileType.ARCHIVE,
                "index.ts" to AtomicFileType.CODE,
                "notes.txt" to AtomicFileType.TEXT,
                "letter.docx" to AtomicFileType.DOCUMENT,
                "budget.xlsx" to AtomicFileType.SPREADSHEET,
                "slides.pptx" to AtomicFileType.PRESENTATION,
                "novel.epub" to AtomicFileType.EBOOK,
                "display.woff2" to AtomicFileType.FONT,
                "unknown.bin" to AtomicFileType.GENERIC,
            )
        examples.forEach { (name, expected) ->
            assertEquals(name, expected, fileTypeFor(thumbnailNode(name = name, mime = "application/octet-stream")))
        }
    }

    @Test
    fun explicitMimeOverridesMisleadingNamesAndNormalizesParameters() {
        assertEquals(
            AtomicFileType.PDF,
            fileTypeFor(thumbnailNode(name = "wrong.jpg", mime = " Application/PDF; v=1 ")),
        )
        assertEquals(AtomicFileType.IMAGE, fileTypeFor(thumbnailNode(name = "wrong.zip", mime = "image/jpeg")))
        assertEquals(AtomicFileType.TEXT, fileTypeFor(thumbnailNode(name = "wrong.jpg", mime = "text/plain")))
        assertEquals(AtomicFileType.AUDIO, fileTypeFor(thumbnailNode(name = "opaque", mime = "audio/mpeg")))
        assertEquals(AtomicFileType.EBOOK, fileTypeFor(thumbnailNode(name = "opaque", mime = "application/epub+zip")))
        assertEquals(
            AtomicFileType.GENERIC,
            fileTypeFor(thumbnailNode(name = "opaque", mime = "application/x-unknown")),
        )
    }

    @Test
    fun sourceCodeAndOfficeMimeTypesHaveTheirOwnShapes() {
        assertSame(AtomicIcons.Code, iconFor(thumbnailNode(name = "source.py", mime = "text/plain")))
        assertSame(AtomicIcons.Code, iconFor(thumbnailNode(name = "payload", mime = "application/json")))
        assertSame(AtomicIcons.Pdf, iconFor(thumbnailNode(name = "document", mime = "application/pdf")))
        assertSame(AtomicIcons.Text, iconFor(thumbnailNode(name = "notes.txt")))
        assertSame(AtomicIcons.Spreadsheet, iconFor(thumbnailNode(name = "table", mime = "application/vnd.ms-excel")))
        assertSame(
            AtomicIcons.Presentation,
            iconFor(thumbnailNode(name = "slides", mime = "application/vnd.ms-powerpoint")),
        )
        assertSame(AtomicIcons.GenericFile, iconFor(thumbnailNode(name = "unknown.blob")))
    }

    @Test
    fun mediaCollectionIdentityWinsOverGenericProviderFilename() {
        val video = thumbnailNode(FileNodeId.media("external", "video", 43), "wrong.jpg", "application/octet-stream")
        assertEquals(AtomicFileType.VIDEO, fileTypeFor(video))
        assertSame(AtomicIcons.Video, iconFor(video))
        val image = thumbnailNode(FileNodeId.media("external", "images", 42), "opaque", null)
        assertEquals(AtomicFileType.IMAGE, fileTypeFor(image))
    }

    @Test
    fun directoryAndPrivateFileIconsNeverEnableThumbnailAccess() {
        val privatePhoto = thumbnailNode(name = ".private.jpg").copy(isHidden = true, access = AccessFlags.NONE)
        assertEquals(AtomicFileType.IMAGE, fileTypeFor(privatePhoto))
        assertNull(thumbnailSourceOf(privatePhoto))
        val folder = privatePhoto.copy(isDirectory = true, name = "archive.zip")
        assertEquals(AtomicFileType.FOLDER, fileTypeFor(folder))
        assertSame(AtomicIcons.Files, iconFor(folder))
        assertNull(thumbnailSourceOf(folder))
    }
}
