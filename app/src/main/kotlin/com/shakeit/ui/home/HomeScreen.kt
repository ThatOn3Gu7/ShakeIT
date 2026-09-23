package com.shakeit.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shakeit.R
import com.shakeit.hardware.DetectionStatus
import com.shakeit.ui.components.IconActionButton
import com.shakeit.ui.components.ShakeItScreen
import com.shakeit.ui.theme.ShakeItTheme
import com.shakeit.ui.theme.ShakeItType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ShakeButtonShape = RoundedCornerShape(22.dp)

private const val THEME_GLYPH = "\u25D0"
private const val SETTINGS_GLYPH = "\u2699"

@Composable
fun HomeScreen(
    torchOn: Boolean,
    activations: Int,
    detectionStatus: DetectionStatus,
    shakeRequest: Int,
    detectedShake: Int,
    animateBlob: Boolean,
    onToggleTorch: () -> Unit,
    onSimulateShake: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShakeItScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header Title & Parameter Stack
            HomeHeaderSection(
                torchOn = torchOn,
                activations = activations,
                detectionStatus = detectionStatus,
                onToggleTheme = onToggleTheme,
                onOpenSettings = onOpenSettings,
            )

            // Centered Hero Blob with Spin Transition & Material Icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Hero(
                    torchOn = torchOn,
                    animateBlob = animateBlob,
                    shakeRequest = shakeRequest,
                    detectedShake = detectedShake,
                    onToggleTorch = onToggleTorch,
                )
            }

            // Bottom Action Trigger
            ShakeButton(
                onClick = onSimulateShake,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomeHeaderSection(
    torchOn: Boolean,
    activations: Int,
    detectionStatus: DetectionStatus,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = ShakeItTheme.colors

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = if (torchOn) "Flashlight\nActive" else "Flashlight\nDisabled",
                style = ShakeItType.stateWord.copy(
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onSurface,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconActionButton(
                    glyph = THEME_GLYPH,
                    description = stringResource(R.string.cd_toggle_theme),
                    onClick = onToggleTheme,
                )
                IconActionButton(
                    glyph = SETTINGS_GLYPH,
                    description = stringResource(R.string.cd_open_settings),
                    onClick = onOpenSettings,
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Reports what the detector is really doing, not what was asked for.
            // A stalled detector is the one state this row exists to surface: the
            // service is alive and its notification is up, but no samples are
            // arriving, and without this the screen would keep saying "active".
            InfoRow(
                label = "Shake Detection",
                value = stringResource(
                    when (detectionStatus) {
                        DetectionStatus.ACTIVE -> R.string.status_detection_active
                        DetectionStatus.STALLED -> R.string.status_detection_stalled
                        DetectionStatus.INACTIVE -> R.string.status_detection_paused
                    },
                ),
                valueColor = when (detectionStatus) {
                    DetectionStatus.ACTIVE -> colors.accent
                    DetectionStatus.STALLED -> colors.primary
                    DetectionStatus.INACTIVE -> colors.onSurfaceVariant
                },
            )
            InfoRow(
                label = stringResource(R.string.stat_activations),
                value = activations.toString(),
            )
            InfoRow(
                label = stringResource(R.string.stat_time_on_today),
                value = stringResource(R.string.stat_time_on_today_value),
            )
            InfoRow(
                label = stringResource(R.string.stat_avg_session),
                value = stringResource(R.string.stat_avg_session_value),
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = ShakeItTheme.colors.onSurface,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = ShakeItType.statLabel,
            color = ShakeItTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = ShakeItType.buttonLabel.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
        )
    }
}

@Composable
private fun Hero(
    torchOn: Boolean,
    animateBlob: Boolean,
    shakeRequest: Int,
    detectedShake: Int,
    onToggleTorch: () -> Unit,
) {
    val colors = ShakeItTheme.colors
    val shape = rememberBlobShape(on = torchOn, animate = animateBlob)
    val interactionSource = remember { MutableInteractionSource() }
    val toggleLabel = stringResource(R.string.cd_toggle_torch)
    val wobble = remember { Animatable(0f) }
    val currentToggle by rememberUpdatedState(onToggleTorch)

    var targetRotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "heroSpin",
    )

    LaunchedEffect(torchOn) {
        targetRotation += 360f
    }

    LaunchedEffect(shakeRequest) {
        if (shakeRequest == 0) return@LaunchedEffect
        launch {
            wobble.snapTo(0f)
            wobble.animateTo(1f, tween(Wobble.DURATION_MS, easing = LinearEasing))
        }
        delay(Wobble.TOGGLE_AT_MS)
        currentToggle()
        wobble.snapTo(0f)
    }

    LaunchedEffect(detectedShake) {
        if (detectedShake == 0) return@LaunchedEffect
        wobble.snapTo(0f)
        wobble.animateTo(1f, tween(Wobble.DURATION_MS, easing = LinearEasing))
        wobble.snapTo(0f)
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                val progress = wobble.value
                val wobbleRotation = Wobble.rotationAt(progress)
                rotationZ = animatedRotation + wobbleRotation

                val offset = Wobble.translationXAt(progress).dp.toPx()
                val radians = wobbleRotation * (PI.toFloat() / 180f)
                translationX = offset * cos(radians)
                translationY = offset * sin(radians)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = toggleLabel,
                role = Role.Button,
                onClick = onToggleTorch,
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BlobCanvas(
            shape = shape,
            restingColor = colors.primary,
            litColor = colors.accent,
            glowColor = colors.glow,
            modifier = Modifier.size(BlobSize),
        )

        Icon(
            imageVector = if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
            tint = if (torchOn) colors.surface else colors.onSurface,
        )
    }
}

@Composable
private fun ShakeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ShakeItTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val label = stringResource(R.string.shake_to_toggle)

    Box(
        modifier = modifier
            .background(if (pressed) colors.surface2 else colors.surface, ShakeButtonShape)
            .border(BorderStroke(1.5.dp, colors.outline), ShakeButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ShakeItType.buttonLabel,
            color = colors.onSurface,
        )
    }
}


