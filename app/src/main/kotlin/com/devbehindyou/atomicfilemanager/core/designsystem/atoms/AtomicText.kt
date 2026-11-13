package com.devbehindyou.atomicfilemanager.core.designsystem.atoms

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography

/** Atomic type roles (ATOMIC_UI_PLAN.md §4.2). */
enum class AtomicTextRole(
    internal val uppercase: Boolean,
    internal val secondary: Boolean,
) {
    DisplayHero(uppercase = false, secondary = false),
    DisplayTitle(uppercase = false, secondary = false),
    DisplayPushed(uppercase = false, secondary = false),
    DisplayCard(uppercase = false, secondary = false),
    DisplayButton(uppercase = false, secondary = false),

    /** File and folder names: always shown exactly as stored. */
    NameLarge(uppercase = false, secondary = false),
    Name(uppercase = false, secondary = false),
    Body(uppercase = false, secondary = false),
    BodySecondary(uppercase = false, secondary = true),
    MonoLabel(uppercase = true, secondary = true),

    /** Sizes, ISO dates, paths, hashes: original case. */
    MonoMeta(uppercase = false, secondary = true),
    ;

    internal val style: TextStyle
        get() =
            when (this) {
                DisplayHero -> AtomicTypography.displayHero
                DisplayTitle -> AtomicTypography.displayTitle
                DisplayPushed -> AtomicTypography.displayPushed
                DisplayCard -> AtomicTypography.displayCard
                DisplayButton -> AtomicTypography.displayButton
                NameLarge -> AtomicTypography.nameLarge
                Name -> AtomicTypography.name
                Body -> AtomicTypography.body
                BodySecondary -> AtomicTypography.bodySecondary
                MonoLabel -> AtomicTypography.monoLabel
                MonoMeta -> AtomicTypography.monoMeta
            }
}

/**
 * Text in an Atomic role. Mono labels are uppercased at render time from the
 * sentence-case string (spec §11.7), and screen readers get the original wording, so
 * "Storage" is read as a word, not spelled out. Never pass user data to an uppercase role.
 */
@Composable
fun AtomicText(
    text: String,
    role: AtomicTextRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
) {
    val colors = Atomic.colors
    val resolved =
        when {
            color != Color.Unspecified -> color
            role.secondary -> colors.contentSecondary
            else -> colors.content
        }
    // Observed through the configuration so a locale change re-renders the label.
    val locale = LocalConfiguration.current.locales[0]
    val shown = if (role.uppercase) text.uppercase(locale) else text
    val semantics = if (role.uppercase) Modifier.clearAndSetSemantics { contentDescription = text } else Modifier
    Text(
        text = shown,
        modifier = modifier.then(semantics),
        style = role.style,
        color = resolved,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}
