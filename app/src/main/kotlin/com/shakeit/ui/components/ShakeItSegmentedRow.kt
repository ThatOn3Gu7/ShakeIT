package com.shakeit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * A single-choice row: gesture, theme — anywhere the options are few, mutually
 * exclusive and all worth showing at once.
 *
 * Material's own segmented button, which the hand-drawn version it replaces was
 * missing three things: a ripple on the option being pressed, `Role.RadioButton`
 * semantics with the selection announced, and keyboard/switch-access focus. It
 * also animates the selected option's indicator itself, so the row no longer
 * cross-fades two background colours by hand on a fixed tween.
 *
 * @param options every choice, all of them visible — a segmented row that hides
 *   an option behind a menu is not a segmented row
 * @param labelOf the words for one option. Kept short by the caller: the row
 *   divides the width equally, so a long label ellipsises rather than wrapping
 * @param enabledFor lets one option be inert without hiding it, which is how a
 *   choice that exists in the design but not in the engine should be shown
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
    val colors = MaterialTheme.colorScheme

    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabledFor(option),
                modifier = Modifier.weight(1f),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primaryContainer,
                    activeContentColor = colors.onPrimaryContainer,
                    inactiveContainerColor = colors.surfaceContainerHigh,
                    inactiveContentColor = colors.onSurfaceVariant,
                ),
                // No checkmark: the container colour already carries the selection,
                // and an icon slot in a three-option row costs label width.
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
