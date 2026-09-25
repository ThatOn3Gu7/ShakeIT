package com.shakeit.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ShakeIT's motion tokens.
 *
 * The rule behind them: **springs for state, tweens for sequences.** Anything
 * that sits at a value and may be interrupted mid-flight — a press, a switch, a
 * screen change, an expanding section — is a spring, because a spring picks up
 * from wherever it currently is and settles, while a tween that is interrupted
 * restarts its clock and visibly stutters. Anything that *is* a fixed sequence
 * in time — the 400ms wobble the hero plays when a shake is recognised, the
 * one-shot energy ring that follows it — keeps a tween, because its timing is
 * the content.
 *
 * No spec here is bouncy. `DampingRatioNoBouncy` and a damping ratio of 0.9 are
 * the most playful this app gets: overshoot on a flashlight toggle reads as a
 * cartoon, not as physics.
 */
object ShakeItMotion {

    /* ------------------------------------------------------------ easings */

    /** Material's emphasized curve: fast out of the gate, long settle. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Material's emphasized decelerate, for things arriving. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material's emphasized accelerate, for things leaving. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /* ------------------------------------------------------------ springs */

    /**
     * Micro-interactions: press states, chip and icon swaps, the hero's scale.
     * High stiffness so the response is immediate — a control that takes a
     * quarter second to acknowledge a finger feels broken.
     */
    val Snap: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1_100f,
        visibilityThreshold = 0.001f,
    )

    /** The same immediate micro-interaction spring for measured Dp properties. */
    val SnapDp: SpringSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1_100f,
    )

    /**
     * Things settling into a new state and staying there: an expanded section, a
     * size change, the hero returning from a press. Slower than [Snap] so the
     * movement is legible rather than instant.
     */
    val Settle: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f,
        visibilityThreshold = 0.001f,
    )

    /**
     * Screen-to-screen movement. Damping just under critical so a navigation
     * interrupted halfway reverses without a hitch.
     */
    val Screen: SpringSpec<Float> = spring(
        dampingRatio = 0.92f,
        stiffness = 430f,
        visibilityThreshold = 0.001f,
    )

    /* ------------------------------------------------------------- tweens */

    /**
     * The theme crossfade. A tween because the two palettes are known at the
     * start and nothing should interrupt a colour change halfway.
     */
    val ThemeCrossfade = tween<Float>(durationMillis = 350, easing = Emphasized)

    /** The hero's spin when the torch changes state. */
    val HeroSpin = tween<Float>(durationMillis = 520, easing = Emphasized)

    /** The one-shot ring of energy after a shake is recognised. */
    const val ENERGY_RING_MS = 620

    /* ------------------------------------------------------------- values */

    /** How far a pressed surface gives. Small enough to feel firm. */
    const val PRESS_SCALE = 0.972f

    /** How far the hero gives when pressed — it is a bigger object, so less. */
    const val HERO_PRESS_SCALE = 0.982f

    /** The lift the hero takes when the torch comes on. */
    const val HERO_LIT_SCALE = 1.03f

    /** How far a screen travels while it is off-stage. */
    val HiddenTravel: Dp = 28.dp
}
