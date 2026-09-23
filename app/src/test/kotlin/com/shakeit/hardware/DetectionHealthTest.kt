package com.shakeit.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two rules that decide whether background detection actually works:
 * when a partial wake lock is needed, and when the detector should stop claiming
 * to be active.
 *
 * Both are pure functions on purpose — the failure they describe only happens on
 * a real device with the screen off, so the logic that prevents it has to be
 * pinned down somewhere a test can reach.
 */
class DetectionHealthTest {

    private val stallAfterMillis = 3_000L

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
    fun `no wake lock for a wake-up sensor, screen off or on`() {
        // A wake-up accelerometer wakes the processor per event, so it needs no
        // help — which is why it is preferred when the device has one.
        assertFalse(needsWakeLock(armed = true, usesWakeUpSensor = true, screenIsOn = false))
        assertFalse(needsWakeLock(armed = true, usesWakeUpSensor = true, screenIsOn = true))
    }

    @Test
    fun `no wake lock once detection is stopped`() {
        // An idle app holding a partial wake lock is the classic battery-usage
        // report; stopping detection has to release it whatever the screen does.
        assertFalse(needsWakeLock(armed = false, usesWakeUpSensor = false, screenIsOn = false))
        assertFalse(needsWakeLock(armed = false, usesWakeUpSensor = false, screenIsOn = true))
    }

    // ----------------------------------------------------------- status: armed

    @Test
    fun `active when samples are arriving`() {
        assertEquals(
            DetectionStatus.ACTIVE,
            detectionStatus(
                armed = true,
                hasSensor = true,
                millisSinceLastSample = 20L,
                stallAfterMillis = stallAfterMillis,
            ),
        )
    }

    @Test
    fun `active right up to the stall threshold`() {
        // The comparison is strict: a sample exactly at the threshold is still a
        // sample, and flickering between two states would make the notification
        // unreadable.
        assertEquals(
            DetectionStatus.ACTIVE,
            detectionStatus(
                armed = true,
                hasSensor = true,
                millisSinceLastSample = stallAfterMillis,
                stallAfterMillis = stallAfterMillis,
            ),
        )
    }

    @Test
    fun `stalled as soon as samples stop arriving`() {
        // The state this app most needs to be able to report: the service is
        // alive and its notification is up, but the platform is delivering
        // nothing.
        assertEquals(
            DetectionStatus.STALLED,
            detectionStatus(
                armed = true,
                hasSensor = true,
                millisSinceLastSample = stallAfterMillis + 1,
                stallAfterMillis = stallAfterMillis,
            ),
        )
    }

    @Test
    fun `still stalled after a long gap, as would follow a night in doze`() {
        assertEquals(
            DetectionStatus.STALLED,
            detectionStatus(
                armed = true,
                hasSensor = true,
                millisSinceLastSample = 6 * 60 * 60 * 1000L,
                stallAfterMillis = stallAfterMillis,
            ),
        )
    }

    // -------------------------------------------------------- status: not armed

    @Test
    fun `inactive when detection has not been started`() {
        assertEquals(
            DetectionStatus.INACTIVE,
            detectionStatus(
                armed = false,
                hasSensor = true,
                millisSinceLastSample = 20L,
                stallAfterMillis = stallAfterMillis,
            ),
        )
    }

    @Test
    fun `inactive when the device has no accelerometer`() {
        // Reporting STALLED here would send the user hunting through battery
        // settings for a problem their hardware cannot solve.
        assertEquals(
            DetectionStatus.INACTIVE,
            detectionStatus(
                armed = true,
                hasSensor = false,
                millisSinceLastSample = null,
                stallAfterMillis = stallAfterMillis,
            ),
        )
    }

    @Test
    fun `inactive when nothing can be measured`() {
        assertEquals(
            DetectionStatus.INACTIVE,
            detectionStatus(
                armed = true,
                hasSensor = true,
                millisSinceLastSample = null,
                stallAfterMillis = stallAfterMillis,
            ),
        )
    }

    @Test
    fun `a detector that never delivered a single sample goes from active to stalled`() {
        // Measured from the moment it was armed, so a listener that registers and
        // then hears nothing reports a growing gap instead of looking healthy.
        val justArmed = detectionStatus(
            armed = true,
            hasSensor = true,
            millisSinceLastSample = 500L,
            stallAfterMillis = stallAfterMillis,
        )
        val neverDelivered = detectionStatus(
            armed = true,
            hasSensor = true,
            millisSinceLastSample = 4_000L,
            stallAfterMillis = stallAfterMillis,
        )
        assertEquals(DetectionStatus.ACTIVE, justArmed)
        assertEquals(DetectionStatus.STALLED, neverDelivered)
    }
}
