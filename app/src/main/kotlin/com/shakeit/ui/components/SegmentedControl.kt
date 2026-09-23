package com.shakeit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shakeit.ui.theme.ShakeItTheme
import com.shakeit.ui.theme.ShakeItType

private val GroupShape = RoundedCornerShape(14.dp)
private val OptionShape = RoundedCornerShape(11.dp)

/**
 * `.segmented` — the pill-group control used for both the gesture picker and
 * the theme picker.
 *
 * `background: var(--surface-2)`, `border-radius: 14px`, `padding: 3px`,
 * `gap: 3px`; the active option gets `background: var(--surface)`,
 * `color: var(--primary)`, bold text and `box-shadow: 0 1px 3px rgba(0,0,0,.12)`.
 * Colours are cross-faded over 150ms to match `transition: background .15s, color .15s`.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ShakeItTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface2, GroupShape)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            SegmentOption(
                label = labelOf(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.SegmentOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ShakeItTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(PRESS_TRANSITION_MS),
        label = "segmentSelection",
    )

    Box(
        modifier = modifier
            // Animating the elevation fades the drop shadow in with the background.
            .shadow(elevation = 2.dp * selection, shape = OptionShape)
            .background(lerp(Color.Transparent, colors.surface, selection), OptionShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = if (selected) ShakeItType.segmentLabelActive else ShakeItType.segmentLabel,
            color = lerp(colors.onSurfaceVariant, colors.primary, selection),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}
