package com.shakeit.ui.home

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

/**
 * How much of the canvas the blob itself occupies.
 *
 * The rest is headroom for the halo and the pool of light, both of which are
 * painted *outside* the blob's outline. Drawing them in the same canvas costs
 * nothing extra, and it is what stops the glow being clipped at the edge of the
 * box — the artefact that made the old fixed-size hero look like a sticker on a
 * card rather than a light in a room.
 */
internal const val BlobCanvasFill = 0.62f

/**
 * The canvas size needed for a blob of a given visible diameter.
 *
 * The caller thinks in terms of the blob — "the hero's light should be about
 * 220dp across on this window" — and the canvas is that plus its headroom.
 */
internal fun blobCanvasSize(blobDiameter: Dp): Dp = blobDiameter / BlobCanvasFill

/** `drop-shadow(0 0 22px rgba(var(--glow), .55))` */
private const val GLOW_BLUR_UNITS = 22f
private const val GLOW_ALPHA = 0.55f

/**
 * The pool of light behind the blob, at its strongest just outside the outline.
 * Kept deliberately faint: it is the reason the hero reads as a lamp rather than
 * a shape, and any stronger and it becomes a gradient for its own sake.
 */
private const val WASH_INNER_ALPHA = 0.30f
private const val WASH_OUTER_ALPHA = 0.10f

/**
 * Live blob parameters.
 *
 * These are snapshot state so writing them from the frame loop invalidates the
 * draw pass, but they are only ever *read* inside a draw block — never during
 * composition — which keeps the per-frame churn out of recomposition. [config]
 * is a single immutable value, so the draw pass settles once the shape stops
 * changing instead of chasing a float that never quite arrives.
 */
@Stable
internal class BlobShape {
    var config by mutableStateOf(BlobOff)
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
 *   `requestAnimationFrame` loop alive for the whole page lifetime, and so does
 *   this, for the same reason.
 */
@Composable
internal fun rememberBlobShape(on: Boolean, animate: Boolean): BlobShape {
    val shape = remember { BlobShape() }
    val currentOn by rememberUpdatedState(on)

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var lastFrameNanos = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                val frames = BlobMath.frameScale(lastFrameNanos, frameNanos)
                lastFrameNanos = frameNanos

                val target = if (currentOn) BlobOn else BlobOff
                shape.config = BlobMath.advance(shape.config, target, frames)
                shape.onMix = BlobMath.advanceOnMix(shape.onMix, if (currentOn) 1f else 0f, frames)
                shape.wobble += BlobMath.WOBBLE_SPEED * frames
            }
        }
    }

    return shape
}

/**
 * Paints the blob: the pool of light first, then the halo, then the fill on top
 * so only the outer half of each shows through — the same compositing a CSS
 * `drop-shadow` produces, plus the wash a real lamp would cast.
 *
 * @param restingColor the fill while the torch is off
 * @param litColor the fill while it is on
 * @param glowColor the halo colour, applied with alpha
 * @param washColor the light pool, applied with alpha. Pass [Color.Transparent]
 *   to draw no pool at all — which is what the previews do, since a static
 *   preview of a half-lit blob with a halo is enough to judge the shape by.
 */
@Composable
internal fun BlobCanvas(
    shape: BlobShape,
    restingColor: Color,
    litColor: Color,
    glowColor: Color,
    washColor: Color,
    modifier: Modifier = Modifier,
) {
    // Reused across frames: a draw block running at 60fps must not allocate a
    // path plus 40 samples plus 240 control points every time.
    val path = remember { Path() }
    val samples = remember { FloatArray(BlobMath.SAMPLE_COUNT * 2) }
    val segments = remember { FloatArray(BlobMath.SAMPLE_COUNT * BlobMath.FLOATS_PER_SEGMENT) }
    val glow = remember { GlowPaint() }

    Canvas(modifier) {
        val mix = shape.onMix
        drawLightWash(washColor, mix)
        buildBlobPath(path, samples, segments, shape, size)
        drawBlobGlow(glow, path, glowColor, mix, size.minDimension * BlobCanvasFill / 200f)
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

/**
 * The soft pool of light the hero sits in while the torch is on.
 *
 * One radial gradient, no blur, and it is skipped entirely once [onMix] settles
 * at zero — an off flashlight casts nothing, and drawing a transparent circle
 * every frame to prove it would be waste.
 */
private fun DrawScope.drawLightWash(colour: Color, onMix: Float) {
    if (onMix <= 0.001f || colour == Color.Transparent) return

    val centre = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0.42f to colour.copy(alpha = WASH_INNER_ALPHA * onMix),
            0.74f to colour.copy(alpha = WASH_OUTER_ALPHA * onMix),
            1f to colour.copy(alpha = 0f),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
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
        // `android.graphics.Canvas.drawPath` wants the framework path, not the
        // Compose wrapper.
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPath(path.asAndroidPath(), holder.paint)
        }
    } else {
        // Soft ring standing in for the halo on older devices.
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = 100f * unit
        drawCircle(
            brush = Brush.radialGradient(
                0.62f to colour.copy(alpha = 0f),
                0.82f to colour.copy(alpha = alpha),
                1f to colour.copy(alpha = 0f),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

/**
 * Fills [path] with the blob outline for the current [shape], working in the
 * SVG's `viewBox="-100 -100 200 200"` space where 1 unit == blob / 200 — with
 * the blob occupying [BlobCanvasFill] of the canvas, so the halo has somewhere
 * to go.
 */
private fun buildBlobPath(
    path: Path,
    samples: FloatArray,
    segments: FloatArray,
    shape: BlobShape,
    canvas: Size,
) {
    val unit = canvas.minDimension * BlobCanvasFill / 200f
    BlobMath.sample(
        config = shape.config,
        wobble = shape.wobble,
        unit = unit,
        centreX = canvas.width / 2f,
        centreY = canvas.height / 2f,
        out = samples,
    )
    BlobMath.closedSpline(samples, segments)

    path.reset()
    path.moveTo(samples[0], samples[1])
    for (i in 0 until BlobMath.SAMPLE_COUNT) {
        val o = i * BlobMath.FLOATS_PER_SEGMENT
        path.cubicTo(
            segments[o],
            segments[o + 1],
            segments[o + 2],
            segments[o + 3],
            segments[o + 4],
            segments[o + 5],
        )
    }
    path.close()
}
