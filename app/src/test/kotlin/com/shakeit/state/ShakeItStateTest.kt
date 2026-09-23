package com.shakeit.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour of the single state holder both screens read from.
 *
 * The state is Compose snapshot state, which is pure Kotlin/JVM, so this runs as
 * an ordinary unit test — no Robolectric, no instrumented device. Persistence
 * ([ShakeItStore]) is deliberately *not* covered here: it needs a real `Context`.
 */
class ShakeItStateTest {

    private fun newState(): ShakeItState = ShakeItState(DefaultShakeItSnapshot)

    // ------------------------------------------------------------------
    // Initial values, read off the prototype's markup
    // ------------------------------------------------------------------

    @Test
    fun `defaults come straight off the prototype`() {
        val state = newState()

        assertEquals(3, state.sensitivity)
        assertEquals("Medium", state.sensitivityLabel)
        assertEquals(ShakeGesture.Shake, state.gesture)
        assertTrue(state.detectionActive)
        assertFalse(state.autoOffAfterFiveMinutes)
        assertTrue(state.startAfterReboot)
        assertTrue(state.runInBackground)
        // The HTML marks "Light" but the page starts without `data-theme`, so it
        // follows the system until something is tapped.
        assertEquals(ThemeMode.System, state.themeMode)
        assertTrue(state.dynamicColor)
        assertFalse(state.shizukuConnected)
        assertFalse(state.torchOn)
        assertEquals(7, state.activations)
        assertEquals(ShakeItState.Screen.HOME, state.screen)
    }

    @Test
    fun `segmented control labels match the prototype`() {
        assertEquals("Shake", ShakeGesture.Shake.label)
        assertEquals("Double-shake", ShakeGesture.DoubleShake.label)
        assertEquals("Flip & shake", ShakeGesture.FlipAndShake.label)
        assertEquals("Light", ThemeMode.Light.label)
        assertEquals("Dark", ThemeMode.Dark.label)
        assertEquals("System", ThemeMode.System.label)
    }

    @Test
    fun `sensitivity labels cover the whole range input`() {
        assertEquals(listOf("Very low", "Low", "Medium", "High", "Very high"), Sensitivity.LABELS)
        assertEquals(Sensitivity.MAX - Sensitivity.MIN + 1, Sensitivity.LABELS.size)
        assertEquals("Very low", Sensitivity.label(Sensitivity.MIN))
        assertEquals("Very high", Sensitivity.label(Sensitivity.MAX))
        // A corrupted preference must not crash the settings row.
        assertEquals("Very low", Sensitivity.label(-5))
        assertEquals("Very high", Sensitivity.label(500))
    }

    // ------------------------------------------------------------------
    // Torch + activation counter
    // ------------------------------------------------------------------

    @Test
    fun `turning the torch on counts an activation, turning it off does not`() {
        val state = newState()

        state.onTorchStateChanged(true)
        assertTrue(state.torchOn)
        assertEquals(8, state.activations)

        state.onTorchStateChanged(false)
        assertFalse(state.torchOn)
        assertEquals("turning off must not count", 8, state.activations)

        state.onTorchStateChanged(true)
        assertEquals(9, state.activations)
    }

    @Test
    fun `reporting the state the screen already has counts nothing`() {
        val state = newState()

        // The hardware mirror is idempotent: a StateFlow hands its current value
        // to every new collector, so an activity recreated while the torch is on
        // must not read as another activation.
        state.onTorchStateChanged(false)
        assertEquals(7, state.activations)

        state.onTorchStateChanged(true)
        state.onTorchStateChanged(true)
        state.onTorchStateChanged(true)

        assertEquals(8, state.activations)
    }

    @Test
    fun `a detected shake animates the hero without touching the torch`() {
        val state = newState()
        assertEquals(0, state.detectedShake)

        state.onShakeDetected()
        state.onShakeDetected()

        // The counter must keep moving so consecutive shakes are each noticed.
        assertEquals(2, state.detectedShake)
        // The engine flipped the torch before it bumped this counter, so the
        // screen animating must not flip it back.
        assertFalse("a detected shake must not toggle the torch", state.torchOn)
        assertEquals(7, state.activations)
        assertEquals("a real shake is not a request from the button", 0, state.shakeRequest)
    }

    @Test
    fun `a shake request only asks the hero to animate`() {
        val state = newState()
        assertEquals(0, state.shakeRequest)

        state.simulateShake()
        state.simulateShake()

        // The counter must keep moving so consecutive shakes are each noticed.
        assertEquals(2, state.shakeRequest)
        // The hero flips the torch 380ms into the wobble, not the button itself.
        assertFalse("a shake request must not toggle the torch", state.torchOn)
        assertEquals(7, state.activations)
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    @Test
    fun `sensitivity is clamped to the range input bounds`() {
        val state = newState()

        state.setSensitivity(0)
        assertEquals(Sensitivity.MIN, state.sensitivity)
        assertEquals("Very low", state.sensitivityLabel)

        state.setSensitivity(99)
        assertEquals(Sensitivity.MAX, state.sensitivity)
        assertEquals("Very high", state.sensitivityLabel)

        state.setSensitivity(-1)
        assertEquals(Sensitivity.MIN, state.sensitivity)

        state.setSensitivity(4)
        assertEquals(4, state.sensitivity)
        assertEquals("High", state.sensitivityLabel)
    }

    @Test
    fun `settings are writable and independent of each other`() {
        val state = newState()

        state.gesture = ShakeGesture.FlipAndShake
        state.detectionActive = false
        state.autoOffAfterFiveMinutes = true
        state.startAfterReboot = false
        state.runInBackground = false
        state.dynamicColor = false

        assertEquals(ShakeGesture.FlipAndShake, state.gesture)
        assertFalse(state.detectionActive)
        assertTrue(state.autoOffAfterFiveMinutes)
        assertFalse(state.startAfterReboot)
        assertFalse(state.runInBackground)
        assertFalse(state.dynamicColor)
        // Nothing above touches the torch.
        assertFalse(state.torchOn)
        assertEquals(7, state.activations)
    }

    @Test
    fun `the theme button commits to an explicit mode`() {
        val state = newState()

        state.toggleTheme(isDarkNow = true)
        assertEquals(ThemeMode.Light, state.themeMode)

        state.toggleTheme(isDarkNow = false)
        assertEquals(ThemeMode.Dark, state.themeMode)

        // `◐` never leaves the app in "System".
        state.themeMode = ThemeMode.System
        state.toggleTheme(isDarkNow = false)
        assertNotEquals(ThemeMode.System, state.themeMode)
        assertEquals(ThemeMode.Dark, state.themeMode)
    }

    @Test
    fun `the shizuku stub reports a connection it never really made`() {
        val state = newState()
        assertFalse(state.shizukuConnected)

        state.connectShizuku()

        assertTrue(state.shizukuConnected)
    }

    // ------------------------------------------------------------------
    // Navigation + persistence boundary
    // ------------------------------------------------------------------

    @Test
    fun `settings opens over home and closes back onto it`() {
        val state = newState()

        state.openSettings()
        assertEquals(ShakeItState.Screen.SETTINGS, state.screen)

        state.closeSettings()
        assertEquals(ShakeItState.Screen.HOME, state.screen)
    }

    @Test
    fun `snapshot carries every persisted field`() {
        val state = newState()
        state.setSensitivity(5)
        state.gesture = ShakeGesture.DoubleShake
        state.detectionActive = false
        state.autoOffAfterFiveMinutes = true
        state.startAfterReboot = false
        state.runInBackground = false
        state.themeMode = ThemeMode.Dark
        state.dynamicColor = false
        state.connectShizuku()
        state.onTorchStateChanged(true)

        val snapshot = state.snapshot()
        val restored = ShakeItState(snapshot)

        assertEquals(snapshot, restored.snapshot())
        // The torch itself is hardware state, so it is not restored — a
        // persisted "on" would be a lie at the next launch, and the blob would
        // spend its first second morphing back to OFF.
        assertFalse(restored.torchOn)
        assertEquals("but what it did is still counted", 8, restored.activations)
        assertEquals(ShakeGesture.DoubleShake, restored.gesture)
        assertEquals(ThemeMode.Dark, restored.themeMode)
        assertTrue(restored.shizukuConnected)
    }

    @Test
    fun `snapshot leaves out the screen, which is navigation not storage`() {
        val state = newState()
        state.openSettings()

        val restored = ShakeItState(state.snapshot())

        assertEquals(ShakeItState.Screen.HOME, restored.screen)
    }

    @Test
    fun `a snapshot is a value, so later edits do not rewrite it`() {
        val state = newState()
        val before = state.snapshot()

        state.setSensitivity(1)
        state.onTorchStateChanged(true)
        state.themeMode = ThemeMode.Light

        assertEquals(3, before.sensitivity)
        assertEquals(ThemeMode.System, before.themeMode)
        assertEquals(DefaultShakeItSnapshot, before)
        assertNotEquals(before, state.snapshot())
    }
}
