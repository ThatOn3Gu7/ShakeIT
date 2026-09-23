package com.shakeit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import com.shakeit.state.ShakeItState
import com.shakeit.state.ThemeMode
import com.shakeit.state.rememberShakeItState
import com.shakeit.ui.home.HomeScreen
import com.shakeit.ui.navigation.ShakeItNavHost
import com.shakeit.ui.settings.SettingsScreen
import com.shakeit.ui.theme.DarkShakeItColors
import com.shakeit.ui.theme.LightShakeItColors
import com.shakeit.ui.theme.ShakeItTheme

/** `.screen-clip { transition: background .35s }` */
private const val BACKGROUND_TRANSITION_MS = 350
private val BackgroundEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * Root of the app: resolves the theme, owns the state, and hosts the two
 * destinations.
 *
 * There is no navigation library in this project's dependency set, so the
 * prototype's stacked `.screen` sections are reproduced directly by
 * [ShakeItNavHost] instead of a NavHost — which also keeps both screens
 * transparent over one shared background, exactly like `.screen-clip`.
 */
@Composable
fun ShakeItApp(state: ShakeItState = rememberShakeItState()) {
    val isDark = when (state.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    // The background is the only colour the prototype transitions on a theme
    // change; everything else in the palette swaps immediately.
    val backgroundMix by animateFloatAsState(
        targetValue = if (isDark) 1f else 0f,
        animationSpec = tween(BACKGROUND_TRANSITION_MS, easing = BackgroundEasing),
        label = "backgroundMix",
    )

    ShakeItTheme(darkTheme = isDark, dynamicColor = state.dynamicColor) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    lerp(
                        LightShakeItColors.background,
                        DarkShakeItColors.background,
                        backgroundMix,
                    ),
                ),
        ) {
            ShakeItNavHost(
                screen = state.screen,
                home = {
                    HomeScreen(
                        torchOn = state.torchOn,
                        activations = state.activations,
                        detectionActive = state.detectionActive,
                        shakeRequest = state.shakeRequest,
                        animateBlob = state.screen == ShakeItState.Screen.HOME,
                        onToggleTorch = state::toggleTorch,
                        onSimulateShake = state::simulateShake,
                        onOpenSettings = state::openSettings,
                        onToggleTheme = { state.toggleTheme(isDarkNow = isDark) },
                    )
                },
                settings = {
                    SettingsScreen(state = state, onBack = state::closeSettings)
                },
            )
        }

        // The prototype's back arrow is the only way out of Settings; map the
        // system gesture/button onto it.
        BackHandler(enabled = state.screen == ShakeItState.Screen.SETTINGS) {
            state.closeSettings()
        }
    }
}
