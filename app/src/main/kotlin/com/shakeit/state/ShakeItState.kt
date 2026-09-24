package com.shakeit.state

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** `.seg-btn` options inside the "Shake mechanics" group. */
enum class ShakeGesture(val label: String) {
    Shake("Shake"),
    DoubleShake("Double-shake"),
    FlipAndShake("Flip & shake"),
}

/** `.seg-btn` options inside the "Appearance" group, i.e. `data-theme` on `<html>`. */
enum class ThemeMode(val label: String) {
    Light("Light"),
    Dark("Dark"),
    System("System"),
}

/**
 * The `<input type="range" min="1" max="5">` in the prototype plus its label
 * lookup table (`sensNames`).
 */
object Sensitivity {
    const val MIN = 1
    const val MAX = 5
    val LABELS = listOf("Very low", "Low", "Medium", "High", "Very high")

    fun label(value: Int): String = LABELS[(value - MIN).coerceIn(0, LABELS.lastIndex)]
}

/**
 * Everything that survives a process restart.
 *
 * `torchOn` is deliberately *not* here. It is hardware state, not a preference:
 * the camera service drops the torch when its client dies, so a persisted `true`
 * would be a lie at the next launch — and since the blob animates towards its
 * target, that lie would play out as a visible OFF morph on every cold start.
 * The screen starts at "off" and is corrected by the hardware mirror.
 */
@Immutable
data class ShakeItSnapshot(
    val sensitivity: Int,
    val gesture: ShakeGesture,
    val detectionActive: Boolean,
    val autoOffAfterFiveMinutes: Boolean,
    val startAfterReboot: Boolean,
    val runInBackground: Boolean,
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val activations: Int,
)

/**
 * Initial values, read straight off the prototype's markup: sensitivity 3
 * ("Medium"), detection/reboot/background/dynamic-color on, auto-off off,
 * `statActivations` = 7.
 *
 * The theme segmented control is marked "Light" in the HTML but the page starts
 * with no `data-theme` attribute, so it actually follows the system until the
 * user taps something — `ThemeMode.System` reproduces that behaviour and keeps
 * the highlighted segment honest.
 */
val DefaultShakeItSnapshot = ShakeItSnapshot(
    sensitivity = 3,
    gesture = ShakeGesture.Shake,
    detectionActive = true,
    autoOffAfterFiveMinutes = false,
    startAfterReboot = true,
    runInBackground = true,
    themeMode = ThemeMode.System,
    dynamicColor = true,
    activations = 7,
)

/**
 * Backing store. `SharedPreferences` keeps the prototype dependency-free while
 * still making settings (and the activation counter) survive a restart, which
 * `rememberSaveable` alone would not.
 */
class ShakeItStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(fallback: ShakeItSnapshot): ShakeItSnapshot = ShakeItSnapshot(
        sensitivity = prefs.getInt(KEY_SENSITIVITY, fallback.sensitivity)
            .coerceIn(Sensitivity.MIN, Sensitivity.MAX),
        gesture = readGesture(KEY_GESTURE, fallback.gesture),
        detectionActive = prefs.getBoolean(KEY_DETECTION_ACTIVE, fallback.detectionActive),
        autoOffAfterFiveMinutes = prefs.getBoolean(KEY_AUTO_OFF, fallback.autoOffAfterFiveMinutes),
        startAfterReboot = prefs.getBoolean(KEY_START_AFTER_REBOOT, fallback.startAfterReboot),
        runInBackground = prefs.getBoolean(KEY_RUN_IN_BACKGROUND, fallback.runInBackground),
        themeMode = readThemeMode(KEY_THEME_MODE, fallback.themeMode),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, fallback.dynamicColor),
        activations = prefs.getInt(KEY_ACTIVATIONS, fallback.activations),
    )

    fun write(snapshot: ShakeItSnapshot) {
        prefs.edit()
            .putInt(KEY_SENSITIVITY, snapshot.sensitivity)
            .putString(KEY_GESTURE, snapshot.gesture.name)
            .putBoolean(KEY_DETECTION_ACTIVE, snapshot.detectionActive)
            .putBoolean(KEY_AUTO_OFF, snapshot.autoOffAfterFiveMinutes)
            .putBoolean(KEY_START_AFTER_REBOOT, snapshot.startAfterReboot)
            .putBoolean(KEY_RUN_IN_BACKGROUND, snapshot.runInBackground)
            .putString(KEY_THEME_MODE, snapshot.themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, snapshot.dynamicColor)
            .putInt(KEY_ACTIVATIONS, snapshot.activations)
            .apply()
    }

    /**
     * Observes preference changes.
     *
     * The engine listens rather than being told: the switches live in the UI, but
     * what they control — the detector, the service, the boot receiver — lives in
     * the process. Both read this one file, and `SharedPreferences` hands the same
     * in-memory instance to every caller in a process, so a write from a
     * composition reaches the engine without either knowing about the other.
     *
     * The listener is held weakly by the platform, so callers must keep a strong
     * reference to it — which is why the engine stores its own.
     */
    fun addChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun removeChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun readGesture(key: String, fallback: ShakeGesture): ShakeGesture {
        val name = prefs.getString(key, null) ?: return fallback
        return ShakeGesture.values().firstOrNull { it.name == name } ?: fallback
    }

    private fun readThemeMode(key: String, fallback: ThemeMode): ThemeMode {
        val name = prefs.getString(key, null) ?: return fallback
        return ThemeMode.values().firstOrNull { it.name == name } ?: fallback
    }

    /**
     * Key names are not private: the engine reacts to specific preferences, and
     * spelling them out twice is how a rename silently breaks background
     * detection.
     */
    companion object {
        const val PREFS_NAME = "shakeit_state"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_GESTURE = "gesture"
        const val KEY_DETECTION_ACTIVE = "detection_active"
        const val KEY_AUTO_OFF = "auto_off_after_5_min"
        const val KEY_START_AFTER_REBOOT = "start_after_reboot"
        const val KEY_RUN_IN_BACKGROUND = "run_in_background"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_ACTIVATIONS = "activations"
    }
}

/**
 * Single source of truth for both screens. Held above the theme and the
 * navigation stack so that navigating, toggling the torch and editing settings
 * never tear state apart.
 */
@Stable
class ShakeItState(initial: ShakeItSnapshot) {

    /** Which screen is currently on top. Deliberately not persisted. */
    var screen by mutableStateOf(Screen.HOME)

    /**
     * The real flashlight, mirrored from [com.shakeit.engine.ShakeItEngine].
     * Drives the hero blob, the state word and the stats.
     *
     * Always starts false: the torch is off when a process starts, and the mirror
     * corrects this the moment the hardware says otherwise.
     */
    var torchOn by mutableStateOf(false)
        private set

    /** `.stat-num` for "Activations" — incremented every time the torch turns on. */
    var activations by mutableIntStateOf(initial.activations)
        private set

    /**
     * Bumped by [simulateShake] to request one shake from the screen. The hero
     * observes it, plays the `@keyframes wobble` animation and toggles 380 ms in
     * — the same sequence the prototype's "Shake to toggle" button runs, except
     * that the toggle now drives real hardware.
     */
    var shakeRequest by mutableIntStateOf(0)
        private set

    /**
     * Bumped by [onShakeDetected] when the accelerometer recognised a shake.
     * The hero wobbles exactly like [shakeRequest] does, but must not toggle
     * anything: the engine has already flipped the torch, so a second toggle
     * here would put the light back the way it was.
     */
    var detectedShake by mutableIntStateOf(0)
        private set

    /**
     * Backing value for [sensitivity]. Private so the clamp in [setSensitivity]
     * cannot be bypassed — and because a public `var sensitivity` would generate
     * its own `setSensitivity(int)` accessor and clash with that function's JVM
     * signature.
     */
    private var sensitivityValue by mutableIntStateOf(initial.sensitivity)

    /** The range input's current value. Change it through [setSensitivity]. */
    val sensitivity: Int
        get() = sensitivityValue

    var gesture by mutableStateOf(initial.gesture)
    var detectionActive by mutableStateOf(initial.detectionActive)
    var autoOffAfterFiveMinutes by mutableStateOf(initial.autoOffAfterFiveMinutes)
    var startAfterReboot by mutableStateOf(initial.startAfterReboot)
    var runInBackground by mutableStateOf(initial.runInBackground)
    var themeMode by mutableStateOf(initial.themeMode)
    var dynamicColor by mutableStateOf(initial.dynamicColor)

    val sensitivityLabel: String
        get() = Sensitivity.label(sensitivity)

    /**
     * `<input type="range" min="1" max="5">` cannot produce an out-of-range
     * value, and neither can this — a corrupted preference is clamped back
     * inside the bounds the label table covers.
     */
    fun setSensitivity(value: Int) {
        sensitivityValue = value.coerceIn(Sensitivity.MIN, Sensitivity.MAX)
    }

    /**
     * The only write path for [torchOn]: the engine reports what the hardware
     * did and this mirrors it. There is no `toggleTorch()` here on purpose — if
     * the screen flipped its own copy, a torch change from a shake, from the
     * notification or from another app would leave it showing a light that is
     * not on.
     *
     * Idempotent, because a `StateFlow` hands its current value to every new
     * collector: an activity recreated while the torch is on must not count a
     * second activation.
     */
    fun onTorchStateChanged(enabled: Boolean) {
        if (torchOn == enabled) return
        torchOn = enabled
        if (enabled) activations++
    }

    /** A shake was recognised by the hardware. Animation only — see [detectedShake]. */
    fun onShakeDetected() {
        detectedShake++
    }

    fun simulateShake() {
        shakeRequest++
    }

    fun openSettings() {
        screen = Screen.SETTINGS
    }

    fun closeSettings() {
        screen = Screen.HOME
    }

    /** `◐` flips to the opposite explicit mode, exactly like the prototype's theme button. */
    fun toggleTheme(isDarkNow: Boolean) {
        themeMode = if (isDarkNow) ThemeMode.Light else ThemeMode.Dark
    }

    fun snapshot(): ShakeItSnapshot = ShakeItSnapshot(
        sensitivity = sensitivity,
        gesture = gesture,
        detectionActive = detectionActive,
        autoOffAfterFiveMinutes = autoOffAfterFiveMinutes,
        startAfterReboot = startAfterReboot,
        runInBackground = runInBackground,
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        activations = activations,
    )

    /** The two destinations from the prototype (`#home` and `#settings`). */
    enum class Screen { HOME, SETTINGS }
}

/**
 * Creates the app state, restoring it from [ShakeItStore] and writing back on
 * every change.
 */
@Composable
fun rememberShakeItState(): ShakeItState {
    val context = LocalContext.current
    val store = remember(context) { ShakeItStore(context) }
    val state = remember(store) { ShakeItState(store.read(DefaultShakeItSnapshot)) }

    val snapshot = state.snapshot()
    LaunchedEffect(snapshot) { store.write(snapshot) }

    return state
}
