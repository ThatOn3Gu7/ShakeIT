package com.shakeit.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.shakeit.state.ShakeItState
import com.shakeit.ui.theme.ShakeItMotion

/**
 * A small, dependency-free navigation host for the app's two destinations.
 *
 * The old host used a fixed 300ms tween and translated both screens by the same
 * 16dp. This one keeps the same lightweight stacked-screen architecture, but the
 * progress is a spring: opening Settings can be reversed by Back immediately,
 * and the screen continues from its current position instead of restarting a
 * clock. The underlying Home remains composed, so the blob loop still pauses
 * exactly when Settings is on top.
 *
 * The navigation state ([screen]) is authoritative about which screen is
 * active, and owns the resting targets. An in-flight system back gesture only
 * temporarily overrides them: while [backGestureActive] is true the rendered
 * transition is [backGestureProgress] (the system's own progress, 0 =
 * Settings fully up, 1 = Home fully up) *exactly* — there is no animation
 * between the finger and the screens. The springs' targets track the same
 * progress in the background, so when the gesture commits or cancels and the
 * gesture state is dropped, the springs settle from wherever the finger left
 * them into the resting state the navigation now describes. That is what
 * keeps the transition from replaying after a commit, and because the
 * progress only enters the targets while the gesture is active, a stale
 * gesture value can never affect a later Settings entry.
 */
@Composable
fun ShakeItNavHost(
    screen: ShakeItState.Screen,
    backGestureActive: Boolean = false,
    backGestureProgress: Float = 0f,
    modifier: Modifier = Modifier,
    home: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    val settingsShown = screen == ShakeItState.Screen.SETTINGS
    val homeTarget = if (backGestureActive) backGestureProgress
        else if (settingsShown) 0f
        else 1f
    val settingsTarget = 1f - homeTarget
    val homeSettled by animateFloatAsState(
        targetValue = homeTarget,
        animationSpec = ShakeItMotion.Screen,
        label = "homeProgress",
    )
    val settingsSettled by animateFloatAsState(
        targetValue = settingsTarget,
        animationSpec = ShakeItMotion.Screen,
        label = "settingsProgress",
    )
    // While the gesture is in flight the layers are driven by the system's
    // progress directly; off the gesture they are driven by the springs,
    // which continue from the gesture position on commit/cancel.
    val homeProgress = if (backGestureActive) backGestureProgress else homeSettled
    val settingsProgress = if (backGestureActive) 1f - backGestureProgress else settingsSettled

    Box(modifier.fillMaxSize()) {
        ScreenLayer(progress = homeProgress, content = home)

        if (settingsShown || settingsProgress > 0.001f) {
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
                translationX = (1f - progress) * HIDDEN_TRANSLATION_X.toPx()
            },
        content = { content() },
    )
}

private val HIDDEN_TRANSLATION_X = 28.dp
