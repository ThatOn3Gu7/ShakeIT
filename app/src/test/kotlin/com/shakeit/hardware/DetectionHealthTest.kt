package com.shakeit.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the three rules that decide whether background detection actually works:
 * when a partial wake lock is needed, what the detector is allowed to call itself,
 * and how long it waits between rebuilds once samples stop.
 *
 * All three are pure functions on purpose. The failures they describe only happen
 * on a real device with the screen off, minutes after the app left the foreground,
 * so the logic that reports and repairs them has to be pinned down somewhere a
 * test can reach — a stall on hardware cannot be reproduced in a unit test, but
 * the classifier that must not call it "active" can.
 */
class DetectionHealthTest {

    private val stallAfterMillis = 3_000L
    private val maxRecoveryAttempts = 5

    /**
     * The classifier with the defaults that mean "a healthy detector": armed, a
     * sensor it registered successfully, a sample that has arrived, and one that
     * is recent.
     */
    private fun status(
        armed: Boolean = true,
        hasSensor: Boolean = true,
        registrationSucceeded: Boolean = true,
        hasDeliveredSample: Boolean = true,
        millisSinceLastSample: Long? = 100L,
        recoveryAttempts: Int = 0,
    ): DetectionStatus = detectionStatus(
        armed = armed,
        hasSensor = hasSensor,
        registrationSucceeded = registrationSucceeded,
        hasDeliveredSample = hasDeliveredSample,
        millisSinceLastSample = millisSinceLastSample,
        stallAfterMillis = stallAfterMillis,
        recoveryAttempts = recoveryAttempts,
        maxRecoveryAttempts = maxRecoveryAttempts,
    )

    // ---------------------------------------------------------------- wake lock

    @Test
    fun `wake lock is held while armed with the screen off on a non wake-up sensor`() {
        // This is the case the whole fix exists for: the processor suspends when
        // the screen goes off, and without a lock the samples stop with it.
        assertTrue(needsWakeLock(armed = true, usesWakeUpSensor = false, screenIsOn = false))
    }

    @Test
    fun `no wake lock while the screen is on`() {
        // The processor is awake already; a lock here would cost battery and buy
        // nothing.
        assertFalse(needsWakeLock(armed = true, usesWakeUpSensor = false, screenIsOn = true))
    }

    @Test
    fun `no wake lock when every sensor in use is a wake-up sensor`() {
        // A wake-up sensor wakes the processor per event, so it needs no help —
        // which is why one is preferred when the device has it. "Every" is the
        // bar: one non-wake-up listener in the set is enough to go silent.
        assertFalse(needsWakeLock(armed = true, usesWakeUpSensor = true, screenIsOn = false))
    }

    @Test
    fun `no wake lock once detection is stopped`() {
        // An idle lock is how an app ends up on a battery-usage report.
        assertFalse(needsWakeLock(armed = false, usesWakeUpSensor = false, screenIsOn = false))
    }

    // --------------------------------------------------------------- the good case

    @Test
    fun `active when samples are arriving`() {
        assertEquals(DetectionStatus.ACTIVE, status())
    }

    @Test
    fun `active right up to the stall threshold`() {
        // A device that batches samples can legitimately be quiet for a moment;
        // the threshold is the only line, and it is exclusive.
        assertEquals(
            DetectionStatus.ACTIVE,
            status(millisSinceLastSample = stallAfterMillis),
        )
    }

    @Test
    fun `active again the moment samples return after the budget was spent`() {
        // Giving up on rebuilds is not giving up on detection: real samples win
        // over any amount of accumulated failure.
        assertEquals(
            DetectionStatus.ACTIVE,
            status(millisSinceLastSample = 40L, recoveryAttempts = maxRecoveryAttempts),
        )
    }

    // --------------------------------------------------------- the honest failures

    @Test
    fun `recovering as soon as samples stop arriving`() {
        // Not "stalled" yet — there is budget left, so the watchdog will rebuild.
        // Reporting a stall here would be reporting a repair as a verdict.
        assertEquals(
            DetectionStatus.RECOVERING,
            status(millisSinceLastSample = stallAfterMillis + 1),
        )
    }

    @Test
    fun `recovering while a rebuild has not delivered a sample yet`() {
        // Fresh samples are not enough after a rebuild: the age still looks
        // current because arming reset the clock, so "has a sample arrived since?"
        // is the only thing that proves delivery resumed.
        assertEquals(
            DetectionStatus.RECOVERING,
            status(
                hasDeliveredSample = false,
                millisSinceLastSample = 100L,
                recoveryAttempts = 2,
            ),
        )
    }

    @Test
    fun `recovering while the first sample after arming is still on its way`() {
        // ACTIVE is earned by a sample, never by a registration that returned
        // true. On a slow device this is a real window, and it is the window in
        // which a lying notification would say "listening".
        assertEquals(
            DetectionStatus.RECOVERING,
            status(hasDeliveredSample = false, millisSinceLastSample = 50L),
        )
    }

    @Test
    fun `still recovering after several rebuilds, while budget remains`() {
        assertEquals(
            DetectionStatus.RECOVERING,
            status(
                millisSinceLastSample = stallAfterMillis + 1,
                recoveryAttempts = maxRecoveryAttempts - 1,
            ),
        )
    }

    @Test
    fun `stalled once the rebuild budget is spent without a sample`() {
        // The distinction the whole watchdog exists for: attempts exhausted, and
        // still nothing arriving. This is the state that must reach the user.
        assertEquals(
            DetectionStatus.STALLED,
            status(
                millisSinceLastSample = stallAfterMillis + 1,
                recoveryAttempts = maxRecoveryAttempts,
            ),
        )
    }

    @Test
    fun `still stalled after a long gap, as would follow a night in doze`() {
        assertEquals(
            DetectionStatus.STALLED,
            status(
                millisSinceLastSample = 8 * 60 * 60 * 1000L,
                recoveryAttempts = maxRecoveryAttempts,
            ),
        )
    }

    @Test
    fun `inactive when detection has not been started`() {
        assertEquals(DetectionStatus.INACTIVE, status(armed = false))
    }

    @Test
    fun `inactive when nothing can be measured`() {
        // A null age means the detector cannot say anything about time at all.
        assertEquals(
            DetectionStatus.INACTIVE,
            status(millisSinceLastSample = null),
        )
    }

    // ---------------------------------------------------- no sensor, or a refused one

    @Test
    fun `no sensor when the device has no accelerometer`() {
        // Not INACTIVE: there is nothing to turn on, and saying "paused" would
        // send the user looking for a switch.
        assertEquals(
            DetectionStatus.NO_SENSOR,
            status(hasSensor = false, registrationSucceeded = false, hasDeliveredSample = false),
        )
    }

    @Test
    fun `no sensor when the platform refused the registration`() {
        // registerListener returns false rather than throwing when it will not
        // deliver. A detector that ignored the return value would report itself
        // running forever — this is the case that catches it.
        assertEquals(
            DetectionStatus.NO_SENSOR,
            status(registrationSucceeded = false, hasDeliveredSample = false),
        )
    }

    @Test
    fun `no sensor outranks a current sample age`() {
        // A refused registration is a verdict about the device, not a moment in
        // time, so no reading of the clock can talk it out of it.
        assertEquals(
            DetectionStatus.NO_SENSOR,
            status(
                registrationSucceeded = false,
                hasDeliveredSample = true,
                millisSinceLastSample = 10L,
            ),
        )
    }

    @Test
    fun `inactive outranks everything when detection is off`() {
        assertEquals(
            DetectionStatus.INACTIVE,
            status(
                armed = false,
                hasSensor = false,
                registrationSucceeded = false,
                millisSinceLastSample = null,
            ),
        )
    }

    // --------------------------------------------------------------- the backoff

    @Test
    fun `the first retry waits the base delay`() {
        assertEquals(
            2_000L,
            recoveryDelayMillis(attempt = 1, baseMillis = 2_000L, maxDelayMillis = 300_000L),
        )
    }

    @Test
    fun `attempt zero is treated as the first retry, not as no delay`() {
        // A counter that has not been incremented yet must not mean "retry now".
        assertEquals(
            2_000L,
            recoveryDelayMillis(attempt = 0, baseMillis = 2_000L, maxDelayMillis = 300_000L),
        )
    }

    @Test
    fun `each attempt doubles the wait`() {
        val delays = (1..6).map {
            recoveryDelayMillis(attempt = it, baseMillis = 2_000L, maxDelayMillis = 300_000L)
        }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L), delays)
    }

    @Test
    fun `the wait is capped so a broken device is not churned forever`() {
        // The cap is the point of the backoff: without it a phone that has
        // decided to stop delivering would be torn down and rebuilt every two
        // seconds for the rest of the night.
        assertEquals(
            300_000L,
            recoveryDelayMillis(attempt = 20, baseMillis = 2_000L, maxDelayMillis = 300_000L),
        )
    }

    @Test
    fun `a cap below the base delay wins immediately`() {
        assertEquals(
            500L,
            recoveryDelayMillis(attempt = 1, baseMillis = 2_000L, maxDelayMillis = 500L),
        )
        assertEquals(
            500L,
            recoveryDelayMillis(attempt = 4, baseMillis = 2_000L, maxDelayMillis = 500L),
        )
    }

    @Test
    fun `doubling never overflows into a negative wait`() {
        // An overflowed Long would come out negative and mean "retry at once",
        // forever — the exact churn the backoff exists to prevent.
        val delay = recoveryDelayMillis(
            attempt = 64,
            baseMillis = Long.MAX_VALUE / 2,
            maxDelayMillis = 10_000L,
        )
        assertEquals(10_000L, delay)
    }
}
