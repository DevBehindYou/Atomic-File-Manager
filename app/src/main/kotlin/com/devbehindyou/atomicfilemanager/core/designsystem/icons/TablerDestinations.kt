package com.devbehindyou.atomicfilemanager.core.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector

/** Bundled destination icons from the pinned MIT Tabler source. */
internal object TablerDestinations {
    val Sun: ImageVector by lazy {
        tabler(
            "sun",
            autoMirror = false,
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
            "M3 12h1m8 -9v1m8 8h1m-9 8v1m-6.4 -15.4l.7 .7m12.1 -.7l-.7 .7m0 11.4l.7 .7m-12.1 -.7l-.7 .7",
        )
    }
    val Plug: ImageVector by lazy {
        tabler(
            "plug",
            autoMirror = false,
            "M9.785 6l8.215 8.215l-2.054 2.054a5.81 5.81 0 1 1 -8.215 -8.215l2.054 -2.054",
            "M4 20l3.5 -3.5",
            "M15 4l-3.5 3.5",
            "M20 9l-3.5 3.5",
        )
    }
    val ChevronRight: ImageVector by lazy {
        tabler(
            "chevron-right",
            autoMirror = true,
            "M9 6l6 6l-6 6",
        )
    }
    val Cloud: ImageVector by lazy {
        tabler(
            "cloud",
            autoMirror = false,
            "M6.657 18c-2.572 0 -4.657 -2.007 -4.657 -4.483c0 -2.475 2.085 -4.482 4.657 -4.482c.393 " +
                "-1.762 1.794 -3.2 3.675 -3.773c1.88 -.572 3.956 -.193 5.444 1c1.488 1.19 2.162 3.007 1.77 " +
                "4.769h.99c1.913 0 3.464 1.56 3.464 3.486c0 1.927 -1.551 3.487 -3.465 3.487h-11.878",
        )
    }
}
