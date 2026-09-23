package com.shakeit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shakeit.ui.theme.ShakeItTheme
import com.shakeit.ui.theme.ShakeItType

private val ButtonShape = RoundedCornerShape(14.dp)

/**
 * `.outline-btn` — full-width, 1.5px `--outline` border, transparent
 * background, `--primary` 600-weight 13.5px label, 11px padding, 14px radius.
 *
 * The prototype defines neither an `:active` nor a `:disabled` style for this
 * button — pressing "Connect Shizuku" simply relabels it to "Connected" and
 * makes it inert — so the disabled state is reproduced as `enabled = false`
 * with the visuals left untouched.
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ShakeItTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.5.dp, colors.outline), ButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = ShakeItType.outlineButton,
            color = colors.primary,
        )
    }
}
