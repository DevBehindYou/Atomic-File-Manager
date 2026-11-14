package com.devbehindyou.atomicfilemanager.core.designsystem.atoms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicColors
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicMotion
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicPalette
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.focusRing

/** Button variants (spec §9.1). Use one [Primary] per view; pair it with a [Ghost]. */
enum class AtomicButtonVariant { Primary, Solid, Ghost, GhostOnDark, LightOnAccent, Destructive, Text }

private data class ButtonStyle(
    val fill: Color,
    val content: Color,
    val border: Color?,
    val borderWidth: Dp,
    val shadow: Color?,
    val labelRole: AtomicTextRole,
    val height: Dp,
)

private fun AtomicColors.styleFor(variant: AtomicButtonVariant): ButtonStyle {
    val paper = Color(AtomicPalette.PAPER)
    val ink = Color(AtomicPalette.INK)
    return when (variant) {
        AtomicButtonVariant.Primary ->
            ButtonStyle(
                accent,
                onAccent,
                null,
                0.dp,
                null,
                AtomicTextRole.DisplayButton,
                AtomicSize.buttonPrimary,
            )
        AtomicButtonVariant.Solid ->
            ButtonStyle(
                content,
                background,
                content,
                AtomicBorder.control,
                null,
                AtomicTextRole.DisplayButton,
                AtomicSize.buttonSecondary,
            )
        AtomicButtonVariant.Ghost ->
            ButtonStyle(
                surfaceInset,
                content,
                null,
                0.dp,
                null,
                AtomicTextRole.DisplayButton,
                AtomicSize.buttonSecondary,
            )
        AtomicButtonVariant.GhostOnDark ->
            ButtonStyle(
                Color.Transparent,
                paper,
                paper,
                AtomicBorder.control,
                null,
                AtomicTextRole.DisplayButton,
                AtomicSize.buttonSecondary,
            )
        AtomicButtonVariant.LightOnAccent ->
            ButtonStyle(
                paper,
                ink,
                ink,
                AtomicBorder.control,
                ink,
                AtomicTextRole.DisplayButton,
                AtomicSize.buttonSecondary,
            )
        AtomicButtonVariant.Destructive ->
            ButtonStyle(
                errorContainer,
                onErrorContainer,
                error,
                AtomicBorder.structure,
                null,
                AtomicTextRole.DisplayButton,
                AtomicSize.buttonSecondary,
            )
        AtomicButtonVariant.Text ->
            ButtonStyle(
                Color.Transparent,
                accentText,
                null,
                0.dp,
                null,
                AtomicTextRole.DisplayButton,
                AtomicSize.buttonSecondary,
            )
    }
}

/**
 * Atomic button. Labels are sentence-case strings (rendered uppercase), written as verb + object:
 * "Create folder", "Move to trash". Pressing moves the button onto its hard shadow like a key
 * (spec §6.3); with reduced motion it changes instantly.
 */
@Composable
fun AtomicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AtomicButtonVariant = AtomicButtonVariant.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    contentColor: Color = Color.Unspecified,
) {
    val colors = Atomic.colors
    val base = colors.styleFor(variant)
    // Callers on an inverted surface (snackbar) pass the label colour that reads there.
    val style = if (contentColor == Color.Unspecified) base else base.copy(content = contentColor)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = if (Atomic.reducedMotion) snap() else AtomicMotion.press(),
        label = "buttonPress",
    )
    val shape = AtomicShape.sm
    var surface =
        Modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
            .focusRing(focused, colors.accentText, AtomicShape.radiusSm)
    surface = surface.background(style.fill, shape)
    if (style.border != null) surface = surface.border(style.borderWidth, style.border, shape)

    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = maxOf(style.height, AtomicSize.touchTarget))
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                surface
                    .defaultMinSize(minHeight = style.height)
                    .padding(
                        horizontal = if (variant == AtomicButtonVariant.Text) AtomicSpacing.s4 else AtomicSpacing.s16,
                    ),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = style.content,
                    modifier = Modifier.size(AtomicSize.iconSmall),
                )
            }
            AtomicText(text = text, role = style.labelRole, color = style.content, maxLines = 1)
        }
    }
}

/** Icon-only button variants (spec §7.2). */
enum class AtomicIconButtonVariant { Ghost, Accent, Back, Toolbar, Destructive }

/** Icon-only button; [contentDescription] is required so TalkBack can name it. */
@Composable
fun AtomicIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AtomicIconButtonVariant = AtomicIconButtonVariant.Ghost,
    enabled: Boolean = true,
) {
    val colors = Atomic.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = AtomicShape.sm
    val (fill, tint) =
        when (variant) {
            AtomicIconButtonVariant.Ghost -> colors.surfaceCard to colors.content
            AtomicIconButtonVariant.Accent -> colors.accent to colors.onAccent
            AtomicIconButtonVariant.Back, AtomicIconButtonVariant.Toolbar -> colors.surfaceCard to colors.content
            AtomicIconButtonVariant.Destructive -> colors.error to colors.onError
        }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.94f else 1f,
        if (Atomic.reducedMotion) snap() else AtomicMotion.press(),
        label = "iconPress",
    )
    val surface =
        Modifier.size(AtomicSize.touchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRing(focused, colors.accentText, AtomicShape.radiusSm)
            .background(fill, shape)
    Box(
        modifier =
            modifier
                .size(AtomicSize.touchTarget)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(surface, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(AtomicSize.iconSmall))
        }
    }
}

private const val DISABLED_ALPHA = 0.4f
