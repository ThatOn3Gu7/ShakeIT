package com.shakeit.ui.settings

import com.shakeit.background.ExitReason
import com.shakeit.background.LowPowerStandby
import com.shakeit.background.PreviousExit
import com.shakeit.background.RestrictionState
import com.shakeit.background.ShizukuState
import com.shakeit.background.ShizukuStatus
import com.shakeit.hardware.DetectionStatus
import com.shakeit.hardware.SensorDiagnostics

/**
 * Turns diagnostic facts into sentences.
 *
 * Deliberately pure: no `Context`, no resources, no clock of its own (a "now" is
 * passed in wherever an age is computed), so every line the diagnostics screen can
 * print is unit-testable on the JVM. That matters more here than elsewhere — a
 * diagnostics page that misreads its own facts is worse than no diagnostics page,
 * because it sends the user to fix the wrong thing.
 *
 * The wording follows one rule: state the mechanism that was actually asked, and
 * say so when the platform refused to answer. "Unknown" is a result, not a gap to
 * be papered over with an optimistic default.
 */
internal object DiagnosticsText {

    /**
     * How old the newest sample is — the single most telling number when a device
     * stops detecting, because it separates "nothing has ever arrived" from
     * "it arrived until a moment ago".
     */
    fun lastSample(sensor: SensorDiagnostics): String = when {
        sensor.millisSinceLastSample == null -> "Detector is not armed"
        !sensor.hasDeliveredSample ->
            "Never — nothing has arrived in the ${duration(sensor.millisSinceLastSample)} " +
                "since the listener was registered"
        else -> "${duration(sensor.millisSinceLastSample)} ago"
    }

    /** `1200` → `"1.2 s"`, `74_000` → `"1m 14s"`, `3_720_000` → `"1h 2m"`. */
    fun duration(millis: Long): String {
        if (millis < 0L) return "—"
        if (millis < 1_000L) return "$millis ms"
        val seconds = millis / 1_000L
        if (seconds < 60L) return "$seconds.${(millis % 1_000L) / 100L} s"
        val minutes = seconds / 60L
        if (minutes < 60L) return "${minutes}m ${seconds % 60L}s"
        val hours = minutes / 60L
        if (hours < 24L) return "${hours}h ${minutes % 60L}m"
        return "${hours / 24L}d ${hours % 24L}h"
    }

    /**
     * Whether the background owner exists, and whether that agrees with the switch.
     * The disagreement is the interesting case: it means the platform refused a
     * start, which no preference write can fix.
     */
    fun service(running: Boolean, wanted: Boolean): String = when {
        running -> "Foreground service running"
        wanted ->
            "Not running — \"Run in background\" is on, so a start was refused " +
                "or has not happened yet"
        else ->
            "Not running — \"Run in background\" is off, so detection lives in the " +
                "app process only"
    }

    /** The detector's own status, plus how much of the rebuild budget it has spent. */
    fun detector(status: DetectionStatus, attempts: Int, maxAttempts: Int): String {
        val budget = if (maxAttempts > 0) " · rebuild $attempts/$maxAttempts used" else ""
        return when (status) {
            DetectionStatus.ACTIVE -> "Armed and receiving samples"
            DetectionStatus.RECOVERING ->
                if (attempts > 0) "Recovering — rebuilding the sensor stack$budget"
                else "Starting — armed, waiting for the first sample"
            DetectionStatus.STALLED ->
                "Stalled — samples stopped and the rebuild budget is spent$budget"
            DetectionStatus.NO_SENSOR -> "No usable accelerometer, or the listener was refused"
            DetectionStatus.INACTIVE -> "Off — detection is switched off or has been stopped"
        }
    }

    fun accelerometer(sensor: SensorDiagnostics): String = when {
        !sensor.accelerometerAvailable -> "Not found on this device"
        sensor.accelerometerWakeUp ->
            "Present · can wake the processor (best case for screen-off detection)"
        else ->
            "Present · cannot wake the processor, so a wake lock is needed while " +
                "the screen is off"
    }

    fun registration(sensor: SensorDiagnostics): String = when {
        !sensor.accelerometerAvailable -> "Not attempted — no accelerometer"
        sensor.registrationSucceeded -> "Accepted by the platform"
        else -> "Refused — registerListener returned false"
    }

    fun wakeLock(sensor: SensorDiagnostics): String = when {
        !sensor.wakeLockRequired && !sensor.wakeLockHeld ->
            "Not needed (wake-up sensor, or the screen is on)"
        sensor.wakeLockRequired && sensor.wakeLockHeld -> "Required and held"
        sensor.wakeLockRequired ->
            "Required but NOT held — samples will stop when the screen is off"
        else -> "Held without being needed"
    }

    fun proximity(sensor: SensorDiagnostics): String = when {
        !sensor.proximityAvailable -> "Not found — the pocket guard is simply not applied"
        sensor.proximityWakeUp -> "Present · wake-up sensor"
        else -> "Present · not a wake-up sensor"
    }

    fun significantMotion(sensor: SensorDiagnostics): String = when {
        !sensor.significantMotionAvailable -> "Not available on this device"
        !sensor.significantMotionArmed -> "Available but not armed right now"
        sensor.significantMotionFires > 0 ->
            "Armed · woke the app ${sensor.significantMotionFires}× — the hardware " +
                "path works here"
        else -> "Armed · has not fired yet"
    }

    /** The Doze/battery-optimisation allowlist only — not any vendor setting. */
    fun dozeExemption(state: RestrictionState?): String = when (state) {
        null -> "Not read yet"
        RestrictionState.ALLOWED -> "Exempt — on Android's battery-optimisation allowlist"
        RestrictionState.RESTRICTED ->
            "Optimised — Android may defer work and ignore wake locks in Doze"
        RestrictionState.NOT_SUPPORTED -> "This Android version has no such switch"
        RestrictionState.UNKNOWN -> "The platform did not answer"
    }

    /** The `RUN_ANY_IN_BACKGROUND` app-op: Android's own background restriction. */
    fun backgroundAppOps(state: RestrictionState?): String = when (state) {
        null -> "Not read yet"
        RestrictionState.ALLOWED -> "Allowed to run in the background"
        RestrictionState.RESTRICTED -> "Restricted — the platform is limiting background work"
        RestrictionState.NOT_SUPPORTED -> "Not applicable on this Android version"
        RestrictionState.UNKNOWN ->
            "Unknown — this op is not readable by ordinary apps on this device"
    }

    /**
     * Vendor auto-start. Always unknown, and said so: no manufacturer publishes a
     * supported way to ask, and guessing a component name to open a screen is not
     * a query.
     */
    fun autoStart(state: RestrictionState?): String = when (state) {
        RestrictionState.UNKNOWN, null ->
            "Unknown — no app can read a vendor's auto-start setting"
        RestrictionState.ALLOWED -> "Allowed"
        RestrictionState.RESTRICTED -> "Blocked"
        RestrictionState.NOT_SUPPORTED -> "Not applicable"
    }

    /**
     * Low Power Standby, the one platform feature that silently invalidates the
     * wake-lock strategy: while it is on and the device is non-interactive, wake
     * locks held by non-exempt apps are ignored — foreground service or not.
     */
    fun lowPowerStandby(standby: LowPowerStandby?): String {
        if (standby == null) return "Not read yet"
        val enabled = standby.enabled ?: return "Not on this Android version (needs 14+)"
        if (!enabled) return "Off"
        return when (standby.exempt) {
            true -> "On — ShakeIT is exempt"
            false -> "On and ShakeIT is NOT exempt: wake locks are ignored while the screen is off"
            null -> "On — exemption could not be read (needs 15+)"
        }
    }

    fun powerModeRightNow(deviceIdle: Boolean, powerSave: Boolean): String = when {
        deviceIdle && powerSave -> "Doze active and Battery Saver on"
        deviceIdle -> "Doze active right now"
        powerSave -> "Battery Saver on"
        else -> "No power mode active"
    }

    /**
     * Why the previous process died, which is the fact that separates "killed"
     * from "alive but starved".
     *
     * @param nowMillis wall-clock now, passed in so the age is testable
     */
    fun previousExit(exit: PreviousExit?, supported: Boolean, nowMillis: Long): String = when {
        !supported -> "Not available — needs Android 11 or newer"
        exit == null -> "No recorded exit — this looks like the first run since install"
        else -> buildString {
            append(exitLabel(exit.reason))
            if (exit.signal > 0) append(" (signal ${exit.signal})")
            // A zero timestamp means the platform recorded no time, which is
            // different from "a moment ago" and must not be printed as an age.
            if (exit.timestampMillis > 0L) {
                val age = nowMillis - exit.timestampMillis
                if (age in 0..YEAR_MILLIS) append(" · ${duration(age)} ago")
            }
            exit.description?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        }
    }

    fun exitLabel(reason: ExitReason): String = when (reason) {
        ExitReason.Unknown -> "Reason not recorded"
        ExitReason.ExitSelf -> "The app ended itself"
        ExitReason.Signaled -> "Killed by a signal"
        ExitReason.LowMemory -> "Killed to free memory"
        ExitReason.Crash -> "Crash (Java/Kotlin)"
        ExitReason.CrashNative -> "Crash (native)"
        ExitReason.Anr -> "ANR — the main thread was blocked"
        ExitReason.InitializationFailure -> "Could not start"
        ExitReason.PermissionChange -> "A permission or app-op was revoked"
        ExitReason.ExcessiveResourceUsage -> "Used too much of a limited resource"
        ExitReason.UserRequested ->
            "Stopped by the user — Recents swipe, force stop, or a vendor's \"close app\""
        ExitReason.UserStopped -> "The Android user was stopped"
        ExitReason.DependencyDied -> "A process it depended on died"
        // REASON_OTHER is the platform's own catch-all, so the honest wording is
        // that it did not name one — not a second "Other" that says nothing.
        ExitReason.Other -> "Stopped for a reason the platform did not name"
        ExitReason.Freezer -> "Frozen by the cached-app freezer"
        ExitReason.PackageStateChange -> "Package state changed"
        ExitReason.PackageUpdated -> "The app was updated"
    }

    fun processUptime(millis: Long): String =
        if (millis <= 0L) "Just started" else "Alive for ${duration(millis)}"

    fun device(manufacturer: String, model: String, sdkInt: Int): String =
        "$manufacturer $model · Android ${androidVersionName(sdkInt)} (SDK $sdkInt)"

    /** The marketing name, because "SDK 34" is not something a user can act on. */
    fun androidVersionName(sdkInt: Int): String = when (sdkInt) {
        24, 25 -> "7"
        26, 27 -> "8"
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31, 32 -> "12"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        else -> if (sdkInt < 24) "older" else "newer than $sdkInt"
    }

    fun shizuku(status: ShizukuStatus): String = when {
        status.state == ShizukuState.READY && status.privilege != null ->
            "Ready · ${status.privilege} · server v${status.serverVersion}"
        status.state == ShizukuState.READY -> "Ready · server v${status.serverVersion}"
        else -> status.privilege ?: "Not connected"
    }

    private const val YEAR_MILLIS = 365L * 24L * 60L * 60L * 1000L
}
