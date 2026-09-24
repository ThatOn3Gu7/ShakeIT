package com.shakeit.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parsers behind the diagnostics and the Shizuku repairs.
 *
 * Both read the output of a privileged command and turn it into a claim the app
 * then shows the user — "ShakeIT is on the allowlist", "background activity is
 * allowed" — so a misread is not a cosmetic bug. A false positive tells the user
 * a restriction was lifted when it was not; a false negative reports a repair as
 * failed when it worked, which is how people stop trusting the page. The formats
 * vary by Android version, which is exactly why they are pinned down here rather
 * than discovered on a device.
 */
class BackgroundParsingTest {

    // ------------------------------------------------- the Doze/power allowlist

    @Test
    fun `a bare package line counts as being on the allowlist`() {
        val output = """
            Whitelist packages:
            com.shakeit
            com.google.android.gms
        """.trimIndent()

        assertEquals(true, dozeAllowlistContains(output, "com.shakeit"))
    }

    @Test
    fun `an entry with its user id appended still counts`() {
        // What `dumpsys deviceidle whitelist` prints on most builds.
        val output = """
            Whitelist (except idle) packages:
            system,com.android.shell
            com.shakeit,10123
        """.trimIndent()

        assertEquals(true, dozeAllowlistContains(output, "com.shakeit"))
    }

    @Test
    fun `the newer allowlist spelling is understood too`() {
        val output = """
            Allowlist packages:
              package:com.shakeit
        """.trimIndent()

        assertEquals(true, dozeAllowlistContains(output, "com.shakeit"))
    }

    @Test
    fun `a package that is not listed answers false, not unknown`() {
        val output = """
            Whitelist packages:
            com.google.android.gms
        """.trimIndent()

        assertEquals(false, dozeAllowlistContains(output, "com.shakeit"))
    }

    @Test
    fun `a package that merely contains the name is not a match`() {
        // Substring matching would report success here, and the whole point of
        // the read-back is that it can be believed.
        val output = """
            Whitelist packages:
            com.shakeit.demo
            com.shakeitx
            org.com.shakeit
        """.trimIndent()

        assertEquals(false, dozeAllowlistContains(output, "com.shakeit"))
    }

    @Test
    fun `output that is not a list at all answers unknown`() {
        // An exception, a permission error, an empty answer: none of these say
        // anything about the allowlist, so none may be reported as "not on it".
        assertNull(dozeAllowlistContains("Exception: no permission", "com.shakeit"))
        assertNull(dozeAllowlistContains("", "com.shakeit"))
    }

    // ------------------------------------------------------- the background op

    @Test
    fun `an allowed app-op reads as allowed`() {
        val output = "RUN_ANY_IN_BACKGROUND: allow; time=+2h13m4s937ms"

        assertEquals(RestrictionState.ALLOWED, parseAppOpMode(output))
    }

    @Test
    fun `the platform default for this op reads as allowed`() {
        // MODE_DEFAULT on RUN_ANY_IN_BACKGROUND means no restriction was written.
        val output = "RUN_ANY_IN_BACKGROUND: default; time=+1d2h3m4s5ms"

        assertEquals(RestrictionState.ALLOWED, parseAppOpMode(output))
    }

    @Test
    fun `an ignored app-op reads as restricted`() {
        val output = "RUN_ANY_IN_BACKGROUND: ignore; time=+13m2s1ms"

        assertEquals(RestrictionState.RESTRICTED, parseAppOpMode(output))
    }

    @Test
    fun `a foreground-only app-op reads as restricted`() {
        val output = "RUN_ANY_IN_BACKGROUND: foreground; duration=+8s"

        assertEquals(RestrictionState.RESTRICTED, parseAppOpMode(output))
    }

    @Test
    fun `an errored app-op reads as no answer rather than as restricted`() {
        // MODE_ERRORED is what appops returns for an op the build does not know.
        // Reporting that as "restricted" would invent a restriction the platform
        // never confirmed.
        val output = "RUN_ANY_IN_BACKGROUND: errored; time=+0ms"

        assertNull(parseAppOpMode(output))
    }

    @Test
    fun `the op is found among the others in a full appops dump`() {
        val output = """
            Uid state: 200
            Package [com.shakeit] (a1b2c3):
                WAKE_LOCK: allow; time=+1m2s3ms; duration=+4ms
                RUN_ANY_IN_BACKGROUND: ignore; time=+5m6s7ms
                RUN_IN_BACKGROUND: allow; time=+8m9s1ms
        """.trimIndent()

        assertEquals(RestrictionState.RESTRICTED, parseAppOpMode(output))
    }

    @Test
    fun `output without the op answers unknown`() {
        assertNull(parseAppOpMode("No operations."))
        assertNull(parseAppOpMode(""))
    }

    // ------------------------------------------------------- process exit codes

    @Test
    fun `every documented exit code maps to its own reason`() {
        // The codes are ApplicationExitInfo's, and each one points at a different
        // fix — so a mapping that collapses two of them loses the diagnosis.
        val expected = mapOf(
            0 to ExitReason.Unknown,
            1 to ExitReason.ExitSelf,
            2 to ExitReason.Signaled,
            3 to ExitReason.LowMemory,
            4 to ExitReason.Crash,
            5 to ExitReason.CrashNative,
            6 to ExitReason.Anr,
            7 to ExitReason.InitializationFailure,
            8 to ExitReason.PermissionChange,
            9 to ExitReason.ExcessiveResourceUsage,
            10 to ExitReason.UserRequested,
            11 to ExitReason.UserStopped,
            12 to ExitReason.DependencyDied,
            13 to ExitReason.Other,
            14 to ExitReason.Freezer,
            15 to ExitReason.PackageStateChange,
            16 to ExitReason.PackageUpdated,
        )

        expected.forEach { (code, reason) ->
            assertEquals("code $code", reason, exitReasonFor(code))
        }
        assertEquals("every code is covered", expected.size, ExitReason.values().size)
    }

    @Test
    fun `a code this app does not know about still maps to something`() {
        // A future Android can add reasons; the diagnostics must not throw or
        // silently claim "unknown exit" for a death that was recorded.
        assertEquals(ExitReason.Unknown, exitReasonFor(99))
        assertEquals(ExitReason.Unknown, exitReasonFor(-1))
    }

    @Test
    fun `the recents-swipe code is the one the enum calls ambiguous`() {
        // Worth stating because it is the code this app most needs to read
        // correctly: it is what a swipe, a force stop and a vendor's "close app"
        // all report, and the timestamp beside it is what separates them.
        assertTrue(exitReasonFor(10) == ExitReason.UserRequested)
    }
}
