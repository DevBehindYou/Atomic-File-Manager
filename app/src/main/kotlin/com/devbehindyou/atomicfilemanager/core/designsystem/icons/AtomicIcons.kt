package com.devbehindyou.atomicfilemanager.core.designsystem.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's one icon set (spec §7.1: outlined only, one style per surface). Screens reference
 * these semantic names, never an icon library directly, so the set can later move to Material
 * Symbols vector drawables in this one file.
 */
object AtomicIcons {
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack
    val Forward: ImageVector = Icons.AutoMirrored.Outlined.ArrowForward
    val Home: ImageVector = Icons.Outlined.Home
    val Files: ImageVector = Icons.Outlined.Folder
    val FolderOpen: ImageVector = Icons.Outlined.FolderOpen
    val NewFolder: ImageVector = Icons.Outlined.CreateNewFolder
    val Storage: ImageVector = Icons.Outlined.Storage
    val Network: ImageVector = Icons.Outlined.Dns
    val Settings: ImageVector = Icons.Outlined.Settings
    val Search: ImageVector = Icons.Outlined.Search
    val More: ImageVector = Icons.Outlined.MoreVert
    val MoreHorizontal: ImageVector = Icons.Outlined.MoreHoriz
    val Add: ImageVector = Icons.Outlined.Add
    val Close: ImageVector = Icons.Outlined.Close
    val Check: ImageVector = Icons.Outlined.Check
    val CheckCircle: ImageVector = Icons.Outlined.CheckCircle
    val Unchecked: ImageVector = Icons.Outlined.RadioButtonUnchecked
    val SelectAll: ImageVector = Icons.Outlined.SelectAll
    val Info: ImageVector = Icons.Outlined.Info
    val Tasks: ImageVector = Icons.Outlined.SyncAlt
    val Download: ImageVector = Icons.Outlined.FileDownload
    val Theme: ImageVector = Icons.Outlined.Palette
    val InvertSelection: ImageVector = Icons.Outlined.FlipToBack
    val SelectSameType: ImageVector = Icons.Outlined.FilterAlt
    val Refresh: ImageVector = Icons.Outlined.Refresh
    val Replay: ImageVector = Icons.Outlined.Replay
    val Restore: ImageVector = Icons.Outlined.Restore
    val Undo: ImageVector = Icons.AutoMirrored.Outlined.Undo
    val Redo: ImageVector = Icons.AutoMirrored.Outlined.Redo
    val OpenExternally: ImageVector = Icons.AutoMirrored.Outlined.OpenInNew
    val Preview: ImageVector = Icons.Outlined.Preview
    val WrapText: ImageVector = Icons.AutoMirrored.Outlined.WrapText
    val DualPane: ImageVector = Icons.Outlined.VerticalSplit
    val Sort: ImageVector = Icons.AutoMirrored.Outlined.Sort

    // File operations
    val Copy: ImageVector = Icons.Outlined.ContentCopy
    val Cut: ImageVector = Icons.Outlined.ContentCut
    val Paste: ImageVector = Icons.Outlined.ContentPaste
    val Move: ImageVector = Icons.AutoMirrored.Outlined.DriveFileMove
    val Rename: ImageVector = Icons.Outlined.DriveFileRenameOutline
    val Favourite: ImageVector = Icons.Outlined.StarBorder
    val Extract: ImageVector = Icons.Outlined.Unarchive
    val Share: ImageVector = Icons.Outlined.Share
    val Trash: ImageVector = Icons.Outlined.Delete
    val Cleanup: ImageVector = Icons.Outlined.CleaningServices
    val Bubble: ImageVector = Icons.Outlined.AllInbox

    // File types
    val Image: ImageVector = Icons.Outlined.Image
    val Video: ImageVector = Icons.Outlined.Movie
    val Camera: ImageVector = Icons.Outlined.Videocam
    val Audio: ImageVector = Icons.Outlined.MusicNote
    val AudioFile: ImageVector = Icons.Outlined.AudioFile
    val Document: ImageVector = Icons.Outlined.Description
    val Pdf: ImageVector = Icons.Outlined.PictureAsPdf
    val Text: ImageVector = Icons.AutoMirrored.Outlined.TextSnippet
    val TextFile: ImageVector = Text
    val Ebook: ImageVector = Icons.AutoMirrored.Outlined.MenuBook
    val Font: ImageVector = Icons.Outlined.FontDownload
    val Spreadsheet: ImageVector = Icons.Outlined.TableChart
    val Presentation: ImageVector = Icons.Outlined.Slideshow
    val GenericFile: ImageVector = Icons.AutoMirrored.Outlined.InsertDriveFile
    val UnknownFile: ImageVector = GenericFile
    val Archive: ImageVector = Icons.Outlined.FolderZip
    val Apk: ImageVector = Icons.Outlined.Android

    // Privacy
    val Lock: ImageVector = Icons.Outlined.Lock
    val Security: ImageVector = Icons.Outlined.Security
    val Hide: ImageVector = Icons.Outlined.HideImage
    val Visible: ImageVector = Icons.Outlined.Visibility
    val Hidden: ImageVector = Icons.Outlined.VisibilityOff

    // Media
    val Play: ImageVector = Icons.Outlined.PlayArrow
    val Pause: ImageVector = Icons.Outlined.Pause
    val Rewind10: ImageVector = Icons.Outlined.Replay10
    val Forward10: ImageVector = Icons.Outlined.Forward10
    val Code: ImageVector = Icons.Outlined.Code
}
