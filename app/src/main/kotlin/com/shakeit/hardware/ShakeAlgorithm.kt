package com.shakeit.hardware

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tuning for [ShakeAlgorithm]. Accelerations are gravity-free and in m/s²;
 * every duration is in milliseconds.
 *
 * The defaults are deliberately stricter than "some movement crossed a
 * threshold". Three things have to line up before a shake is accepted:
 *
 *  * **Intensity** — an impulse has to reach [impulseThreshold], roughly 1.4g
 *    of acceleration that is *not* gravity. Setting the phone down or walking
 *    with it rarely gets there.
 *  * **Rate** — [requiredImpulses] of them inside [windowMillis], about six
 *    per second. That is an easy hand shake (3 Hz and up) but well above a
 *    walking cadence (≈2 Hz) or a single knock.
 *  * **Reversal** — the impulses must alternate direction along one axis, so a
 *    sustained push (a car accelerating, an escalator) can never add up to a
 *    shake no matter how hard it is.
 *
 * Once a shake fires, the gesture has to end — acceleration back below the
 * threshold for [ShakeConfig.quietMillis] — before another can, with
 * [ShakeConfig.cooldownMillis] as a floor underneath it. Shaking for two seconds
 * is still one toggle; shaking again after a beat is two.
 */
data class ShakeConfig(
    /** Gravity-free acceleration that starts an impulse, in m/s². */
    val impulseThreshold: Float = 14f,
    /** An impulse also ends below `impulseThreshold * releaseRatio`. */
    val releaseRatio: Float = 0.45f,
    /** Longest one impulse may last before it is force-closed. */
    val maxImpulseMillis: Long = 250,
    /** Impulses older than this are forgotten. */
    val windowMillis: Long = 450,
    /** How many impulses inside the window make a shake. */
    val requiredImpulses: Int = 3,
    /** How many direction reversals those impulses must contain. */
    val requiredReversals: Int = 2,
    /**
     * After a shake fires, acceleration has to stay below [impulseThreshold] for
     * this long before another one can. This is what makes one long shake mean
     * one toggle however long it goes on for: the burst already fired, and the
     * next one has to be a separate gesture.
     */
    val quietMillis: Long = 300,
    /** Hard floor between two toggles, whatever the motion does in between. */
    val cooldownMillis: Long = 500,
    /** Time constant of the gravity low-pass filter, in seconds. */
    val gravityTimeConstantSeconds: Float = 0.15f,
    /** A longer gap between samples means the motion was not continuous. */
    val maxSampleGapMillis: Long = 300,
)

/** The tuning the detector ships with. */
val DefaultShakeConfig = ShakeConfig()

/** The Settings slider's range: `<input type="range" min="1" max="5">`. */
const val SENSITIVITY_MIN = 1
const val SENSITIVITY_MAX = 5

/** The slider value the shipped tuning corresponds to ("Medium"). */
const val SENSITIVITY_DEFAULT = 3

/**
 * Maps the sensitivity slider onto the recognition tuning.
 *
 * Only [ShakeConfig.impulseThreshold] moves: how hard a stroke has to be before
 * it counts as an impulse. Everything that shapes *timing* — the impulse window,
 * the reversal requirement, the quiet period and the cooldown — is left exactly
 * as tuned and tested, because "one shake, one toggle" has to hold at every
 * level. A slider that quietly rewrote the debounce would change what the gesture
 * means rather than how hard it has to be.
 *
 * Level [SENSITIVITY_DEFAULT] reproduces [DefaultShakeConfig] precisely, so an
 * untouched install behaves identically to before the slider was wired.
 *
 * Pure, so the whole ladder is testable on the JVM.
 */
fun shakeConfigFor(sensitivity: Int): ShakeConfig {
    val level = sensitivity.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)
    val threshold = DefaultShakeConfig.impulseThreshold +
        (SENSITIVITY_DEFAULT - level) * SENSITIVITY_STEP
    return DefaultShakeConfig.copy(impulseThreshold = threshold)
}

/** How much harder (or easier) each step of the slider makes a stroke, in m/s². */
private const val SENSITIVITY_STEP = 2.5f

/**
 * Decides whether a stream of accelerometer samples is a deliberate shake.
 *
 * Pure maths on purpose, like [com.shakeit.ui.home.Wobble]: no Android types and
 * no clock of its own — the caller hands in the sensor timestamp — so the whole
 * recognition rule is testable on the JVM and behaves the same at any sampling
 * rate.
 *
 * Usage is one call per sample:
 * ```
 * if (algorithm.onAccelerometer(event.timestamp, x, y, z)) shake()
 * ```
 * [onAccelerometer] returns true exactly once per recognised shake.
 */
class ShakeAlgorithm(private val config: ShakeConfig = DefaultShakeConfig) {

    /**
     * Whether the proximity sensor says the phone is covered — pocket, bag, or
     * face down on a table. While covered nothing is recognised *and* nothing is
     * remembered, so the jostling of a pocket cannot be banked up and fired the
     * instant the phone is pulled out.
     */
    var covered: Boolean = false
        set(value) {
            if (value && !field) forgetMotion()
            field = value
        }

    private val impulses = ArrayDeque<Impulse>()

    private var gravityInitialised = false
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    private var lastSampleMillis = 0L
    private var haveLastSample = false

    private var impulseActive = false
    private var impulseStartMillis = 0L
    private var impulsePeak = 0f
    private var impulseAxis = AXIS_X
    private var impulseSign = 1

    private var lastFireMillis: Long? = null

    /** When acceleration last dropped below the threshold, or null while it is above. */
    private var quietSinceMillis: Long? = null

    /** Set on firing: the burst must be over before the next shake counts. */
    private var awaitingQuiet = false

    /**
     * Feeds one accelerometer sample.
     *
     * @param timestampNanos the sensor event timestamp: monotonic, so it can be
     *   differenced, but with no wall-clock meaning
     * @return true when this sample completes a recognised shake
     */
    fun onAccelerometer(timestampNanos: Long, x: Float, y: Float, z: Float): Boolean {
        val now = timestampNanos / NANOS_PER_MILLI

        if (covered) {
            // In a pocket: stay deaf and forget what was heard.
            forgetMotion()
            haveLastSample = false
            return false
        }

        val deltaSeconds = when {
            !haveLastSample -> {
                // First sample after a start or a gap: assume the nominal rate so
                // the filter settles instead of jumping.
                gravityInitialised = false
                NOMINAL_DELTA_SECONDS
            }
            now - lastSampleMillis > config.maxSampleGapMillis -> {
                // The detector was starved (doze, batched delivery). Samples on
                // either side of a gap are not one continuous motion.
                forgetMotion()
                gravityInitialised = false
                NOMINAL_DELTA_SECONDS
            }
            else -> ((now - lastSampleMillis) / MILLIS_PER_SECOND)
                .coerceIn(MIN_DELTA_SECONDS, MAX_DELTA_SECONDS)
        }
        lastSampleMillis = now
        haveLastSample = true

        if (!gravityInitialised) {
            // Seed the filter with the first reading, otherwise a stationary
            // phone looks like one enormous impulse.
            gravityX = x
            gravityY = y
            gravityZ = z
            gravityInitialised = true
            return false
        }

        // Low-pass the reading to estimate gravity, then subtract it: what is
        // left is the acceleration the user caused. Deriving the coefficient
        // from the real sample interval keeps the cutoff identical at 50 Hz and
        // at whatever rate the device manages under load.
        val alpha = config.gravityTimeConstantSeconds /
            (config.gravityTimeConstantSeconds + deltaSeconds)
        gravityX += (x - gravityX) * (1f - alpha)
        gravityY += (y - gravityY) * (1f - alpha)
        gravityZ += (z - gravityZ) * (1f - alpha)

        trackImpulse(
            now = now,
            linearX = x - gravityX,
            linearY = y - gravityY,
            linearZ = z - gravityZ,
        )

        return evaluate(now)
    }

    /** Drops every remembered sample, impulse and cooldown. */
    fun reset() {
        forgetMotion()
        gravityInitialised = false
        haveLastSample = false
        lastFireMillis = null
        quietSinceMillis = null
        awaitingQuiet = false
    }

    /**
     * Collects the samples into impulses: magnitude rises through the threshold,
     * peaks, and the stroke ends. Only a *completed* impulse is remembered, so a
     * steady hard acceleration stays one long impulse instead of becoming a
     * burst of them.
     *
     * A stroke can end three ways — the acceleration falls back through the
     * hysteresis band, it turns around along the axis it was pushing on, or it
     * has gone on too long. The turnaround test matters because at 50 Hz the
     * brief magnitude dip at a reversal is easy to step straight over.
     */
    private fun trackImpulse(now: Long, linearX: Float, linearY: Float, linearZ: Float) {
        val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        // Track how long the phone has been calm, which is what separates one
        // gesture from the next.
        if (magnitude >= config.impulseThreshold) {
            quietSinceMillis = null
        } else if (quietSinceMillis == null) {
            quietSinceMillis = now
        }

        if (!impulseActive) {
            if (magnitude >= config.impulseThreshold) {
                impulseActive = true
                impulseStartMillis = now
                impulsePeak = magnitude
                impulseAxis = dominantAxis(linearX, linearY, linearZ)
                impulseSign = signOf(linearX, linearY, linearZ)
            }
            return
        }

        if (magnitude > impulsePeak) {
            impulsePeak = magnitude
            impulseAxis = dominantAxis(linearX, linearY, linearZ)
            impulseSign = signOf(linearX, linearY, linearZ)
        }

        val released = magnitude <= config.impulseThreshold * config.releaseRatio
        val turnedAround = componentOn(impulseAxis, linearX, linearY, linearZ) * impulseSign <
            -REVERSAL_FLOOR
        val expired = now - impulseStartMillis >= config.maxImpulseMillis
        if (released || turnedAround || expired) {
            impulses.addLast(Impulse(now, impulseAxis, impulseSign, impulsePeak))
            impulseActive = false
        }
    }

    /** True when the recent impulses look like a shake and the cooldown is over. */
    private fun evaluate(now: Long): Boolean {
        while (impulses.isNotEmpty() && now - impulses.first().timestampMillis > config.windowMillis) {
            impulses.removeFirst()
        }

        val lastFire = lastFireMillis
        if (lastFire != null && now - lastFire < config.cooldownMillis) return false

        if (awaitingQuiet) {
            val quietSince = quietSinceMillis
            if (quietSince == null || now - quietSince < config.quietMillis) {
                // Still the same gesture. Drop what it has produced so its tail
                // cannot fire late, once the phone finally goes calm.
                forgetMotion()
                return false
            }
            awaitingQuiet = false
        }

        if (impulses.size < config.requiredImpulses) return false

        // The window's impulses have to be one back-and-forth along a single
        // axis: same axis, alternating sign.
        val from = impulses.size - config.requiredImpulses
        val axis = impulses[from].axis
        var reversals = 0
        for (index in from until impulses.size) {
            if (impulses[index].axis != axis) return false
            if (index > from && impulses[index].sign != impulses[index - 1].sign) reversals++
        }
        if (reversals < config.requiredReversals) return false

        // One shake, one toggle: forget the burst and wait for the gesture to end.
        lastFireMillis = now
        awaitingQuiet = true
        forgetMotion()
        return true
    }

    private fun forgetMotion() {
        impulses.clear()
        impulseActive = false
        impulsePeak = 0f
    }

    private fun dominantAxis(x: Float, y: Float, z: Float): Int {
        val magnitudeX = abs(x)
        val magnitudeY = abs(y)
        val magnitudeZ = abs(z)
        return when {
            magnitudeX >= magnitudeY && magnitudeX >= magnitudeZ -> AXIS_X
            magnitudeY >= magnitudeZ -> AXIS_Y
            else -> AXIS_Z
        }
    }

    private fun signOf(x: Float, y: Float, z: Float): Int =
        if (componentOn(dominantAxis(x, y, z), x, y, z) < 0f) -1 else 1

    private fun componentOn(axis: Int, x: Float, y: Float, z: Float): Float = when (axis) {
        AXIS_X -> x
        AXIS_Y -> y
        else -> z
    }

    /** One completed acceleration peak, and the direction it pushed in. */
    private data class Impulse(
        val timestampMillis: Long,
        val axis: Int,
        val sign: Int,
        val peak: Float,
    )

    private companion object {
        const val AXIS_X = 0
        const val AXIS_Y = 1
        const val AXIS_Z = 2

        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000f
        const val NOMINAL_DELTA_SECONDS = 0.02f
        const val MIN_DELTA_SECONDS = 0.001f
        const val MAX_DELTA_SECONDS = 0.2f

        /**
         * How far the acceleration has to swing back along the impulse's axis
         * before that counts as a turnaround. A small floor keeps sensor noise
         * from chopping one stroke into several impulses.
         */
        const val REVERSAL_FLOOR = 1.5f
    }
}
