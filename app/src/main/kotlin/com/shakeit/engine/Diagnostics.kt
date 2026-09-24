package com.shakeit.engine

import com.shakeit.background.PowerDiagnosticsSnapshot
import com.shakeit.background.PreviousExit
import com.shakeit.background.ShizukuState
import com.shakeit.background.ShizukuStatus
import com.shakeit.hardware.DetectionStatus
import com.shakeit.hardware.SensorDiagnostics

/**
 * Everything worth knowing about whether background detection is working, in one
 * immutable read.
 *
 * This exists because the interesting failures are invisible. "The notification
 * is there but shaking does nothing" has at least four different causes — the
 * process was killed, the listener was refused, delivery was starved, or a wake
 * lock was ignored — and they need different fixes. Answering "which one?" needs
 * facts that live in different subsystems, so they are gathered here rather than
 * pieced together from a screen.
 *
 * Every field is read from the subsystem that owns it. Nothing is inferred from
 * another field, and nothing is remembered from a previous run except
 * [previousExit], which is by definition about a process that no longer exists.
 *
 * @param serviceRunning the foreground service is alive, as reported by the
 *   service itself rather than by asking the ActivityManager
 * @param wantsDetection the "Detection active" preference
 * @param wantsBackgroundService the "Run in background" preference. Shown next to
 *   [serviceRunning] because a contradiction between them is a finding
 * @param wantsStartAfterReboot the "Start after reboot" preference
 * @param wantsAutoOff the "Auto-off after 5 min" preference
 * @param sensitivity the sensitivity slider, which now really retunes the detector
 * @param power the platform's power-policy answers; null until the first read,
 *   which is deliberately not at process start
 * @param previousExit why the last process died, on API 30+
 * @param exitHistorySupported whether this Android version can answer that at all
 * @param processUptimeMillis how long *this* process has been alive. Paired with
 *   [previousExit] it separates "killed and restarted" from "alive and frozen"
 * @param recoveryAttempts how many sensor-stack rebuilds the current stall has
 *   consumed, out of [maxRecoveryAttempts]
 */
data class DiagnosticsSnapshot(
    val serviceRunning: Boolean = false,
    val detectionStatus: DetectionStatus = DetectionStatus.INACTIVE,
    val sensor: SensorDiagnostics = SensorDiagnostics(),
    val wantsDetection: Boolean = true,
    val wantsBackgroundService: Boolean = true,
    val wantsStartAfterReboot: Boolean = true,
    val wantsAutoOff: Boolean = false,
    val sensitivity: Int = 0,
    val power: PowerDiagnosticsSnapshot? = null,
    val previousExit: PreviousExit? = null,
    val exitHistorySupported: Boolean = false,
    val processUptimeMillis: Long = 0,
    val shizuku: ShizukuStatus = ShizukuStatus(state = ShizukuState.NOT_INSTALLED),
    val lastPrivilegedAction: PrivilegedAction? = null,
    val recoveryAttempts: Int = 0,
    val maxRecoveryAttempts: Int = 0,
)

/**
 * One privileged action Shizuku was asked to perform, and what came of it.
 *
 * Recorded rather than assumed: the point of the whole exercise is that the app
 * can say what it changed, with the command's own exit status beside it, instead
 * of reporting success because a button was tapped.
 *
 * @param description the command in plain words, including its exit code
 * @param succeeded the command exited 0 *and* a follow-up read confirmed the
 *   change, where one was possible
 * @param atElapsedMillis when it happened, on the clock that survives suspend
 */
data class PrivilegedAction(
    val description: String,
    val succeeded: Boolean,
    val atElapsedMillis: Long,
)
