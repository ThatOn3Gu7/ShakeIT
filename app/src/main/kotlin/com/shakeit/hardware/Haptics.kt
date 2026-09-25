package com.shakeit.hardware

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService

/**
 * One short tick whenever the torch really changes state.
 *
 * A tap, not a buzz: the haptic is confirmation that the hardware moved, so it
 * fires on the state change rather than on the gesture — a shake that turns the
 * torch on while the phone is in your hand should feel the same as a tap that
 * does.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        context.getSystemService<Vibrator>()
    }

    fun tick() {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        // Each branch reads Build.VERSION.SDK_INT directly: lint's API-level
        // check does not follow the value through a local, and an unguarded
        // VibrationEffect would be a lint error rather than a warning.
        when {
            // A predefined effect is tuned per device, so it stays subtle on
            // hardware with a strong motor.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                device.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                device.vibrate(VibrationEffect.createOneShot(TICK_MILLIS, TICK_AMPLITUDE))

            else -> vibrateLegacy(device)
        }
    }

    /** `VibrationEffect` arrived in API 26; below that there is only the plain call. */
    @Suppress("DEPRECATION")
    private fun vibrateLegacy(device: Vibrator) {
        device.vibrate(TICK_MILLIS)
    }

    private companion object {
        const val TICK_MILLIS = 15L

        /** Of 255 — enough to feel through a case, not enough to be a nuisance. */
        const val TICK_AMPLITUDE = 90
    }
}
