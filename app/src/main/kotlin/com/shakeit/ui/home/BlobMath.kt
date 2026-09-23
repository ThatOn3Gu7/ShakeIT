package com.shakeit.ui.home

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** `{lobes, amp, radius}` — the prototype's `OFF_CFG` / `ON_CFG` shape. */
internal data class BlobConfig(val lobes: Float, val amplitude: Float, val radius: Float)

/** `OFF_CFG={lobes:4,amp:.16,radius:82}` */
internal val BlobOff = BlobConfig(lobes = 4f, amplitude = 0.16f, radius = 82f)

/** `ON_CFG={lobes:10,amp:.085,radius:90}` */
internal val BlobOn = BlobConfig(lobes = 10f, amplitude = 0.085f, radius = 90f)

/**
 * The blob's maths, ported from the `frame()` loop in
 * `debug/mockup/shakeit-prototype.html`:
 * ```js
 * cur.X += (target.X - cur.X) * .1;   // once per rAF frame
 * wobble += .018;
 * r = cur.radius * (1 + cur.amp*Math.sin(cur.lobes*a + wobble*1.3)
 *                     + .03*Math.sin(3*a - wobble*.7));
 * ```
 * sampled at 40 points and closed with the prototype's Catmull-Rom to cubic
 * Bezier `smoothPath()`.
 *
 * This file deliberately has no Compose or Android dependency beyond
 * `kotlin.math`, so it runs on the JVM and is covered by `BlobMathTest`, which
 * asserts it against a transcription of the JavaScript original.
 * [BlobCanvas] only turns the numbers into a [androidx.compose.ui.graphics.Path].
 */
internal object BlobMath {

    /** `const N=40` */
    const val SAMPLE_COUNT = 40

    /** c1x, c1y, c2x, c2y, endX, endY per spline segment. */
    const val FLOATS_PER_SEGMENT = 6

    /** `cur += (target - cur) * .1` at the prototype's 60fps. */
    const val SHAPE_LERP = 0.1f

    /**
     * The CSS runs `transition: fill .5s ease, filter .5s ease`, so colour and
     * glow settle a little behind the shape itself.
     */
    const val ON_STATE_LERP = 0.065f

    /** `wobble += .018` */
    const val WOBBLE_SPEED = 0.018f
    const val WOBBLE_PHASE_SCALE = 1.3f
    const val SECONDARY_LOBES = 3f
    const val SECONDARY_AMPLITUDE = 0.03f
    const val SECONDARY_PHASE_SCALE = 0.7f

    private const val NANOS_PER_REFERENCE_FRAME = 16_666_667f
    private val TWO_PI = (PI * 2).toFloat()

    /**
     * Elapsed time expressed in 60fps reference frames, so a 90Hz or 120Hz
     * panel morphs at the same speed as the prototype's `requestAnimationFrame`
     * loop. The first frame after the loop starts counts as one frame.
     */
    fun frameScale(previousFrameNanos: Long, frameNanos: Long): Float =
        if (previousFrameNanos < 0L) {
            1f
        } else {
            ((frameNanos - previousFrameNanos) / NANOS_PER_REFERENCE_FRAME).coerceIn(0f, 4f)
        }

    /**
     * Exponential approach with the same time constant as `+= (target - cur) * k`
     * at 60fps, but scaled by [frames]. At `frames == 1f` this returns exactly
     * [perFrame], which is what keeps the port frame-identical to the original.
     */
    fun easeFactor(perFrame: Float, frames: Float): Float = 1f - (1f - perFrame).pow(frames)

    /** One `cur += (target - cur) * .1` step for the three shape parameters. */
    fun advance(current: BlobConfig, target: BlobConfig, frames: Float): BlobConfig {
        val k = easeFactor(SHAPE_LERP, frames)
        return BlobConfig(
            lobes = current.lobes + (target.lobes - current.lobes) * k,
            amplitude = current.amplitude + (target.amplitude - current.amplitude) * k,
            radius = current.radius + (target.radius - current.radius) * k,
        )
    }

    /** One step of the fill/glow mix, which eases more slowly than the shape. */
    fun advanceOnMix(current: Float, target: Float, frames: Float): Float =
        current + (target - current) * easeFactor(ON_STATE_LERP, frames)

    /**
     * `r = cur.radius * (1 + cur.amp*sin(cur.lobes*a + wobble*1.3) + .03*sin(3*a - wobble*.7))`
     */
    fun radiusAt(config: BlobConfig, wobble: Float, angle: Float): Float =
        config.radius * (
            1f +
                config.amplitude * sin(config.lobes * angle + wobble * WOBBLE_PHASE_SCALE) +
                SECONDARY_AMPLITUDE * sin(SECONDARY_LOBES * angle - wobble * SECONDARY_PHASE_SCALE)
            )

    /**
     * Writes [SAMPLE_COUNT] `(x, y)` pairs into [out] (which must hold at least
     * [SAMPLE_COUNT] * 2 floats), mapped from viewBox units — where the SVG is
     * `-100 -100 200 200` — into canvas pixels.
     */
    fun sample(
        config: BlobConfig,
        wobble: Float,
        unit: Float,
        centreX: Float,
        centreY: Float,
        out: FloatArray,
    ) {
        require(out.size >= SAMPLE_COUNT * 2) { "out must hold ${SAMPLE_COUNT * 2} floats" }
        for (i in 0 until SAMPLE_COUNT) {
            val angle = i.toFloat() / SAMPLE_COUNT * TWO_PI
            val r = radiusAt(config, wobble, angle)
            out[i * 2] = centreX + r * cos(angle) * unit
            out[i * 2 + 1] = centreY + r * sin(angle) * unit
        }
    }

    /**
     * Port of `smoothPath(pts)`: the closed Catmull-Rom spline through
     * [samples] converted to cubic Beziers with the usual `/6` tangent scaling.
     *
     * Writes [SAMPLE_COUNT] * [FLOATS_PER_SEGMENT] floats into [out] — for each
     * segment `i`: `c1x, c1y, c2x, c2y, endX, endY`. The path's `moveTo` point
     * is simply `samples[0], samples[1]`.
     */
    fun closedSpline(samples: FloatArray, out: FloatArray) {
        val n = samples.size / 2
        require(out.size >= n * FLOATS_PER_SEGMENT) {
            "out must hold ${n * FLOATS_PER_SEGMENT} floats"
        }

        fun x(i: Int): Float {
            val k = ((i % n) + n) % n
            return samples[k * 2]
        }

        fun y(i: Int): Float {
            val k = ((i % n) + n) % n
            return samples[k * 2 + 1]
        }

        for (i in 0 until n) {
            val o = i * FLOATS_PER_SEGMENT
            out[o] = x(i) + (x(i + 1) - x(i - 1)) / 6f
            out[o + 1] = y(i) + (y(i + 1) - y(i - 1)) / 6f
            out[o + 2] = x(i + 1) - (x(i + 2) - x(i)) / 6f
            out[o + 3] = y(i + 1) - (y(i + 2) - y(i)) / 6f
            out[o + 4] = x(i + 1)
            out[o + 5] = y(i + 1)
        }
    }
}
