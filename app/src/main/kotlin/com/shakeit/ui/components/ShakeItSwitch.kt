package com.shakeit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shakeit.ui.theme.ShakeItTheme
import com.shakeit.ui.theme.SwitchKnobColor

private val TrackShape = RoundedCornerShape(14.dp)
private val TrackWidth = 44.dp
private val TrackHeight = 26.dp
private val KnobSize = 20.dp
private val KnobInset = 3.dp
private val KnobTravel = 18.dp

/**
 * `.switch` — the prototype's own toggle, not the Material one.
 *
 * 44x26dp track with a 14dp radius, a 20dp white knob inset 3dp from the
 * top-left that translates 18dp when on, `--track-off` → `--track-on` over
 * 200ms, and a `0 1px 2px rgba(0,0,0,.25)` shadow on the knob.
 *
 * The knob is positioned with the lambda overload of [offset] so the animation
 * only moves it on the draw pass instead of re-measuring the row every frame.
 */
@Composable
fun ShakeItSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ShakeItTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(SWITCH_TRANSITION_MS),
        label = "switchProgress",
    )

    Box(
        modifier = modifier
            .size(width = TrackWidth, height = TrackHeight)
            .background(lerp(colors.trackOff, colors.trackOn, progress), TrackShape)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Box(
            Modifier
                .offset {
                    IntOffset(
                        x = (KnobInset + KnobTravel * progress).roundToPx(),
                        y = KnobInset.roundToPx(),
                    )
                }
                .size(KnobSize)
                .shadow(elevation = 1.5.dp, shape = CircleShape)
                .background(SwitchKnobColor, CircleShape),
        )
    }
}
