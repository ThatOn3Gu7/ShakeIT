package com.shakeit.background

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.getSystemService
import java.util.Locale

/**
 * Which power manager the device ships with, so guidance can name the setting the
 * user will actually see rather than describing a stock screen their phone does
 * not have.
 */
enum class PowerManagerVendor {
    /** Infinix, Tecno and itel: XOS / HiOS, all Transsion. */
    Transsion,
    Xiaomi,
    Stock,
}

/**
 * The answer to "is something restricting ShakeIT in the background?".
 *
 * Four states rather than a boolean, because the honest answer is often "this
 * platform will not tell a third-party app". Collapsing that into *allowed* is
 * how an app ends up claiming a setting is fixed when it only ever checked a
 * different mechanism.
 */
enum class RestrictionState {
    /** The mechanism was queried and ShakeIT is on the right side of it. */
    ALLOWED,

    /** The mechanism was queried and it restricts ShakeIT. */
    RESTRICTED,

    /** This Android version does not have the mechanism at all. */
    NOT_SUPPORTED,

    /** The mechanism exists but will not answer, or answered with an error. */
    UNKNOWN,
}

/**
 * Low Power Standby, which Android 14 introduced and which is the one platform
 * feature that silently invalidates this app's whole wake-lock strategy.
 *
 * The documentation is blunt: when Low Power Standby is enabled, apps —
 * **including apps running foreground services** — lose network access and have
 * any wake locks they hold *ignored* while the device is non-interactive, outside
 * maintenance windows. So a detector can be armed, in a foreground service,
 * holding a partial wake lock, and still receive nothing.
 *
 * There is no permission a normal app can hold to exempt itself. Exemption comes
 * from the system: being on the power-exemption allowlist (the same list the
 * "Unrestricted" battery setting writes), being a device or profile owner, or
 * holding an allowed reason such as an active voice-interaction session. ShakeIT
 * can therefore only *report* this, and point at the one setting that changes it.
 */
data class LowPowerStandby(
    /** `PowerManager.isLowPowerStandbyEnabled`, or null below API 33. */
    val enabled: Boolean?,
    /** `PowerManager.isExemptFromLowPowerStandby`, or null below API 34. */
    val exempt: Boolean?,
    val state: RestrictionState,
)

/** Everything the diagnostics screen reports about power policy, as plain facts. */
data class PowerDiagnosticsSnapshot(
    /** The Android/Doze power-exemption allowlist — the "Don't optimise" switch. */
    val dozeExemption: RestrictionState,
    /** Whether the device is in Doze right now, which ignores wake locks. */
    val deviceIdleMode: Boolean,
    /** Whether Battery Saver is on, which tightens everything below it. */
    val powerSaveMode: Boolean,
    /** The `RUN_ANY_IN_BACKGROUND` app-op: the platform's background-restriction flag. */
    val backgroundAppOps: RestrictionState,
    val lowPowerStandby: LowPowerStandby,
    /**
     * Always [RestrictionState.UNKNOWN]: no vendor publishes a supported way to
     * ask whether auto-start is enabled for a package, and guessing a component
     * name to open the screen is not a query. Reported rather than assumed.
     */
    val autoStart: RestrictionState,
    val vendor: PowerManagerVendor,
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
)

/**
 * Reads every power-policy mechanism Android actually exposes to a third-party
 * app, and keeps them *separate*.
 *
 * The distinction is the whole point. `isIgnoringBatteryOptimizations` answers
 * one question — "is this package on the Doze/App-Standby power-exemption
 * allowlist?" — and nothing else. It does not report an OEM's "Battery →
 * Unrestricted" menu, its auto-start manager, or its app freezer, and on a
 * Transsion device those are separate switches in separate places. Earlier this
 * app labelled the allowlist answer "Unrestricted", which read as a claim about
 * all of them; each mechanism now reports its own state, including when the
 * answer is that no third-party app can ask.
 *
 * Only standard AOSP intents are used to change anything.
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — the yes/no dialog — is
 * deliberately avoided: it needs a permission Play restricts, and it decides for
 * the user before they have read anything.
 */
class PowerDiagnostics(context: Context) {

    private val appContext = context.applicationContext
    private val powerManager: PowerManager? = appContext.getSystemService()
    private val appOpsManager: AppOpsManager? = appContext.getSystemService()

    /** Which vendor's power manager is in charge of this device. */
    val vendor: PowerManagerVendor
        get() = when (Build.MANUFACTURER.lowercase(Locale.ROOT)) {
            "infinix", "tecno", "itel", "transsion" -> PowerManagerVendor.Transsion
            "xiaomi", "redmi", "poco", "blackshark" -> PowerManagerVendor.Xiaomi
            else -> PowerManagerVendor.Stock
        }

    /**
     * The Android battery-optimisation exemption: whether ShakeIT is on the
     * power-exemption allowlist, which is what stops Doze from ignoring its wake
     * locks and deferring its work.
     *
     * This is *not* an OEM "Unrestricted" reading. On a device whose manufacturer
     * has its own background policy, both can be true at once: exempt here, and
     * still frozen there.
     */
    fun dozeExemption(): RestrictionState {
        val manager = powerManager ?: return RestrictionState.UNKNOWN
        return try {
            val exempt = manager.isIgnoringBatteryOptimizations(appContext.packageName)
            if (exempt) RestrictionState.ALLOWED else RestrictionState.RESTRICTED
        } catch (error: RuntimeException) {
            Log.w(TAG, "could not read the battery-optimisation exemption", error)
            RestrictionState.UNKNOWN
        }
    }

    /**
     * The `RUN_ANY_IN_BACKGROUND` app-op — the flag the platform's own "restrict
     * background activity" action writes, and the closest thing to a queryable
     * answer about background restriction.
     *
     * The op name is spelled out rather than referenced: `AppOpsManager`'s
     * constant for it is `@SystemApi`, so a normal app cannot compile against it,
     * while the string has been stable since Android 8. A device that does not
     * know the op answers with an error, which is reported as
     * [RestrictionState.UNKNOWN] rather than guessed at.
     */
    // unsafeCheckOpNoThrow was renamed to checkOpNoThrow in API 36, and the new
    // name is not callable below that, so the deprecated one stays.
    @Suppress("DEPRECATION")
    fun backgroundAppOps(): RestrictionState {
        val manager = appOpsManager ?: return RestrictionState.UNKNOWN
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return RestrictionState.NOT_SUPPORTED
        return try {
            val mode = manager.unsafeCheckOpNoThrow(
                OP_RUN_ANY_IN_BACKGROUND,
                Process.myUid(),
                appContext.packageName,
            )
            when (mode) {
                AppOpsManager.MODE_ALLOWED, AppOpsManager.MODE_DEFAULT -> RestrictionState.ALLOWED
                // FOREGROUND means "only while the app is in the foreground",
                // which for this app is a restriction by another name.
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_FOREGROUND ->
                    RestrictionState.RESTRICTED
                // MODE_ERRORED and anything unrecognised: no answer, not a verdict.
                else -> RestrictionState.UNKNOWN
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "could not read the background app-op", error)
            RestrictionState.UNKNOWN
        }
    }

    /**
     * Low Power Standby: whether it is enabled, and whether ShakeIT is exempt
     * from it. Read separately because the two are different APIs on different
     * levels — enabled from API 33, exemption from API 34.
     */
    fun lowPowerStandby(): LowPowerStandby {
        val manager = powerManager
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return LowPowerStandby(
                enabled = null,
                exempt = null,
                state = RestrictionState.NOT_SUPPORTED,
            )
        }
        val enabled = try {
            manager.isLowPowerStandbyEnabled
        } catch (error: RuntimeException) {
            Log.w(TAG, "could not read the Low Power Standby state", error)
            return LowPowerStandby(null, null, RestrictionState.UNKNOWN)
        }
        if (!enabled) {
            return LowPowerStandby(enabled = false, exempt = null, state = RestrictionState.ALLOWED)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Enabled, and no way to ask whether ShakeIT is exempt: say so.
            return LowPowerStandby(enabled = true, exempt = null, state = RestrictionState.UNKNOWN)
        }
        val exempt = try {
            manager.isExemptFromLowPowerStandby
        } catch (error: RuntimeException) {
            Log.w(TAG, "could not read the Low Power Standby exemption", error)
            return LowPowerStandby(enabled = true, exempt = null, state = RestrictionState.UNKNOWN)
        }
        return LowPowerStandby(
            enabled = true,
            exempt = exempt,
            state = if (exempt) RestrictionState.ALLOWED else RestrictionState.RESTRICTED,
        )
    }

    /** Every reading at once, for the diagnostics panel and the log. */
    fun snapshot(): PowerDiagnosticsSnapshot = PowerDiagnosticsSnapshot(
        dozeExemption = dozeExemption(),
        deviceIdleMode = powerManager?.isDeviceIdleMode ?: false,
        powerSaveMode = powerManager?.isPowerSaveMode ?: false,
        backgroundAppOps = backgroundAppOps(),
        lowPowerStandby = lowPowerStandby(),
        // No supported query exists, on any vendor. Reported as unknown rather
        // than assumed to follow the Android answer.
        autoStart = RestrictionState.UNKNOWN,
        vendor = vendor,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        sdkInt = Build.VERSION.SDK_INT,
    )

    /**
     * Opens the standard battery-optimisation screen, where the user can set
     * ShakeIT to "Unrestricted" / "Don't optimise" — the power-exemption
     * allowlist, and on AOSP-derived builds the same list Low Power Standby
     * exempts from.
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
        const val TAG = "PowerDiagnostics"

        /** `AppOpsManager.OPSTR_RUN_ANY_IN_BACKGROUND`, which is `@SystemApi`. */
        const val OP_RUN_ANY_IN_BACKGROUND = "android:run_any_in_background"
    }
}
