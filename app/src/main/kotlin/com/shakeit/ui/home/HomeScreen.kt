package com.shakeit.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shakeit.R
import com.shakeit.ui.components.IconActionButton
import com.shakeit.ui.components.ShakeItScreen
import com.shakeit.ui.components.StatusPill
import com.shakeit.ui.theme.ShakeItTheme
import com.shakeit.ui.theme.ShakeItType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ShakeButtonShape = RoundedCornerShape(22.dp)

/** `◐` and `⚙` — the same glyphs the prototype renders. */
private const val THEME_GLYPH = "\u25D0"
private const val SETTINGS_GLYPH = "\u2699"

/** `.hero.shaking { animation: wobble .4s ease }`, toggled 380ms in. */
private const val WOBBLE_DURATION_MS = 400
private const val WOBBLE_TOGGLE_AT_MS = 380L

/**
 * `@keyframes wobble`. A CSS `animation-timing-function` applies *per keyframe
 * interval*, so each segment below is eased on its own local fraction.
 */
private val WobbleEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val WobbleTimes = floatArrayOf(0f, 0.20f, 0.45f, 0.70f, 1f)
private val WobbleRotation = floatArrayOf(0f, -7f, 6f, -4f, 0f)
private val WobbleTranslationX = floatArrayOf(0f, -3f, 3f, 0f, 0f)

/**
 * The `#home` screen: status pill plus theme and settings actions, the hero
 * blob with its OFF/ON label, the "Shake to toggle" button, and the stats row.
 *
 * @param shakeRequest bumping this plays one shake, mirroring the prototype's
 *   `shakeBtn` handler: start the wobble, then toggle 380ms later.
 * @param animateBlob false while Settings covers this screen, so the blob's
 *   frame loop parks instead of redrawing a layer nobody can see.
 */
@Composable
fun HomeScreen(
    torchOn: Boolean,
    activations: Int,
    detectionActive: Boolean,
    shakeRequest: Int,
    animateBlob: Boolean,
    onToggleTorch: () -> Unit,
    onSimulateShake: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShakeItScreen(modifier = modifier) {
        HomeTopBar(
            statusText = stringResource(
                if (detectionActive) R.string.status_detection_active
                else R.string.status_detection_paused,
            ),
            detectionActive = detectionActive,
            onToggleTheme = onToggleTheme,
            onOpenSettings = onOpenSettings,
        )

        // `.hero-wrap` — flex:1, centred, 20px gap, 10px vertical padding.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Hero(
                torchOn = torchOn,
                stateWord = stringResource(if (torchOn) R.string.state_on else R.string.state_off),
                stateSub = stringResource(if (torchOn) R.string.state_sub_on else R.string.state_sub_off),
                animateBlob = animateBlob,
                shakeRequest = shakeRequest,
                onToggleTorch = onToggleTorch,
            )
            ShakeButton(onClick = onSimulateShake)
        }

        StatsRow(activations = activations)
    }
}

@Composable
private fun HomeTopBar(
    statusText: String,
    detectionActive: Boolean,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusPill(text = statusText, active = detectionActive)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
}

/**
 * `.hero` — the tappable blob + label column, and the node the wobble keyframes
 * are applied to.
 *
 * Owning the wobble here keeps the [Animatable] local, so it never has to be
 * passed around with a star-projected vector type.
 */
@Composable
private fun Hero(
    torchOn: Boolean,
    stateWord: String,
    stateSub: String,
    animateBlob: Boolean,
    shakeRequest: Int,
    onToggleTorch: () -> Unit,
) {
    val colors = ShakeItTheme.colors
    val shape = rememberBlobShape(on = torchOn, animate = animateBlob)
    val interactionSource = remember { MutableInteractionSource() }
    val toggleLabel = stringResource(R.string.cd_toggle_torch)
    val wobble = remember { Animatable(0f) }
    val currentToggle by rememberUpdatedState(onToggleTorch)

    LaunchedEffect(shakeRequest) {
        if (shakeRequest == 0) return@LaunchedEffect
        launch {
            wobble.snapTo(0f)
            wobble.animateTo(1f, tween(WOBBLE_DURATION_MS, easing = LinearEasing))
        }
        // The prototype removes `.shaking` and toggles inside the same 380ms
        // timeout, which snaps the transform back to rest a beat before the
        // state flips. At 95% of the curve the remaining rotation is ~0.1deg,
        // so the snap is invisible.
        delay(WOBBLE_TOGGLE_AT_MS)
        currentToggle()
        wobble.snapTo(0f)
    }

    Column(
        modifier = Modifier
            // `wobble.value` is read inside the layer block, so the animation
            // invalidates only this layer and never triggers recomposition.
            .graphicsLayer {
                val progress = wobble.value
                val rotation = sampleKeyframes(WobbleTimes, WobbleRotation, progress)
                rotationZ = rotation
                // CSS `rotate(θ) translateX(d)` composes as
                // translate(d·cosθ, d·sinθ) then rotate(θ), which is the order
                // `graphicsLayer` applies in.
                val offset = sampleKeyframes(WobbleTimes, WobbleTranslationX, progress).dp.toPx()
                val radians = rotation * (PI.toFloat() / 180f)
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        BlobCanvas(
            shape = shape,
            restingColor = colors.primary,
            litColor = colors.accent,
            glowColor = colors.glow,
            modifier = Modifier.size(BlobSize),
        )

        // `.hero-label` — 5px gap between the state word and its subtitle.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(text = stateWord, style = ShakeItType.stateWord, color = colors.onSurface)
            Text(text = stateSub, style = ShakeItType.stateSub, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * `.shake-btn` — `border: 1.5px solid var(--outline)`, `background: var(--surface)`
 * (`--surface-2` on `:active`), 10px/22px padding, 22px radius.
 */
@Composable
private fun ShakeButton(onClick: () -> Unit) {
    val colors = ShakeItTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val label = stringResource(R.string.shake_to_toggle)

    Box(
        modifier = Modifier
            // Background first, border second: draw modifiers paint in chain
            // order, so this puts the outline on top of the fill.
            .background(if (pressed) colors.surface2 else colors.surface, ShakeButtonShape)
            .border(BorderStroke(1.5.dp, colors.outline), ShakeButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = ShakeItType.buttonLabel, color = colors.onSurface)
    }
}

/** `.stats-row` — a hairline rule above three centred stats. */
@Composable
private fun StatsRow(activations: Int) {
    val colors = ShakeItTheme.colors

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Stat(value = activations.toString(), label = stringResource(R.string.stat_activations))
            Stat(
                value = stringResource(R.string.stat_time_on_today_value),
                label = stringResource(R.string.stat_time_on_today),
            )
            Stat(
                value = stringResource(R.string.stat_avg_session_value),
                label = stringResource(R.string.stat_avg_session),
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val colors = ShakeItTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = value, style = ShakeItType.statNumber, color = colors.onSurface)
        Text(text = label, style = ShakeItType.statLabel, color = colors.onSurfaceVariant)
    }
}

/** Interpolates the prototype's CSS keyframes, easing each segment separately. */
private fun sampleKeyframes(times: FloatArray, values: FloatArray, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    var index = 0
    while (index < times.lastIndex - 1 && p >= times[index + 1]) index++

    val span = times[index + 1] - times[index]
    val local = if (span <= 0f) 1f else ((p - times[index]) / span).coerceIn(0f, 1f)
    val eased = WobbleEase.transform(local)
    return values[index] + (values[index + 1] - values[index]) * eased
}
