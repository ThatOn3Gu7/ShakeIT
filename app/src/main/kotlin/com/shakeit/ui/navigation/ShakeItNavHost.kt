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
        animationSpec = ShakeItMotion.Screen,
        label = "homeProgress",
    )
    val settingsProgress by animateFloatAsState(
        targetValue = if (settingsShown) 1f else 0f,
        animationSpec = ShakeItMotion.Screen,
        label = "settingsProgress",
    )

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
