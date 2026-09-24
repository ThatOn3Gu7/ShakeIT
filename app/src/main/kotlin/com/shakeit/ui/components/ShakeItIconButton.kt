package com.shakeit.ui.components

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The circular buttons in a screen's top corner: theme, settings, back.
 *
 * A Material icon button rather than a hand-drawn one. The earlier version drew
 * its own circle, its own press state and no ripple at all, and used text glyphs
 * (◐ ⚙ ←) as icons — which meant it was the one control in the app that gave no
 * standard feedback for being touched, and the only one whose artwork was not an
 * icon. Both are fixed by using the component: it has a ripple, a 48dp touch
 * target, focus handling for keyboards and switch access, and `Role.Button`
 * semantics, none of which a `Box` with `indication = null` had.
 *
 * @param description the spoken label. Icon buttons carry no visible text, so
 *   this is the only thing a screen reader has.
 */
@Composable
fun ShakeItIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.onSurface,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}
