package com.shakeit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * ShakeIT's brand palette, expressed as a Material 3 [ColorScheme].
 *
 * There is deliberately only one colour system in this app. An earlier revision
 * kept a second, parallel palette (`ShakeItColors`) that every custom component
 * read from, which meant the "Dynamic color" switch only ever changed colours
 * nothing on screen was using. Everything now reads
 * `MaterialTheme.colorScheme`, so the switch below is the *whole* palette:
 * wallpaper colours on Android 12+ when it is on, these when it is not.
 *
 * The identity these two schemes carry is the same one the prototype had —
 * an indigo/violet structure with a warm amber for the flashlight — but pushed
 * down the tonal scale so light mode has real separation between surfaces
 * instead of three near-white greys, and dark mode is a deep violet-charcoal
 * with genuinely lighter containers rather than the light theme with darker hex
 * values pasted over it.
 *
 * Role usage, so the accent does not end up everywhere:
 *  - [ColorScheme.primary] — identity and the resting hero: important actions,
 *    the section labels, the blob while the torch is off.
 *  - [ColorScheme.secondary] — supporting emphasis only.
 *  - [ColorScheme.tertiary] — the flashlight's energy, and nothing else. See
 *    [torchPalette].
 *  - the `surfaceContainer*` ladder — structure. Groups, panels and rows are
 *    separated by tone, not by borders and shadows.
 */

/* ---------------------------------------------------------------- light */

private val LightPrimary = Color(0xFF5A45A8)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFE8DEFF)
private val LightOnPrimaryContainer = Color(0xFF1A0A52)

private val LightSecondary = Color(0xFF635B73)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFE9DEFB)
private val LightOnSecondaryContainer = Color(0xFF1F182D)

/** The flashlight amber: saturated enough to read as light against a pale tone. */
private val LightTertiary = Color(0xFF8C5000)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFFFDCBB)
private val LightOnTertiaryContainer = Color(0xFF2D1600)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

/**
 * A visible lavender-grey rather than the prototype's near-white. Cards and
 * panels sit *above* it on the tonal ladder, so the page reads as depth instead
 * of as a stack of white rectangles on white.
 */
private val LightBackground = Color(0xFFEDE7F3)
private val LightOnBackground = Color(0xFF1B1621)
private val LightSurface = Color(0xFFFBF8FE)
private val LightOnSurface = Color(0xFF1B1621)
private val LightSurfaceVariant = Color(0xFFE5DEEA)
private val LightOnSurfaceVariant = Color(0xFF48414E)

private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF7F2FA)
private val LightSurfaceContainer = Color(0xFFF1EBF6)
private val LightSurfaceContainerHigh = Color(0xFFEAE4F0)
private val LightSurfaceContainerHighest = Color(0xFFE4DEEB)

private val LightOutline = Color(0xFF79727F)
private val LightOutlineVariant = Color(0xFFC9C2D4)

private val LightInverseSurface = Color(0xFF302B37)
private val LightInverseOnSurface = Color(0xFFF2ECF7)
private val LightInversePrimary = Color(0xFFCEBDFF)

/** ShakeIT's brand light scheme, used when Dynamic color is off. */
val ShakeItLightColors: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceTint = LightPrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color.Black,
)

/* ----------------------------------------------------------------- dark */

private val DarkPrimary = Color(0xFFCDBDFF)
private val DarkOnPrimary = Color(0xFF331F7E)
private val DarkPrimaryContainer = Color(0xFF4A3697)
private val DarkOnPrimaryContainer = Color(0xFFE8DEFF)

private val DarkSecondary = Color(0xFFCDC2DB)
private val DarkOnSecondary = Color(0xFF342D41)
private val DarkSecondaryContainer = Color(0xFF4B4358)
private val DarkOnSecondaryContainer = Color(0xFFE9DEFB)

private val DarkTertiary = Color(0xFFFFB868)
private val DarkOnTertiary = Color(0xFF4E2700)
private val DarkTertiaryContainer = Color(0xFF6E3D00)
private val DarkOnTertiaryContainer = Color(0xFFFFDCBB)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

/** Deep violet-charcoal, with containers climbing well clear of it. */
private val DarkBackground = Color(0xFF141119)
private val DarkOnBackground = Color(0xFFE6E0EC)
private val DarkSurface = Color(0xFF141119)
private val DarkOnSurface = Color(0xFFE6E0EC)
private val DarkSurfaceVariant = Color(0xFF48414E)
private val DarkOnSurfaceVariant = Color(0xFFC9C2D0)

private val DarkSurfaceContainerLowest = Color(0xFF0E0C13)
private val DarkSurfaceContainerLow = Color(0xFF1C1924)
private val DarkSurfaceContainer = Color(0xFF211E29)
private val DarkSurfaceContainerHigh = Color(0xFF2B2834)
private val DarkSurfaceContainerHighest = Color(0xFF36323F)

private val DarkOutline = Color(0xFF938C9A)
private val DarkOutlineVariant = Color(0xFF48414E)

private val DarkInverseSurface = Color(0xFFE6E0EC)
private val DarkInverseOnSurface = Color(0xFF322D3A)
private val DarkInversePrimary = Color(0xFF5A45A8)

/** ShakeIT's brand dark scheme, used when Dynamic color is off. */
val ShakeItDarkColors: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color.Black,
)

/* ------------------------------------------------------- torch identity */

/**
 * The colours the hero paints itself with.
 *
 * Derived from the active [ColorScheme] rather than hardcoded, so the flashlight
 * follows the device wallpaper when Dynamic color is on and ShakeIT's amber when
 * it is off — one rule, two sources.
 *
 * @param rest the blob's fill while the torch is off
 * @param onRest the icon colour on that fill
 * @param lit the blob's fill while the torch is on
 * @param onLit the icon colour on that fill
 * @param glow the halo colour, applied with alpha
 * @param wash the wide, low-alpha pool of light behind the hero while lit
 */
@Immutable
data class TorchPalette(
    val rest: Color,
    val onRest: Color,
    val lit: Color,
    val onLit: Color,
    val glow: Color,
    val wash: Color,
)

/**
 * Picks the *bright* member of the tertiary pair for the lit fill, so "the torch
 * is on" always reads as light: in a light scheme `tertiary` is a deep amber and
 * `tertiaryContainer` is the bright one, while in a dark scheme those are the
 * other way round. The same rule keeps the icon colour readable on both.
 *
 * @param scheme the palette currently in force
 * @param darkTheme whether that palette is a dark one. Passed in rather than
 *   inferred from a colour's luminance, because the app's own theme switch — not
 *   the system — decides which scheme is showing.
 */
fun torchPalette(scheme: ColorScheme, darkTheme: Boolean): TorchPalette {
    val lit = if (darkTheme) scheme.tertiary else scheme.tertiaryContainer
    val onLit = if (darkTheme) scheme.onTertiary else scheme.onTertiaryContainer
    return TorchPalette(
        rest = scheme.primary,
        onRest = scheme.onPrimary,
        lit = lit,
        onLit = onLit,
        // A deep amber halo reads as mud over a pale background, so the light
        // scheme's glow is pulled towards the bright end of the pair. In dark
        // the tertiary is already the bright one.
        glow = if (darkTheme) scheme.tertiary else lerp(scheme.tertiary, scheme.tertiaryContainer, 0.55f),
        wash = lit,
    )
}

/** Whether the app is showing a dark palette, as decided by its own theme switch. */
internal val LocalShakeItDarkTheme = staticCompositionLocalOf { false }

/**
 * Reads the torch palette for the scheme in force.
 *
 * This is the only place ShakeIT keeps colours of its own, and it keeps none:
 * every value is computed from [MaterialTheme.colorScheme]. Not called
 * `remember…` because it remembers nothing — it is a pure read of the palette,
 * and the result is immutable.
 */
@Composable
@ReadOnlyComposable
fun torchColors(): TorchPalette =
    torchPalette(MaterialTheme.colorScheme, LocalShakeItDarkTheme.current)
