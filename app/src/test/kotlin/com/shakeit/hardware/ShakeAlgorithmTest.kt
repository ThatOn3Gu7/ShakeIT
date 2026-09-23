package com.shakeit.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The shake rule is the part of this app that is easy to get subtly wrong — too
 * loose and a walk turns the light on, too strict and a real shake does nothing
 * — and impossible to judge from a code review. These tests drive it with
 * synthetic sensor streams shaped like the motions it has to tell apart.
 *
 * Every model below layers motion on top of a gravity vector, because that is
 * what an accelerometer actually reports: a phone lying still reads 9.81 m/s²,
 * not zero.
 */
class ShakeAlgorithmTest {

    // ------------------------------------------------------------------
    // Recognition
    // ------------------------------------------------------------------

    @Test
    fun `a deliberate shake is recognised exactly once`() {
        val fires = ShakeAlgorithm().feedTimed(
            still(0.4f),
            shake(0.8f, hz = 4f, peak = 25f),
            still(0.4f),
        )

        assertEquals("one shake, one toggle", 1, fires.size)
    }

    @Test
    fun `a shake is recognised along any device axis`() {
        DeviceAxis.values().forEach { axis ->
            val fires = ShakeAlgorithm().feed(
                still(0.4f),
                shake(0.8f, hz = 4f, peak = 25f, axis = axis),
                still(0.4f),
            )
            assertEquals("shake along $axis was not recognised once", 1, fires)
        }
    }

    @Test
    fun `a shake is acted on within half a second of starting`() {
        val fires = ShakeAlgorithm().feedTimed(
            still(0.4f),
            shake(0.8f, hz = 4f, peak = 25f),
            still(0.4f),
        )

        val latency = fires.single() - SHAKE_STARTS_AT
        assertTrue(
            "took ${latency}s to recognise a shake, which reads as lag",
            latency <= 0.5f,
        )
        assertTrue("fired before the shaking started", latency > 0f)
    }

    @Test
    fun `recognition does not depend on the sampling rate`() {
        // Devices deliver SENSOR_DELAY_GAME anywhere between roughly 20 and 200
        // Hz depending on load and vendor; the filter derives its coefficient
        // from the real interval so the rule behaves the same across them.
        listOf(20, 50, 200).forEach { rate ->
            val fires = ShakeAlgorithm().feed(
                motions = arrayOf(still(0.4f), shake(0.8f, hz = 4f, peak = 25f), still(0.4f)),
                rateHz = rate,
            )
            assertEquals("$rate Hz delivery was not handled the same as 50 Hz", 1, fires)
        }
    }

    // ------------------------------------------------------------------
    // One shake, one toggle
    // ------------------------------------------------------------------

    @Test
    fun `shaking for two whole seconds is still one toggle`() {
        val fires = ShakeAlgorithm().feed(
            still(0.3f),
            shake(2f, hz = 4f, peak = 25f),
            still(0.6f),
        )

        assertEquals("a single long shake toggled ${fires} times", 1, fires)
    }

    @Test
    fun `the tail of a shake cannot fire after the phone goes calm`() {
        // The burst fires while it is still going; the impulses it leaves behind
        // must not produce a second, delayed toggle at the moment motion stops.
        val fires = ShakeAlgorithm().feedTimed(
            still(0.4f),
            shake(2f, hz = 4f, peak = 25f),
            still(1f),
        )

        assertEquals(1, fires.size)
        assertTrue(
            "second toggle came ${fires.last()}s in, after the shaking had stopped",
            fires.last() < SHAKE_STARTS_AT + 2f,
        )
    }

    @Test
    fun `two shakes separated by a beat are two toggles`() {
        val fires = ShakeAlgorithm().feedTimed(
            still(0.3f),
            shake(0.5f, hz = 4f, peak = 25f),
            still(0.6f),
            shake(0.5f, hz = 4f, peak = 25f),
            still(0.4f),
        )

        assertEquals("shake on, then shake off, should both land", 2, fires)
    }

    @Test
    fun `two shakes with no gap between them collapse into one`() {
        val fires = ShakeAlgorithm().feed(
            still(0.3f),
            shake(0.5f, hz = 4f, peak = 25f),
            still(0.2f),
            shake(0.5f, hz = 4f, peak = 25f),
            still(0.4f),
        )

        assertEquals("the gesture never really ended", 1, fires)
    }

    @Test
    fun `reset lets a continuous shake be recognised again`() {
        val algorithm = ShakeAlgorithm()
        assertEquals(1, algorithm.feed(still(0.3f), shake(1.2f, hz = 4f, peak = 25f)))

        // Without reset() the quiet rule would still be holding the next burst
        // back, because the motion never stopped.
        algorithm.reset()

        assertEquals(1, algorithm.feed(shake(1.2f, hz = 4f, peak = 25f)))
    }

    // ------------------------------------------------------------------
    // Motions that must not be mistaken for a shake
    // ------------------------------------------------------------------

    @Test
    fun `running is not a shake`() {
        // One sharp impact per stride, always in the same direction — the shape
        // real locomotion has. Stronger than the threshold and rhythmic, so only
        // the reversal rule stands between it and a false toggle.
        val fires = ShakeAlgorithm().feed(
            still(0.3f),
            footfalls(3f, hz = 3f, peak = 22f),
            still(0.4f),
        )

        assertEquals("running toggled the torch $fires times", 0, fires)
    }

    @Test
    fun `walking is not a shake`() {
        val fires = ShakeAlgorithm().feed(
            still(0.3f),
            footfalls(3f, hz = 2f, peak = 8f),
            still(0.4f),
        )

        assertEquals(0, fires)
    }

    @Test
    fun `setting the phone down is not a shake`() {
        val fires = ShakeAlgorithm().feed(
            still(0.3f),
            knock(0.4f, peak = 35f),
            still(0.6f),
        )

        assertEquals("a hard knock should not turn the light on", 0, fires)
    }

    @Test
    fun `a sustained push is not a shake`() {
        // A car accelerating, an escalator, a lift: plenty of gravity-free
        // acceleration, but it never comes back the other way.
        val fires = ShakeAlgorithm().feed(
            still(0.3f),
            sustained(3f, acceleration = 20f),
            still(0.4f),
        )

        assertEquals(0, fires)
    }

    @Test
    fun `a stationary phone is not a shake`() {
        // The regression this guards is the classic one: forgetting to remove
        // gravity makes 9.81 m/s² look like a permanent impulse.
        val fires = ShakeAlgorithm().feed(still(5f))

        assertEquals(0, fires)
    }

    @Test
    fun `a stationary phone held at an angle is not a shake`() {
        val fires = ShakeAlgorithm().feed(
            Motion(5f) { Sample(6.94f, 0f, 6.94f) }, // 45 degrees, still 1g total
        )

        assertEquals(0, fires)
    }

    @Test
    fun `slowly waving the phone about is not a shake`() {
        val fires = ShakeAlgorithm().feed(
            still(0.3f),
            shake(3f, hz = 1f, peak = 6f),
            still(0.4f),
        )

        assertEquals(0, fires)
    }

    // ------------------------------------------------------------------
    // Pocket guard
    // ------------------------------------------------------------------

    @Test
    fun `shaking in a pocket is ignored`() {
        val fires = ShakeAlgorithm().feed(
            still(0.3f),
            shake(2f, hz = 5f, peak = 40f, covered = true),
            still(0.5f),
        )

        assertEquals("a pocketed phone toggled the torch $fires times", 0, fires)
    }

    @Test
    fun `a pocketed shake does not fire the moment the phone comes out`() {
        val fires = ShakeAlgorithm().feedTimed(
            shake(1f, hz = 5f, peak = 40f, covered = true),
            still(1f),
        )

        assertEquals("the burst was banked up and released on uncovering", 0, fires.size)
    }

    @Test
    fun `detection resumes once the phone is out of the pocket`() {
        val fires = ShakeAlgorithm().feed(
            shake(1f, hz = 5f, peak = 40f, covered = true),
            still(0.4f),
            shake(0.7f, hz = 4f, peak = 25f),
            still(0.4f),
        )

        assertEquals(1, fires)
    }

    @Test
    fun `a slow shake below the impulse threshold never counts`() {
        // Guards the threshold itself: 12 m/s² is under the 14 m/s² bar, so no
        // impulse starts at all and the rate rule never gets a vote.
        val fires = ShakeAlgorithm().feed(
            still(0.3f),
            shake(2f, hz = 5f, peak = 12f),
            still(0.4f),
        )

        assertEquals(0, fires)
    }

    private companion object {
        /** When the shake phase begins in the timelines above, in seconds. */
        const val SHAKE_STARTS_AT = 0.4f
    }
}

// ----------------------------------------------------------------------
// Synthetic sensor streams
// ----------------------------------------------------------------------

/** One accelerometer reading: device-frame x/y/z in m/s², gravity included. */
private data class Sample(val x: Float, val y: Float, val z: Float)

/** Which way the phone is being shaken. */
private enum class DeviceAxis { X, Y, Z }

/**
 * A stretch of motion, plus what the proximity sensor reports during it. Each
 * motion is handed the time since *it* started, so a shake always begins from
 * rest rather than inheriting the phase of whatever came before.
 */
private class Motion(
    val seconds: Float,
    val covered: Boolean = false,
    val sample: (Float) -> Sample,
)

/** A phone lying face up and not moving: gravity and nothing else. */
private fun still(seconds: Float) = Motion(seconds) { Sample(0f, 0f, GRAVITY) }

/** A symmetric back-and-forth shake — what a deliberate one looks like. */
private fun shake(
    seconds: Float,
    hz: Float,
    peak: Float,
    axis: DeviceAxis = DeviceAxis.X,
    covered: Boolean = false,
) = Motion(seconds, covered) { t ->
    val acceleration = peak * sin(TWO_PI * hz * t)
    when (axis) {
        DeviceAxis.X -> Sample(acceleration, 0f, GRAVITY)
        DeviceAxis.Y -> Sample(0f, acceleration, GRAVITY)
        DeviceAxis.Z -> Sample(0f, 0f, GRAVITY + acceleration)
    }
}

/**
 * Running or stairs: one sharp impact per stride, never a mirrored one. Cubed so
 * the peak is spike-shaped like a heel strike instead of a smooth sine.
 */
private fun footfalls(seconds: Float, hz: Float, peak: Float) = Motion(seconds) { t ->
    val impact = sin(TWO_PI * hz * t).coerceAtLeast(0f)
    Sample(0f, 0f, GRAVITY + peak * impact * impact * impact)
}

/** A constant push that never reverses: a car, a lift, an escalator. */
private fun sustained(seconds: Float, acceleration: Float) =
    Motion(seconds) { Sample(acceleration, 0f, GRAVITY) }

/** The phone being set down on a table: one spike dying away in tens of ms. */
private fun knock(seconds: Float, peak: Float) =
    Motion(seconds) { t -> Sample(peak * exp(-t / KNOCK_DECAY_SECONDS), 0f, GRAVITY) }

/**
 * Concatenated motions addressed by absolute time, clamped at the ends so the
 * last sample of a phase and the first of the next cannot overlap.
 */
private class Timeline(private val motions: List<Motion>) {

    val seconds = motions.map { it.seconds }.sum()

    private val starts = motions.runningFold(0f) { total, motion -> total + motion.seconds }

    fun at(seconds: Float): Pair<Sample, Boolean> {
        var index = motions.lastIndex
        while (index > 0 && seconds < starts[index]) index--
        val motion = motions[index]
        val local = (seconds - starts[index]).coerceAtMost(motion.seconds)
        return motion.sample(local) to motion.covered
    }
}

/** Plays the motions through the algorithm and returns how many shakes it saw. */
private fun ShakeAlgorithm.feed(
    vararg motions: Motion,
    rateHz: Int = SAMPLE_RATE_HZ,
): Int = feedTimed(motions = motions, rateHz = rateHz).size

/** As [feed], but returns when each shake was recognised, in seconds. */
private fun ShakeAlgorithm.feedTimed(
    vararg motions: Motion,
    rateHz: Int = SAMPLE_RATE_HZ,
): List<Float> {
    val timeline = Timeline(motions.toList())
    val sampleCount = (timeline.seconds * rateHz).roundToInt()
    val nanosPerSample = NANOS_PER_SECOND / rateHz
    val firedAt = mutableListOf<Float>()

    repeat(sampleCount) { index ->
        val seconds = index / rateHz.toFloat()
        val (sample, isCovered) = timeline.at(seconds)
        covered = isCovered
        val fired = onAccelerometer(START_NANOS + index.toLong() * nanosPerSample, sample.x, sample.y, sample.z)
        if (fired) firedAt += seconds
    }

    return firedAt
}

private const val GRAVITY = 9.81f
private const val SAMPLE_RATE_HZ = 50
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val KNOCK_DECAY_SECONDS = 0.03f
private val TWO_PI = (2.0 * PI).toFloat()

/**
 * Sensor timestamps come from a monotonic clock with an arbitrary origin, so the
 * tests start five seconds in rather than at zero to keep that honest.
 */
private const val START_NANOS = 5_000_000_000L
