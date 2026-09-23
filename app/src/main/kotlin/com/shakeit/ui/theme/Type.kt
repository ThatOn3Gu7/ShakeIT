package com.shakeit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Typography for ShakeIT.
 *
 * The prototype loads two families: **Manrope** (weights 600/800) for the
 * display text — the OFF/ON state word, the Settings title, the stat numbers —
 * and **Roboto** (400/500/700) for everything else.
 *
 * Roboto *is* the Android system font, so `FontFamily.Default` already matches
 * the body text exactly. Manrope is not shipped with Android and there is no
 * font binary in this repository yet, so the display family currently resolves
 * to the system font as well. To finish the swap, drop `manrope_bold.ttf` /
 * `manrope_extrabold.ttf` into `app/src/main/res/font/` and change
 * [DisplayFontFamily] to:
 *
 * ```
 * val DisplayFontFamily = FontFamily(
 *     Font(R.font.manrope_bold, FontWeight.Bold),
 *     Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
 * )
 * ```
 *
 * No other code has to change — every display style below reads from it.
 */
val DisplayFontFamily: FontFamily = FontFamily.Default
val BodyFontFamily: FontFamily = FontFamily.Default

/**
 * Named text styles, one per distinct type treatment in the prototype.
 * Sizes are the prototype's `px` values read as `sp`.
 */
object ShakeItType {

    /** `.state-word` — Manrope 800, 38px, letter-spacing .5px */
    val stateWord = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
    )

    /** `#settings h1` — Manrope 800, 20px */
    val settingsTitle = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
    )

    /** `.stat-num` — Manrope 700, 16px */
    val statNumber = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
    )

    /** `.group h2` — 12.5px, 700, letter-spacing .2px, `--primary` */
    val groupHeader = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        letterSpacing = 0.2.sp,
    )

    /** `.switch-row span` — 14px */
    val rowLabel = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    )

    /** `.state-sub` — 13.5px */
    val stateSub = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        textAlign = TextAlign.Center,
    )

    /** `.shake-btn` / `.seg-btn` body — Roboto 500, 13.5px / 12.5px */
    val buttonLabel = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
    )

    /** `.seg-btn` — Roboto 500, 12.5px */
    val segmentLabel = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        textAlign = TextAlign.Center,
    )

    /** `.seg-btn.active` — Roboto 700, 12.5px */
    val segmentLabelActive = segmentLabel.copy(fontWeight = FontWeight.Bold)

    /** `.status-pill` — 12.5px */
    val statusText = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
    )

    /** `.row-text small` — 12px */
    val caption = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    )

    /** `.row-text-block small` — 12.5px, line-height 1.4 */
    val captionBlock = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.5.sp,
    )

    /** `.stat-label` — 11px */
    val statLabel = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
    )

    /** `.fine-print` — 11.5px, line-height 1.5 */
    val finePrint = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 17.25.sp,
    )

    /** `.outline-btn` — Roboto 600, 13.5px */
    val outlineButton = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        textAlign = TextAlign.Center,
    )

    /** Glyphs used by the top bar buttons (◐ ⚙ ←) — 17px in the prototype. */
    val iconGlyph = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        textAlign = TextAlign.Center,
    )
}

/**
 * Material 3 type scale, derived from the ShakeIT styles above so that any stock
 * Material component picks up the same metrics instead of the template defaults.
 */
val Typography = Typography(
    displayLarge = ShakeItType.stateWord,
    headlineSmall = ShakeItType.settingsTitle,
    titleMedium = ShakeItType.statNumber,
    labelSmall = ShakeItType.groupHeader,
    bodyLarge = ShakeItType.rowLabel,
    bodyMedium = ShakeItType.buttonLabel,
    bodySmall = ShakeItType.caption,
    labelLarge = ShakeItType.outlineButton,
    labelMedium = ShakeItType.segmentLabel,
)
