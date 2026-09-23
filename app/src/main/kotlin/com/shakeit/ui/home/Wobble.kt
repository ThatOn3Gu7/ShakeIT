package com.shakeit.ui.home

import androidx.compose.animation.core.CubicBezierEasing

/**
 * The hero's shake animation, ported from the prototype:
 * ```css
 * .hero.shaking { animation: wobble .4s ease }
 * @keyframes wobble {
 *   0%, 100% { transform: rotate(0) }
 *   20%      { transform: rotate(-7deg) translateX(-3px) }
 *   45%      { transform: rotate(6deg)  translateX(3px) }
 *   70%      { transform: rotate(-4deg) }
 * }
 * ```
 * and from its handler, which toggles 380ms into the 400ms animation.
 *
 * Kept free of Compose UI so `WobbleTest` can assert the curve on the JVM.
 * [CubicBezierEasing] is pure maths and safe to use there.
 */
internal object Wobble {

    /** `animation: wobble .4s ease` */
    const val DURATION_MS = 400

    /** `setTimeout(() => { ...; toggle() }, 380)` */
    const val TOGGLE_AT_MS = 380L

    /**
     * CSS `ease`. A CSS `animation-timing-function` applies *per keyframe
     * interval* rather than across the whole animation, so [sample] eases each
     * segment on its own local fraction.
     */
    val EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** Keyframe offsets: 0%, 20%, 45%, 70%, 100%. */
    val TIMES = floatArrayOf(0f, 0.20f, 0.45f, 0.70f, 1f)

    /** `rotate(...)` at each keyframe, in degrees. */
    val ROTATION = floatArrayOf(0f, -7f, 6f, -4f, 0f)

    /** `translateX(...)` at each keyframe, in dp. The 70% frame has none. */
    val TRANSLATION_X = floatArrayOf(0f, -3f, 3f, 0f, 0f)

    /**
     * Interpolates [values] at [progress] (0f..1f) across [times], easing every
     * segment separately the way CSS does.
     */
    fun sample(times: FloatArray, values: FloatArray, progress: Float): Float {
        require(times.size == values.size) { "times and values must be the same length" }
        require(times.size >= 2) { "at least two keyframes are required" }

        val p = progress.coerceIn(0f, 1f)
        var index = 0
        while (index < times.lastIndex - 1 && p >= times[index + 1]) index++

        val span = times[index + 1] - times[index]
        val local = if (span <= 0f) 1f else ((p - times[index]) / span).coerceIn(0f, 1f)
        val eased = EASE.transform(local)
        return values[index] + (values[index + 1] - values[index]) * eased
    }

    fun rotationAt(progress: Float): Float = sample(TIMES, ROTATION, progress)

    fun translationXAt(progress: Float): Float = sample(TIMES, TRANSLATION_X, progress)
}
