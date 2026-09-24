package com.shakeit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import com.shakeit.ui.theme.ShakeItMotion
import kotlin.math.floor

/**
 * A single-choice row: gesture, theme — anywhere the options are few, mutually
 * exclusive and all worth showing at once.
 *
 * The Material segmented buttons remain the visual and accessibility surface. The
 * row additionally owns a horizontal gesture recogniser which waits for
 * horizontal touch-slop before consuming anything. That distinction matters in
 * a vertically scrolling Settings page: a vertical finger movement is left to
 * the parent scroll container, while a deliberate horizontal movement becomes
 * a physical tile sliding across the existing slots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ShakeItSegmentedRow(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabledFor: (T) -> Boolean = { true },
) {
    val currentSelection by rememberUpdatedState(onSelect)
    val currentEnabled by rememberUpdatedState(enabledFor)

    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(options) {
                if (options.isEmpty()) return@pointerInput

                fun selectAt(x: Float) {
                    val slotWidth = size.width / options.size.toFloat()
                    if (slotWidth <= 0f) return
                    val index = floor((x / slotWidth))
                        .toInt()
                        .coerceIn(options.indices)
                    val option = options[index]
                    if (currentEnabled(option)) currentSelection(option)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val horizontal = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        // Consume only after the gesture has proved horizontal.
                        // Before this point, a vertical Settings scroll remains
                        // completely owned by the parent.
                        change.consume()
                        selectAt(change.position.x)
                    }
                    if (horizontal != null) {
                        horizontalDrag(horizontal.id) { change ->
                            change.consume()
                            selectAt(change.position.x)
                        }
                    }
                }
            },
    ) {
        options.forEachIndexed { index, option ->
            val selectedProgress by animateFloatAsState(
                targetValue = if (option == selected) 1f else 0f,
                animationSpec = ShakeItMotion.Snap,
                label = "segmentedSelection$index",
            )
            val activeContainer = lerp(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.primaryContainer,
                selectedProgress,
            )
            val activeContent = lerp(
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.onPrimaryContainer,
                selectedProgress,
            )
            SegmentedButton(
                selected = option == selected,
                onClick = { if (currentEnabled(option)) currentSelection(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = currentEnabled(option),
                modifier = Modifier.weight(1f),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = activeContainer,
                    activeContentColor = activeContent,
                    inactiveContainerColor = activeContainer,
                    inactiveContentColor = activeContent,
                ),
                // No checkmark: the current visual language uses the selected
                // container itself as the indicator, so the rail stays unchanged.
                icon = {},
            ) {
                Text(
                    text = labelOf(option),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
