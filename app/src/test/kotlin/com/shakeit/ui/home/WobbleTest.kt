package com.shakeit.ui.home

import com.shakeit.assertClose
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the hero's shake animation against the prototype:
 * ```css
 * .hero.shaking { animation: wobble .4s ease }
 * @keyframes wobble { ... }
 * ```
 * plus the handler that removes `.shaking` and toggles 380ms later.
 *
 * [Wobble] is deliberately free of Compose UI so this runs as an ordinary JVM
 * test; `CubicBezierEasing` is pure maths and is cross-checked here against an
 * independent solver written in `Double`.
 */
class WobbleTest {

    // ------------------------------------------------------------------
    // The prototype's numbers
    // ------------------------------------------------------------------

    @Test
    fun `timing matches the prototype handler`() {
        assertEquals(400, Wobble.DURATION_MS)
        assertEquals(380L, Wobble.TOGGLE_AT_MS)
        assertTrue(
            "the toggle must land inside the animation, not after it",
            Wobble.TOGGLE_AT_MS < Wobble.DURATION_MS,
        )
    }

    @Test
    fun `keyframes are the css wobble keyframes`() {
        assertArrayEquals(floatArrayOf(0f, 0.20f, 0.45f, 0.70f, 1f), Wobble.TIMES, 0f)
        assertArrayEquals(floatArrayOf(0f, -7f, 6f, -4f, 0f), Wobble.ROTATION, 0f)
        assertArrayEquals(floatArrayOf(0f, -3f, 3f, 0f, 0f), Wobble.TRANSLATION_X, 0f)
    }

    // ------------------------------------------------------------------
    // The curve
    // ------------------------------------------------------------------

    @Test
    fun `curve is at rest at both ends of the animation`() {
        assertClose(0f, Wobble.rotationAt(0f), message = "rotation at 0%")
        assertClose(0f, Wobble.rotationAt(1f), message = "rotation at 100%")
        assertClose(0f, Wobble.translationXAt(0f), message = "translation at 0%")
        assertClose(0f, Wobble.translationXAt(1f), message = "translation at 100%")
    }

    @Test
    fun `curve passes exactly through every keyframe`() {
        for (index in Wobble.TIMES.indices) {
            val progress = Wobble.TIMES[index]
            assertClose(
                Wobble.ROTATION[index],
                Wobble.rotationAt(progress),
                message = "rotation at ${(progress * 100).toInt()}%",
            )
            assertClose(
                Wobble.TRANSLATION_X[index],
                Wobble.translationXAt(progress),
                message = "translation at ${(progress * 100).toInt()}%",
            )
        }
    }

    @Test
    fun `the easing is css ease, not a linear ramp`() {
        for (i in 0..10) {
            val t = i / 10f
            assertClose(
                referenceCubicBezier(0.25, 0.1, 0.25, 1.0, t.toDouble()),
                Wobble.EASE.transform(t).toDouble(),
                delta = 1e-3,
                message = "cubic-bezier(.25,.1,.25,1) at $t",
            )
        }
        // A linear ramp would be at 50% halfway through; `ease` is well ahead.
        assertTrue(Wobble.EASE.transform(0.5f) > 0.6f)
    }

    @Test
    fun `each keyframe interval is eased on its own local fraction`() {
        // CSS applies `animation-timing-function` per interval, so halfway into
        // 0%..20% the hero is already 80% of the way to -7deg.
        val eased = Wobble.EASE.transform(0.5f)
        assertClose(-7f * eased, Wobble.rotationAt(0.10f), message = "10% of the animation")
        assertClose(-3f * eased, Wobble.translationXAt(0.10f), message = "10% translation")

        // And the same holds inside the 45%..70% interval.
        val localHalf = 0.45f + (0.70f - 0.45f) / 2f
        assertClose(
            6f + (-4f - 6f) * eased,
            Wobble.rotationAt(localHalf),
            delta = 1e-3f,
            message = "halfway from 45% to 70%",
        )
    }

    @Test
    fun `curve swings negative, positive, negative as the keyframes say`() {
        assertTrue(Wobble.rotationAt(0.10f) < 0f)
        assertTrue(Wobble.rotationAt(0.30f) > 0f)
        assertTrue(Wobble.rotationAt(0.60f) < 0f)
        assertTrue(Wobble.translationXAt(0.30f) > 0f)
        assertTrue(Wobble.translationXAt(0.10f) < 0f)
    }

    @Test
    fun `curve stays inside the keyframe envelope and never goes nan`() {
        for (i in 0..1000) {
            val progress = i / 1000f
            val rotation = Wobble.rotationAt(progress)
            val translation = Wobble.translationXAt(progress)

            assertFalse("rotation went NaN at $progress", rotation.isNaN())
            assertFalse("translation went NaN at $progress", translation.isNaN())
            assertTrue("rotation $rotation out of range at $progress", rotation in -7f..6f)
            assertTrue("translation $translation out of range at $progress", translation in -3f..3f)
        }
    }

    @Test
    fun `the 70 percent keyframe has no horizontal offset`() {
        // 70% and 100% are both 0, so the last interval must stay still
        // horizontally while the rotation is still settling.
        for (i in 0..20) {
            val progress = 0.70f + 0.30f * (i / 20f)
            assertClose(0f, Wobble.translationXAt(progress), message = "translation at $progress")
        }
    }

    @Test
    fun `progress outside the animation is clamped to its ends`() {
        assertClose(Wobble.rotationAt(0f), Wobble.rotationAt(-0.5f), message = "before the start")
        assertClose(Wobble.rotationAt(1f), Wobble.rotationAt(1.5f), message = "after the end")
        assertClose(0f, Wobble.rotationAt(-3f), message = "far before the start")
        assertClose(0f, Wobble.translationXAt(42f), message = "far after the end")
    }

    // ------------------------------------------------------------------
    // Guard rails
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched keyframe arrays are rejected`() {
        Wobble.sample(times = floatArrayOf(0f, 1f), values = floatArrayOf(0f), progress = 0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fewer than two keyframes are rejected`() {
        Wobble.sample(times = floatArrayOf(0f), values = floatArrayOf(0f), progress = 0.5f)
    }

    @Test
    fun `a degenerate keyframe interval does not divide by zero`() {
        // Two keyframes sharing a timestamp leave a zero-length interval; the
        // sampler must fall through to the later value instead of yielding NaN.
        val atEnd = Wobble.sample(
            times = floatArrayOf(0f, 1f, 1f),
            values = floatArrayOf(0f, 10f, -10f),
            progress = 1f,
        )
        assertFalse("degenerate interval produced NaN", atEnd.isNaN())
        assertClose(-10f, atEnd, message = "zero-length final interval")

        // And a repeated timestamp mid-curve still resolves to a real number.
        val midCurve = Wobble.sample(
            times = floatArrayOf(0f, 0.5f, 0.5f, 1f),
            values = floatArrayOf(0f, 10f, -10f, 0f),
            progress = 0.5f,
        )
        assertFalse("repeated timestamp produced NaN", midCurve.isNaN())
        assertClose(-10f, midCurve, message = "repeated mid-curve timestamp")
    }

    // ------------------------------------------------------------------
    // Test oracle
    // ------------------------------------------------------------------

    /**
     * Solves `cubic-bezier(x1, y1, x2, y2)` the way a browser does for a CSS
     * timing function: find the parameter whose x equals [t], then read y.
     * Bisection in `Double` is slower than Compose's solver but is an
     * independent implementation, which is the point.
     */
    private fun referenceCubicBezier(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        t: Double,
    ): Double {
        if (t <= 0.0) return 0.0
        if (t >= 1.0) return 1.0

        fun component(a1: Double, a2: Double, u: Double): Double {
            val c = 3 * a1
            val b = 3 * (a2 - a1) - c
            val a = 1 - c - b
            return ((a * u + b) * u + c) * u
        }

        var low = 0.0
        var high = 1.0
        var u = 0.5
        repeat(80) {
            val x = component(x1, x2, u)
            if (abs(x - t) < 1e-14) return component(y1, y2, u)
            if (x < t) low = u else high = u
            u = (low + high) / 2
        }
        return component(y1, y2, u)
    }
}
