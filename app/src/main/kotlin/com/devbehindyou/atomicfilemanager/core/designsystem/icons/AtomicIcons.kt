package com.devbehindyou.atomicfilemanager.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Bundled Tabler outline vectors. MIT license and pinned provenance in assets/licenses and docs/design. */
object AtomicIcons {
    val Sun: ImageVector get() = TablerDestinations.Sun
    val Plug: ImageVector get() = TablerDestinations.Plug
    val ChevronRight: ImageVector get() = TablerDestinations.ChevronRight
    val Cloud: ImageVector get() = TablerDestinations.Cloud

    val Back: ImageVector get() = TablerNavigation.Back
    val Forward: ImageVector get() = TablerNavigation.Forward
    val Home: ImageVector get() = TablerNavigation.Home
    val Files: ImageVector get() = TablerNavigation.Files
    val FolderOpen: ImageVector get() = TablerNavigation.FolderOpen
    val NewFolder: ImageVector get() = TablerNavigation.NewFolder
    val Storage: ImageVector get() = TablerNavigation.Storage
    val Network: ImageVector get() = TablerNavigation.Network
    val Settings: ImageVector get() = TablerNavigation.Settings
    val Search: ImageVector get() = TablerNavigation.Search
    val More: ImageVector get() = TablerNavigation.More
    val MoreHorizontal: ImageVector get() = TablerNavigation.MoreHorizontal
    val Add: ImageVector get() = TablerNavigation.Add
    val Close: ImageVector get() = TablerNavigation.Close
    val Check: ImageVector get() = TablerNavigation.Check
    val CheckCircle: ImageVector get() = TablerNavigation.CheckCircle
    val Unchecked: ImageVector get() = TablerNavigation.Unchecked
    val SelectAll: ImageVector get() = TablerNavigation.SelectAll
    val Info: ImageVector get() = TablerNavigation.Info
    val Tasks: ImageVector get() = TablerNavigation.Tasks
    val Download: ImageVector get() = TablerNavigation.Download
    val Theme: ImageVector get() = TablerNavigation.Theme
    val InvertSelection: ImageVector get() = TablerNavigation.InvertSelection
    val SelectSameType: ImageVector get() = TablerNavigation.SelectSameType
    val Refresh: ImageVector get() = TablerNavigation.Refresh
    val Replay: ImageVector get() = TablerFiles.Replay
    val Stop: ImageVector get() = TablerFiles.Stop
    val Restore: ImageVector get() = TablerFiles.Restore
    val Undo: ImageVector get() = TablerFiles.Undo
    val Redo: ImageVector get() = TablerFiles.Redo
    val OpenExternally: ImageVector get() = TablerFiles.OpenExternally
    val Preview: ImageVector get() = TablerFiles.Preview
    val WrapText: ImageVector get() = TablerFiles.WrapText
    val DualPane: ImageVector get() = TablerFiles.DualPane
    val Sort: ImageVector get() = TablerFiles.Sort
    val Copy: ImageVector get() = TablerFiles.Copy
    val Cut: ImageVector get() = TablerFiles.Cut
    val Paste: ImageVector get() = TablerFiles.Paste
    val Move: ImageVector get() = TablerFiles.Move
    val Rename: ImageVector get() = TablerFiles.Rename
    val Favourite: ImageVector get() = TablerFiles.Favourite
    val Extract: ImageVector get() = TablerFiles.Extract
    val Share: ImageVector get() = TablerFiles.Share
    val Trash: ImageVector get() = TablerFiles.Trash
    val Cleanup: ImageVector get() = TablerFiles.Cleanup
    val Bubble: ImageVector get() = TablerFiles.Bubble
    val Image: ImageVector get() = TablerFiles.Image
    val Video: ImageVector get() = TablerFiles.Video
    val Camera: ImageVector get() = TablerFiles.Camera
    val Audio: ImageVector get() = TablerFiles.Audio
    val AudioFile: ImageVector get() = TablerActions.AudioFile
    val Document: ImageVector get() = TablerActions.Document
    val Pdf: ImageVector get() = TablerActions.Pdf
    val Text: ImageVector get() = TablerActions.Text
    val Ebook: ImageVector get() = TablerActions.Ebook
    val Font: ImageVector get() = TablerActions.Font
    val Spreadsheet: ImageVector get() = TablerActions.Spreadsheet
    val Presentation: ImageVector get() = TablerActions.Presentation
    val GenericFile: ImageVector get() = TablerActions.GenericFile
    val Archive: ImageVector get() = TablerActions.Archive
    val Apk: ImageVector get() = TablerActions.Apk
    val Lock: ImageVector get() = TablerActions.Lock
    val Security: ImageVector get() = TablerActions.Security
    val Hide: ImageVector get() = TablerActions.Hide
    val Visible: ImageVector get() = TablerActions.Visible
    val Hidden: ImageVector get() = TablerActions.Hidden
    val Play: ImageVector get() = TablerActions.Play
    val Pause: ImageVector get() = TablerActions.Pause
    val Rewind10: ImageVector get() = TablerActions.Rewind10
    val Forward10: ImageVector get() = TablerActions.Forward10
    val Code: ImageVector get() = TablerActions.Code
    val List: ImageVector get() = TablerActions.List
    val Grid: ImageVector get() = TablerActions.Grid
    val TextFile: ImageVector get() = Text
    val UnknownFile: ImageVector get() = GenericFile
}

internal fun tabler(
    name: String,
    autoMirror: Boolean,
    vararg paths: String,
): ImageVector {
    val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror)
    paths.forEach { data ->
        builder.addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}
