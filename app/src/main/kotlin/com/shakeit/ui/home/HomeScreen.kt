package com.shakeit.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shakeit.R
import com.shakeit.hardware.DetectionStatus
import com.shakeit.state.ShakeGesture
import com.shakeit.ui.components.DetectionStatusChip
import com.shakeit.ui.components.ShakeItIconButton
import com.shakeit.ui.components.ShakeItScreen
import com.shakeit.ui.layout.ScreenMetrics
import com.shakeit.ui.theme.ShakeItMotion
import com.shakeit.ui.theme.ShakeItShapes
import com.shakeit.ui.theme.torchColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Home is built around one sentence: **shake your phone, the flashlight changes**.
 *
 * The old screen was a header, four dashboard rows, a fixed 190dp blob and a
 * button that secretly simulated a shake. This composition gives the hero the
 * window's available space, makes the current state readable before the user
 * looks for the icon, and turns the former button into a plainly informational
 * gesture card. The only action on the hero is the intentional manual flashlight
 * toggle; the shake card has no click handler and no button semantics.
 */
@Composable
fun HomeScreen(
    torchOn: Boolean,
    activations: Int,
    detectionStatus: DetectionStatus,
    gesture: ShakeGesture,
    detectedShake: Int,
    animateBlob: Boolean,
    onToggleTorch: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShakeItScreen(modifier = modifier) { metrics ->
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeTopBar(
                detectionStatus = detectionStatus,
                onToggleTheme = onToggleTheme,
                onOpenSettings = onOpenSettings,
            )

            Spacer(Modifier.height(metrics.sectionGap))

            HomeHero(
                torchOn = torchOn,
                detectedShake = detectedShake,
                animateBlob = animateBlob,
                onToggleTorch = onToggleTorch,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .heightIn(min = if (metrics.isCompact) 260.dp else 320.dp),
            )

            Spacer(Modifier.height(metrics.sectionGap))

            ShakeInstructionCard(
                gesture = gesture,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(metrics.innerGap))

            ActivitySummary(
                activations = activations,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HomeTopBar(
    detectionStatus: DetectionStatus,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShakeItIconButton(
                    icon = Icons.Rounded.DarkMode,
                    description = stringResource(R.string.cd_toggle_theme),
                    onClick = onToggleTheme,
                )
                ShakeItIconButton(
                    icon = Icons.Rounded.Settings,
                    description = stringResource(R.string.cd_open_settings),
                    onClick = onOpenSettings,
                )
            }
        }
        DetectionStatusChip(status = detectionStatus)
    }
}

@Composable
private fun HomeHero(
    torchOn: Boolean,
    detectedShake: Int,
    animateBlob: Boolean,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = torchColors()
    val pressScale = 1f
    val litScale by animateFloatAsState(
        targetValue = if (torchOn) ShakeItMotion.HERO_LIT_SCALE else 1f,
        animationSpec = ShakeItMotion.Settle,
        label = "heroLitScale",
    )
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = maxWidth
        val heroDiameter = minOf(maxWidth, maxHeight * 0.82f).coerceIn(176.dp, 320.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AnimatedContent(
                targetState = torchOn,
                transitionSpec = {
                    (fadeIn(tween(220, easing = ShakeItMotion.Emphasized)) + scaleIn(initialScale = 0.92f)) togetherWith
                        fadeOut(tween(160, easing = ShakeItMotion.EmphasizedAccelerate))
                },
                label = "heroStateLabel",
            ) { on ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(if (on) R.string.home_flashlight_active else R.string.home_flashlight_off),
                        style = if (availableWidth < 340.dp) {
                            MaterialTheme.typography.displaySmall
                        } else if (availableWidth < 500.dp) {
                            MaterialTheme.typography.displayMedium
                        } else {
                            MaterialTheme.typography.displayLarge
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(
                            when {
                                gesture == ShakeGesture.DoubleShake && on -> R.string.home_light_is_on_double
                                gesture == ShakeGesture.DoubleShake -> R.string.home_light_is_off_double
                                on -> R.string.home_light_is_on
                                else -> R.string.home_light_is_off
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(width = heroDiameter + 36.dp, height = heroDiameter + 36.dp),
                shape = ShakeItShapes.hero,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = if (torchOn) 4.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Hero(
                        torchOn = torchOn,
                        animateBlob = animateBlob,
                        detectedShake = detectedShake,
                        onToggleTorch = onToggleTorch,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsScale(pressScale * litScale),
                    )
                    Text(
                        text = stringResource(R.string.cd_manual_toggle_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp),
                    )
                }
            }
        }
    }
}

/** A render-thread scale helper to keep the hero's measured size stable. */
private fun Modifier.graphicsScale(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}

/**
 * The expressive, manually tappable light. This is the intentional manual-toggle
 * affordance; the gesture instruction below is deliberately a different kind of
 * surface and cannot invoke it.
 */
@Composable
private fun Hero(
    torchOn: Boolean,
    animateBlob: Boolean,
    detectedShake: Int,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = torchColors()
    val toggleLabel = stringResource(R.string.cd_toggle_torch)
    val activeState = stringResource(R.string.home_flashlight_active)
    val offState = stringResource(R.string.home_flashlight_off)
    val wobble = remember { Animatable(0f) }
    val currentToggle by rememberUpdatedState(onToggleTorch)

    var targetRotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = ShakeItMotion.HeroSpin,
        label = "heroStateSpin",
    )

    LaunchedEffect(torchOn) {
        targetRotation += 360f
    }

    LaunchedEffect(detectedShake) {
        if (detectedShake == 0) return@LaunchedEffect
        wobble.snapTo(0f)
        wobble.animateTo(1f, tween(Wobble.DURATION_MS, easing = LinearEasing))
        wobble.snapTo(0f)
    }

    val wobbleRotation = Wobble.rotationAt(wobble.value)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationZ = animatedRotation + wobbleRotation
                val offset = Wobble.translationXAt(wobble.value).dp.toPx()
                val radians = wobbleRotation * (PI.toFloat() / 180f)
                translationX = offset * cos(radians)
                translationY = offset * sin(radians)
            }
            .clickable(
                onClickLabel = toggleLabel,
                role = Role.Button,
                onClick = currentToggle,
            )
            .clearAndSetSemantics {
                contentDescription = toggleLabel
                stateDescription = if (torchOn) activeState else offState
                role = Role.Button
                onClick(action = { currentToggle(); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val blobDiameter = minOf(maxWidth, maxHeight) * BlobCanvasFill
            BlobCanvas(
                shape = rememberBlobShape(on = torchOn, animate = animateBlob),
                restingColor = palette.rest,
                litColor = palette.lit,
                glowColor = palette.glow,
                washColor = palette.wash,
                modifier = Modifier.size(blobCanvasSize(blobDiameter)),
            )

            Surface(
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
                color = if (torchOn) palette.onLit.copy(alpha = 0.18f) else palette.onRest.copy(alpha = 0.12f),
                contentColor = if (torchOn) palette.onLit else palette.onRest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }
    }
}

/**
 * Formerly a button labelled "Shake to toggle". It is now a non-clickable
 * instructional surface. There is intentionally no `clickable`, no role, no
 * ripple, and no callback. The user is told what to do; the accelerometer is the
 * thing that does it.
 */
@Composable
private fun ShakeInstructionCard(
    gesture: ShakeGesture,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val titleRes = if (gesture == ShakeGesture.DoubleShake) {
        R.string.double_shake_instruction_title
    } else {
        R.string.shake_instruction_title
    }
    val bodyRes = if (gesture == ShakeGesture.DoubleShake) {
        R.string.double_shake_instruction_body
    } else {
        R.string.shake_instruction_body
    }
    val accessibilityRes = if (gesture == ShakeGesture.DoubleShake) {
        R.string.double_shake_instruction_accessibility
    } else {
        R.string.shake_instruction_accessibility
    }
    val instructionDescription = stringResource(accessibilityRes)
    Surface(
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = instructionDescription
            },
        shape = ShakeItShapes.informational,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = ShakeItShapes.control,
                color = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.WbSunny,
                contentDescription = null,
                tint = colors.tertiary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Real activity only: fabricated duration/session values are gone. */
@Composable
private fun ActivitySummary(
    activations: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = ShakeItShapes.container,
        color = colors.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.home_activity_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.home_activity_value, activations),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.home_activity_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
