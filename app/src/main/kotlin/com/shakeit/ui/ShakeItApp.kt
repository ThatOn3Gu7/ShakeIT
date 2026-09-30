package com.shakeit.ui

import android.util.Log
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.shakeit.engine.ShakeItEngine
import com.shakeit.engine.rememberShakeItEngine
import com.shakeit.state.ShakeItState
import com.shakeit.state.ThemeMode
import com.shakeit.state.rememberShakeItState
import com.shakeit.ui.home.HomeScreen
import com.shakeit.ui.navigation.ShakeItNavHost
import com.shakeit.ui.settings.SettingsScreen
import com.shakeit.ui.splash.ShakeItSplash
import com.shakeit.ui.theme.ShakeItTheme
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// TEMPORARY DIAGNOSTIC — logcat tag for the predictive-back event flow
// logging below. Remove this constant together with the logging once it has
// served its purpose.
private const val PREDICTIVE_BACK_DIAG_TAG = "ShakeIT-PredictiveBack"

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
    var showSplash = remember { mutableStateOf(true) }

    // Transient interactive gesture state, kept strictly apart from the
    // navigation state: `state.screen` is authoritative about which screen is
    // active, while these two values exist only while the system's predictive
    // back gesture is in flight. The handler resets both the moment the
    // gesture ends (commit or cancel), and the host ignores them whenever the
    // gesture is not active, so a stale gesture position can never contaminate
    // a later Settings entry.
    var backGestureActive by remember { mutableStateOf(false) }
    var backGestureProgress by remember { mutableFloatStateOf(0f) }
    // Temporary on-screen diagnostic state (drives PredictiveBackOverlay);
    // remove together with the overlay and the logcat logging.
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_NONE) }
    var backOutcome by remember { mutableStateOf<String?>(null) }
    var showBackDiag by remember { mutableStateOf(false) }

    // Temporary diagnostic: keep the on-screen readout up briefly after the
    // gesture ends so the final COMMIT/CANCEL is readable without any tooling.
    LaunchedEffect(backGestureActive) {
        if (!backGestureActive) {
            delay(1200)
            if (!backGestureActive) showBackDiag = false
        }
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
                backGestureActive = backGestureActive,
                backGestureProgress = backGestureProgress,
                home = {
                    HomeScreen(
                        torchOn = state.torchOn,
                        activations = state.activations,
                        detectionStatus = detectionStatus,
                        gesture = state.gesture,
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
            if (showSplash.value) {
                ShakeItSplash(onFinished = { showSplash.value = false })
            }
            if (showBackDiag) {
                PredictiveBackOverlay(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    progress = backGestureProgress,
                    swipeEdge = backSwipeEdge,
                    outcome = backOutcome,
                )
            }
        }

        // The prototype's back arrow is the tap-side way out of Settings; the
        // system gesture/button follows the same contract, but on Android 14+
        // it arrives as a stream of progress events. While the gesture is in
        // flight those events are the transition's progress itself (the host
        // renders them directly, with no animation in between); when the
        // system commits, the navigation flips and the host's springs settle
        // into Home from the position the gesture reached, and on cancel they
        // settle back to Settings. On plain back presses (and on pre-Android
        // 14 devices) the flow completes empty and the normal full animation
        // plays, exactly as before.
        // ------------------------------------------------------------------
        // TEMPORARY DIAGNOSTIC — this logging exists to answer exactly one
        // question: does the platform deliver BackEventCompat progress events
        // to this app during the system edge-back gesture? It changes no
        // behavior of the handler: the same flow is collected, the same
        // commit/cancel handling runs. Remove it once the event flow has been
        // verified on-device.
        // ------------------------------------------------------------------
        PredictiveBackHandler(enabled = state.screen == ShakeItState.Screen.SETTINGS) { progress ->
            Log.d(PREDICTIVE_BACK_DIAG_TAG, "handler invoked: back operation started (enabled while Settings is shown)")
            // Temporary on-screen diagnostic: show the readout for this gesture.
            backOutcome = null
            showBackDiag = true
            try {
                backGestureActive = true
                progress.collect { event ->
                    Log.d(
                        PREDICTIVE_BACK_DIAG_TAG,
                        "progress event: progress=${event.progress} swipeEdge=${event.swipeEdge} " +
                            "(0=left, 1=right, 2=not-an-edge-swipe) touchX=${event.touchX} touchY=${event.touchY}",
                    )
                    backGestureProgress = event.progress
                    backSwipeEdge = event.swipeEdge
                }
                Log.d(PREDICTIVE_BACK_DIAG_TAG, "flow completed normally → COMMIT")
                backOutcome = "COMMIT"
                // Committed: flip the navigation; the host finishes the
                // transition from the gesture's position (the last progress
                // event is not necessarily 1f, so the settle completes it).
                state.closeSettings()
            } catch (e: CancellationException) {
                Log.d(PREDICTIVE_BACK_DIAG_TAG, "flow cancelled via CancellationException → CANCEL")
                backOutcome = "CANCEL"
                // Cancelled: the screen stays Settings and the host settles
                // back from the preview position on its own.
                throw e
            } finally {
                // The gesture is over, committed or cancelled: drop the
                // transient state so it cannot affect any later navigation.
                backGestureActive = false
                backGestureProgress = 0f
            }
        }
    }
}

/**
 * TEMPORARY diagnostic overlay — shows, with no external tooling, whether the
 * system's predictive-back gesture is delivering BackEventCompat progress to
 * the app and what the handler did with it. Visible only while a back
 * operation from Settings is in flight, plus a short moment afterwards so the
 * final outcome is readable. Remove this composable together with the
 * diagnostic state and the logcat logging once the event flow has been
 * verified on-device.
 */
@Composable
private fun PredictiveBackOverlay(
    modifier: Modifier,
    progress: Float,
    swipeEdge: Int,
    outcome: String?,
) {
    val edgeLabel = when (swipeEdge) {
        BackEventCompat.EDGE_LEFT -> "left"
        BackEventCompat.EDGE_RIGHT -> "right"
        BackEventCompat.EDGE_NONE -> "not-an-edge-swipe"
        else -> "unknown"
    }
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                text = "Predictive Back: " + (outcome ?: "ACTIVE"),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Text(
                text = "progress = %.3f".format(progress),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Text(
                text = "swipeEdge = $swipeEdge ($edgeLabel)",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}
