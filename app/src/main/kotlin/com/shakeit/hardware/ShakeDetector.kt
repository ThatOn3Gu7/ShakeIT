package com.shakeit.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Wires the accelerometer and the proximity sensor to [ShakeAlgorithm], and
 * keeps them delivering while the app is in the background.
 *
 * All the judgement lives in the algorithm; this class delivers samples and
 * translates the proximity reading into "is the phone covered". Three things
 * here exist purely because detection has to survive the activity being gone:
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
 * the screen is off — with the screen on the processor is awake already, so
 * holding it then would burn battery for nothing. This is the piece that makes
 * "screen off, phone locked" work at all; without it the service stays alive and
 * its notification stays up while samples silently stop arriving.
 *
 * It also records when the last sample arrived, so the engine can tell the
 * difference between "detecting" and "registered but being starved" — see
 * [DetectionStatus].
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
    private val onCoveredChanged: (Boolean) -> Unit = {},
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager: SensorManager? = appContext.getSystemService()
    private val powerManager: PowerManager? = appContext.getSystemService()
    private val algorithm = ShakeAlgorithm()

    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenReceiverRegistered = false

    @Volatile
    private var running = false

    @Volatile
    private var armedAtElapsedMillis = 0L

    @Volatile
    private var lastSampleElapsedMillis = 0L

    /** Whether an accelerometer was found and the listener is registered. */
    val isRunning: Boolean
        get() = running

    /** Whether this device has an accelerometer to listen to at all. */
    val hasAccelerometer: Boolean
        get() = accelerometer != null

    /**
     * Whether *every* sensor in use can wake the processor from suspend itself —
     * the accelerometer and, when the device has one, the proximity sensor.
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

    /** Whether this device has a proximity sensor, i.e. whether the pocket guard can work. */
    val hasProximitySensor: Boolean
        get() = proximitySensor != null

    /** Whether the proximity sensor currently says the phone is covered. */
    val isCovered: Boolean
        get() = algorithm.covered

    /**
     * Registers the listeners.
     *
     * @return false when the device has no accelerometer, in which case nothing
     *   is registered and [onShake] will never fire
     */
    fun start(): Boolean {
        if (running) return true
        val manager = sensorManager ?: run {
            Log.w(TAG, "no sensor manager; shake detection unavailable")
            return false
        }
        // A wake-up accelerometer is preferred: it wakes the processor itself, so
        // no wake lock is needed to keep samples coming with the screen off.
        val sensor = manager.wakeUpAccelerometer()
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: run {
                Log.w(TAG, "no accelerometer; shake detection unavailable")
                return false
            }

        val thread = HandlerThread(THREAD_NAME).also { it.start() }
        sensorThread = thread
        sensorHandler = Handler(thread.looper)

        accelerometer = sensor
        // GAME (≈50 Hz) resolves a 3-8 Hz hand shake with several samples per
        // stroke; anything faster only costs battery.
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, sensorHandler)

        // Proximity only changes when something covers or uncovers the phone, so
        // the slowest rate is plenty.
        proximitySensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.also {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
        }

        algorithm.reset()
        armedAtElapsedMillis = SystemClock.elapsedRealtime()
        lastSampleElapsedMillis = 0L
        running = true

        registerScreenReceiver()
        updateWakeLock()

        Log.i(
            TAG,
            "detection armed: allSensorsWakeUp=$usesWakeUpSensor, " +
                "accelWakeUp=${sensor.isWakeUpSensor}, proximity=${proximitySensor != null}, " +
                "wakeLock=${wakeLock?.isHeld == true}",
        )
        return true
    }

    /** Unregisters everything, releases the wake lock and drops half-recognised motion. */
    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(this)
        unregisterScreenReceiver()
        updateWakeLock()
        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null
        accelerometer = null
        proximitySensor = null
        algorithm.reset()
        algorithm.covered = false
        lastSampleElapsedMillis = 0L
        Log.i(TAG, "detection stopped")
    }

    /**
     * How long it has been since a sample arrived, measured on the clock that
     * keeps running while the device sleeps. Falls back to the moment detection
     * was armed, so a listener that never receives anything reports a growing
     * gap instead of looking healthy forever. Null when not running.
     */
    fun millisSinceLastSample(): Long? {
        if (!running) return null
        val reference =
            if (lastSampleElapsedMillis == 0L) armedAtElapsedMillis else lastSampleElapsedMillis
        return SystemClock.elapsedRealtime() - reference
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val values = event.values
                if (values.size < AXIS_COUNT) return
                lastSampleElapsedMillis = SystemClock.elapsedRealtime()
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
     * it is simply called on every transition and on arming.
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
