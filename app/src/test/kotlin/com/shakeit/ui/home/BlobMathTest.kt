package com.shakeit.ui.home

import com.shakeit.assertClose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Guards the blob maths against drifting away from the prototype.
 *
 * The expected values here are recomputed in `Double` from the JavaScript in
 * `debug/mockup/shakeit-prototype.html`, so the port is checked against an
 * independent transcription of the original rather than against itself.
 */
class BlobMathTest {

    private companion object {
        /** Same literal the production code uses, spelled out here. */
        val TWO_PI = (PI * 2).toFloat()
        const val CANVAS_DP = 190f
        const val VIEWBOX_UNITS = 200f

        /** `cur.X += (target.X - cur.X) * .1`, run once per rAF frame. */
        fun referenceStep(current: Double, target: Double): Double =
            current + (target - current) * 0.1

        /**
         * ```js
         * r = cur.radius * (1 + cur.amp*Math.sin(cur.lobes*a + wobble*1.3)
         *                     + .03*Math.sin(3*a - wobble*.7))
         * ```
         */
        fun referenceRadius(
            lobes: Double,
            amplitude: Double,
            radius: Double,
            wobble: Double,
            angle: Double,
        ): Double = radius * (
            1 +
                amplitude * sin(lobes * angle + wobble * 1.3) +
                0.03 * sin(3 * angle - wobble * 0.7)
            )

        /** Theoretical worst-case distance from the centre, in viewBox units. */
        fun maximumRadius(config: BlobConfig): Float =
            config.radius * (1f + config.amplitude + BlobMath.SECONDARY_AMPLITUDE)
    }

    // ------------------------------------------------------------------
    // The prototype's literals
    // ------------------------------------------------------------------

    @Test
    fun `constants are the prototype literals`() {
        assertEquals(40, BlobMath.SAMPLE_COUNT)
        assertEquals(6, BlobMath.FLOATS_PER_SEGMENT)
        assertClose(0.1f, BlobMath.SHAPE_LERP, 0f)
        assertClose(0.018f, BlobMath.WOBBLE_SPEED, 0f)
        assertClose(1.3f, BlobMath.WOBBLE_PHASE_SCALE, 0f)
        assertClose(3f, BlobMath.SECONDARY_LOBES, 0f)
        assertClose(0.03f, BlobMath.SECONDARY_AMPLITUDE, 0f)
        assertClose(0.7f, BlobMath.SECONDARY_PHASE_SCALE, 0f)
    }

    @Test
    fun `off and on configs are OFF_CFG and ON_CFG`() {
        assertEquals(BlobConfig(lobes = 4f, amplitude = 0.16f, radius = 82f), BlobOff)
        assertEquals(BlobConfig(lobes = 10f, amplitude = 0.085f, radius = 90f), BlobOn)
    }

    @Test
    fun `the lit shape is bigger, tighter and smoother than the resting one`() {
        assertTrue(BlobOn.radius > BlobOff.radius)
        assertTrue(BlobOn.lobes > BlobOff.lobes)
        assertTrue(BlobOn.amplitude < BlobOff.amplitude)
    }

    // ------------------------------------------------------------------
    // The per-frame easing
    // ------------------------------------------------------------------

    @Test
    fun `advance at one frame is exactly the prototype lerp`() {
        val next = BlobMath.advance(BlobOff, BlobOn, frames = 1f)
        assertClose(
            referenceStep(BlobOff.lobes.toDouble(), BlobOn.lobes.toDouble()).toFloat(),
            next.lobes,
            1e-5f,
            "lobes",
        )
        assertClose(
            referenceStep(BlobOff.amplitude.toDouble(), BlobOn.amplitude.toDouble()).toFloat(),
            next.amplitude,
            1e-6f,
            "amplitude",
        )
        assertClose(
            referenceStep(BlobOff.radius.toDouble(), BlobOn.radius.toDouble()).toFloat(),
            next.radius,
            1e-4f,
            "radius",
        )
    }

    @Test
    fun `advance with no elapsed frames changes nothing`() {
        val mid = BlobConfig(lobes = 7f, amplitude = 0.12f, radius = 86f)
        assertEquals(mid, BlobMath.advance(mid, BlobOn, frames = 0f))
    }

    @Test
    fun `advance approaches the target monotonically and settles on it`() {
        var config = BlobOff
        var previous = distance(config, BlobOn)
        repeat(120) {
            config = BlobMath.advance(config, BlobOn, frames = 1f)
            val current = distance(config, BlobOn)
            assertTrue(
                "advance moved away from the target: $previous -> $current",
                current <= previous + 1e-6f,
            )
            previous = current
        }
        assertClose(BlobOn.lobes, config.lobes, 1e-2f, "lobes")
        assertClose(BlobOn.amplitude, config.amplitude, 1e-3f, "amplitude")
        assertClose(BlobOn.radius, config.radius, 1e-2f, "radius")
    }

    @Test
    fun `advance is frame-rate independent in the exponential sense`() {
        // Two 1-frame steps must land exactly where one 2-frame step lands.
        // That identity is what makes the loop resolution independent.
        val twice = BlobMath.advance(
            BlobMath.advance(BlobOff, BlobOn, 1f),
            BlobOn,
            1f,
        )
        val combined = BlobMath.advance(BlobOff, BlobOn, 2f)
        assertClose(twice.radius, combined.radius, 1e-4f, "radius")
        assertClose(twice.lobes, combined.lobes, 1e-4f, "lobes")
        assertClose(twice.amplitude, combined.amplitude, 1e-6f, "amplitude")
    }

    @Test
    fun `easeFactor reproduces repeated per-frame lerping`() {
        val perFrame = BlobMath.SHAPE_LERP
        assertClose(perFrame, BlobMath.easeFactor(perFrame, 1f), 1e-6f, "one frame")
        assertClose(0f, BlobMath.easeFactor(perFrame, 0f), 0f, "no frames")
        assertClose(
            1f - (1f - perFrame) * (1f - perFrame),
            BlobMath.easeFactor(perFrame, 2f),
            1e-6f,
            "two frames",
        )
        assertTrue(BlobMath.easeFactor(perFrame, 4f) > BlobMath.easeFactor(perFrame, 2f))
        assertTrue(BlobMath.easeFactor(perFrame, 8f) < 1f)
    }

    @Test
    fun `advanceOnMix lags the shape the way the css transition does`() {
        assertClose(BlobMath.ON_STATE_LERP, BlobMath.advanceOnMix(0f, 1f, 1f), 1e-6f)
        // The colour and glow transition is .5s while the shape lerp settles in
        // about .35s, so per frame the mix must cover less ground than the shape.
        assertTrue(
            "the fill must lag the shape",
            BlobMath.easeFactor(BlobMath.ON_STATE_LERP, 1f) <
                BlobMath.easeFactor(BlobMath.SHAPE_LERP, 1f),
        )
        assertClose(1f, BlobMath.advanceOnMix(1f, 1f, 4f), 0f, "already lit")
        assertClose(0f, BlobMath.advanceOnMix(0f, 0f, 4f), 0f, "already resting")

        val eased = BlobMath.advanceOnMix(0.5f, 1f, 1f)
        assertTrue("mix must move towards its target", eased > 0.5f)
        assertTrue("mix must not overshoot its target", eased < 1f)
    }

    // ------------------------------------------------------------------
    // The frame clock
    // ------------------------------------------------------------------

    @Test
    fun `frameScale counts the first frame as one reference frame`() {
        assertClose(1f, BlobMath.frameScale(previousFrameNanos = -1L, frameNanos = 0L), 0f)
    }

    @Test
    fun `frameScale converts elapsed nanos to 60fps frames`() {
        assertClose(1f, BlobMath.frameScale(0L, 16_666_667L), 1e-3f, "60Hz")
        assertClose(2f, BlobMath.frameScale(0L, 33_333_334L), 1e-3f, "30Hz")
        assertClose(0.5f, BlobMath.frameScale(0L, 8_333_333L), 1e-3f, "120Hz")
    }

    @Test
    fun `frameScale clamps stalls and ignores out-of-order stamps`() {
        // A one second stall must not teleport the shape: four frames at most.
        assertClose(4f, BlobMath.frameScale(0L, 1_000_000_000L), 0f, "stall")
        // And a frame stamp that goes backwards must not run the loop in reverse.
        assertClose(0f, BlobMath.frameScale(100L, 50L), 0f, "backwards")
    }

    // ------------------------------------------------------------------
    // The outline
    // ------------------------------------------------------------------

    @Test
    fun `radiusAt matches the prototype formula`() {
        val configs = listOf(BlobOff, BlobOn, BlobConfig(7f, 0.12f, 86f))
        for (config in configs) {
            for (wobble in doubleArrayOf(0.0, 0.35, 2.7, 11.9)) {
                for (i in 0 until BlobMath.SAMPLE_COUNT) {
                    val angle = i.toFloat() / BlobMath.SAMPLE_COUNT * TWO_PI
                    val expected = referenceRadius(
                        lobes = config.lobes.toDouble(),
                        amplitude = config.amplitude.toDouble(),
                        radius = config.radius.toDouble(),
                        wobble = wobble,
                        angle = angle.toDouble(),
                    )
                    assertClose(
                        expected.toFloat(),
                        BlobMath.radiusAt(config, wobble.toFloat(), angle),
                        1e-3f,
                        "radius for $config at angle $angle, wobble $wobble",
                    )
                }
            }
        }
    }

    @Test
    fun `off shape has four lobes and the lit shape has ten`() {
        assertEquals(4, countLocalMaxima(BlobOff))
        assertEquals(10, countLocalMaxima(BlobOn))
    }

    @Test
    fun `the outline stays inside the viewBox the canvas is scaled from`() {
        // 190dp of canvas for a 200-unit viewBox, so the lit blob at full
        // amplitude reaches 100.35 units — a third of a unit of overshoot, which
        // the prototype tolerates too and the glow needs the headroom anyway.
        for (config in listOf(BlobOff, BlobOn)) {
            val maximum = maximumRadius(config)
            assertTrue("$config reaches $maximum units from the centre", maximum <= 100.5f)
        }
    }

    @Test
    fun `sample lays 40 points around the centre in canvas pixels`() {
        val unit = CANVAS_DP / VIEWBOX_UNITS
        val centreX = CANVAS_DP / 2f
        val centreY = CANVAS_DP / 2f
        val wobble = 1.234f
        val out = FloatArray(BlobMath.SAMPLE_COUNT * 2)

        BlobMath.sample(BlobOn, wobble, unit, centreX, centreY, out)

        for (i in 0 until BlobMath.SAMPLE_COUNT) {
            val x = out[i * 2]
            val y = out[i * 2 + 1]
            val angle = i.toFloat() / BlobMath.SAMPLE_COUNT * TWO_PI
            val radius = BlobMath.radiusAt(BlobOn, wobble, angle)

            assertClose(centreX + radius * cos(angle) * unit, x, 1e-3f, "x[$i]")
            assertClose(centreY + radius * sin(angle) * unit, y, 1e-3f, "y[$i]")
            assertClose(radius * unit, hypot(x - centreX, y - centreY), 1e-3f, "distance[$i]")
        }
    }

    @Test
    fun `sample fills the caller buffer with finite coordinates`() {
        val out = FloatArray(BlobMath.SAMPLE_COUNT * 2)
        BlobMath.sample(BlobOff, wobble = 3.5f, unit = 0.95f, centreX = 95f, centreY = 95f, out = out)

        assertEquals(BlobMath.SAMPLE_COUNT * 2, out.size)
        assertTrue("sample produced a non-finite coordinate", out.all { it.isFinite() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sample refuses an undersized buffer`() {
        BlobMath.sample(
            config = BlobOff,
            wobble = 0f,
            unit = 1f,
            centreX = 0f,
            centreY = 0f,
            out = FloatArray(BlobMath.SAMPLE_COUNT * 2 - 1),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `closedSpline refuses an undersized buffer`() {
        BlobMath.closedSpline(
            FloatArray(BlobMath.SAMPLE_COUNT * 2),
            FloatArray(BlobMath.SAMPLE_COUNT * BlobMath.FLOATS_PER_SEGMENT - 1),
        )
    }

    // ------------------------------------------------------------------
    // smoothPath()
    // ------------------------------------------------------------------

    @Test
    fun `closedSpline reproduces the prototype smoothPath tangents`() {
        val n = BlobMath.SAMPLE_COUNT
        val samples = FloatArray(n * 2)
        BlobMath.sample(BlobOff, wobble = 0.42f, unit = 1f, centreX = 0f, centreY = 0f, out = samples)

        val segments = FloatArray(n * BlobMath.FLOATS_PER_SEGMENT)
        BlobMath.closedSpline(samples, segments)

        fun x(i: Int): Float = samples[wrap(i, n) * 2]
        fun y(i: Int): Float = samples[wrap(i, n) * 2 + 1]

        for (i in 0 until n) {
            val o = i * BlobMath.FLOATS_PER_SEGMENT
            assertClose(x(i) + (x(i + 1) - x(i - 1)) / 6f, segments[o], 1e-4f, "c1x[$i]")
            assertClose(y(i) + (y(i + 1) - y(i - 1)) / 6f, segments[o + 1], 1e-4f, "c1y[$i]")
            assertClose(x(i + 1) - (x(i + 2) - x(i)) / 6f, segments[o + 2], 1e-4f, "c2x[$i]")
            assertClose(y(i + 1) - (y(i + 2) - y(i)) / 6f, segments[o + 3], 1e-4f, "c2y[$i]")
            assertClose(x(i + 1), segments[o + 4], 1e-4f, "endX[$i]")
            assertClose(y(i + 1), segments[o + 5], 1e-4f, "endY[$i]")
        }
    }

    @Test
    fun `closedSpline closes the loop back onto the moveTo point`() {
        val n = BlobMath.SAMPLE_COUNT
        val samples = FloatArray(n * 2) { index -> 10f + index }
        val segments = FloatArray(n * BlobMath.FLOATS_PER_SEGMENT)

        BlobMath.closedSpline(samples, segments)

        val last = (n - 1) * BlobMath.FLOATS_PER_SEGMENT
        assertClose(samples[0], segments[last + 4], 1e-4f, "closing x")
        assertClose(samples[1], segments[last + 5], 1e-4f, "closing y")
        // The first segment ends on sample 1, which is where the path continues.
        assertClose(samples[2], segments[4], 1e-4f, "first segment endpoint x")
        assertClose(samples[3], segments[5], 1e-4f, "first segment endpoint y")
    }

    @Test
    fun `closedSpline is translation invariant`() {
        val n = BlobMath.SAMPLE_COUNT
        val atOrigin = FloatArray(n * 2)
        val shifted = FloatArray(n * 2)
        BlobMath.sample(BlobOn, wobble = 2f, unit = 1f, centreX = 0f, centreY = 0f, out = atOrigin)
        BlobMath.sample(BlobOn, wobble = 2f, unit = 1f, centreX = 40f, centreY = -25f, out = shifted)

        val originSegments = FloatArray(n * BlobMath.FLOATS_PER_SEGMENT)
        val shiftedSegments = FloatArray(n * BlobMath.FLOATS_PER_SEGMENT)
        BlobMath.closedSpline(atOrigin, originSegments)
        BlobMath.closedSpline(shifted, shiftedSegments)

        for (i in originSegments.indices) {
            val dx = if (i % 2 == 0) 40f else -25f
            assertClose(
                originSegments[i] + dx,
                shiftedSegments[i],
                1e-3f,
                "segment float $i",
            )
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun wrap(index: Int, size: Int): Int = ((index % size) + size) % size

    private fun distance(config: BlobConfig, target: BlobConfig): Float = maxOf(
        abs(config.lobes - target.lobes),
        abs(config.amplitude - target.amplitude),
        abs(config.radius - target.radius),
    )

    /**
     * Counts the peaks of the radius over one full turn. The secondary
     * `.03*sin(3a)` term is far too small to add peaks of its own.
     */
    private fun countLocalMaxima(config: BlobConfig): Int {
        val steps = 4000
        val radii = DoubleArray(steps) { i ->
            val angle = i.toDouble() / steps * PI * 2
            BlobMath.radiusAt(config, wobble = 0f, angle = angle.toFloat()).toDouble()
        }
        return (0 until steps).count { i ->
            radii[i] > radii[wrap(i - 1, steps)] && radii[i] > radii[wrap(i + 1, steps)]
        }
    }
}
