package com.shakeit.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Accessor for the ShakeIT palette, mirroring how `MaterialTheme.colorScheme`
 * is read. Usage: `ShakeItTheme.colors.primary`.
 */
object ShakeItTheme {
    val colors: ShakeItColors
        @Composable
        @ReadOnlyComposable
        get() = LocalShakeItColors.current
}

private val LightMaterialColors = lightColorScheme(
    primary = LightShakeItColors.primary,
    onPrimary = LightShakeItColors.onPrimary,
    secondary = LightShakeItColors.trackOn,
    onSecondary = LightShakeItColors.onPrimary,
    tertiary = LightShakeItColors.accent,
    onTertiary = LightShakeItColors.onAccent,
    background = LightShakeItColors.background,
    onBackground = LightShakeItColors.onSurface,
    surface = LightShakeItColors.surface,
    onSurface = LightShakeItColors.onSurface,
    surfaceVariant = LightShakeItColors.surface2,
    onSurfaceVariant = LightShakeItColors.onSurfaceVariant,
    outline = LightShakeItColors.outline,
)

private val DarkMaterialColors = darkColorScheme(
    primary = DarkShakeItColors.primary,
    onPrimary = DarkShakeItColors.onPrimary,
    secondary = DarkShakeItColors.trackOn,
    onSecondary = DarkShakeItColors.onPrimary,
    tertiary = DarkShakeItColors.accent,
    onTertiary = DarkShakeItColors.onAccent,
    background = DarkShakeItColors.background,
    onBackground = DarkShakeItColors.onSurface,
    surface = DarkShakeItColors.surface,
    onSurface = DarkShakeItColors.onSurface,
    surfaceVariant = DarkShakeItColors.surface2,
    onSurfaceVariant = DarkShakeItColors.onSurfaceVariant,
    outline = DarkShakeItColors.outline,
)

/**
 * Theme for the whole app.
 *
 * Two layers are provided:
 *
 *  1. [LocalShakeItColors] — the prototype's palette. Every ShakeIT component
 *     reads from this, which is what keeps the UI looking like
 *     `debug/mockup/shakeit-prototype.html` rather than a stock Material app.
 *  2. [MaterialTheme] — the brand palette expressed as an M3 colour scheme, so
 *     that stock Material components (and things that read `LocalContentColor`)
 *     stay coherent.
 *
 * @param dynamicColor when true, and on Android 12+, the *Material* colour
 *   scheme is taken from the device wallpaper. ShakeIT's own palette is
 *   deliberately not re-tinted: the "Dynamic color" switch in the prototype has
 *   no visual effect either, and swapping the brand purple/amber for the
 *   wallpaper colours would break the design reference. The switch is still
 *   wired end-to-end and persisted, so the behaviour can be extended later.
 */
@Composable
fun ShakeItTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shakeItColors = if (darkTheme) DarkShakeItColors else LightShakeItColors

    val materialColors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkMaterialColors
        else -> LightMaterialColors
    }

    CompositionLocalProvider(LocalShakeItColors provides shakeItColors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = Typography,
            content = content,
        )
    }
}
