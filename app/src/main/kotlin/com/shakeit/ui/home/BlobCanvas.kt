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
import androidx.compose.ui.unit.dp

/** `.hero-blob { width: 190px; height: 190px }` */
internal val BlobSize = 190.dp

/** `drop-shadow(0 0 22px rgba(var(--glow), .55))` */
private const val GLOW_BLUR_UNITS = 22f
private const val GLOW_ALPHA = 0.55f

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
 *   `requestAnimationFrame` loop alive for the whole page lifetime.
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
    // path plus 40 samples plus 240 control points every time.
    val path = remember { Path() }
    val samples = remember { FloatArray(BlobMath.SAMPLE_COUNT * 2) }
    val segments = remember { FloatArray(BlobMath.SAMPLE_COUNT * BlobMath.FLOATS_PER_SEGMENT) }
    val glow = remember { GlowPaint() }

    Canvas(modifier) {
        buildBlobPath(path, samples, segments, shape, size)
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
 * SVG's `viewBox="-100 -100 200 200"` space where 1 unit == canvas / 200.
 */
private fun buildBlobPath(
    path: Path,
    samples: FloatArray,
    segments: FloatArray,
    shape: BlobShape,
    canvas: Size,
) {
    val unit = canvas.minDimension / 200f
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
