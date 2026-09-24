package com.shakeit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import com.shakeit.engine.ShakeItEngine
import com.shakeit.engine.rememberShakeItEngine
import com.shakeit.state.ShakeItState
import com.shakeit.state.ThemeMode
import com.shakeit.state.rememberShakeItState
import com.shakeit.ui.home.HomeScreen
import com.shakeit.ui.navigation.ShakeItNavHost
import com.shakeit.ui.settings.SettingsScreen
import com.shakeit.ui.theme.ShakeItTheme
import kotlinx.coroutines.launch

/**
 * Root of the app: resolves the theme, owns the state, mirrors the hardware into
 * it, and hosts the two destinations.
 *
 * There is no navigation library in this project's dependency set, so the
 * prototype's stacked `.screen` sections are reproduced directly by
 * [ShakeItNavHost] instead of a NavHost — which also keeps both screens
 * transparent over one shared background, exactly like `.screen-clip`.
 */
/**
 * @param openSettingsOnLaunch land on Settings instead of the home screen. Used by
 *   the notification's Diagnostics action, which is offered only while something
 *   is actually wrong — sending the user to the home screen first would hide the
 *   answer behind a tap they did not ask for.
 */
@Composable
fun ShakeItApp(
    state: ShakeItState = rememberShakeItState(),
    engine: ShakeItEngine = rememberShakeItEngine(),
    openSettingsOnLaunch: Boolean = false,
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

    // Detection health comes from the engine rather than from a stored
    // preference, because these rows have to keep telling the truth when the
    // device stops delivering samples in the background. Same for the diagnostics:
    // they are re-read whenever the app resumes, which is the only moment the app
    // gets to look at what happened while it was away.
    val detectionStatus by engine.detectionStatus.collectAsState()
    val diagnostics by engine.diagnostics.collectAsState()

    LaunchedEffect(openSettingsOnLaunch) {
        if (openSettingsOnLaunch) state.openSettings()
    }

    val isDark = when (state.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    // The background is the only colour the prototype transitions on a theme
    // change; everything else in the palette swaps immediately.
    ShakeItTheme(darkTheme = isDark, dynamicColor = state.dynamicColor) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ShakeItNavHost(
                screen = state.screen,
                home = {
                    HomeScreen(
                        torchOn = state.torchOn,
                        activations = state.activations,
                        detectionStatus = detectionStatus,
                        detectedShake = state.detectedShake,
                        animateBlob = state.screen == ShakeItState.Screen.HOME,
                        onToggleTorch = engine::toggleTorch,
                        onOpenSettings = state::openSettings,
                        onToggleTheme = { state.toggleTheme(isDarkNow = isDark) },
                    )
                },
                settings = {
                    SettingsScreen(
                        state = state,
                        onBack = state::closeSettings,
                        diagnostics = diagnostics,
                        onOpenBatterySettings = { engine.openBatterySettings() },
                        onOpenAppSettings = { engine.openAppSettings() },
                        onOpenShizuku = { engine.openShizuku() },
                        onRequestShizukuPermission = engine::requestShizukuPermission,
                        onRepairDozeAllowlist = engine::repairDozeAllowlist,
                        onRepairBackgroundAppOp = engine::repairBackgroundAppOp,
                    )
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
