package com.devbehindyou.atomicfilemanager.core.designsystem

import android.os.Build
import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AccentResolution
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AccentResolver
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicColorFactory
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicColorRoles
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography

/** Compose view of [AtomicColorRoles]. Screens read colours only through this. */
@Immutable
class AtomicColors(val roles: AtomicColorRoles) {
    val isDark = roles.isDark
    val background = Color(roles.background)
    val surfaceCard = Color(roles.surfaceCard)
    val surfaceInset = Color(roles.surfaceInset)
    val surfaceRaised = Color(roles.surfaceRaised)
    val content = Color(roles.content)
    val contentSecondary = Color(roles.contentSecondary)
    val contentMuted = Color(roles.contentMuted)
    val accent = Color(roles.accent.fill)
    val onAccent = Color(roles.accent.onFill)
    val accentPressed = Color(roles.accent.pressed)
    val accentDeep = Color(roles.accent.deep)
    val accentText = Color(roles.accent.text)
    val borderStrong = Color(roles.borderStrong)
    val borderHair = Color(roles.borderHair)
    val shadow = Color(roles.shadow)
    val error = Color(roles.error.error)
    val onError = Color(roles.error.onError)
    val errorContainer = Color(roles.error.container)
    val onErrorContainer = Color(roles.error.onContainer)
    val meterNormal = Color(roles.meter.normal)
    val meterHigh = Color(roles.meter.high)
    val meterTrack = Color(roles.meter.track)
    val live = Color(roles.meter.live)
}

val LocalAtomicColors = staticCompositionLocalOf { AtomicColors(AtomicColorFactory.light()) }
val LocalAccentResolution = staticCompositionLocalOf { AccentResolver.signal }

/** True when the system asks for no animation; Atomic motion then keeps only short fades. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Entry point for screens: `Atomic.colors.content`, `Atomic.reducedMotion`, … */
object Atomic {
    val colors: AtomicColors
        @Composable @ReadOnlyComposable
        get() = LocalAtomicColors.current

    val accent: AccentResolution
        @Composable @ReadOnlyComposable
        get() = LocalAccentResolution.current

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalReducedMotion.current
}

/**
 * The single app theme. Provides the Atomic roles and a Material 3 theme mapped from them, so
 * Material widgets that have not been rebuilt yet already use ink, paper and the accent.
 *
 * @param wallpaperAccent the WALLPAPER COLOURS setting; only the accent changes, with guard rails.
 */
@Composable
fun AtomicTheme(
    darkTheme: Boolean,
    wallpaperAccent: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val accent =
        remember(wallpaperAccent, context) {
            if (wallpaperAccent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AccentResolver.resolve(
                    lightCandidate = dynamicLightColorScheme(context).primary.toArgb(),
                    darkCandidate = dynamicDarkColorScheme(context).primary.toArgb(),
                )
            } else {
                AccentResolver.signal
            }
        }
    val colors =
        remember(darkTheme, accent) {
            AtomicColors(
                if (darkTheme) AtomicColorFactory.dark(accent.dark) else AtomicColorFactory.light(accent.light),
            )
        }
    val reducedMotion =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    CompositionLocalProvider(
        LocalAtomicColors provides colors,
        LocalAccentResolution provides accent,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(Color(if (darkTheme) accent.light.text else accent.dark.text)),
            typography = AtomicTypography.material,
            shapes = AtomicShape.material,
            content = content,
        )
    }
}

/**
 * Material uses primary for both text and fills. Atomic components can use the brighter brand
 * fill separately because they specify their own foreground.
 */
private fun AtomicColors.toMaterial(inverseAccentText: Color): ColorScheme {
    val primary = accentText
    val onPrimary = if (isDark) background else Color.White
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = surfaceInset,
            onPrimaryContainer = content,
            inversePrimary = inverseAccentText,
            secondary = content,
            onSecondary = background,
            secondaryContainer = surfaceInset,
            onSecondaryContainer = content,
            tertiary = primary,
            onTertiary = onPrimary,
            background = background,
            onBackground = content,
            surface = background,
            onSurface = content,
            surfaceVariant = surfaceInset,
            onSurfaceVariant = contentSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = content,
            inverseOnSurface = background,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = borderStrong,
            outlineVariant = borderHair,
            scrim = Color.Black,
            surfaceContainerLowest = background,
            surfaceContainerLow = surfaceCard,
            surfaceContainer = surfaceCard,
            surfaceContainerHigh = surfaceRaised,
            surfaceContainerHighest = surfaceRaised,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = surfaceInset,
            onPrimaryContainer = content,
            inversePrimary = inverseAccentText,
            secondary = content,
            onSecondary = background,
            secondaryContainer = surfaceInset,
            onSecondaryContainer = content,
            tertiary = primary,
            onTertiary = onPrimary,
            background = background,
            onBackground = content,
            surface = background,
            onSurface = content,
            surfaceVariant = surfaceInset,
            onSurfaceVariant = contentSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = content,
            inverseOnSurface = background,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = borderStrong,
            outlineVariant = borderHair,
            scrim = Color.Black,
            surfaceContainerLowest = surfaceCard,
            surfaceContainerLow = surfaceRaised,
            surfaceContainer = surfaceInset,
            surfaceContainerHigh = surfaceInset,
            surfaceContainerHighest = surfaceInset,
        )
    }
}
