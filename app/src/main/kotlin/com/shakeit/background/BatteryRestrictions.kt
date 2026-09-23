package com.shakeit.background

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.getSystemService
import java.util.Locale

/**
 * Which power manager the device ships with, so the guidance can name the
 * setting the user will actually see rather than describing a stock screen that
 * their phone does not have.
 */
enum class PowerManagerVendor {
    /** Infinix, Tecno and itel: XOS / HiOS, all Transsion. */
    Transsion,
    Xiaomi,
    Stock,
}

/**
 * What the platform and the device's own power manager will let ShakeIT do in
 * the background, and the standard route to lifting it.
 *
 * The distinction that matters is the one the platform actually draws. An app
 * that is *optimised* for battery use is put into Doze with everything else, and
 * **Doze ignores partial wake locks** — so the detector can be armed, its service
 * alive, its notification visible, and still receive nothing. Being exempt
 * ("Unrestricted" in the system UI) is what makes the wake lock count.
 *
 * Only standard AOSP intents are used here. `ACTION_REQUEST_IGNORE_BATTERY_
 * OPTIMIZATIONS` — the one that pops a yes/no dialog — is deliberately avoided:
 * it needs a permission that Play restricts, and it pre-empts a choice the user
 * should make on a screen they can read. The vendor's own auto-start manager has
 * no public intent that survives an OTA, so it is described in words and reached
 * through the app's own settings page instead of guessed at by component name.
 */
class BatteryRestrictions(context: Context) {

    private val appContext = context.applicationContext
    private val powerManager: PowerManager? = appContext.getSystemService()

    /**
     * Whether the user has exempted ShakeIT from battery optimisation.
     *
     * Defaults to "unrestricted" when there is no power manager to ask, so a
     * device that cannot answer never produces a warning it cannot justify.
     */
    val isUnrestricted: Boolean
        get() = powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) ?: true

    /** Which vendor's power manager is in charge of this device. */
    val vendor: PowerManagerVendor
        get() = when (Build.MANUFACTURER.lowercase(Locale.ROOT)) {
            "infinix", "tecno", "itel", "transsion" -> PowerManagerVendor.Transsion
            "xiaomi", "redmi", "poco", "blackshark" -> PowerManagerVendor.Xiaomi
            else -> PowerManagerVendor.Stock
        }

    /**
     * Opens the standard battery-optimisation screen, where the user can set
     * ShakeIT to "Unrestricted" / "Don't optimise".
     *
     * @return false if this device has no such screen, which is worth knowing
     *   rather than crashing over
     */
    fun openBatterySettings(): Boolean =
        launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    /**
     * Opens ShakeIT's own system settings page. On stock Android that is where
     * Battery → Unrestricted lives; on XOS and MIUI it is also the doorway to the
     * vendor's auto-start and background-management controls.
     */
    fun openAppSettings(): Boolean = launch(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", appContext.packageName, null),
        ),
    )

    private fun launch(intent: Intent): Boolean = try {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: ActivityNotFoundException) {
        Log.w(TAG, "no settings screen for ${intent.action}", error)
        false
    } catch (error: SecurityException) {
        Log.w(TAG, "not allowed to open ${intent.action}", error)
        false
    }

    private companion object {
        const val TAG = "BatteryRestrictions"
    }
}
