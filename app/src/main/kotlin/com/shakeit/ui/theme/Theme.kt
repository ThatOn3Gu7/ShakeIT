package com.shakeit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the palette in force is a dark one.
 *
 * Not read from a colour's luminance and not from the system, because ShakeIT has
 * its own theme switch: a user who has pinned Light on a device set to dark is
 * looking at a light palette, and anything derived from the theme has to agree
 * with what is on screen.
 */
object ShakeItTheme {
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalShakeItDarkTheme.current
}

/**
 * The app's theme.
 *
 * One colour system, and [MaterialTheme.colorScheme] is it. There used to be a
 * second palette alongside this one that every custom component read from, which
 * had a consequence worth stating plainly: the "Dynamic color" switch changed a
 * scheme nothing visible was using, so it did nothing. Every component now reads
 * Material's roles — including the full `surfaceContainer*` ladder, which is what
 * separates panels, groups and rows by tone instead of by borders — so this switch
 * really does retheme the app.
 *
 * @param dynamicColor on Android 12+, take the scheme from the device wallpaper.
 *   Off, or on an older device, fall back to ShakeIT's own brand palette
 *   ([ShakeItLightColors] / [ShakeItDarkColors]). Both paths are complete
 *   schemes, so nothing downstream needs to know which one arrived.
 */
@Composable
fun ShakeItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> ShakeItDarkColors
        else -> ShakeItLightColors
    }

    CompositionLocalProvider(LocalShakeItDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ShakeItShapeScale,
            typography = ShakeItTypography,
            content = content,
        )
    }
}
