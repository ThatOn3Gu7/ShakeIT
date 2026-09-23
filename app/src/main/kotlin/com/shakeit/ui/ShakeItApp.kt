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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import com.shakeit.engine.ShakeItEngine
import com.shakeit.engine.rememberShakeItEngine
import com.shakeit.state.ShakeItState
import com.shakeit.state.ThemeMode
import com.shakeit.state.rememberShakeItState
import com.shakeit.ui.home.HomeScreen
import com.shakeit.ui.navigation.ShakeItNavHost
import com.shakeit.ui.settings.SettingsScreen
import com.shakeit.ui.theme.DarkShakeItColors
import com.shakeit.ui.theme.LightShakeItColors
import com.shakeit.ui.theme.ShakeItTheme
import kotlinx.coroutines.launch

/** `.screen-clip { transition: background .35s }` */
private const val BACKGROUND_TRANSITION_MS = 350
private val BackgroundEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * Root of the app: resolves the theme, owns the state, mirrors the hardware into
 * it, and hosts the two destinations.
 *
 * There is no navigation library in this project's dependency set, so the
 * prototype's stacked `.screen` sections are reproduced directly by
 * [ShakeItNavHost] instead of a NavHost — which also keeps both screens
 * transparent over one shared background, exactly like `.screen-clip`.
 */
@Composable
fun ShakeItApp(
    state: ShakeItState = rememberShakeItState(),
    engine: ShakeItEngine = rememberShakeItEngine(),
) {
    // One-way mirror: hardware state flows into the screen state and never back,
    // so a shake with the screen locked, a tap here, or another app taking the
    // flash all land in the same place. The screen asks the engine to change the
    // torch; it never claims a change of its own.
    LaunchedEffect(engine, state) {
        launch { engine.torch.torchOn.collect { enabled -> state.onTorchStateChanged(enabled) } }
        launch {
            // Compare against the last count seen rather than treating every
            // emission as a shake: a StateFlow replays its current value to each
            // new collector, and a rotation must not replay last night's shake.
            var seen = engine.shakeEvents.value
            engine.shakeEvents.collect { count ->
                if (count > seen) state.onShakeDetected()
                seen = count
            }
        }
    }

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
                        detectedShake = state.detectedShake,
                        animateBlob = state.screen == ShakeItState.Screen.HOME,
                        onToggleTorch = engine::toggleTorch,
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
