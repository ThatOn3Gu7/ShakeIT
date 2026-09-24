package com.shakeit.hardware

/**
 * What the detector is actually doing, as opposed to what it was asked to do.
 *
 * The distinction matters because a foreground service can be perfectly alive —
 * notification visible, process running, listener still registered — while no
 * accelerometer samples are arriving at all. That is the failure mode this app
 * has to be honest about, and it has two faces: [RECOVERING] while the detector
 * is rebuilding its sensor stack, and [STALLED] once it has run out of attempts.
 */
enum class DetectionStatus {
    /** Armed and samples are arriving. */
    ACTIVE,

    /**
     * Armed, samples have stopped, and a recovery is in progress or due: the
     * sensor stack is being torn down and rebuilt, or is waiting out a backoff
     * before the next attempt. It only leaves this state when real samples
     * arrive again, or when the attempt budget runs out.
     */
    RECOVERING,

    /**
     * Armed, no samples, and recovery has been attempted [maxRecoveryAttempts]
     * times without a single sample arriving. The listener is registered and the
     * service is alive; the platform or the device's power manager is not
     * delivering, and rebuilding the stack has not changed that.
     */
    STALLED,

    /** Not armed: detection is off, either by preference or because nothing started it. */
    INACTIVE,

    /**
     * This device cannot arm detection: no accelerometer, or the platform refused
     * `SensorManager.registerListener`. Distinct from [INACTIVE] because the fix
     * is not "turn detection on" — there is nothing to turn on.
     */
    NO_SENSOR,
}

/**
 * Whether the detector has to hold a partial wake lock right now.
 *
 * This is the rule that makes screen-off detection work, so it is worth stating
 * on its own rather than buried in an `if`:
 *
 * - **Not armed:** nothing to keep alive. Holding a lock while idle is the
 *   classic way an app ends up on a battery-usage report.
 * - **Wake-up sensors:** every sensor in use wakes the application processor
 *   itself for each event, so a lock would add nothing. This is why a wake-up
 *   accelerometer is preferred when the device exposes one — and why "every
 *   sensor" is the bar: the pocket guard's proximity sensor almost never is one,
 *   and a single non-wake-up listener is enough to freeze with the screen off.
 * - **Screen on:** the processor is awake already, and samples are arriving
 *   without help.
 *
 * Only the remaining case — armed, non-wake-up sensor, screen off — is the one
 * `SensorManager` documents as needing a lock: the processor suspends, and with
 * it the delivery of every sample.
 *
 * Note what a wake lock cannot do. Doze ignores them, and Android 14's Low Power
 * Standby ignores them for apps that are not exempt — so holding one is necessary
 * but not sufficient, which is why the stall below is reported rather than
 * assumed away.
 *
 * @param screenIsOn `PowerManager.isInteractive`; when there is no power manager
 *   to ask, callers should pass true so no lock is taken that cannot be managed
 */
fun needsWakeLock(
    armed: Boolean,
    usesWakeUpSensor: Boolean,
    screenIsOn: Boolean,
): Boolean = armed && !usesWakeUpSensor && !screenIsOn

/**
 * Classifies the detector from facts the watchdog can observe.
 *
 * Pure, like the rest of the recognition layer, so the thresholds and the
 * recovery states are testable without a device — which matters because the
 * failures they describe only reproduce on hardware with the screen off.
 *
 * @param armed whether detection has been started and not since stopped
 * @param hasSensor whether an accelerometer was found to listen to
 * @param registrationSucceeded whether `registerListener` accepted it. The
 *   platform returns false rather than throwing when it will not deliver, and a
 *   detector that ignored that would report itself running forever
 * @param hasDeliveredSample whether a sample has arrived since the listener was
 *   (re)registered. A rebuild that has not yet heard anything must not be
 *   reported as healthy: ACTIVE has to be earned by a real sample
 * @param millisSinceLastSample elapsed realtime since the last sample, or since
 *   the listener was armed if none has arrived yet; null when not running
 * @param stallAfterMillis how long without a sample counts as stalled
 * @param recoveryAttempts how many rebuilds have been tried for this stall
 * @param maxRecoveryAttempts the budget before the detector gives up and reports
 *   [DetectionStatus.STALLED] instead of [DetectionStatus.RECOVERING]
 */
fun detectionStatus(
    armed: Boolean,
    hasSensor: Boolean,
    registrationSucceeded: Boolean,
    hasDeliveredSample: Boolean,
    millisSinceLastSample: Long?,
    stallAfterMillis: Long,
    recoveryAttempts: Int,
    maxRecoveryAttempts: Int,
): DetectionStatus {
    if (!armed) return DetectionStatus.INACTIVE
    if (!hasSensor || !registrationSucceeded) return DetectionStatus.NO_SENSOR

    // When no sample has arrived since arming, this is the age of the arming
    // itself — so a listener that registers and hears nothing ages into a stall
    // exactly like one that stopped hearing.
    val age = millisSinceLastSample ?: return DetectionStatus.INACTIVE
    val stalled = age > stallAfterMillis

    if (!stalled) {
        // ACTIVE is earned by a real sample, never by a successful registration:
        // the gap between arming and the first event — and the same gap after
        // every rebuild — is still "getting there", and calling it active is how
        // a notification ends up claiming to listen to a sensor that is silent.
        return if (hasDeliveredSample) DetectionStatus.ACTIVE else DetectionStatus.RECOVERING
    }

    return if (recoveryAttempts >= maxRecoveryAttempts) {
        DetectionStatus.STALLED
    } else {
        DetectionStatus.RECOVERING
    }
}

/**
 * How long to wait before the next sensor-stack rebuild: [baseMillis] doubled
 * per attempt, capped at [maxDelayMillis].
 *
 * The cap is the point. A device that has stopped delivering for good would
 * otherwise be torn down and rebuilt every few seconds forever, spending battery
 * on a repair that cannot succeed; the exponential ramp keeps the first attempts
 * quick (a transient starvation recovers in seconds) and the later ones rare.
 *
 * @param attempt 1 for the first retry, 2 for the second, and so on
 */
fun recoveryDelayMillis(attempt: Int, baseMillis: Long, maxDelayMillis: Long): Long {
    if (attempt <= 1) return minOf(baseMillis, maxDelayMillis)
    var delay = baseMillis
    repeat(attempt - 1) {
        if (delay >= maxDelayMillis) return maxDelayMillis
        delay *= 2
    }
    return delay.coerceAtMost(maxDelayMillis)
}
