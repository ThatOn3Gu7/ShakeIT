package com.shakeit.hardware

/**
 * What the detector is actually doing, as opposed to what it was asked to do.
 *
 * The distinction matters because a foreground service can be perfectly alive —
 * notification visible, process running, listener still registered — while no
 * accelerometer samples are arriving at all. That is the failure mode this app
 * has to be honest about, and [STALLED] is how it says so.
 */
enum class DetectionStatus {
    /** Armed and samples are arriving. */
    ACTIVE,

    /**
     * Armed, but no sample has arrived for longer than the stall threshold. The
     * listener is registered and the service is alive; the platform or the
     * device's power manager is not delivering.
     */
    STALLED,

    /** Not armed, or this device has no accelerometer to arm with. */
    INACTIVE,
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
 * @param screenIsOn `PowerManager.isInteractive`; when there is no power manager
 *   to ask, callers should pass true so no lock is taken that cannot be managed
 */
fun needsWakeLock(
    armed: Boolean,
    usesWakeUpSensor: Boolean,
    screenIsOn: Boolean,
): Boolean =
    armed && !usesWakeUpSensor && !screenIsOn

/**
 * Classifies the detector from facts the watchdog can observe.
 *
 * Pure, like the rest of the recognition layer, so the thresholds are testable
 * without a device.
 *
 * @param armed whether detection has been started and not since stopped
 * @param hasSensor whether an accelerometer was found to listen to
 * @param millisSinceLastSample elapsed realtime since the last sample, or since
 *   the detector was armed if no sample has arrived yet; null when not running
 * @param stallAfterMillis how long without a sample counts as stalled
 */
fun detectionStatus(
    armed: Boolean,
    hasSensor: Boolean,
    millisSinceLastSample: Long?,
    stallAfterMillis: Long,
): DetectionStatus = when {
    !armed || !hasSensor -> DetectionStatus.INACTIVE
    millisSinceLastSample == null -> DetectionStatus.INACTIVE
    millisSinceLastSample > stallAfterMillis -> DetectionStatus.STALLED
    else -> DetectionStatus.ACTIVE
}
