package com.shakeit.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.shakeit.state.ShakeItState
import com.shakeit.ui.theme.ShakeItMotion

/**
 * Dependency-free two-screen host. One progress value drives both layers:
 * zero is Home, one is Settings. Predictive back temporarily supplies that
 * value directly; ordinary navigation animates the same value with Screen.
 */
@Composable
fun ShakeItNavHost(
    screen: ShakeItState.Screen,
    modifier: Modifier = Modifier,
    interactiveBackProgress: Float? = null,
    cancelledBackFrom: Float? = null,
    skipNextAnimation: Boolean = false,
    onCancelAnimationFinished: () -> Unit = {},
    onSkippedAnimationApplied: () -> Unit = {},
    home: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    val settingsShown = screen == ShakeItState.Screen.SETTINGS
    val targetProgress = if (settingsShown) 1f else 0f
    val transition = remember { Animatable(targetProgress) }
    var cancelledProgress by remember { mutableStateOf<Float?>(null) }

    // Normal navigation remains the existing spring-driven screen motion. A
    // committed predictive gesture snaps to its already-completed destination,
    // preventing a second Settings-to-Home animation after the finger lifts.
    LaunchedEffect(screen, skipNextAnimation) {
        if (skipNextAnimation) {
            transition.snapTo(targetProgress)
            onSkippedAnimationApplied()
        } else {
            transition.animateTo(targetProgress, ShakeItMotion.Screen)
        }
    }

    // A cancelled gesture hands its last visual position to the same screen
    // spring, so the transition returns naturally to Settings.
    LaunchedEffect(cancelledBackFrom) {
        val start = cancelledBackFrom ?: return@LaunchedEffect
        val settle = Animatable(start)
        cancelledProgress = start
        settle.animateTo(1f, ShakeItMotion.Screen) {
            cancelledProgress = value
        }
        cancelledProgress = null
        onCancelAnimationFinished()
    }

    val progress = when {
        interactiveBackProgress != null -> 1f - interactiveBackProgress
        cancelledProgress != null -> cancelledProgress!!
        skipNextAnimation -> targetProgress
        else -> transition.value
    }

    Box(modifier.fillMaxSize()) {
        ScreenLayer(progress = 1f - progress, content = home)

        if (settingsShown || progress > 0.001f || interactiveBackProgress != null) {
            ScreenLayer(progress = progress, content = settings)
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
