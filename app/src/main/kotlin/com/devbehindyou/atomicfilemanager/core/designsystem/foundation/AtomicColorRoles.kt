package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

/**
 * Raw Atomic palette (docs/design/ATOMIC_DESIGN_SYSTEM.md §3) as ARGB ints.
 * Screens never use these directly; they read semantic roles from [AtomicColorRoles].
 */
internal object AtomicPalette {
    const val INK = 0xFF1B1B1B.toInt()
    const val PAPER = 0xFFF6F6F6.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF010101.toInt()
    const val SURFACE = 0xFFE5E5E5.toInt()
    const val SIGNAL = 0xFFFE6902.toInt()
    const val SIGNAL_HOVER = 0xFFED5F00.toInt()
    const val SIGNAL_DEEP = 0xFF974300.toInt()
    const val SLATE = 0xFF5B5959.toInt()
    const val LINE = 0xFFC5C3C3.toInt()
    const val CHARCOAL = 0xFF262626.toInt()
    const val STONE = 0xFF393737.toInt()
    const val DARK_LINE = 0xFF555353.toInt()
    const val DARK_METADATA = 0xFFB9B9B9.toInt()
    const val ERROR = 0xFFBA1A1A.toInt()
    const val ERROR_CONTAINER = 0xFFFFDAD6.toInt()
    const val ON_ERROR_CONTAINER = 0xFF93000A.toInt()

    // Dark error family: Material's baseline dark error tones (spec §13.5 keeps error as Material's palette).
    const val ERROR_DARK = 0xFFFFB4AB.toInt()
    const val ON_ERROR_DARK = 0xFF690005.toInt()
    const val ERROR_CONTAINER_DARK = 0xFF93000A.toInt()
    const val ON_ERROR_CONTAINER_DARK = 0xFFFFDAD6.toInt()
    const val ENERGY_HIGH = 0xFFB51A0B.toInt()
    const val LIVE = 0xFF3DDC84.toInt()
}

/** The accent family. Only this group changes when wallpaper colours are on (ATOMIC_UI_PLAN.md §4.5). */
data class AccentSet(
    /** Fill for primary buttons, selected borders and meters, paired with [onFill]. */
    val fill: Int,
    val onFill: Int,
    /** Pressed/hover fill, about 15 % darker. */
    val pressed: Int,
    /** Offset-shadow colour under featured items (the spec's signal-deep role). */
    val deep: Int,
    /** Accent used as text or icon colour on this theme's background. */
    val text: Int,
)

/** Semantic colour roles. Pure Kotlin so contrast can be unit-tested; [AtomicColors] wraps them for Compose. */
data class AtomicColorRoles(
    val isDark: Boolean,
    val background: Int,
    /** Things the user owns or acts on (file cards, rows on light). */
    val surfaceCard: Int,
    /** Groups and tools (settings groups, tool tiles). */
    val surfaceInset: Int,
    val surfaceRaised: Int,
    val content: Int,
    val contentSecondary: Int,
    val contentMuted: Int,
    val accent: AccentSet,
    /** Borders the user must see: controls, cards, rules. */
    val borderStrong: Int,
    /** Decorative hairlines only. */
    val borderHair: Int,
    val shadow: Int,
    val error: ErrorSet,
    val meter: MeterSet,
)

data class ErrorSet(val error: Int, val onError: Int, val container: Int, val onContainer: Int)

/** Storage meter (the spec's energy bar): normal fill is the accent, ≥ 80 % used turns [high]. */
data class MeterSet(val normal: Int, val high: Int, val track: Int, val live: Int)

object AtomicColorFactory {
    val signalLight =
        AccentSet(
            fill = AtomicPalette.SIGNAL,
            onFill = AtomicPalette.BLACK,
            pressed = AtomicPalette.SIGNAL_HOVER,
            deep = AtomicPalette.SIGNAL_DEEP,
            text = AtomicPalette.SIGNAL_DEEP,
        )

    val signalDark = signalLight.copy(text = AtomicPalette.SIGNAL)

    fun light(accent: AccentSet = signalLight): AtomicColorRoles =
        AtomicColorRoles(
            isDark = false,
            background = AtomicPalette.PAPER,
            surfaceCard = AtomicPalette.WHITE,
            surfaceInset = AtomicPalette.SURFACE,
            surfaceRaised = AtomicPalette.WHITE,
            content = AtomicPalette.INK,
            contentSecondary = AtomicPalette.SLATE,
            contentMuted = AtomicPalette.SLATE,
            accent = accent,
            borderStrong = AtomicPalette.INK,
            borderHair = AtomicPalette.LINE,
            shadow = AtomicPalette.INK,
            error =
                ErrorSet(
                    AtomicPalette.ERROR,
                    AtomicPalette.WHITE,
                    AtomicPalette.ERROR_CONTAINER,
                    AtomicPalette.ON_ERROR_CONTAINER,
                ),
            meter = MeterSet(accent.text, AtomicPalette.ENERGY_HIGH, AtomicPalette.SURFACE, AtomicPalette.LIVE),
        )

    fun dark(accent: AccentSet = signalDark): AtomicColorRoles =
        AtomicColorRoles(
            isDark = true,
            background = AtomicPalette.BLACK,
            surfaceCard = AtomicPalette.INK,
            surfaceInset = AtomicPalette.CHARCOAL,
            surfaceRaised = AtomicPalette.CHARCOAL,
            content = AtomicPalette.PAPER,
            contentSecondary = AtomicPalette.DARK_METADATA,
            contentMuted = AtomicPalette.DARK_METADATA,
            accent = accent,
            borderStrong = AtomicPalette.DARK_METADATA,
            borderHair = AtomicPalette.DARK_LINE,
            shadow = AtomicPalette.BLACK,
            error =
                ErrorSet(
                    AtomicPalette.ERROR_DARK,
                    AtomicPalette.ON_ERROR_DARK,
                    AtomicPalette.ERROR_CONTAINER_DARK,
                    AtomicPalette.ON_ERROR_CONTAINER_DARK,
                ),
            meter = MeterSet(accent.text, AtomicPalette.ERROR_DARK, AtomicPalette.STONE, AtomicPalette.LIVE),
        )
}
