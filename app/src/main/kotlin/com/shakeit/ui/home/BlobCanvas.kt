package com.shakeit.ui.home

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The morphing hero blob (`.hero-blob` + `#blobPath` in the prototype).
 *
 * This is a direct port of the prototype's `frame()` loop:
 * ```js
 * const OFF_CFG={lobes:4,amp:.16,radius:82}, ON_CFG={lobes:10,amp:.085,radius:90};
 * cur.X += (target.X - cur.X) * .1;      // once per rAF frame
 * wobble += .018;
 * r = cur.radius * (1 + cur.amp*Math.sin(cur.lobes*a + wobble*1.3)
 *                     + .03*Math.sin(3*a - wobble*.7));
 * ```
 * sampled at 40 points around the circle and closed with the prototype's
 * Catmull-Rom → cubic Bézier `smoothPath()`, inside the SVG's
 * `viewBox="-100 -100 200 200"`.
 *
 * Coordinates are the prototype's viewBox units where 1 unit == canvas/200, so
 * at the CSS size of 190dp the blob spans 78–86dp — the same ratio as the web
 * version.
 */

/** `{lobes, amp, radius}` */
@Immutable
internal data class BlobConfig(val lobes: Float, val amplitude: Float, val radius: Float)

/** `OFF_CFG` */
internal val BlobOff = BlobConfig(lobes = 4f, amplitude = 0.16f, radius = 82f)

/** `ON_CFG` */
internal val BlobOn = BlobConfig(lobes = 10f, amplitude = 0.085f, radius = 90f)

/** `.hero-blob { width: 190px; height: 190px }` */
internal val BlobSize = 190.dp

private const val SAMPLE_COUNT = 40
private const val WOBBLE_SPEED = 0.018f
private const val WOBBLE_PHASE_SCALE = 1.3f
private const val SECONDARY_LOBES = 3f
private const val SECONDARY_AMPLITUDE = 0.03f
private const val SECONDARY_PHASE_SCALE = 0.7f

/** `cur += (target - cur) * .1` at the prototype's 60fps. */
private const val SHAPE_LERP = 0.1f

/**
 * The CSS runs `transition: fill .5s ease, filter .5s ease`, so the colour and
 * the glow settle a little behind the shape itself.
 */
private const val ON_STATE_LERP = 0.065f

private const val NANOS_PER_REFERENCE_FRAME = 16_666_667f

// Not `const`: a `toFloat()` call is not a compile-time constant expression.
private val TWO_PI = (PI * 2).toFloat()

/** `drop-shadow(0 0 22px rgba(var(--glow), .55))` */
private const val GLOW_BLUR_UNITS = 22f
private const val GLOW_ALPHA = 0.55f

/**
 * Live blob parameters.
 *
 * These are snapshot state so that writing them from the frame loop invalidates
 * the draw pass, but they are only ever *read* inside a draw block — never
 * during composition — which keeps the per-frame churn out of recomposition.
 */
@Stable
internal class BlobShape {
    var lobes by mutableFloatStateOf(BlobOff.lobes)
    var amplitude by mutableFloatStateOf(BlobOff.amplitude)
    var radius by mutableFloatStateOf(BlobOff.radius)
    var wobble by mutableFloatStateOf(0f)

    /** 0f = resting/OFF, 1f = lit/ON. Drives both the fill colour and the glow. */
    var onMix by mutableFloatStateOf(0f)
}

/**
 * Runs the blob's frame loop.
 *
 * @param animate false freezes the loop at its current phase. Home passes this
 *   so the blob stops burning frames while Settings covers it and resumes
 *   exactly where it left off — the prototype keeps a single
 *   `requestAnimationFrame` loop alive for the whole page lifetime.
 */
@Composable
internal fun rememberBlobShape(on: Boolean, animate: Boolean): BlobShape {
    val shape = remember { BlobShape() }
    val currentOn by rememberUpdatedState(on)

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var lastFrame = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                // Normalised to the prototype's 60fps step so a 90/120Hz panel
                // does not morph faster than the reference.
                val frames = if (lastFrame < 0L) {
                    1f
                } else {
                    ((frameNanos - lastFrame) / NANOS_PER_REFERENCE_FRAME).coerceIn(0f, 4f)
                }
                lastFrame = frameNanos

                val target = if (currentOn) BlobOn else BlobOff
                val shapeFactor = 1f - (1f - SHAPE_LERP).pow(frames)
                shape.lobes += (target.lobes - shape.lobes) * shapeFactor
                shape.amplitude += (target.amplitude - shape.amplitude) * shapeFactor
                shape.radius += (target.radius - shape.radius) * shapeFactor

                val onFactor = 1f - (1f - ON_STATE_LERP).pow(frames)
                shape.onMix += ((if (currentOn) 1f else 0f) - shape.onMix) * onFactor

                shape.wobble += WOBBLE_SPEED * frames
            }
        }
    }

    return shape
}

/**
 * Paints the blob: glow first, then the fill on top so only the outer half of
 * the halo shows — the same compositing a CSS `drop-shadow` produces.
 */
@Composable
internal fun BlobCanvas(
    shape: BlobShape,
    restingColor: Color,
    litColor: Color,
    glowColor: Color,
    modifier: Modifier = Modifier,
) {
    // Reused across frames: a draw block running at 60fps must not allocate a
    // path plus 40 sample points every time.
    val path = remember { Path() }
    val samples = remember { FloatArray(SAMPLE_COUNT * 2) }
    val glow = remember { GlowPaint() }

    Canvas(modifier) {
        buildBlobPath(path, samples, shape, size)
        val mix = shape.onMix
        drawBlobGlow(glow, path, glowColor, mix, size.minDimension / 200f)
        drawPath(path, lerp(restingColor, litColor, mix))
    }
}

/** Mutable, non-snapshot scratch space for the glow's native paint. */
private class GlowPaint {
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    var blurRadiusPx = -1f
    var blurFilter: BlurMaskFilter? = null
}

private fun DrawScope.drawBlobGlow(
    holder: GlowPaint,
    path: Path,
    colour: Color,
    onMix: Float,
    unit: Float,
) {
    if (onMix <= 0.001f) return
    val alpha = GLOW_ALPHA * onMix

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // A mask filter follows the lobed outline exactly, like the CSS filter.
        // Hardware-accelerated mask filters are only dependable from API 29 up.
        val blurPx = GLOW_BLUR_UNITS * unit
        if (holder.blurRadiusPx != blurPx) {
            holder.blurRadiusPx = blurPx
            holder.blurFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        holder.paint.color = colour.copy(alpha = alpha).toArgb()
        holder.paint.maskFilter = holder.blurFilter
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(path, holder.paint) }
    } else {
        // Soft ring standing in for the halo on older devices.
        val radius = 100f * unit
        drawCircle(
            brush = Brush.radialGradient(
                0.62f to colour.copy(alpha = 0f),
                0.82f to colour.copy(alpha = alpha),
                1f to colour.copy(alpha = 0f),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius,
            ),
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

/** Fills [path] with the blob outline for the current [shape]. */
private fun buildBlobPath(path: Path, samples: FloatArray, shape: BlobShape, canvas: Size) {
    val unit = canvas.minDimension / 200f
    val centreX = canvas.width / 2f
    val centreY = canvas.height / 2f

    for (i in 0 until SAMPLE_COUNT) {
        val a = i.toFloat() / SAMPLE_COUNT * TWO_PI
        val r = shape.radius * (
            1f +
                shape.amplitude * sin(shape.lobes * a + shape.wobble * WOBBLE_PHASE_SCALE) +
                SECONDARY_AMPLITUDE * sin(SECONDARY_LOBES * a - shape.wobble * SECONDARY_PHASE_SCALE)
            )
        samples[i * 2] = centreX + r * cos(a) * unit
        samples[i * 2 + 1] = centreY + r * sin(a) * unit
    }

    path.reset()
    appendSmoothClosedPath(path, samples)
}

/**
 * Port of the prototype's `smoothPath(pts)`: a closed Catmull-Rom spline
 * converted to cubic Béziers with the usual `/6` tangent scaling.
 */
private fun appendSmoothClosedPath(path: Path, samples: FloatArray) {
    val n = samples.size / 2

    fun x(i: Int): Float {
        val k = ((i % n) + n) % n
        return samples[k * 2]
    }

    fun y(i: Int): Float {
        val k = ((i % n) + n) % n
        return samples[k * 2 + 1]
    }

    path.moveTo(x(0), y(0))
    for (i in 0 until n) {
        val c1x = x(i) + (x(i + 1) - x(i - 1)) / 6f
        val c1y = y(i) + (y(i + 1) - y(i - 1)) / 6f
        val c2x = x(i + 1) - (x(i + 2) - x(i)) / 6f
        val c2y = y(i + 1) - (y(i + 2) - y(i)) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, x(i + 1), y(i + 1))
    }
    path.close()
}
