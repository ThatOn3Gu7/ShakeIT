package com.shakeit.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shakeit.R
import kotlinx.coroutines.delay

private val SplashBackground = Color(0xFF050811)
private val StrikeAmber = Color(0xFFFFB84A)
private val StrikeYellow = Color(0xFFFFE18A)

/**
 * A short activation strike using the same bolt mark as the existing ShakeIT
 * torch identity. The bolt is deliberately kept as a single, quiet shape so
 * the splash hands focus to the home screen instead of becoming a second logo.
 */
@Composable
fun ShakeItSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Give the strike enough time to read as an intentional activation on
        // very fast launches: twice the original 480 ms travel, plus a slightly
        // longer impact hold before handing off to the home screen.
        progress.animateTo(1f, tween(960, easing = FastOutSlowInEasing))
        delay(160)
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground),
        contentAlignment = Alignment.Center,
    ) {
        val travel = with(LocalDensity.current) { maxHeight.toPx() * 0.56f }
        val strikeY = -travel * (1f - progress.value)
        val impact = ((progress.value - 0.68f) / 0.32f).coerceIn(0f, 1f)
        val fade = ((progress.value - 0.82f) / 0.18f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(1f - fade),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = center
                val radius = size.minDimension * (0.10f + 0.24f * impact)
                val glowAlpha = (1f - impact) * 0.08f + impact * 0.26f
                drawCircle(StrikeAmber.copy(alpha = glowAlpha), radius, center)
                drawCircle(StrikeYellow.copy(alpha = impact * 0.12f), radius * 0.52f, center)
            }

            Icon(
                painter = painterResource(R.drawable.ic_torch),
                contentDescription = null,
                tint = lerp(StrikeAmber, StrikeYellow, impact),
                modifier = Modifier
                    .size(132.dp)
                    .align(Alignment.Center)
                    .graphicsLayer { translationY = strikeY }
                    .alpha(0.92f + 0.08f * impact),
            )
        }
    }
}
