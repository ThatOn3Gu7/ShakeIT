package com.shakeit.background

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService

/**
 * Why a process ended, in the platform's own words.
 *
 * The codes mirror `ApplicationExitInfo.REASON_*`. They are written out rather
 * than referenced so this enum stays free of Android types: the mapping is the
 * part worth testing, and a JVM unit test cannot load `ApplicationExitInfo`.
 * They have been stable since API 30, when the constants were introduced.
 */
enum class ExitReason(val code: Int) {
    /** The platform recorded an exit but not a reason. */
    Unknown(0),

    /** The app ended itself: `System.exit`, `Process.killProcess`. */
    ExitSelf(1),

    /**
     * Killed by a signal, with the signal number in [PreviousExit.signal]. This
     * is what an external kill usually looks like — `SIGKILL` (9) from the system
     * server or from an OEM power manager, which cannot be caught or refused.
     */
    Signaled(2),

    /** The system was low on memory and reclaimed the process. */
    LowMemory(3),

    /** An uncaught exception in Java or Kotlin code. */
    Crash(4),

    /** A crash in native code. */
    CrashNative(5),

    /** Application not responding: the main thread was blocked for too long. */
    Anr(6),

    /** The process could not be started at all. */
    InitializationFailure(7),

    /** A runtime permission or app-op the process depended on was revoked. */
    PermissionChange(8),

    /** The process consumed too much of a limited resource: memory, file descriptors. */
    ExcessiveResourceUsage(9),

    /**
     * The user asked for the app to stop.
     *
     * This is the ambiguous one, and the reason the timestamp beside it matters:
     * swiping a task out of Recents reports `USER_REQUESTED` on stock Android,
     * and so does an OEM power manager's "close app" — but a foreground service
     * that survives the swipe keeps the process alive, so an exit with this
     * reason means something *did* stop the process rather than merely the task.
     */
    UserRequested(10),

    /** The user the app runs as was stopped. */
    UserStopped(11),

    /** Another process this one depended on died. */
    DependencyDied(12),

    /** None of the above. */
    Other(13),

    /** The Android 12+ cached-app freezer put the process on ice and it did not thaw. */
    Freezer(14),

    /** A package was added, removed or its state changed. */
    PackageStateChange(15),

    /** This package was updated, which ends the running process. */
    PackageUpdated(16),
}

/** Maps an `ApplicationExitInfo` reason code onto [ExitReason]. Pure, so it is testable. */
fun exitReasonFor(code: Int): ExitReason =
    ExitReason.values().firstOrNull { it.code == code } ?: ExitReason.Unknown

/**
 * One recorded process death.
 *
 * @param reason the platform's classification, mapped
 * @param reasonCode the raw `ApplicationExitInfo` code, kept because a future
 *   reason this enum does not know about still deserves to be shown as a number
 *   rather than silently reported as `Unknown`
 * @param signal the signal number when [reason] is [ExitReason.Signaled], else 0.
 *   Nine is `SIGKILL` — an uncatchable kill from outside the process
 * @param timestampMillis when it happened, wall clock, so it can be compared with
 *   the moment the user swiped the task away or locked the screen
 * @param description free text the platform sometimes adds, e.g. the exception
 * @param importanceCode the process's importance at the time: a foreground
 *   service is 125 (`IMPORTANCE_FOREGROUND_SERVICE`) and a cached process 400
 * @param pid the process id that died
 * @param pssKilobytes its proportional set size at the time, which is how an
 *   `EXCESSIVE_RESOURCE_USAGE` death explains itself
 */
data class PreviousExit(
    val reason: ExitReason,
    val reasonCode: Int,
    val signal: Int,
    val timestampMillis: Long,
    val description: String?,
    val importanceCode: Int,
    val pid: Int,
    val pssKilobytes: Long,
)

/**
 * Answers the question that decides what to fix next: **was the process killed,
 * or is it alive and being starved?**
 *
 * Those two look identical from the outside — the notification may still be
 * there, shaking does nothing — but they have different causes and different
 * remedies. A death leaves an [ApplicationExitInfo] record; a frozen or starved
 * process leaves none, and shows up instead as a stalled detector with a long
 * process uptime. Reading the record is the only way to tell them apart without
 * guessing, so it is read rather than inferred.
 *
 * API 30+: `getHistoricalProcessExitReasons` does not exist below Android 11, so
 * on older devices this reports "not supported" and the diagnosis falls back to
 * the sensor facts alone.
 */
class ProcessExitDiagnostics(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager: ActivityManager? = appContext.getSystemService()

    /** Whether this Android version can answer at all. */
    val supported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * The most recent recorded exit that carries a reason, or null when the
     * platform has nothing to report — a first launch, an Android below 11, or a
     * device that has never killed the app.
     *
     * Entries with no recorded reason are skipped rather than reported: an older
     * exit that says `SIGNALED` is more useful than a newer one that says
     * nothing, and both are shown honestly by keeping the raw code and timestamp.
     */
    fun previousExit(maxEvents: Int = MAX_EVENTS): PreviousExit? {
        // The version check is inlined rather than read from [supported]: Lint's
        // NewApi check does not follow the flag through a property, and an API
        // call it cannot place behind a guard fails the build.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = activityManager ?: return null
        return try {
            val history = manager.getHistoricalProcessExitReasons(
                appContext.packageName,
                0, // 0 = every pid this package has had, not just the current one
                maxEvents,
            )
            history.asSequence()
                .map { it.toPreviousExit() }
                .firstOrNull { it.reason != ExitReason.Unknown }
        } catch (error: RuntimeException) {
            Log.w(TAG, "could not read the process exit history", error)
            null
        }
    }

    /**
     * How long the current process has been alive, on the clock that keeps
     * running through suspend.
     *
     * Paired with [previousExit], this is what separates the two failure modes: a
     * process that restarted itself after a kill has a short uptime and an exit
     * record to explain it, while a frozen one has a long uptime, no record, and
     * a detector that stopped hearing anything.
     */
    fun processUptimeMillis(): Long =
        SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.toPreviousExit() = PreviousExit(
        reason = exitReasonFor(reason),
        reasonCode = reason,
        signal = status,
        timestampMillis = timestamp,
        description = description,
        importanceCode = importance,
        pid = pid,
        pssKilobytes = pssKilobytes,
    )

    private companion object {
        const val TAG = "ProcessExitDiagnostics"

        /** How far back to look. One death is enough to diagnose; five gives context. */
        const val MAX_EVENTS = 5
    }
}
