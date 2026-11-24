package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

/** Where the accent came from, so Settings can explain a fallback. */
enum class AccentSource { SIGNAL, WALLPAPER, WALLPAPER_REJECTED }

enum class AccentRejection { TOO_GREY, RESERVED_HUE, TOO_LOW_CONTRAST }

data class AccentResolution(
    val source: AccentSource,
    val light: AccentSet,
    val dark: AccentSet,
    val rejection: AccentRejection? = null,
)

/**
 * Guard rails for the WALLPAPER COLOURS setting (ATOMIC_UI_PLAN.md §4.5, design spec §15):
 * only the accent changes; it must read on paper and carry white text (light), have a readable
 * variant on ink (dark), and must not look like red, orange or plum, which already mean error,
 * nearly full and low. Darker or lighter tones of the same hue are tried before falling back
 * to Atomic Orange.
 */
object AccentResolver {
    private const val MIN_TEXT_CONTRAST = 4.5
    private const val MIN_SATURATION = 0.15f
    private const val TONE_STEP = 0.05f
    private const val MAX_TONE_SHIFT = 0.8f
    private const val PRESSED_DARKEN = 0.15f
    private const val DEEP_DARKEN = 0.45f

    val signal =
        AccentResolution(
            source = AccentSource.SIGNAL,
            light = AtomicColorFactory.signalLight,
            dark = AtomicColorFactory.signalDark,
        )

    /** Hues reserved for meaning: red and orange (error, nearly full) and plum/pink (low). */
    fun isReservedHue(hue: Float): Boolean = hue < 45f || hue >= 300f

    /**
     * @param lightCandidate the wallpaper scheme's light primary.
     * @param darkCandidate the wallpaper scheme's dark primary (usually a lighter tone).
     */
    fun resolve(
        lightCandidate: Int,
        darkCandidate: Int,
    ): AccentResolution {
        val (hue, saturation) = ColorMath.hueSaturation(lightCandidate)
        val rejection =
            when {
                saturation < MIN_SATURATION -> AccentRejection.TOO_GREY
                isReservedHue(hue) -> AccentRejection.RESERVED_HUE
                else -> null
            }
        if (rejection != null) return rejected(rejection)

        val fill = toneUntil(lightCandidate, AtomicPalette.BLACK) { passesLight(it) }
        val textOnInk =
            toneUntil(darkCandidate, AtomicPalette.WHITE) {
                ColorMath.contrast(it, AtomicPalette.STONE) >= MIN_TEXT_CONTRAST
            }
        if (fill == null || textOnInk == null) return rejected(AccentRejection.TOO_LOW_CONTRAST)

        val light =
            AccentSet(
                fill = fill,
                onFill = AtomicPalette.WHITE,
                pressed = ColorMath.mix(fill, AtomicPalette.BLACK, PRESSED_DARKEN),
                deep = ColorMath.mix(fill, AtomicPalette.BLACK, DEEP_DARKEN),
                text = fill,
            )
        return AccentResolution(AccentSource.WALLPAPER, light, light.copy(text = textOnInk))
    }

    fun passesLight(fill: Int): Boolean =
        ColorMath.contrast(AtomicPalette.WHITE, fill) >= MIN_TEXT_CONTRAST &&
            ColorMath.contrast(fill, AtomicPalette.SURFACE) >= MIN_TEXT_CONTRAST

    private fun rejected(reason: AccentRejection) =
        signal.copy(
            source = AccentSource.WALLPAPER_REJECTED,
            rejection = reason,
        )

    /** Moves [start] towards [target] in small steps until [ok]; null if it never passes. */
    private inline fun toneUntil(
        start: Int,
        target: Int,
        ok: (Int) -> Boolean,
    ): Int? {
        var shift = 0f
        while (shift <= MAX_TONE_SHIFT + 1e-4f) {
            val candidate = ColorMath.mix(start, target, shift)
            if (ok(candidate)) return candidate
            shift += TONE_STEP
        }
        return null
    }
}
