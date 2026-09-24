package com.shakeit.ui.settings

import com.shakeit.background.ExitReason
import com.shakeit.background.LowPowerStandby
import com.shakeit.background.PreviousExit
import com.shakeit.background.RestrictionState
import com.shakeit.background.ShizukuState
import com.shakeit.background.ShizukuStatus
import com.shakeit.hardware.DetectionStatus
import com.shakeit.hardware.SensorDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wording of the diagnostics page.
 *
 * Testing prose looks pedantic until the page is what a user reads after a night
 * in which detection silently stopped. The rules worth pinning down are the
 * honest ones: a mechanism that did not answer must not be described as fine, an
 * unverified repair must not be described as done, and "armed" must never read as
 * "working" when no sample has arrived.
 */
class DiagnosticsTextTest {

    // ---------------------------------------------------------------- durations

    @Test
    fun `durations are readable at every scale`() {
        assertEquals("42 ms", DiagnosticsText.duration(42))
        assertEquals("1.2 s", DiagnosticsText.duration(1_234))
        assertEquals("59.9 s", DiagnosticsText.duration(59_999))
        assertEquals("1m 14s", DiagnosticsText.duration(74_000))
        assertEquals("1h 2m", DiagnosticsText.duration(3_720_000))
        assertEquals("2d 3h", DiagnosticsText.duration((2 * 24 + 3) * 3_600_000L))
    }

    @Test
    fun `a negative duration is not printed as a number`() {
        // A clock that went backwards must not produce "-4 s ago".
        assertEquals("—", DiagnosticsText.duration(-1))
    }

    // ------------------------------------------------------------------ service

    @Test
    fun `a running service says so plainly`() {
        assertEquals(
            "Foreground service running",
            DiagnosticsText.service(running = true, wanted = true),
        )
    }

    @Test
    fun `a service that should be running but is not says that, not just that it is off`() {
        val text = DiagnosticsText.service(running = false, wanted = true)

        assertTrue(text, text.contains("refused"))
        assertFalse("must not read as the user's choice", text.contains("is off"))
    }

    @Test
    fun `a service the user switched off explains where detection lives instead`() {
        val text = DiagnosticsText.service(running = false, wanted = false)

        assertTrue(text, text.contains("off"))
        assertTrue(text, text.contains("app process"))
    }

    // ----------------------------------------------------------------- detector

    @Test
    fun `an active detector mentions samples, because that is the proof`() {
        val text = DiagnosticsText.detector(DetectionStatus.ACTIVE, attempts = 0, maxAttempts = 5)

        assertTrue(text, text.contains("samples"))
    }

    @Test
    fun `a detector that has not delivered its first sample is starting, not recovering`() {
        val text = DiagnosticsText.detector(DetectionStatus.RECOVERING, attempts = 0, maxAttempts = 5)

        assertTrue(text, text.contains("first sample"))
        assertFalse("nothing has been rebuilt yet", text.contains("rebuild"))
    }

    @Test
    fun `a rebuild in progress shows how much budget is left`() {
        val text = DiagnosticsText.detector(DetectionStatus.RECOVERING, attempts = 2, maxAttempts = 5)

        assertTrue(text, text.contains("rebuild 2/5 used"))
    }

    @Test
    fun `a stall says the budget is spent rather than blaming a setting outright`() {
        val text = DiagnosticsText.detector(DetectionStatus.STALLED, attempts = 5, maxAttempts = 5)

        assertTrue(text, text.contains("budget is spent"))
    }

    @Test
    fun `no sensor is not described as off`() {
        val text = DiagnosticsText.detector(DetectionStatus.NO_SENSOR, attempts = 0, maxAttempts = 5)

        assertTrue(text, text.contains("accelerometer") || text.contains("refused"))
        assertFalse(text, text.contains("switched off"))
    }

    // ------------------------------------------------------------------- sensors

    @Test
    fun `a missing accelerometer is stated as missing`() {
        val text = DiagnosticsText.accelerometer(SensorDiagnostics())

        assertTrue(text, text.contains("Not found"))
    }

    @Test
    fun `a non wake-up accelerometer comes with the consequence`() {
        val text = DiagnosticsText.accelerometer(
            SensorDiagnostics(accelerometerAvailable = true, accelerometerWakeUp = false),
        )

        assertTrue(text, text.contains("wake lock is needed"))
    }

    @Test
    fun `a refused registration is reported as refused`() {
        // The single most misreported failure there is: registerListener returned
        // false, and the app kept behaving as if it had not.
        val text = DiagnosticsText.registration(
            SensorDiagnostics(accelerometerAvailable = true, registrationSucceeded = false),
        )

        assertTrue(text, text.contains("Refused"))
    }

    @Test
    fun `a wake lock that is needed but not held is called out`() {
        val text = DiagnosticsText.wakeLock(
            SensorDiagnostics(wakeLockRequired = true, wakeLockHeld = false),
        )

        assertTrue(text, text.contains("NOT held"))
    }

    @Test
    fun `a wake lock that is held says nothing alarming`() {
        assertEquals(
            "Required and held",
            DiagnosticsText.wakeLock(
                SensorDiagnostics(wakeLockRequired = true, wakeLockHeld = true),
            ),
        )
    }

    @Test
    fun `the last sample distinguishes never from not armed from recent`() {
        assertTrue(
            DiagnosticsText.lastSample(SensorDiagnostics()).contains("not armed"),
        )

        val never = DiagnosticsText.lastSample(
            SensorDiagnostics(
                accelerometerAvailable = true,
                registrationSucceeded = true,
                millisSinceLastSample = 4_200L,
                hasDeliveredSample = false,
            ),
        )
        assertTrue(never, never.contains("Never"))
        assertTrue(never, never.contains("4.2 s"))

        val recent = DiagnosticsText.lastSample(
            SensorDiagnostics(millisSinceLastSample = 120L, hasDeliveredSample = true),
        )
        assertEquals("120 ms ago", recent)
    }

    @Test
    fun `a significant motion trigger that has fired proves the hardware path`() {
        val text = DiagnosticsText.significantMotion(
            SensorDiagnostics(
                significantMotionAvailable = true,
                significantMotionArmed = true,
                significantMotionFires = 3,
            ),
        )

        assertTrue(text, text.contains("3×"))
        assertTrue(text, text.contains("hardware"))
    }

    // ------------------------------------------------------------- power policy

    @Test
    fun `an unread power answer is not reported as allowed`() {
        assertEquals("Not read yet", DiagnosticsText.dozeExemption(null))
        assertEquals("Not read yet", DiagnosticsText.backgroundAppOps(null))
    }

    @Test
    fun `each power mechanism names itself`() {
        // The whole reason these are separate rows: one answer must never be
        // presented as an answer about a different mechanism.
        assertTrue(
            DiagnosticsText.dozeExemption(RestrictionState.ALLOWED).contains("battery-optimisation"),
        )
        assertTrue(
            DiagnosticsText.backgroundAppOps(RestrictionState.RESTRICTED).contains("background"),
        )
    }

    @Test
    fun `a mechanism that will not answer says unknown`() {
        assertTrue(
            DiagnosticsText.dozeExemption(RestrictionState.UNKNOWN).contains("did not answer"),
        )
        assertTrue(
            DiagnosticsText.backgroundAppOps(RestrictionState.UNKNOWN).contains("Unknown"),
        )
    }

    @Test
    fun `vendor auto-start is never claimed to be known`() {
        val text = DiagnosticsText.autoStart(RestrictionState.UNKNOWN)

        assertTrue(text, text.contains("no app can read"))
        assertEquals(text, DiagnosticsText.autoStart(null))
    }

    @Test
    fun `low power standby reports exemption separately from being enabled`() {
        val off = LowPowerStandby(enabled = false, exempt = false, state = RestrictionState.ALLOWED)
        assertEquals("Off", DiagnosticsText.lowPowerStandby(off))

        val exempt = LowPowerStandby(enabled = true, exempt = true, state = RestrictionState.ALLOWED)
        assertTrue(DiagnosticsText.lowPowerStandby(exempt).contains("exempt"))

        val notExempt =
            LowPowerStandby(enabled = true, exempt = false, state = RestrictionState.RESTRICTED)
        val text = DiagnosticsText.lowPowerStandby(notExempt)
        assertTrue(text, text.contains("NOT exempt"))
        // The consequence has to be stated: this is the case where a held wake
        // lock buys nothing.
        assertTrue(text, text.contains("wake locks are ignored"))

        val unreadable = LowPowerStandby(enabled = true, exempt = null, state = RestrictionState.UNKNOWN)
        assertTrue(DiagnosticsText.lowPowerStandby(unreadable).contains("could not be read"))

        val tooOld = LowPowerStandby(enabled = null, exempt = null, state = RestrictionState.NOT_SUPPORTED)
        assertTrue(DiagnosticsText.lowPowerStandby(tooOld).contains("Android version"))

        assertEquals("Not read yet", DiagnosticsText.lowPowerStandby(null))
    }

    @Test
    fun `doze and battery saver are reported as the separate states they are`() {
        assertEquals(
            "Doze active and Battery Saver on",
            DiagnosticsText.powerModeRightNow(deviceIdle = true, powerSave = true),
        )
        assertEquals(
            "Doze active right now",
            DiagnosticsText.powerModeRightNow(deviceIdle = true, powerSave = false),
        )
        assertEquals(
            "No power mode active",
            DiagnosticsText.powerModeRightNow(deviceIdle = false, powerSave = false),
        )
    }

    // ----------------------------------------------------------- process history

    @Test
    fun `exit history on an older Android says it cannot answer`() {
        val text = DiagnosticsText.previousExit(exit = null, supported = false, nowMillis = 0)

        assertTrue(text, text.contains("Android 11"))
    }

    @Test
    fun `no recorded exit is described as a first run, not as a crash-free night`() {
        val text = DiagnosticsText.previousExit(exit = null, supported = true, nowMillis = 0)

        assertTrue(text, text.contains("No recorded exit"))
    }

    @Test
    fun `a recorded exit carries its reason, its signal and its age`() {
        val exit = PreviousExit(
            reason = ExitReason.Signaled,
            reasonCode = 2,
            signal = 9,
            timestampMillis = 1_000_000L,
            description = null,
            importanceCode = 200,
            pid = 4242,
            pssKilobytes = 51_000L,
        )

        val text = DiagnosticsText.previousExit(exit = exit, supported = true, nowMillis = 1_600_000L)

        assertTrue(text, text.contains("signal 9"))
        assertTrue(text, text.contains("10m 0s ago"))
        assertFalse("no description was recorded", text.contains("null"))
    }

    @Test
    fun `the recents-swipe reason is explained, because it is ambiguous`() {
        val exit = PreviousExit(
            reason = ExitReason.UserRequested,
            reasonCode = 10,
            signal = 0,
            timestampMillis = 0L,
            description = "user requested",
            importanceCode = 125,
            pid = 1,
            pssKilobytes = 0L,
        )

        val text = DiagnosticsText.previousExit(exit = exit, supported = true, nowMillis = 0L)

        assertTrue(text, text.contains("Recents swipe"))
        assertTrue(text, text.contains("user requested"))
        assertFalse("a wall-clock zero is not an age", text.contains("ago"))
    }

    @Test
    fun `every exit reason has words of its own`() {
        ExitReason.values().forEach { reason ->
            val label = DiagnosticsText.exitLabel(reason)

            assertTrue("$reason produced \"$label\"", label.isNotBlank())
            assertFalse("$reason leaked its enum name", label == reason.name)
        }
    }

    @Test
    fun `uptime is reported for the process that is alive now`() {
        assertEquals("Just started", DiagnosticsText.processUptime(0))
        assertTrue(DiagnosticsText.processUptime(75_000).contains("1m 15s"))
    }

    // ------------------------------------------------------------------- device

    @Test
    fun `the device line names the manufacturer and an Android version, not just a sdk`() {
        val text = DiagnosticsText.device("Infinix", "NOTE 30", 33)

        assertEquals("Infinix NOTE 30 · Android 13 (SDK 33)", text)
    }

    @Test
    fun `sdk numbers map to the release names users recognise`() {
        assertEquals("7", DiagnosticsText.androidVersionName(24))
        assertEquals("10", DiagnosticsText.androidVersionName(29))
        assertEquals("14", DiagnosticsText.androidVersionName(34))
        assertEquals("16", DiagnosticsText.androidVersionName(36))
        assertTrue(DiagnosticsText.androidVersionName(40).contains("newer"))
    }

    // ------------------------------------------------------------------ shizuku

    @Test
    fun `a ready connection shows the privilege it actually has`() {
        val status = ShizukuStatus(state = ShizukuState.READY, serverVersion = 72, uid = 2000)

        val text = DiagnosticsText.shizuku(status)

        assertTrue(text, text.contains("shell (adb)"))
        assertTrue(text, text.contains("v72"))
        // Shizuku started through adb is shell, and shell is not root: the page
        // must never imply otherwise.
        assertFalse(text, text.contains("root"))
    }

    @Test
    fun `a root connection is labelled root`() {
        val status = ShizukuStatus(state = ShizukuState.READY, serverVersion = 72, uid = 0)

        assertTrue(DiagnosticsText.shizuku(status).startsWith("Ready · root"))
    }

    @Test
    fun `anything short of a connection does not claim one`() {
        listOf(
            ShizukuState.NOT_INSTALLED,
            ShizukuState.NOT_RUNNING,
            ShizukuState.PERMISSION_NEEDED,
            ShizukuState.DENIED,
        ).forEach { state ->
            val text = DiagnosticsText.shizuku(ShizukuStatus(state = state))

            assertEquals("$state", "Not connected", text)
        }
    }
}
