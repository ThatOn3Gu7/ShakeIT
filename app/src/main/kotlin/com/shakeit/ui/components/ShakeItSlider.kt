package com.shakeit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.shakeit.ui.theme.ShakeItTheme
import kotlin.math.roundToInt

private val TrackHeight = 4.dp
private val ThumbRadius = 9.dp
private val SliderHeight = 28.dp

/**
 * Stand-in for `<input type="range" min="1" max="5">` with
 * `accent-color: var(--primary)`.
 *
 * A Material `Slider` is deliberately not used: its track/thumb geometry and
 * Material colour roles do not match the prototype, and a hand-drawn track
 * keeps the `--primary` accent exactly as the CSS specifies. The control snaps
 * to whole steps, matching the range input's default `step="1"`.
 *
 * @param description spoken label prefix, e.g. "Sensitivity".
 */
@Composable
fun ShakeItSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 1..5,
    valueLabel: String = value.toString(),
    description: String? = null,
) {
    val colors = ShakeItTheme.colors
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val steps = (valueRange.last - valueRange.first).coerceAtLeast(0)
    val fraction = if (steps == 0) {
        0f
    } else {
        ((value - valueRange.first).toFloat() / steps).coerceIn(0f, 1f)
    }

    val spokenLabel = if (description == null) valueLabel else "$description: $valueLabel"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .accessibilityLabel(spokenLabel)
            .pointerInput(steps, valueRange) {
                // PointerInputScope is a Density, so the thumb inset converts here
                // and the mapping stays correct through rotation and resize.
                fun valueAt(x: Float): Int {
                    val inset = ThumbRadius.toPx()
                    val trackWidth = size.width - inset * 2f
                    if (trackWidth <= 0f) return valueRange.first
                    val raw = ((x - inset) / trackWidth).coerceIn(0f, 1f)
                    val snappedSteps = (raw * steps).roundToInt()
                    return (valueRange.first + snappedSteps).coerceIn(valueRange.first, valueRange.last)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnValueChange(valueAt(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        currentOnValueChange(valueAt(change.position.x))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = ThumbRadius.toPx()
            val trackHeight = TrackHeight.toPx()
            val trackWidth = size.width - inset * 2f
            val centerY = size.height / 2f
            val topLeft = Offset(inset, centerY - trackHeight / 2f)
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            val filledWidth = trackWidth * fraction

            drawRoundRect(
                color = colors.outline,
                topLeft = topLeft,
                size = Size(trackWidth, trackHeight),
                cornerRadius = corner,
            )
            if (filledWidth > 0f) {
                drawRoundRect(
                    color = colors.primary,
                    topLeft = topLeft,
                    size = Size(filledWidth, trackHeight),
                    cornerRadius = corner,
                )
            }
            drawCircle(
                color = colors.primary,
                radius = inset,
                center = Offset(inset + filledWidth, centerY),
            )
        }
    }
}
