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

/** The supported gesture modes. */
enum class ShakeGesture(val label: String) {
    Shake("Shake"),
    DoubleShake("Double Shake"),
}

/**
 * The time gate for Double Shake.
 *
 * The accelerometer pipeline already emits distinct, debounced shake events. This
 * small state machine only decides whether one event is the first half of a pair
 * or completes the pair. It has no Android or coroutine dependency, which makes
 * the boundary easy to test and keeps the engine's lifecycle reset explicit.
 */
class DoubleShakeGate(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private var firstShakeAtMillis: Long? = null

    /**
     * Accepts one valid shake event. Returns true only for the second event of a
     * pair. An event arriving after the timeout becomes a new first event rather
     * than toggling immediately.
     */
    fun accept(nowMillis: Long): Boolean {
        val first = firstShakeAtMillis
        if (first == null || nowMillis - first > timeoutMillis || nowMillis < first) {
            firstShakeAtMillis = nowMillis
            return false
        }
        firstShakeAtMillis = null
        return true
    }

    fun reset() {
        firstShakeAtMillis = null
    }

    fun isWaiting(): Boolean = firstShakeAtMillis != null

    companion object {
        /** Tunable middle of the requested roughly one-to-two-second window. */
        const val DEFAULT_TIMEOUT_MILLIS = 1_500L
    }
}

/** The sensitivity slider's five discrete levels. */
object Sensitivity {
    const val MIN = 1
    const val MAX = 5
    val LABELS = listOf("Very low", "Low", "Medium", "High", "Very high")

    fun label(value: Int): String = LABELS[(value - MIN).coerceIn(0, LABELS.lastIndex)]
}

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

class ShakeItStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(fallback: ShakeItSnapshot): ShakeItSnapshot = ShakeItSnapshot(
        sensitivity = prefs.getInt(KEY_SENSITIVITY, fallback.sensitivity).coerceIn(Sensitivity.MIN, Sensitivity.MAX),
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

    fun addChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)
    fun removeChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun readGesture(key: String, fallback: ShakeGesture): ShakeGesture {
        val name = prefs.getString(key, null) ?: return fallback
        return ShakeGesture.values().firstOrNull { it.name == name } ?: fallback
    }

    private fun readThemeMode(key: String, fallback: ThemeMode): ThemeMode {
        val name = prefs.getString(key, null) ?: return fallback
        return ThemeMode.values().firstOrNull { it.name == name } ?: fallback
    }

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

@Stable
class ShakeItState(initial: ShakeItSnapshot) {
    var screen by mutableStateOf(ShakeItState.Screen.HOME)
    var torchOn by mutableStateOf(false)
        private set
    var activations by mutableIntStateOf(initial.activations)
        private set
    var shakeRequest by mutableIntStateOf(0)
        private set
    var detectedShake by mutableIntStateOf(0)
        private set
    private var sensitivityValue by mutableIntStateOf(initial.sensitivity)
    val sensitivity: Int get() = sensitivityValue
    var gesture by mutableStateOf(initial.gesture)
    var detectionActive by mutableStateOf(initial.detectionActive)
    var autoOffAfterFiveMinutes by mutableStateOf(initial.autoOffAfterFiveMinutes)
    var startAfterReboot by mutableStateOf(initial.startAfterReboot)
    var runInBackground by mutableStateOf(initial.runInBackground)
    var themeMode by mutableStateOf(initial.themeMode)
    var dynamicColor by mutableStateOf(initial.dynamicColor)
    val sensitivityLabel: String get() = Sensitivity.label(sensitivity)

    fun setSensitivity(value: Int) { sensitivityValue = value.coerceIn(Sensitivity.MIN, Sensitivity.MAX) }
    fun onTorchStateChanged(enabled: Boolean) {
        if (torchOn == enabled) return
        torchOn = enabled
        if (enabled) activations++
    }
    fun onShakeDetected() { detectedShake++ }
    fun simulateShake() { shakeRequest++ }
    fun openSettings() { screen = Screen.SETTINGS }
    fun closeSettings() { screen = Screen.HOME }
    fun toggleTheme(isDarkNow: Boolean) { themeMode = if (isDarkNow) ThemeMode.Light else ThemeMode.Dark }
    fun snapshot() = ShakeItSnapshot(sensitivity, gesture, detectionActive, autoOffAfterFiveMinutes, startAfterReboot, runInBackground, themeMode, dynamicColor, activations)
    enum class Screen { HOME, SETTINGS }
}

@Composable
fun rememberShakeItState(): ShakeItState {
    val context = LocalContext.current
    val store = remember(context) { ShakeItStore(context) }
    val state = remember(store) { ShakeItState(store.read(DefaultShakeItSnapshot)) }
    val snapshot = state.snapshot()
    LaunchedEffect(snapshot) { store.write(snapshot) }
    return state
}
