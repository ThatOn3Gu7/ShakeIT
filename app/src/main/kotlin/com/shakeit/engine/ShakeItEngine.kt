package com.shakeit.engine

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.shakeit.ShakeItApplication
import com.shakeit.background.BatteryRestrictions
import com.shakeit.hardware.DetectionStatus
import com.shakeit.hardware.Haptics
import com.shakeit.hardware.ShakeDetector
import com.shakeit.hardware.TorchController
import com.shakeit.hardware.detectionStatus
import com.shakeit.service.ShakeItService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one place hardware is touched, owned by the process rather than by an
 * [android.app.Activity] or a [android.app.Service].
 *
 * Both the UI and the foreground service reach the same instance through
 * [ShakeItApplication], which is what makes the screen and the background agree:
 * a shake with the screen off flips [torchOn], and the composition that is still
 * alive observes it and redraws; a tap in the UI drives the very same controller
 * the service would.
 *
 * Nothing here depends on the activity or on composition. Detection runs from a
 * sensor thread into [toggleTorch]; the UI is only ever an observer, so closing
 * it — or the system destroying it — cannot interrupt the path from a shake to
 * the flash.
 *
 * Everything is exposed as a [StateFlow] so observers never have to register
 * callbacks, and so state survives configuration changes and the UI going away.
 */
class ShakeItEngine(private val context: Context) {

    /** The real flashlight, and the truth about whether it is lit. */
    val torch = TorchController(context)

    /** What the platform and the OEM power manager allow in the background. */
    val battery = BatteryRestrictions(context)

    private val haptics = Haptics(context)

    /**
     * Deliberately not the main dispatcher: the watchdog has to keep telling the
     * truth even if the main thread is blocked, and none of this work is UI work.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _shakeEvents = MutableStateFlow(0)

    /**
     * Counts recognised shakes. Monotonic on purpose: an observer compares it
     * against the value it last saw, so it can animate once per shake without
     * replaying old ones after a configuration change.
     */
    val shakeEvents: StateFlow<Int> = _shakeEvents.asStateFlow()

    private val _detectionStatus = MutableStateFlow(DetectionStatus.INACTIVE)

    /**
     * Whether detection is *actually* working, not merely requested.
     *
     * [DetectionStatus.STALLED] is the state this app most needs to be able to
     * report: the service is alive and its notification is up, but no samples
     * are arriving — which is what a device power manager freezing the process,
     * or Doze ignoring the wake lock, looks like from the inside.
     */
    val detectionStatus: StateFlow<DetectionStatus> = _detectionStatus.asStateFlow()

    private val _covered = MutableStateFlow(false)

    /** Whether the proximity sensor says the phone is covered, i.e. in a pocket. */
    val covered: StateFlow<Boolean> = _covered.asStateFlow()

    private val _batteryUnrestricted = MutableStateFlow(true)

    /**
     * Whether the user has exempted ShakeIT from battery optimisation, which is
     * what decides whether Doze honours the detector's wake lock.
     *
     * Starts optimistic so the process does not pay a binder call during
     * `Application.onCreate`, and is corrected by [refreshBatteryRestrictions] —
     * which happens on the activity's first resume, milliseconds later.
     */
    val batteryUnrestricted: StateFlow<Boolean> = _batteryUnrestricted.asStateFlow()

    private var detector: ShakeDetector? = null
    private var watchdog: Job? = null

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
     *
     * Called from the sensor thread when a shake lands, which is why nothing on
     * this path touches a view, a composition or the main looper.
     */
    fun setTorch(enabled: Boolean) {
        if (torch.setTorch(enabled)) haptics.tick()
    }

    /**
     * Starts listening for shakes. Safe to call repeatedly; the service calls it
     * on every start, and again from `onTaskRemoved`, so a restart by the system
     * picks detection back up instead of leaving an armed-looking notification
     * over a dead listener.
     */
    fun startDetection() {
        if (detector?.isRunning == true) {
            publishStatus()
            return
        }
        val candidate = ShakeDetector(
            context = context,
            onShake = ::onShakeDetected,
            onCoveredChanged = { isCovered -> _covered.value = isCovered },
        )
        // False on a device with no accelerometer: nothing was registered, so
        // there is nothing to hold on to and nothing to stop later.
        if (candidate.start()) {
            detector = candidate
            startWatchdog()
        }
        publishStatus()
    }

    /** Stops listening. The torch is left alone — detection and light are separate. */
    fun stopDetection() {
        detector?.stop()
        detector = null
        _covered.value = false
        stopWatchdog()
        publishStatus()
    }

    /**
     * Brings the detection service to the foreground. The activity calls this on
     * launch; from there the service outlives the activity and keeps working with
     * the screen off or the app backgrounded.
     */
    fun startService() {
        val intent = ShakeItService.intent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (error: IllegalStateException) {
            // Android 12+ refuses a foreground-service start from the background
            // (ForegroundServiceStartNotAllowedException). Reaching this means
            // something tried after the activity had already gone; detection
            // waits for the next legitimate start rather than crashing the app.
            Log.w(TAG, "the platform refused to start the detection service", error)
        } catch (error: SecurityException) {
            Log.w(TAG, "not allowed to start the detection service", error)
        }
    }

    /** Stops the detection service. */
    fun stopService() {
        context.stopService(ShakeItService.intent(context))
    }

    /**
     * Re-reads the battery-optimisation answer.
     *
     * Called on resume, because that is the only moment it can have changed: the
     * system settings screen is another app's activity, so there is no callback
     * to listen for and nothing else to observe.
     */
    fun refreshBatteryRestrictions() {
        _batteryUnrestricted.value = battery.isUnrestricted
    }

    /**
     * Sends the user to the system screen that decides whether background
     * detection is allowed to keep running.
     *
     * @return false when this device has no such screen
     */
    fun openBatterySettings(): Boolean = battery.openBatterySettings()

    /** As [openBatterySettings], but for this app's own system settings page. */
    fun openAppSettings(): Boolean = battery.openAppSettings()

    /** A shake got through: count it for the UI, then act on it. */
    private fun onShakeDetected() {
        _shakeEvents.update { it + 1 }
        toggleTorch()
    }

    /**
     * Asks the detector how old its newest sample is, on a timer.
     *
     * A `delay` on a background dispatcher does not wake the processor, so this
     * costs nothing while the device sleeps — it simply resumes when the device
     * does, and by then the elapsed-realtime gap it measures says exactly how
     * long delivery had stopped.
     */
    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        watchdog = scope.launch {
            while (true) {
                delay(WATCHDOG_INTERVAL_MILLIS)
                publishStatus()
            }
        }
    }

    private fun stopWatchdog() {
        watchdog?.cancel()
        watchdog = null
    }

    private fun publishStatus() {
        val current = detector
        val status = detectionStatus(
            armed = current?.isRunning == true,
            hasSensor = current?.hasAccelerometer == true,
            millisSinceLastSample = current?.millisSinceLastSample(),
            stallAfterMillis = STALL_AFTER_MILLIS,
        )
        val previous = _detectionStatus.value
        _detectionStatus.value = status
        if (previous != status) {
            // The single most useful line for diagnosing a device that stops
            // detecting: what changed, and how stale the samples had gone.
            Log.i(
                TAG,
                "detection $previous -> $status " +
                    "(last sample ${current?.millisSinceLastSample()}ms ago, " +
                    "batteryUnrestricted=${battery.isUnrestricted})",
            )
        }
    }

    private companion object {
        const val TAG = "ShakeItEngine"

        /** How often the watchdog checks that samples are still arriving. */
        const val WATCHDOG_INTERVAL_MILLIS = 2_000L

        /**
         * How long without a sample counts as stalled. Armed and healthy, the
         * accelerometer delivers roughly every 20ms, so this leaves a wide margin
         * for scheduling hiccups while still catching a device that has stopped
         * delivering altogether.
         */
        const val STALL_AFTER_MILLIS = 3_000L
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
