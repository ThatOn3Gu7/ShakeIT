package com.shakeit.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.getSystemService

/**
 * Wires the accelerometer and the proximity sensor to [ShakeAlgorithm].
 *
 * All the judgement lives in the algorithm; this class only delivers samples and
 * translates the proximity reading into "is the phone covered".
 *
 * The proximity part is the pocket guard: when something is close enough to the
 * top of the screen the algorithm goes deaf and forgets what it heard, so
 * walking with the phone in a pocket cannot toggle the torch. Devices without a
 * proximity sensor simply never report covered, and shake recognition still
 * works.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
    private val onCoveredChanged: (Boolean) -> Unit = {},
) : SensorEventListener {

    private val sensorManager: SensorManager? = context.getSystemService()
    private val algorithm = ShakeAlgorithm()

    private var proximitySensor: Sensor? = null
    private var running = false

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
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            Log.w(TAG, "no accelerometer; shake detection unavailable")
            return false
        }

        // GAME (≈50 Hz) resolves a 3-8 Hz hand shake with several samples per
        // stroke; anything faster only costs battery.
        manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)

        // Proximity only changes when something covers or uncovers the phone, so
        // the slowest rate is plenty.
        proximitySensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.also {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        algorithm.reset()
        running = true
        return true
    }

    /** Unregisters everything and drops any half-recognised motion. */
    fun stop() {
        if (!running) return
        sensorManager?.unregisterListener(this)
        running = false
        algorithm.reset()
        algorithm.covered = false
    }

    /** Whether the proximity sensor currently says the phone is covered. */
    val isCovered: Boolean
        get() = algorithm.covered

    /**
     * Whether this device has a proximity sensor at all, i.e. whether the pocket
     * guard can work. Worth knowing: without one, shake recognition still runs,
     * it just has nothing to suppress it.
     */
    val hasProximitySensor: Boolean
        get() = proximitySensor != null

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val values = event.values
                if (values.size < AXIS_COUNT) return
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

    private companion object {
        const val TAG = "ShakeDetector"
        const val AXIS_COUNT = 3

        /** Closer than this to the proximity sensor means "in a pocket". */
        const val COVERED_LIMIT_CM = 4f
    }
}
