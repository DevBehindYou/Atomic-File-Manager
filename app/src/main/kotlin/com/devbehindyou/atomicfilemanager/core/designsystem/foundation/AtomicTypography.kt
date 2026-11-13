@file:OptIn(ExperimentalTextApi::class)

package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devbehindyou.atomicfilemanager.R

/** Bundled Atomic families (offline app: no downloadable fonts). Licences: assets/licenses/OFL-*.txt. */
object AtomicFonts {
    private fun hanken(weight: Int) =
        Font(
            R.font.hanken_grotesk,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )

    private fun mono(weight: Int) =
        Font(
            R.font.jetbrains_mono,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )

    /** Bebas Neue has no lowercase: use it only for short, app-authored text, never file names. */
    val display = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal))
    val body = FontFamily(hanken(400), hanken(500), hanken(700))
    val mono = FontFamily(mono(400), mono(500), mono(700))
}

/**
 * Atomic type roles use the bundled Hanken family for headings, labels and file names.
 */
object AtomicTypography {
    private val displayBase =
        TextStyle(fontFamily = AtomicFonts.body, fontWeight = FontWeight.Bold, lineHeight = 1.2.em)

    val displayHero = displayBase.copy(fontSize = 36.sp)
    val displayTitle = displayBase.copy(fontSize = 32.sp)
    val displayPushed = displayBase.copy(fontSize = 24.sp)
    val displayCard = displayBase.copy(fontSize = 20.sp)
    val displayButton = displayBase.copy(fontSize = 16.sp)

    val nameLarge =
        TextStyle(fontFamily = AtomicFonts.body, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    val name =
        TextStyle(fontFamily = AtomicFonts.body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
    val body =
        TextStyle(fontFamily = AtomicFonts.body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val bodySecondary = body.copy(fontSize = 15.sp, lineHeight = 22.sp)

    /** Uppercased by the text atom; 12 sp minimum (spec §4.4.7). */
    val monoLabel =
        TextStyle(
            fontFamily = AtomicFonts.mono,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 1.4.sp,
        )
    val monoMeta =
        TextStyle(fontFamily = AtomicFonts.mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

    /**
     * Bridge for Material widgets not yet rebuilt. Every Material role stays in Hanken Grotesk
     * (labels in JetBrains Mono) because existing screens put user data such as file names and
     * Markdown headings into title and headline roles, and Bebas Neue would show them in capitals.
     * bodySmall stays at 14 sp until those screens move to Atomic roles (spec minimum is 15 sp).
     */
    val material =
        Typography(
            displayLarge = nameLarge.copy(fontSize = 44.sp, lineHeight = 50.sp),
            displayMedium = nameLarge.copy(fontSize = 36.sp, lineHeight = 42.sp),
            displaySmall = nameLarge.copy(fontSize = 30.sp, lineHeight = 36.sp),
            headlineLarge = nameLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
            headlineMedium = nameLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
            headlineSmall = nameLarge,
            titleLarge = nameLarge,
            titleMedium = name,
            titleSmall = name.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = body,
            bodyMedium = bodySecondary,
            bodySmall = body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            labelLarge = monoLabel.copy(fontSize = 13.sp, letterSpacing = 0.5.sp),
            labelMedium = monoLabel.copy(letterSpacing = 0.5.sp),
            labelSmall = monoMeta.copy(letterSpacing = 0.5.sp),
        )
}
