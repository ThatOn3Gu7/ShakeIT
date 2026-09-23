package com.shakeit.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.shakeit.state.ShakeItState

/**
 * Native stand-in for the prototype's two absolutely positioned `.screen`
 * sections.
 *
 * The CSS is:
 * ```
 * .screen        { transition: transform .3s ease, opacity .3s ease }
 * .screen.hidden { opacity: 0; pointer-events: none; transform: translateX(16px) }
 * ```
 * so both screens always slide the same direction (16dp to the right) and cross
 * fade over 300ms with the CSS `ease` curve. Each screen is driven by a
 * 0f..1f "shown" progress and rendered through a [graphicsLayer], which keeps
 * the transition on the render thread instead of re-laying out every frame.
 *
 * Home is kept composed at all times so its blob never restarts mid-morph; the
 * blob's own frame loop is paused while Settings is on top.
 */
@Composable
fun ShakeItNavHost(
    screen: ShakeItState.Screen,
    modifier: Modifier = Modifier,
    home: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    val settingsShown = screen == ShakeItState.Screen.SETTINGS

    val homeProgress by animateFloatAsState(
        targetValue = if (settingsShown) 0f else 1f,
        animationSpec = tween(TRANSITION_MS, easing = TransitionEasing),
        label = "homeProgress",
    )
    val settingsProgress by animateFloatAsState(
        targetValue = if (settingsShown) 1f else 0f,
        animationSpec = tween(TRANSITION_MS, easing = TransitionEasing),
        label = "settingsProgress",
    )

    Box(modifier) {
        ScreenLayer(progress = homeProgress, content = home)

        if (settingsShown) {
            // `pointer-events: none` for the screen underneath: anything that
            // falls through Settings is swallowed here instead of reaching Home.
            Box(Modifier.fillMaxSize().consumeEveryPointerEvent())
        }

        if (settingsShown || settingsProgress > 0f) {
            ScreenLayer(progress = settingsProgress, content = settings)
        }
    }
}

@Composable
private fun ScreenLayer(progress: Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress
                // `GraphicsLayerScope` is a `Density`, so the dp offset converts here.
                translationX = (1f - progress) * HIDDEN_TRANSLATION_X.toPx()
            },
        content = { content() },
    )
}

private fun Modifier.consumeEveryPointerEvent(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change ->
                change.consume()
            }
        }
    }
}

private const val TRANSITION_MS = 300

/** CSS `ease` — cubic-bezier(0.25, 0.1, 0.25, 1). */
private val TransitionEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

private val HIDDEN_TRANSLATION_X = 16.dp
