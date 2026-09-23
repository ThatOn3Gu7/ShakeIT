package com.shakeit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shakeit.ui.theme.ShakeItTheme
import com.shakeit.ui.theme.ShakeItType

private val PillShape = RoundedCornerShape(20.dp)

/**
 * `.status-pill` — the detection status chip on the left of the home top bar.
 *
 * `padding: 6px 12px 6px 10px`, `gap: 7px`, 12.5px text in `--on-var` on a
 * `--surface-2` background.
 */
@Composable
fun StatusPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ShakeItTheme.colors

    Row(
        modifier = modifier
            .background(colors.surface2, PillShape)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StatusDot(color = if (active) colors.statusActive else colors.onSurfaceVariant)
        Text(text = text, style = ShakeItType.statusText, color = colors.onSurfaceVariant)
    }
}

/**
 * `.dot` — a 7px circle with `box-shadow: 0 0 0 3px rgba(61,194,107,.18)`,
 * i.e. a 3px halo, giving a 13px overall footprint.
 */
@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(13.dp)
            .background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
    }
}
