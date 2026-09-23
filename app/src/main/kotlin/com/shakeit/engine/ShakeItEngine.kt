package com.shakeit.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.shakeit.ShakeItApplication
import com.shakeit.hardware.Haptics
import com.shakeit.hardware.ShakeDetector
import com.shakeit.hardware.TorchController
import com.shakeit.service.ShakeItService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place hardware is touched, owned by the process rather than by an
 * [android.app.Activity] or a [android.app.Service].
 *
 * Both the UI and the foreground service reach the same instance through
 * [com.shakeit.ShakeItApplication], which is what makes the screen and the
 * background agree: a shake with the screen off flips [torchOn], and the
 * composition that is still alive observes it and redraws; a tap in the UI
 * drives the very same controller the service would.
 *
 * Everything is exposed as a [StateFlow] so observers never have to register
 * callbacks, and so state survives configuration changes and the UI going away.
 */
class ShakeItEngine(private val context: Context) {

    /** The real flashlight, and the truth about whether it is lit. */
    val torch = TorchController(context)

    private val haptics = Haptics(context)

    private val _shakeEvents = MutableStateFlow(0)

    /**
     * Counts recognised shakes. Monotonic on purpose: an observer compares it
     * against the value it last saw, so it can animate once per shake without
     * replaying old ones after a configuration change.
     */
    val shakeEvents: StateFlow<Int> = _shakeEvents.asStateFlow()

    private val _detectionRunning = MutableStateFlow(false)

    /** Whether the accelerometer is currently being listened to. */
    val detectionRunning: StateFlow<Boolean> = _detectionRunning.asStateFlow()

    private val _covered = MutableStateFlow(false)

    /** Whether the proximity sensor says the phone is covered, i.e. in a pocket. */
    val covered: StateFlow<Boolean> = _covered.asStateFlow()

    private var detector: ShakeDetector? = null

    /**
     * Called once when the process starts. Registering the torch callback costs
     * nothing while it sits idle, and it is what lets the UI report the real
     * flashlight state even when another app changed it.
     */
    fun start() {
        torch.start()
    }

    /** Flips the torch to the opposite of its real current state. */
    fun toggleTorch() {
        setTorch(!torch.torchOn.value)
    }

    /**
     * Drives the torch and confirms with a haptic — but only when the request
     * landed, so a device without a flash does not pretend it did something.
     */
    fun setTorch(enabled: Boolean) {
        if (torch.setTorch(enabled)) haptics.tick()
    }

    /**
     * Starts listening for shakes. Safe to call repeatedly; the service calls it
     * on every start so a restart by the system picks detection back up.
     */
    fun startDetection() {
        if (_detectionRunning.value) return
        val candidate = ShakeDetector(
            context = context,
            onShake = ::onShakeDetected,
            onCoveredChanged = { isCovered -> _covered.value = isCovered },
        )
        // False on a device with no accelerometer: nothing was registered, so
        // there is nothing to hold on to and nothing to stop later.
        if (!candidate.start()) return
        detector = candidate
        _detectionRunning.value = true
    }

    /** Stops listening. The torch is left alone — detection and light are separate. */
    fun stopDetection() {
        detector?.stop()
        detector = null
        _detectionRunning.value = false
        _covered.value = false
    }

    /**
     * Brings the detection service to the foreground. The activity calls this on
     * launch; from there the service outlives the activity and keeps working with
     * the screen off or the app backgrounded.
     */
    fun startService() {
        val intent = ShakeItService.intent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /** Stops the detection service. */
    fun stopService() {
        context.stopService(ShakeItService.intent(context))
    }

    /** A shake got through: count it for the UI, then act on it. */
    private fun onShakeDetected() {
        _shakeEvents.value += 1
        toggleTorch()
    }
}

/**
 * Reaches the process-wide engine from composition, the same way
 * [com.shakeit.state.rememberShakeItState] reaches the state.
 *
 * Read through the [android.app.Application] rather than the activity's context
 * so a configuration change hands back the very same instance — which is what
 * keeps the torch state, and the shake count, continuous across rotations.
 */
@Composable
fun rememberShakeItEngine(): ShakeItEngine {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        (context.applicationContext as ShakeItApplication).engine
    }
}
