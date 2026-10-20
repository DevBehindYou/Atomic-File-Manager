package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale (spec §5.1); build on these steps only. */
object AtomicSpacing {
    val s2 = 2.dp
    val s4 = 4.dp
    val s6 = 6.dp
    val s8 = 8.dp
    val s10 = 10.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s22 = 22.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s44 = 44.dp
    val s56 = 56.dp
    val s74 = 74.dp

    /** Screen side margin in the app (spec §5.3). */
    val screenMargin = s16
}

/** Corner radii (spec §6.1): nearly square; pills and sheet tops are the only round shapes. */
object AtomicShape {
    val radiusXs = 3.dp
    val radiusSm = 4.dp
    val radiusMd = 6.dp
    val radiusLg = 8.dp
    val sheetTopRadius = 28.dp

    val xs = RoundedCornerShape(radiusXs)
    val sm = RoundedCornerShape(radiusSm)
    val md = RoundedCornerShape(radiusMd)
    val lg = RoundedCornerShape(radiusLg)
    val pill = RoundedCornerShape(percent = 50)
    val sheetTop = RoundedCornerShape(topStart = sheetTopRadius, topEnd = sheetTopRadius)

    /** Material shapes for widgets not yet rebuilt: everything 4 dp, sheets 28 dp. */
    val material =
        Shapes(
            extraSmall = sm,
            small = sm,
            medium = sm,
            large = md,
            extraLarge = RoundedCornerShape(sheetTopRadius),
        )
}

/** Border widths (spec §6.2); colours come from [AtomicColorRoles]. */
object AtomicBorder {
    val hair = 1.dp
    val rule = 1.dp
    val structure = 1.5.dp
    val control = 2.dp
    val selected = 2.dp
    val danger = 2.dp
    val priority = 4.dp
}

/** Hard offset shadows (spec §6.3). No blur anywhere: draw with [hardShadow][com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.hardShadow]. */
object AtomicElevation {
    val level1 = 2.dp
    val level2 = 3.dp
    val level3 = 4.dp
    val level4 = 5.dp
    val level5 = 6.dp
    val level6 = 8.dp
}

/** Component and touch sizes (spec §5.3, §7.2, §9). */
object AtomicSize {
    val touchTarget = 48.dp
    val iconSmall = 20.dp
    val icon = 24.dp
    val iconTile = 40.dp
    val backButton = 36.dp
    val chipHeight = 32.dp
    val buttonPrimary = 52.dp
    val buttonSecondary = 44.dp
    val headerContent = 56.dp
    val meterHeight = 8.dp
    val popupMaxWidth = 360.dp
    val popupMaxHeight = 480.dp
    val fileGridCellMin = 144.dp
    val fileGridCellHeight = 168.dp
    val gridThumbnail = 64.dp
}

enum class AtomicWidthClass { COMPACT, MEDIUM, EXPANDED }

/** One responsive model for the whole app (ATOMIC_UI_PLAN.md §8). */
object AtomicBreakpoints {
    val medium = 600.dp
    val expanded = 840.dp

    fun widthClass(width: Dp): AtomicWidthClass =
        when {
            width >= expanded -> AtomicWidthClass.EXPANDED
            width >= medium -> AtomicWidthClass.MEDIUM
            else -> AtomicWidthClass.COMPACT
        }
}

/**
 * Motion tokens (spec §8): `ease` timing, no springs that overshoot. Gesture-driven motion
 * (drag, fling, Quick Peek) keeps velocity with a critically damped spring, which still never
 * bounces (ATOMIC_UI_PLAN.md §9).
 */
object AtomicMotion {
    const val PRESS_MS = 120
    const val HOVER_MS = 150
    const val TOGGLE_MS = 200
    const val ENTER_MS = 350
    const val REVEAL_MS = 500

    /** CSS `ease`. */
    val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    fun <T> press(): AnimationSpec<T> = tween(PRESS_MS, easing = Ease)

    fun <T> hover(): AnimationSpec<T> = tween(HOVER_MS, easing = Ease)

    fun <T> toggle(): AnimationSpec<T> = tween(TOGGLE_MS, easing = Ease)

    fun <T> enter(): AnimationSpec<T> = tween(ENTER_MS, easing = Ease)

    fun <T> gesture(): AnimationSpec<T> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
}
