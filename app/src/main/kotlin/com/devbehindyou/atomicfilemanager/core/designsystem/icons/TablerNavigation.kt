package com.devbehindyou.atomicfilemanager.core.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector

/** Pinned bundled Tabler vector data; see tabler-icons-manifest.json. */
internal object TablerNavigation {
    val Back: ImageVector by lazy {
        tabler(
            "arrow-left",
            autoMirror = true,
            "M5 12l14 0",
            "M5 12l6 6",
            "M5 12l6 -6",
        )
    }
    val Forward: ImageVector by lazy {
        tabler(
            "arrow-right",
            autoMirror = true,
            "M5 12l14 0",
            "M13 18l6 -6",
            "M13 6l6 6",
        )
    }
    val Home: ImageVector by lazy {
        tabler(
            "home",
            autoMirror = false,
            "M5 12l-2 0l9 -9l9 9l-2 0",
            "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7",
            "M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6",
        )
    }
    val Files: ImageVector by lazy {
        tabler(
            "folder",
            autoMirror = false,
            "M5 4h4l3 3h7a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2",
        )
    }
    val FolderOpen: ImageVector by lazy {
        tabler(
            "folder-open",
            autoMirror = false,
            "M5 19l2.757 -7.351a1 1 0 0 1 .936 -.649h12.307a1 1 0 0 1 .986 1.164l-.996 5.211a2 2 " +
                "0 0 1 -1.964 1.625h-14.026a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2h4l3 3h7a2 2 0 0 1 2 " +
                "2v2",
        )
    }
    val NewFolder: ImageVector by lazy {
        tabler(
            "folder-plus",
            autoMirror = false,
            "M12 19h-7a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2h4l3 3h7a2 2 0 0 1 2 2v3.5",
            "M16 19h6",
            "M19 16v6",
        )
    }
    val Storage: ImageVector by lazy {
        tabler(
            "server",
            autoMirror = false,
            "M3 7a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v2a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-2",
            "M3 15a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v2a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3l0 -2",
            "M7 8l0 .01",
            "M7 16l0 .01",
        )
    }
    val Network: ImageVector by lazy {
        tabler(
            "network", autoMirror = false,
            "M6 9a6 6 0 1 0 12 0a6 6 0 0 0 -12 0",
            "M12 3c1.333 .333 2 2.333 2 6s-.667 5.667 -2 6",
            "M12 3c-1.333 .333 -2 2.333 -2 6s.667 5.667 2 6",
            "M6 9h12",
            "M3 20h7",
            "M14 20h7",
            "M10 20a2 2 0 1 0 4 0a2 2 0 0 0 -4 0",
            "M12 15v3",
        )
    }
    val Settings: ImageVector by lazy {
        tabler(
            "settings",
            autoMirror = false,
            "M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 " +
                "-.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 " +
                "3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 " +
                "0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 " +
                "-1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 " +
                "-.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 " +
                "2.37 -2.37c1 .608 2.296 .07 2.572 -1.065",
            "M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0",
        )
    }
    val Search: ImageVector by lazy {
        tabler(
            "search",
            autoMirror = false,
            "M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0",
            "M21 21l-6 -6",
        )
    }
    val More: ImageVector by lazy {
        tabler(
            "dots-vertical",
            autoMirror = false,
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        )
    }
    val MoreHorizontal: ImageVector by lazy {
        tabler(
            "dots",
            autoMirror = false,
            "M4 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M18 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        )
    }
    val Add: ImageVector by lazy {
        tabler(
            "plus",
            autoMirror = false,
            "M12 5l0 14",
            "M5 12l14 0",
        )
    }
    val Close: ImageVector by lazy {
        tabler(
            "x",
            autoMirror = false,
            "M18 6l-12 12",
            "M6 6l12 12",
        )
    }
    val Check: ImageVector by lazy {
        tabler(
            "check",
            autoMirror = false,
            "M5 12l5 5l10 -10",
        )
    }
    val CheckCircle: ImageVector by lazy {
        tabler(
            "circle-check",
            autoMirror = false,
            "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
            "M9 12l2 2l4 -4",
        )
    }
    val Unchecked: ImageVector by lazy {
        tabler(
            "circle",
            autoMirror = false,
            "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
        )
    }
    val SelectAll: ImageVector by lazy {
        tabler(
            "select-all", autoMirror = false,
            "M8 9a1 1 0 0 1 1 -1h6a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1l0 -6",
            "M12 20v.01",
            "M16 20v.01",
            "M8 20v.01",
            "M4 20v.01",
            "M4 16v.01",
            "M4 12v.01",
            "M4 8v.01",
            "M4 4v.01",
            "M8 4v.01",
            "M12 4v.01",
            "M16 4v.01",
            "M20 4v.01",
            "M20 8v.01",
            "M20 12v.01",
            "M20 16v.01",
            "M20 20v.01",
        )
    }
    val Info: ImageVector by lazy {
        tabler(
            "info-circle",
            autoMirror = false,
            "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0",
            "M12 9h.01",
            "M11 12h1v4h1",
        )
    }
    val Tasks: ImageVector by lazy {
        tabler(
            "arrows-exchange",
            autoMirror = false,
            "M7 10h14l-4 -4",
            "M17 14h-14l4 4",
        )
    }
    val Download: ImageVector by lazy {
        tabler(
            "download",
            autoMirror = false,
            "M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-2",
            "M7 11l5 5l5 -5",
            "M12 4l0 12",
        )
    }
    val Theme: ImageVector by lazy {
        tabler(
            "palette",
            autoMirror = false,
            "M12 21a9 9 0 0 1 0 -18c4.97 0 9 3.582 9 8c0 1.06 -.474 2.078 -1.318 2.828c-.844 .75 " +
                "-1.989 1.172 -3.182 1.172h-2.5a2 2 0 0 0 -1 3.75a1.3 1.3 0 0 1 -1 2.25",
            "M7.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11.5 7.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M15.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        )
    }
    val InvertSelection: ImageVector by lazy {
        tabler(
            "select",
            autoMirror = false,
            "M3 5a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14",
            "M9 11l3 3l3 -3",
        )
    }
    val SelectSameType: ImageVector by lazy {
        tabler(
            "filter",
            autoMirror = false,
            "M4 4h16v2.172a2 2 0 0 1 -.586 1.414l-4.414 4.414v7l-6 2v-8.5l-4.48 -4.928a2 2 0 0 1 " +
                "-.52 -1.345v-2.227",
        )
    }
    val Refresh: ImageVector by lazy {
        tabler(
            "refresh",
            autoMirror = false,
            "M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4",
            "M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4",
        )
    }
}
