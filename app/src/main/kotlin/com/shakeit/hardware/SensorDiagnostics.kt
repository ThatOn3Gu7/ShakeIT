package com.shakeit.hardware

/**
 * Everything worth knowing about the sensor stack, as plain facts.
 *
 * This exists because "is it working?" cannot be answered from the UI alone on a
 * device that stops delivering samples: the answer depends on which sensors were
 * found, whether they can wake the processor, whether the platform accepted the
 * registration, whether a wake lock is needed and actually held, and how long it
 * has been since anything arrived. Every field here is read off the detector
 * rather than inferred, so the diagnostics screen can be believed.
 *
 * @param accelerometerAvailable an accelerometer was found on this device
 * @param accelerometerWakeUp it can wake the application processor from suspend,
 *   which is what makes screen-off delivery work without a wake lock
 * @param proximityAvailable a proximity sensor was found, i.e. the pocket guard can work
 * @param proximityWakeUp the proximity sensor is itself a wake-up sensor
 * @param significantMotionAvailable a `TYPE_SIGNIFICANT_MOTION` trigger sensor
 *   exists, i.e. the hardware wake/recovery path is possible
 * @param significantMotionArmed the trigger is currently requested. It is
 *   one-shot, so "available" and "armed" are different facts
 * @param significantMotionFires how many times the trigger has fired since
 *   detection was armed — evidence that the hardware path works on this device
 * @param registrationSucceeded `SensorManager.registerListener` returned true.
 *   It returns false rather than throwing when the platform will not deliver
 * @param wakeLockRequired a partial wake lock is needed right now: armed, a
 *   non-wake-up sensor in use, and the screen off
 * @param wakeLockHeld that lock is actually held
 * @param millisSinceLastSample age of the newest accelerometer sample on the
 *   clock that keeps running while the device sleeps, or the age of the arming
 *   itself when nothing has arrived; null when detection is not running
 * @param hasDeliveredSample a sample has arrived since the listener was registered
 * @param recoveries how many times the sensor stack has been torn down and
 *   rebuilt since detection was armed
 * @param lastRecoverySucceeded whether the most recent rebuild registered
 *   successfully; null when there has not been one
 */
data class SensorDiagnostics(
    val accelerometerName: String? = null,
    val accelerometerType: Int = 0,
    val accelerometerReportingMode: Int = 0,
    val accelerometerAvailable: Boolean = false,
    val accelerometerWakeUp: Boolean = false,
    val proximityAvailable: Boolean = false,
    val proximityWakeUp: Boolean = false,
    val significantMotionAvailable: Boolean = false,
    val significantMotionArmed: Boolean = false,
    val significantMotionFires: Int = 0,
    val registrationSucceeded: Boolean = false,
    val wakeLockRequired: Boolean = false,
    val wakeLockHeld: Boolean = false,
    val millisSinceLastSample: Long? = null,
    val hasDeliveredSample: Boolean = false,
    val recoveries: Int = 0,
    val lastRecoverySucceeded: Boolean? = null,
)
