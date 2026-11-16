package com.devbehindyou.atomicfilemanager.core.navigation

/**
 * Screens pushed over the four top-level tabs (ATOMIC_UI_PLAN.md §6). Each route encodes to a
 * short string so the whole stack survives rotation and process death in a saved-state bundle.
 */
sealed interface AtomicRoute {
    fun encode(): String

    /** Private files behind the app lock. */
    data object PrivateFiles : AtomicRoute {
        override fun encode() = PRIVATE
    }

    /** Running, waiting and past file operations. */
    data object Operations : AtomicRoute {
        override fun encode() = OPERATIONS
    }

    /** App version, help and bundled notices. */
    data object About : AtomicRoute {
        override fun encode() = ABOUT
    }

    /** The catalog of tools included with the installed app. */
    data object InAppPlugins : AtomicRoute {
        override fun encode() = PLUGINS
    }

    /** Files opened through this application. */
    data object RecentlyOpened : AtomicRoute {
        override fun encode() = RECENTLY_OPENED
    }

    /** Manage the file and folder shortcuts saved from Browse. */
    data object PinnedLocations : AtomicRoute {
        override fun encode() = PINNED_LOCATIONS
    }

    /** Search across all storage. */
    data object Search : AtomicRoute {
        override fun encode() = SEARCH
    }

    /** What is in the Trash: restore, delete for good, empty. */
    data object Trash : AtomicRoute {
        override fun encode() = TRASH
    }

    /** Launchable apps: open, info, save or share the APK, uninstall. */
    data object Apps : AtomicRoute {
        override fun encode() = APPS
    }

    /** The guarded Wi-Fi share (ALL_IN_ONE_PLAN.md 3.4). */
    data object WifiShare : AtomicRoute {
        override fun encode() = WIFI_SHARE
    }

    /** A category collection; [collection] is a `FileCollection` name. */
    data class Category(
        val collection: String,
    ) : AtomicRoute {
        override fun encode() = "$CATEGORY$SEPARATOR$collection"
    }

    /** Storage analysis rooted at [rootRaw], a raw `FileNodeId`. */
    data class Analysis(
        val rootRaw: String,
    ) : AtomicRoute {
        override fun encode() = "$ANALYSIS$SEPARATOR$rootRaw"
    }

    /** Folder compare and sync (ALL_IN_ONE_PLAN.md 4.1) between two raw `FileNodeId`s. */
    data class FolderCompare(
        val leftRaw: String,
        val rightRaw: String,
    ) : AtomicRoute {
        override fun encode() = "$COMPARE$SEPARATOR$leftRaw$PAIR$rightRaw"
    }

    /** A dedicated tool reached from the Storage tab. */
    data class StorageTool(val page: StorageToolPage) : AtomicRoute {
        override fun encode() = "$STORAGE_TOOL$SEPARATOR${page.name}"
    }

    companion object {
        private const val STORAGE_TOOL = "storage-tool"
        private const val PRIVATE = "private"
        private const val PINNED_LOCATIONS = "pinned-locations"
        private const val OPERATIONS = "operations"
        private const val TRASH = "trash"
        private const val RECENTLY_OPENED = "recently-opened"
        private const val SEARCH = "search"
        private const val ABOUT = "about"
        private const val PLUGINS = "plugins"
        private const val APPS = "apps"
        private const val WIFI_SHARE = "wifi-share"
        private const val CATEGORY = "category"
        private const val ANALYSIS = "analysis"
        private const val COMPARE = "compare"
        private const val SEPARATOR = ':'

        /** Ids contain ':' and '/', never this. */
        private const val PAIR = '\u001F'

        /** Returns null for anything it does not recognise, so a stale bundle never crashes. */
        fun decode(value: String): AtomicRoute? {
            if (value == PRIVATE) return PrivateFiles
            if (value == PINNED_LOCATIONS) return PinnedLocations
            if (value == OPERATIONS) return Operations
            if (value == TRASH) return Trash
            if (value == RECENTLY_OPENED) return RecentlyOpened
            if (value == SEARCH) return Search
            if (value == ABOUT) return About
            if (value == PLUGINS) return InAppPlugins
            if (value == APPS) return Apps
            if (value == WIFI_SHARE) return WifiShare
            val kind = value.substringBefore(SEPARATOR, missingDelimiterValue = "")
            val argument = value.substringAfter(SEPARATOR, missingDelimiterValue = "")
            if (argument.isEmpty()) return null
            return when (kind) {
                CATEGORY -> Category(argument)
                ANALYSIS -> Analysis(argument)
                STORAGE_TOOL -> StorageToolPage.entries.firstOrNull { it.name == argument }?.let(::StorageTool)
                COMPARE -> {
                    val left = argument.substringBefore(PAIR, missingDelimiterValue = "")
                    val right = argument.substringAfter(PAIR, missingDelimiterValue = "")
                    if (left.isEmpty() || right.isEmpty()) null else FolderCompare(left, right)
                }
                else -> null
            }
        }
    }
}

enum class StorageToolPage { CONNECTIONS, FOLDER_COMPARE, STORAGE_ACCESS, SHIZUKU_LABS }

/**
 * Immutable back stack of [AtomicRoute]s. The top entry is the visible screen; an empty stack
 * shows the current tab. Pushing the route already on top is a no-op so a double tap cannot
 * stack two copies of the same screen.
 */
data class RouteStack(
    val entries: List<AtomicRoute> = emptyList(),
) {
    val top: AtomicRoute? get() = entries.lastOrNull()

    val isEmpty: Boolean get() = entries.isEmpty()

    fun push(route: AtomicRoute): RouteStack = if (top == route) this else RouteStack(entries + route)

    fun pop(): RouteStack = if (isEmpty) this else RouteStack(entries.dropLast(1))

    /** Replaces the top entry, for example when a sheet hands over to a full screen. */
    fun replaceTop(route: AtomicRoute): RouteStack = pop().push(route)

    fun encode(): List<String> = entries.map(AtomicRoute::encode)

    companion object {
        fun decode(values: List<String>): RouteStack = RouteStack(values.mapNotNull(AtomicRoute::decode))
    }
}
