package com.shakeit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shakeit.ui.theme.ShakeItTheme
import com.shakeit.ui.theme.ShakeItType

private val IconSize = 38.dp

/**
 * `.icon-btn` — the 38dp circular buttons in the top bar.
 *
 * The prototype has no ripple; it swaps `--surface-2` for `--outline` on
 * `:active` with a 150ms transition, so the indication is disabled and the
 * press state is cross-faded by hand.
 *
 * [glyph] is the same character the prototype renders (◐ ⚙ ←). Swapping these
 * for vector assets later only touches the call sites.
 */
@Composable
fun IconActionButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ShakeItTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(PRESS_TRANSITION_MS),
        label = "iconButtonPress",
    )

    Box(
        modifier = modifier
            .size(IconSize)
            .background(lerp(colors.surface2, colors.outline, pressProgress), CircleShape)
            .accessibilityLabel(description)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = ShakeItType.iconGlyph, color = colors.onSurface)
    }
}
