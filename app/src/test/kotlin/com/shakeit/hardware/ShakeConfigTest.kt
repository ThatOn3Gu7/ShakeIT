package com.shakeit.hardware

import com.shakeit.state.DefaultShakeItSnapshot
import com.shakeit.state.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sensitivity slider, which is one of the settings that has to do something
 * real rather than merely persist.
 *
 * What it may change is narrow: how hard a stroke has to be. What it must never
 * change is the *shape* of the gesture — the impulse window, the reversal
 * requirement, the quiet period and the cooldown are what make "one shake, one
 * toggle" true, and a slider that quietly rewrote them would mean the same hand
 * motion does something different at level 5 than at level 1.
 */
class ShakeConfigTest {

    private val levels = (SENSITIVITY_MIN..SENSITIVITY_MAX).toList()

    @Test
    fun `the default level reproduces the shipped tuning exactly`() {
        // An untouched install has to behave exactly as it did before the slider
        // was wired up, which is the strongest form of "no regression".
        assertEquals(DefaultShakeConfig, shakeConfigFor(SENSITIVITY_DEFAULT))
    }

    @Test
    fun `only the impulse threshold moves across the ladder`() {
        levels.forEach { level ->
            val config = shakeConfigFor(level)
            assertEquals(
                "level $level changed something other than the threshold",
                DefaultShakeConfig,
                config.copy(impulseThreshold = DefaultShakeConfig.impulseThreshold),
            )
        }
    }

    @Test
    fun `each step is the same distance apart`() {
        val thresholds = levels.map { shakeConfigFor(it).impulseThreshold }

        assertEquals(listOf(19f, 16.5f, 14f, 11.5f, 9f), thresholds)
    }

    @Test
    fun `a higher setting lowers the bar and never raises it`() {
        val thresholds = levels.map { shakeConfigFor(it).impulseThreshold }

        thresholds.zipWithNext().forEach { (harder, easier) ->
            assertTrue("$harder should be above $easier", harder > easier)
        }
    }

    @Test
    fun `the timing rules survive every level`() {
        levels.forEach { level ->
            val config = shakeConfigFor(level)

            assertEquals(DefaultShakeConfig.requiredImpulses, config.requiredImpulses)
            assertEquals(DefaultShakeConfig.requiredReversals, config.requiredReversals)
            assertEquals(DefaultShakeConfig.windowMillis, config.windowMillis)
            assertEquals(DefaultShakeConfig.quietMillis, config.quietMillis)
            assertEquals(DefaultShakeConfig.cooldownMillis, config.cooldownMillis)
            assertEquals(DefaultShakeConfig.maxImpulseMillis, config.maxImpulseMillis)
            assertEquals(DefaultShakeConfig.releaseRatio, config.releaseRatio)
        }
    }

    @Test
    fun `values outside the slider are clamped to its ends`() {
        // A persisted value from an older version, or a hand-edited preferences
        // file, must not produce a threshold the ladder never intended.
        assertEquals(shakeConfigFor(SENSITIVITY_MIN), shakeConfigFor(SENSITIVITY_MIN - 4))
        assertEquals(shakeConfigFor(SENSITIVITY_MIN), shakeConfigFor(0))
        assertEquals(shakeConfigFor(SENSITIVITY_MAX), shakeConfigFor(SENSITIVITY_MAX + 94))
    }

    @Test
    fun `the stored default sensitivity is the level the shipped tuning belongs to`() {
        assertEquals(SENSITIVITY_DEFAULT, DefaultShakeItSnapshot.sensitivity)
    }

    @Test
    fun `the slider in the UI and the ladder agree about its own range`() {
        // Two constants, two packages, one control: if they ever drift, the UI
        // would offer a level the tuning silently clamps away.
        assertEquals(Sensitivity.MIN, SENSITIVITY_MIN)
        assertEquals(Sensitivity.MAX, SENSITIVITY_MAX)
        assertEquals(Sensitivity.LABELS.size, levels.size)
    }
}
