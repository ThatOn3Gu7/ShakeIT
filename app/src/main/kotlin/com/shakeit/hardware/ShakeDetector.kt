package com.shakeit.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.shakeit.state.ShakeGesture

/**
 * Wires the accelerometer and the proximity sensor to [ShakeAlgorithm], keeps
 * them delivering while the app is in the background, and rebuilds the whole
 * stack when delivery stops.
 *
 * All the judgement about motion lives in the algorithm; this class delivers
 * samples, translates proximity into "is the phone covered", and owns the
 * plumbing that has to survive the activity being gone:
 *
 * **A thread of its own.** Sensor callbacks are dispatched on a [HandlerThread]
 * rather than the main looper, so a busy, blocked or OEM-frozen UI thread cannot
 * stall recognition — and a shake recognised with the screen off never has to
 * wait for a composition that no longer exists.
 *
 * **Wake-up sensors, if the device has them.** `SensorManager` documents that
 * non-wake-up sensors only deliver while the application processor is awake: to
 * keep receiving them with the screen off, the app must hold a partial wake
 * lock. A wake-up sensor does the waking itself, which is strictly better, so a
 * wake-up accelerometer is preferred when present — and the lock is skipped only
 * when every sensor in use is one.
 *
 * **A partial wake lock otherwise.** Held only while detection is armed *and*
 * the screen is off, because with the screen on the processor is awake already.
 * This is the piece that makes "screen off, phone locked" work at all; without
 * it the service stays alive and its notification stays up while samples
 * silently stop arriving.
 *
 * **A hardware wake path.** `TYPE_SIGNIFICANT_MOTION` is a one-shot trigger
 * sensor that is a wake-up sensor by definition, so it fires even while the
 * processor is suspended. It is *not* used to recognise shakes — it is far too
 * broad for that, and firing the torch on any movement would defeat the whole
 * tuning of [ShakeAlgorithm]. It is used to wake the device and check whether the
 * accelerometer is still delivering, which is exactly what a frozen detector
 * needs.
 *
 * **A rebuild.** [recover] tears the listeners, the thread and the lock down and
 * builds them again, re-selecting the best available accelerometer on the way.
 * Whether to call it, and how often, is policy and lives in the engine; this
 * class only reports the facts — see [diagnostics].
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
    private val onCoveredChanged: (Boolean) -> Unit = {},
    private val onHardwareWake: () -> Unit = {},
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager: SensorManager? = appContext.getSystemService()
    private val powerManager: PowerManager? = appContext.getSystemService()

    /** Replaced wholesale on a sensitivity change; volatile because the sensor thread reads it. */
    @Volatile
    private var doubleShakeMode = false

    @Volatile
    private var sensitivity = SENSITIVITY_DEFAULT

    @Volatile
    private var algorithm = ShakeAlgorithm()

    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var significantMotionSensor: Sensor? = null
    private var triggerArmed = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenReceiverRegistered = false

    @Volatile
    private var running = false

    @Volatile
    private var registrationSucceeded = false

    @Volatile
    private var hasDeliveredSample = false

    @Volatile
    private var armedAtElapsedMillis = 0L

    @Volatile
    private var lastSampleElapsedMillis = 0L

    @Volatile
    private var recoveries = 0

    @Volatile
    private var lastRecoverySucceeded: Boolean? = null

    @Volatile
    private var significantMotionFires = 0

    /** Whether the listeners are registered and expected to be delivering. */
    val isRunning: Boolean
        get() = running

    /** Whether this device has an accelerometer to listen to at all. */
    val hasAccelerometer: Boolean
        get() = accelerometer != null

    /** Whether `SensorManager.registerListener` accepted the accelerometer. */
    val isRegistrationSucceeded: Boolean
        get() = registrationSucceeded

    /** Whether a sample has arrived since the listener was (re)registered. */
    val isDeliveringSamples: Boolean
        get() = hasDeliveredSample

    /**
     * Whether *every* sensor in use can wake the processor from suspend itself —
     * the accelerometer and, when it registered, the proximity sensor.
     *
     * One non-wake-up listener is enough to require a lock, and the pocket guard
     * is exactly that listener: proximity sensors are essentially never wake-up
     * sensors, so treating the accelerometer alone as sufficient would leave
     * "is the phone covered" frozen at whatever it read when the screen went off.
     */
    val usesWakeUpSensor: Boolean
        get() {
            val accel = accelerometer ?: return false
            if (!accel.isWakeUpSensor) return false
            val proximity = proximitySensor ?: return true
            return proximity.isWakeUpSensor
        }

    /** Whether the proximity sensor registered, i.e. whether the pocket guard can work. */
    val hasProximitySensor: Boolean
        get() = proximitySensor != null

    /** Whether the proximity sensor currently says the phone is covered. */
    val isCovered: Boolean
        get() = algorithm.covered

    /**
     * Arms the listeners.
     *
     * @return false when this device cannot deliver: no accelerometer, no sensor
     *   manager, or a platform that refused `registerListener`. In every one of
     *   those cases nothing is marked as running, because a detector that claims
     *   to be armed over a listener that never fires is the failure this class
     *   exists to avoid.
     */
    fun start(): Boolean {
        if (running) return true
        recoveries = 0
        lastRecoverySucceeded = null
        significantMotionFires = 0
        return arm()
    }

    /** Unregisters everything, releases the wake lock and drops half-recognised motion. */
    fun stop() {
        teardown()
        recoveries = 0
        lastRecoverySucceeded = null
        significantMotionFires = 0
        accelerometer = null
        Log.i(TAG, "detection stopped")
    }

    /**
     * Tears the sensor stack down and builds it again: listeners unregistered,
     * thread quit, lock released, then a fresh thread, a re-selected
     * accelerometer, both listeners re-registered, the trigger re-armed, the
     * algorithm reset and the wake lock re-evaluated.
     *
     * This is what runs when samples stop arriving while the service is alive.
     * It is cheap — a few binder calls and one thread — but not free, so the
     * caller rate-limits it; see
     * [recoveryDelayMillis][com.shakeit.hardware.recoveryDelayMillis].
     *
     * Nothing here reports success on its own: the detector only becomes
     * [ACTIVE][DetectionStatus.ACTIVE] again once a real sample arrives, which
     * the caller verifies through [millisSinceLastSample].
     *
     * @return whether the listeners registered. False does not mean "give up" —
     *   the next attempt re-selects the sensor from scratch.
     */
    fun recover(): Boolean {
        val wasCovered = algorithm.covered
        teardown()
        val armed = arm()
        recoveries++
        lastRecoverySucceeded = armed
        if (armed && wasCovered) algorithm.covered = true
        Log.i(
            TAG,
            "sensor stack rebuilt (attempt #$recoveries): armed=$armed, " +
                "accelerometer=${accelerometer?.name}, wakeUp=$usesWakeUpSensor",
        )
        return armed
    }

    /**
     * Re-tunes recognition without touching the listeners: the algorithm is pure
     * state, so replacing it is cheaper and safer than re-registering sensors.
     * The pocket-guard reading carries over, because that is a fact about the
     * world rather than about the tuning.
     */
    fun setSensitivity(sensitivity: Int) {
        this.sensitivity = sensitivity.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)
        rebuildAlgorithm(this.sensitivity)
        Log.i(TAG, "sensitivity ${this.sensitivity} -> impulseThreshold ${algorithmConfig().impulseThreshold}")
    }

    /**
     * Double Shake needs a shorter detector re-arm interval, while normal Shake
     * retains its existing duplicate protection. The burst and reversal rules
     * remain identical in both modes, so a continuous shake still cannot become
     * two events.
     */
    fun setGesture(gesture: ShakeGesture) {
        val nextDoubleShake = gesture == ShakeGesture.DoubleShake
        if (doubleShakeMode == nextDoubleShake) return
        doubleShakeMode = nextDoubleShake
        rebuildAlgorithm(sensitivity)
    }

    private fun rebuildAlgorithm(sensitivity: Int) {
        val config = shakeConfigFor(sensitivity, doubleShake = doubleShakeMode)
        val wasCovered = algorithm.covered
        algorithm = ShakeAlgorithm(config).also { it.covered = wasCovered }
    }

    private fun algorithmConfig(): ShakeConfig = shakeConfigFor(
        sensitivity,
        doubleShake = doubleShakeMode,
    )

    /**
     * How long it has been since a sample arrived, measured on the clock that
     * keeps running while the device sleeps. Falls back to the moment the
     * listener was armed, so a listener that never receives anything reports a
     * growing gap instead of looking healthy forever. Null when not running.
     */
    fun millisSinceLastSample(): Long? {
        if (!running) return null
        val reference =
            if (lastSampleElapsedMillis == 0L) armedAtElapsedMillis else lastSampleElapsedMillis
        return SystemClock.elapsedRealtime() - reference
    }

    /** Every fact the diagnostics screen reports about the sensor stack. */
    fun diagnostics(): SensorDiagnostics {
        val screenIsOn = powerManager?.isInteractive ?: true
        return SensorDiagnostics(
            accelerometerAvailable = accelerometer != null,
            accelerometerWakeUp = accelerometer?.isWakeUpSensor == true,
            proximityAvailable = proximitySensor != null,
            proximityWakeUp = proximitySensor?.isWakeUpSensor == true,
            significantMotionAvailable = significantMotionSensor != null,
            significantMotionArmed = triggerArmed,
            significantMotionFires = significantMotionFires,
            registrationSucceeded = registrationSucceeded,
            wakeLockRequired = needsWakeLock(
                armed = running,
                usesWakeUpSensor = usesWakeUpSensor,
                screenIsOn = screenIsOn,
            ),
            wakeLockHeld = wakeLock?.isHeld == true,
            millisSinceLastSample = millisSinceLastSample(),
            hasDeliveredSample = hasDeliveredSample,
            recoveries = recoveries,
            lastRecoverySucceeded = lastRecoverySucceeded,
        )
    }

    // ------------------------------------------------------------------ arming

    /**
     * Selects the sensors, starts the thread and registers. Shared by [start]
     * and [recover] so a rebuild cannot drift from a first arm.
     */
    private fun arm(): Boolean {
        val manager = sensorManager ?: run {
            Log.w(TAG, "no sensor manager; shake detection unavailable")
            registrationSucceeded = false
            return false
        }
        // A wake-up accelerometer is preferred: it wakes the processor itself, so
        // no wake lock is needed to keep samples coming with the screen off.
        val sensor = manager.wakeUpAccelerometer()
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: run {
                Log.w(TAG, "no accelerometer; shake detection unavailable")
                registrationSucceeded = false
                return false
            }
        accelerometer = sensor

        val thread = HandlerThread(THREAD_NAME).also { it.start() }
        val handler = Handler(thread.looper)

        // registerListener returns false instead of throwing when the platform
        // will not deliver. Believing a false here is how a detector ends up
        // reporting itself armed over a listener that never fires.
        // GAME (≈50 Hz) resolves a 3-8 Hz hand shake with several samples per
        // stroke; anything faster only costs battery, and delivery — not rate —
        // is what this class is fighting for.
        if (!manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, handler)) {
            Log.w(TAG, "the platform refused the accelerometer listener; detection cannot arm")
            thread.quitSafely()
            registrationSucceeded = false
            running = false
            return false
        }

        sensorThread = thread
        sensorHandler = handler
        registrationSucceeded = true

        // Proximity only changes when something covers or uncovers the phone, so
        // the slowest rate is plenty. A refused registration leaves the pocket
        // guard off rather than pretending it is on.
        val proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val proximityRegistered = proximity != null &&
            manager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL, handler)
        proximitySensor = if (proximityRegistered) proximity else null
        if (proximity != null && !proximityRegistered) {
            Log.w(TAG, "the platform refused the proximity listener; the pocket guard is off")
        }

        // The hardware wake path, where the device has one.
        significantMotionSensor = manager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        armSignificantMotion()

        algorithm.reset()
        algorithm.covered = false
        armedAtElapsedMillis = SystemClock.elapsedRealtime()
        lastSampleElapsedMillis = 0L
        hasDeliveredSample = false
        running = true

        registerScreenReceiver()
        updateWakeLock()

        Log.i(
            TAG,
            "detection armed: sensor=${sensor.name}, allSensorsWakeUp=$usesWakeUpSensor, " +
                "proximity=$proximityRegistered, significantMotion=$triggerArmed, " +
                "wakeLock=${wakeLock?.isHeld == true}",
        )
        return true
    }

    /** Unregisters and releases everything. Safe to call when nothing is armed. */
    private fun teardown() {
        running = false
        registrationSucceeded = false
        hasDeliveredSample = false
        sensorManager?.let { manager ->
            manager.unregisterListener(this)
            significantMotionSensor?.let { manager.cancelTriggerSensor(triggerListener, it) }
        }
        triggerArmed = false
        unregisterScreenReceiver()
        // running is already false, so this releases rather than acquires.
        updateWakeLock()
        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null
        proximitySensor = null
        algorithm.reset()
        algorithm.covered = false
        lastSampleElapsedMillis = 0L
    }

    // ----------------------------------------------------------------- sensors

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val values = event.values
                if (values.size < AXIS_COUNT) return
                lastSampleElapsedMillis = SystemClock.elapsedRealtime()
                hasDeliveredSample = true
                if (algorithm.onAccelerometer(event.timestamp, values[0], values[1], values[2])) {
                    onShake()
                }
            }

            Sensor.TYPE_PROXIMITY -> {
                val covered = readsAsCovered(event.values[0], event.sensor)
                if (covered != algorithm.covered) {
                    algorithm.covered = covered
                    onCoveredChanged(covered)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Recognition does not depend on calibration, so accuracy is ignored.
    }

    /**
     * Proximity sensors report centimetres, but most are binary and only ever
     * return 0 (near) or their maximum range (far). Capping the limit at the
     * sensor's own maximum makes both kinds work: a binary sensor's "far" value
     * is never below the cap, and a real distance reading has to be under
     * [COVERED_LIMIT_CM] to count as a pocket.
     */
    private fun readsAsCovered(distanceCm: Float, sensor: Sensor): Boolean =
        distanceCm < minOf(sensor.maximumRange, COVERED_LIMIT_CM)

    /**
     * The one-shot significant-motion trigger.
     *
     * `requestTriggerSensor` has no `Handler` parameter — AOSP dispatches trigger
     * events through the main looper — so this callback is a convenience, not the
     * recovery mechanism: if the main thread is frozen the engine's watchdog,
     * which runs on its own dispatcher, still notices the stall. What the trigger
     * reliably provides is the *wake*: it is a wake-up sensor, so the hardware
     * brings the processor out of suspend to deliver it, and a starved
     * accelerometer listener gets another chance to be heard.
     */
    private val triggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            significantMotionFires++
            // One-shot: the request is cancelled by the platform the moment it
            // fires, so re-arm before anything else can be judged.
            armSignificantMotion()
            Log.i(
                TAG,
                "significant motion (fire #$significantMotionFires): last sample " +
                    "${millisSinceLastSample()}ms ago, trigger re-armed=$triggerArmed",
            )
            onHardwareWake()
        }
    }

    private fun armSignificantMotion() {
        val manager = sensorManager ?: return
        val sensor = significantMotionSensor ?: return
        triggerArmed = try {
            manager.requestTriggerSensor(triggerListener, sensor)
        } catch (error: RuntimeException) {
            // OEM sensor stacks throw here rather than returning false.
            Log.w(TAG, "requestTriggerSensor failed", error)
            false
        }
    }

    // --------------------------------------------------------------- wake lock

    /**
     * `ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON` only reach context-registered
     * receivers, and the wake lock is only needed while the screen is off — so
     * the lock is re-evaluated on each transition, and on arming (a service
     * restarted by the system with the screen already off gets no broadcast).
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateWakeLock()
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // System broadcasts only, so NOT_EXPORTED is both correct and what
        // Android 14 expects a context-registered receiver to declare.
        ContextCompat.registerReceiver(
            appContext,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        appContext.unregisterReceiver(screenReceiver)
        screenReceiverRegistered = false
    }

    /**
     * Re-evaluates the wake lock from the current facts. Cheap and idempotent, so
     * it is simply called on every transition, on arming and on teardown.
     */
    private fun updateWakeLock() {
        // With no power manager there is no lock to take and no way to manage
        // one, so treat the screen as on and hold nothing.
        val screenIsOn = powerManager?.isInteractive ?: true
        val needed = needsWakeLock(
            armed = running,
            usesWakeUpSensor = usesWakeUpSensor,
            screenIsOn = screenIsOn,
        )
        if (needed) acquireWakeLock() else releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val manager = powerManager ?: return
        val lock = wakeLock ?: manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .also { created ->
                // Not reference counted: acquire and release are idempotent, so a
                // stray screen broadcast can never leave the lock held twice or
                // release it early.
                created.setReferenceCounted(false)
                wakeLock = created
            }
        if (!lock.isHeld) {
            lock.acquire()
            Log.i(TAG, "screen off: holding a partial wake lock so samples keep arriving")
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release()
            Log.i(TAG, "screen on: wake lock released")
        }
    }

    /** A wake-up accelerometer, or null: most devices do not expose one. */
    private fun SensorManager.wakeUpAccelerometer(): Sensor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        } else {
            null
        }

    private companion object {
        const val TAG = "ShakeDetector"
        const val THREAD_NAME = "ShakeItSensors"
        const val WAKE_LOCK_TAG = "ShakeIT:detection"
        const val AXIS_COUNT = 3

        /** Closer than this to the proximity sensor means "in a pocket". */
        const val COVERED_LIMIT_CM = 4f
    }
}
