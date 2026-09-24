package com.shakeit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import com.shakeit.ui.theme.ShakeItMotion

/**
 * Sets a spoken label for the node and merges whatever its children would have
 * contributed.
 *
 * `SemanticsProperties.ContentDescription` is keyed as a *list* of strings, so
 * it is written through `SemanticsPropertyReceiver.set` rather than as a plain
 * assignment.
 *
 * Used on surfaces whose meaning is not fully carried by their text — a status
 * chip whose colour is only one of three ways it says what it means, an
 * informational panel that should be announced as a group.
 */
internal fun Modifier.accessibilityLabel(label: String): Modifier =
    semantics(mergeDescendants = true) {
        set(SemanticsProperties.ContentDescription, listOf(label))
    }

/**
 * The scale a pressed surface should be drawn at.
 *
 * A spring rather than a tween, so a press released halfway back down continues
 * from where the finger left it instead of restarting. This is the *additional*
 * feedback on top of a ripple, not a replacement for one: a surface that only
 * scales can feel unresponsive on a light tap, and a surface that only ripples
 * gives no sense of the object moving under the finger.
 */
@Composable
internal fun pressScale(
    pressed: Boolean,
    pressedValue: Float = ShakeItMotion.PRESS_SCALE,
): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedValue else 1f,
        animationSpec = ShakeItMotion.Snap,
        label = "pressScale",
    )
    return scale
}

/** Uniform scale about the centre, applied on the render thread. */
internal fun Modifier.scaledBy(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}
