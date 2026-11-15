package com.devbehindyou.atomicfilemanager.core.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector

/** Pinned bundled Tabler vector data; see tabler-icons-manifest.json. */
internal object TablerFiles {
    val Replay: ImageVector by lazy {
        tabler(
            "rotate-clockwise",
            autoMirror = false,
            "M4.05 11a8 8 0 1 1 .5 4m-.5 5v-5h5",
        )
    }
    val Stop: ImageVector by lazy {
        tabler(
            "player-stop",
            autoMirror = false,
            "M5 7a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2l0 -10",
        )
    }
    val Restore: ImageVector by lazy {
        tabler(
            "history",
            autoMirror = false,
            "M12 8l0 4l2 2",
            "M3.05 11a9 9 0 1 1 .5 4m-.5 5v-5h5",
        )
    }
    val Undo: ImageVector by lazy {
        tabler(
            "arrow-back-up",
            autoMirror = true,
            "M9 14l-4 -4l4 -4",
            "M5 10h11a4 4 0 1 1 0 8h-1",
        )
    }
    val Redo: ImageVector by lazy {
        tabler(
            "arrow-forward-up",
            autoMirror = true,
            "M15 14l4 -4l-4 -4",
            "M19 10h-11a4 4 0 1 0 0 8h1",
        )
    }
    val OpenExternally: ImageVector by lazy {
        tabler(
            "external-link",
            autoMirror = true,
            "M12 6h-6a2 2 0 0 0 -2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-6",
            "M11 13l9 -9",
            "M15 4h5v5",
        )
    }
    val Preview: ImageVector by lazy {
        tabler(
            "eye",
            autoMirror = false,
            "M10 12a2 2 0 1 0 4 0a2 2 0 0 0 -4 0",
            "M21 12c-2.4 4 -5.4 6 -9 6c-3.6 0 -6.6 -2 -9 -6c2.4 -4 5.4 -6 9 -6c3.6 0 6.6 2 9 6",
        )
    }
    val WrapText: ImageVector by lazy {
        tabler(
            "text-wrap",
            autoMirror = true,
            "M4 6l16 0",
            "M4 18l5 0",
            "M4 12h13a3 3 0 0 1 0 6h-4l2 -2m0 4l-2 -2",
        )
    }
    val DualPane: ImageVector by lazy {
        tabler(
            "layout-columns",
            autoMirror = false,
            "M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2l0 -12",
            "M12 4l0 16",
        )
    }
    val Sort: ImageVector by lazy {
        tabler(
            "sort-descending",
            autoMirror = false,
            "M4 6l9 0",
            "M4 12l7 0",
            "M4 18l7 0",
            "M15 15l3 3l3 -3",
            "M18 6l0 12",
        )
    }
    val Copy: ImageVector by lazy {
        tabler(
            "copy",
            autoMirror = false,
            "M7 9.667a2.667 2.667 0 0 1 2.667 -2.667h8.666a2.667 2.667 0 0 1 2.667 " +
                "2.667v8.666a2.667 2.667 0 0 1 -2.667 2.667h-8.666a2.667 2.667 0 0 1 -2.667 -2.667l0 " +
                "-8.666",
            "M4.012 16.737a2.005 2.005 0 0 1 -1.012 -1.737v-10c0 -1.1 .9 -2 2 -2h10c.75 0 1.158 .385 1.5 1",
        )
    }
    val Cut: ImageVector by lazy {
        tabler(
            "cut",
            autoMirror = false,
            "M4 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M14 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M9.15 14.85l8.85 -10.85",
            "M6 4l8.85 10.85",
        )
    }
    val Paste: ImageVector by lazy {
        tabler(
            "clipboard",
            autoMirror = false,
            "M9 5h-2a2 2 0 0 0 -2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-12a2 2 0 0 0 -2 -2h-2",
            "M9 5a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2a2 2 0 0 1 -2 2h-2a2 2 0 0 1 -2 -2",
        )
    }
    val Move: ImageVector by lazy {
        tabler(
            "folder-symlink",
            autoMirror = true,
            "M3 21v-4a3 3 0 0 1 3 -3h5",
            "M8 17l3 -3l-3 -3",
            "M3 11v-5a2 2 0 0 1 2 -2h4l3 3h7a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8",
        )
    }
    val Rename: ImageVector by lazy {
        tabler(
            "edit",
            autoMirror = false,
            "M7 7h-1a2 2 0 0 0 -2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2 -2v-1",
            "M20.385 6.585a2.1 2.1 0 0 0 -2.97 -2.97l-8.415 8.385v3h3l8.385 -8.415",
            "M16 5l3 3",
        )
    }
    val Favourite: ImageVector by lazy {
        tabler(
            "star",
            autoMirror = false,
            "M12 17.75l-6.172 3.245l1.179 -6.873l-5 -4.867l6.9 -1l3.086 -6.253l3.086 6.253l6.9 " +
                "1l-5 4.867l1.179 6.873l-6.158 -3.245",
        )
    }
    val Extract: ImageVector by lazy {
        tabler(
            "archive",
            autoMirror = false,
            "M3 6a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2",
            "M5 8v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-10",
            "M10 12l4 0",
        )
    }
    val Share: ImageVector by lazy {
        tabler(
            "share",
            autoMirror = false,
            "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M15 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M15 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M8.7 10.7l6.6 -3.4",
            "M8.7 13.3l6.6 3.4",
        )
    }
    val Trash: ImageVector by lazy {
        tabler(
            "trash",
            autoMirror = false,
            "M4 7l16 0",
            "M10 11l0 6",
            "M14 11l0 6",
            "M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12",
            "M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3",
        )
    }
    val Cleanup: ImageVector by lazy {
        tabler(
            "brush",
            autoMirror = false,
            "M3 21v-4a4 4 0 1 1 4 4h-4",
            "M21 3a16 16 0 0 0 -12.8 10.2",
            "M21 3a16 16 0 0 1 -10.2 12.8",
            "M10.6 9a9 9 0 0 1 4.4 4.4",
        )
    }
    val Bubble: ImageVector by lazy {
        tabler(
            "layers-intersect",
            autoMirror = false,
            "M8 6a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2l0 -8",
            "M4 10a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2l0 -8",
        )
    }
    val Image: ImageVector by lazy {
        tabler(
            "photo",
            autoMirror = false,
            "M15 8h.01",
            "M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12",
            "M3 16l5 -5c.928 -.893 2.072 -.893 3 0l5 5",
            "M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3",
        )
    }
    val Video: ImageVector by lazy {
        tabler(
            "movie", autoMirror = false,
            "M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2l0 -12",
            "M8 4l0 16",
            "M16 4l0 16",
            "M4 8l4 0",
            "M4 16l4 0",
            "M4 12l16 0",
            "M16 8l4 0",
            "M16 16l4 0",
        )
    }
    val Camera: ImageVector by lazy {
        tabler(
            "video",
            autoMirror = false,
            "M15 10l4.553 -2.276a1 1 0 0 1 1.447 .894v6.764a1 1 0 0 1 -1.447 .894l-4.553 -2.276v-4",
            "M3 8a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2l0 -8",
        )
    }
    val Audio: ImageVector by lazy {
        tabler(
            "music",
            autoMirror = false,
            "M3 17a3 3 0 1 0 6 0a3 3 0 0 0 -6 0",
            "M13 17a3 3 0 1 0 6 0a3 3 0 0 0 -6 0",
            "M9 17v-13h10v13",
            "M9 8h10",
        )
    }
}
