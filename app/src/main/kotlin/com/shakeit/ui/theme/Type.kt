package com.shakeit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ShakeIT's type scale.
 *
 * Every style in the app is one of Material's fifteen roles. The prototype's
 * per-element `px` sizes (`38px` for the state word, `12.5px` for a row label,
 * `11px` for a caption) are gone: they were a one-to-one transcription of one
 * CSS file, they did not scale with each other, and they left the hierarchy
 * carried almost entirely by font weight.
 *
 * The scale starts from Material's own numbers, with two deliberate deviations
 * that give ShakeIT its voice:
 *
 *  - **display and headline are ExtraBold with negative tracking.** A large,
 *    tight, heavy word is the app's identity — it is what says "flashlight is
 *    ON" from across a room.
 *  - **label roles are Bold with positive tracking.** They are used for section
 *    headers and status text, which are short, and short text at small sizes
 *    reads better with air in it. Section headers uppercase them at the call
 *    site, which is a text decision rather than a type one.
 *
 * All sizes are `sp`, so the whole scale follows the user's font-size setting.
 * Nothing here hardcodes a pixel value or disables scaling.
 *
 * The prototype loaded **Manrope** for its display text. No font binary is
 * committed and none could be fetched here, so [DisplayFontFamily] currently
 * resolves to the system font. Dropping the two TTFs into
 * `app/src/main/res/font/` and pointing [DisplayFontFamily] at them is the whole
 * change — every display and headline style below reads from it.
 */
val DisplayFontFamily: FontFamily = FontFamily.Default
val BodyFontFamily: FontFamily = FontFamily.Default

val ShakeItTypography = Typography(

    /* ------------------------------------------------------- hero state */

    /** The hero's state word at its largest: "Flashlight Active". */
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.25).sp,
    ),
    /** The hero's state word on a compact phone. */
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1).sp,
    ),

    /* ---------------------------------------------------- screen titles */

    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.75).sp,
    ),
    /** The Settings title. */
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp,
    ),

    /* ------------------------------------------------- titles and stats */

    titleLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    /** A settings row's title, and a stat's number. */
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    /* --------------------------------------------------------- body copy */

    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    /** Row subtitles and supporting text. */
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),

    /* ----------------------------------------------------------- labels */

    /** Section headers, button labels, chip text. */
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp,
    ),
    /** The smallest text in the app: diagnostic detail and fine print. */
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp,
    ),
)
