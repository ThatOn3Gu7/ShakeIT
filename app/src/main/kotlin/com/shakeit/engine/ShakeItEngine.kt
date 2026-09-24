package com.shakeit.engine

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.shakeit.ShakeItApplication
import com.shakeit.background.PowerDiagnostics
import com.shakeit.background.PowerDiagnosticsSnapshot
import com.shakeit.background.PreviousExit
import com.shakeit.background.ProcessExitDiagnostics
import com.shakeit.background.RestrictionState
import com.shakeit.background.ShizukuController
import com.shakeit.background.dozeAllowlistContains
import com.shakeit.background.parseAppOpMode
import com.shakeit.hardware.DetectionStatus
import com.shakeit.hardware.Haptics
import com.shakeit.hardware.SensorDiagnostics
import com.shakeit.hardware.ShakeDetector
import com.shakeit.hardware.TorchController
import com.shakeit.hardware.detectionStatus
import com.shakeit.hardware.recoveryDelayMillis
import com.shakeit.service.ShakeItService
import com.shakeit.state.DefaultShakeItSnapshot
import com.shakeit.state.DoubleShakeGate
import com.shakeit.state.ShakeGesture
import com.shakeit.state.ShakeItSnapshot
import com.shakeit.state.ShakeItStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one place hardware and platform state are touched, owned by the process
 * rather than by an [android.app.Activity] or a [android.app.Service].
 *
 * Both the UI and the foreground service reach the same instance through
 * [ShakeItApplication], which is what makes the screen and the background agree:
 * a shake with the screen off flips [torchOn], and the composition that is still
 * alive observes it and redraws; a tap in the UI drives the very same controller
 * the service would.
 *
 * Nothing here depends on the activity or on composition. Detection runs from a
 * sensor thread into [toggleTorch]; the recovery watchdog runs on its own
 * dispatcher; the UI is only ever an observer, so closing it — or the system
 * destroying it — cannot interrupt the path from a shake to the flash, and cannot
 * interrupt the repair of that path either.
 *
 * Three jobs live here rather than in the service, because they have to survive
 * the service being restarted and the UI being gone:
 *
 *  * **Arming detection** and re-tuning it when a preference changes.
 *  * **Supervising it**: a tick every two seconds asks how old the newest sample
 *    is, and rebuilds the sensor stack when delivery has stopped — with a budget
 *    and a backoff, because a device that has stopped delivering for good must
 *    not be torn down and rebuilt forever.
 *  * **Collecting the truth** into [DiagnosticsSnapshot], so "why did it stop"
 *    can be answered from the device rather than guessed at from a bug report.
 */
class ShakeItEngine(private val context: Context) {

    /** The real flashlight, and the truth about whether it is lit. */
    val torch = TorchController(context)

    /** Every power-policy mechanism Android lets a third-party app query. */
    val power = PowerDiagnostics(context)

    /** The optional privileged path: real state, never a remembered one. */
    val shizuku = ShizukuController(context)

    private val exits = ProcessExitDiagnostics(context)
    private val haptics = Haptics(context)
    private val store = ShakeItStore(context)

    /**
     * Read on first use rather than at construction: it is a binder call, and the
     * answer is about a process that no longer exists, so it cannot change while
     * this one lives.
     */
    private val previousExit: PreviousExit? by lazy { exits.previousExit() }

    /**
     * Deliberately not the main dispatcher: the watchdog has to keep telling the
     * truth even if the main thread is blocked or frozen, and none of this work is
     * UI work.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _shakeEvents = MutableStateFlow(0)

    /**
     * Counts recognised shakes. Monotonic on purpose: an observer compares it
     * against the value it last saw, so it can animate once per shake without
     * replaying old ones after a configuration change.
     */
    val shakeEvents: StateFlow<Int> = _shakeEvents.asStateFlow()

    private val _detectionStatus = MutableStateFlow(DetectionStatus.INACTIVE)

    /**
     * Whether detection is *actually* working, not merely requested.
     *
     * [DetectionStatus.RECOVERING] and [DetectionStatus.STALLED] are the states
     * this app most needs to be able to report: the service is alive and its
     * notification is up, but no samples are arriving — which is what a device
     * power manager freezing the process, Doze ignoring a wake lock, or a refused
     * sensor registration looks like from the inside.
     */
    val detectionStatus: StateFlow<DetectionStatus> = _detectionStatus.asStateFlow()

    private val _covered = MutableStateFlow(false)

    /** Whether the proximity sensor says the phone is covered, i.e. in a pocket. */
    val covered: StateFlow<Boolean> = _covered.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)

    /**
     * Whether the foreground service is alive, as reported by the service itself.
     * Asking the `ActivityManager` instead would be slower and would answer about
     * the past; the service knows, and it is in this process.
     */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _diagnostics = MutableStateFlow(DiagnosticsSnapshot())

    /** Every fact about background reliability, refreshed by the watchdog. */
    val diagnostics: StateFlow<DiagnosticsSnapshot> = _diagnostics.asStateFlow()

    private val _lastPrivilegedAction = MutableStateFlow<PrivilegedAction?>(null)

    private var detector: ShakeDetector? = null
    private var watchdog: Job? = null
    private var autoOffJob: Job? = null

    /** The pending pair is engine-owned so service restarts and UI changes can reset it. */
    private val doubleShakeGate = DoubleShakeGate()
    private var doubleShakeTimeoutJob: Job? = null

    /** How many rebuilds the current stall has consumed. */
    private var recoveryAttempts = 0
    private var lastRecoveryAtElapsed = 0L
    private var healthySinceElapsed = 0L
    private var powerSnapshot: PowerDiagnosticsSnapshot? = null

    /**
     * Held as a field on purpose: the platform keeps only a weak reference to
     * preference listeners, so an inline lambda here would be collected and the
     * engine would silently stop hearing about settings changes.
     */
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key -> onPreferenceChanged(key) }

    /** The persisted preferences, read on demand. In-memory after the first load. */
    private val preferences: ShakeItSnapshot
        get() = store.read(DefaultShakeItSnapshot)

    /**
     * Brings the process up. Called once, from `Application.onCreate`.
     *
     * No service start here: at this moment the process may have been created in
     * the background, where the platform refuses a foreground-service start. The
     * activity, the service itself and the boot receiver each ask for it from a
     * place where it is allowed.
     */
    fun start() {
        torch.start()
        shizuku.start()
        store.addChangeListener(preferenceListener)
        scope.launch {
            torch.torchOn.collect { torchOn -> rescheduleAutoOff(torchOn) }
        }
        startWatchdog()
        applyPreferences(fromUserAction = false)
    }

    /** Flips the torch to the opposite of its real current state. */
    fun toggleTorch() {
        setTorch(!torch.torchOn.value)
    }

    /**
     * Drives the torch and confirms with a haptic — but only when the request
     * landed, so a device without a flash does not pretend it did something.
     *
     * Called from the sensor thread when a shake lands, which is why nothing on
     * this path touches a view, a composition or the main looper.
     */
    fun setTorch(enabled: Boolean) {
        if (torch.setTorch(enabled)) haptics.tick()
    }

    // --------------------------------------------------------------- detection

    /**
     * Arms detection, creating the detector if this is the first time.
     *
     * A detector that fails to arm is *kept*, not discarded: a platform that
     * refuses `registerListener` once may accept it later, and the watchdog is the
     * only thing that will ask again. The status says [DetectionStatus.NO_SENSOR]
     * until it does.
     */
    fun startDetection() {
        val current = detector ?: ShakeDetector(
            context = context,
            onShake = ::onShakeDetected,
            onCoveredChanged = { covered -> _covered.value = covered },
            onHardwareWake = ::onHardwareWake,
        ).also { created ->
            detector = created
            created.setGesture(preferences.gesture)
            created.setSensitivity(preferences.sensitivity)
        }
        if (current.isRunning) {
            publishStatus()
            return
        }
        resetDoubleShake()
        if (!current.start()) {
            Log.w(TAG, "detection did not arm; diagnostics will say why")
        }
        publishStatus()
    }

    /**
     * Stops listening. The torch is left alone — detection and light are separate
     * — and the detector instance is kept so its sensor facts stay readable.
     */
    fun stopDetection() {
        detector?.stop()
        resetDoubleShake()
        _covered.value = false
        resetRecovery()
        publishStatus()
    }

    // ----------------------------------------------------------------- service

    /**
     * Brings the detection service to the foreground, from a place where the
     * platform allows it. From there the service outlives the activity and keeps
     * working with the screen off or the app backgrounded.
     */
    fun startService() {
        val intent = ShakeItService.intent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (error: IllegalStateException) {
            // Android 12+ refuses a foreground-service start from the background
            // (ForegroundServiceStartNotAllowedException). Reaching this means
            // something tried after the activity had already gone; detection
            // waits for the next legitimate start rather than crashing the app.
            Log.w(TAG, "the platform refused to start the detection service", error)
        } catch (error: SecurityException) {
            Log.w(TAG, "not allowed to start the detection service", error)
        }
    }

    /** Stops the detection service, and with it the notification. */
    fun stopService() {
        try {
            context.stopService(ShakeItService.intent(context))
        } catch (error: IllegalStateException) {
            // Stopping a service between startForegroundService() and its
            // startForeground() is refused rather than queued, and the system then
            // throws ForegroundServiceDidNotStartInTimeException at the service.
            // Logging beats crashing; the next legitimate stop finishes the job.
            Log.w(TAG, "the platform refused to stop the detection service now", error)
        }
    }

    /** The service reports itself foreground. */
    fun onServiceStarted() {
        _serviceRunning.value = true
        publishDiagnostics()
    }

    /** The service reports itself gone. */
    fun onServiceStopped() {
        _serviceRunning.value = false
        publishDiagnostics()
    }

    /**
     * What the activity does when it comes to the foreground: bring up the
     * background owner *if the user asked for one*, and arm detection either way.
     *
     * This is the only place a service start is attempted on the user's behalf,
     * because it is the one moment the app is definitely in the foreground and the
     * platform will therefore allow it.
     */
    fun onForegroundStart() {
        val prefs = preferences
        if (!prefs.detectionActive) {
            Log.i(TAG, "detection is switched off; nothing to start")
            return
        }
        if (prefs.runInBackground) startService() else startDetection()
    }

    /**
     * The task was swiped out of Recents.
     *
     * The service stays: `stopWithTask` is false and a foreground service outlives
     * its task, which is the behaviour this app is designed around. What must not
     * happen is accepting the existing listener as healthy — some OEM power
     * managers treat a removed task as a reason to freeze the package, so this is
     * a recovery point, not a no-op.
     */
    fun onTaskRemoved() {
        scope.launch { recoverNow("the task was removed from Recents") }
    }

    // ------------------------------------------------------------- preferences

    /**
     * Reconciles the persisted switches with what is actually running.
     *
     * Each of these used to be a stored preference with no effect; each now does
     * the thing its label says:
     *
     *  * **Detection active** off — no detector, and no service, because there
     *    would be nothing for the service to keep alive.
     *  * **Run in background** off — detection stays, but in-process only: no
     *    service, no notification, and Android stops delivering sensor events to
     *    background apps, so it works until the app leaves the foreground. That is
     *    what the switch promises, so that is what it does.
     *
     * @param fromUserAction whether a switch was just flipped in the UI, which is
     *   the only situation where starting the service is both wanted and allowed
     */
    private fun applyPreferences(fromUserAction: Boolean) {
        val prefs = preferences
        detector?.setSensitivity(prefs.sensitivity)
        rescheduleAutoOff(torch.torchOn.value)

        when {
            !prefs.detectionActive -> {
                if (_serviceRunning.value) stopService()
                stopDetection()
            }

            !prefs.runInBackground -> {
                if (_serviceRunning.value) stopService()
                startDetection()
            }

            // The service owns detection and arms it on start, so all that is
            // needed here is to ask for the service — and only from the UI, where
            // the platform allows a foreground-service start.
            fromUserAction -> startService()
        }
        publishDiagnostics()
    }

    private fun onPreferenceChanged(key: String?) {
        when (key) {
            ShakeItStore.KEY_SENSITIVITY -> {
                val sensitivity = preferences.sensitivity
                detector?.setSensitivity(sensitivity)
                Log.i(TAG, "sensitivity preference -> $sensitivity")
            }

            ShakeItStore.KEY_GESTURE -> {
                detector?.setGesture(preferences.gesture)
                resetDoubleShake()
            }

            ShakeItStore.KEY_DETECTION_ACTIVE, ShakeItStore.KEY_RUN_IN_BACKGROUND ->
                applyPreferences(fromUserAction = true)

            ShakeItStore.KEY_AUTO_OFF -> rescheduleAutoOff(torch.torchOn.value)

            // Theme, gesture, dynamic colour and the activation count change
            // nothing here; the diagnostics refresh on the next tick regardless.
            else -> return
        }
        publishDiagnostics()
    }

    /**
     * "Auto-off after 5 min", for real: five minutes after the torch came on it
     * goes off again, so a shake in a bag cannot leave the flash on until the
     * battery notices. Rescheduled on every torch change, and cancelled when the
     * torch goes off by any other route.
     */
    private fun rescheduleAutoOff(torchOn: Boolean) {
        autoOffJob?.cancel()
        autoOffJob = null
        if (!torchOn || !preferences.autoOffAfterFiveMinutes) return
        autoOffJob = scope.launch {
            delay(AUTO_OFF_MILLIS)
            if (torch.torchOn.value) {
                Log.i(TAG, "auto-off: the torch has been on for five minutes")
                setTorch(false)
            }
        }
    }

    // -------------------------------------------------------------- supervision

    /**
     * Asks the detector how old its newest sample is, on a timer, and acts on the
     * answer.
     *
     * A `delay` on a background dispatcher does not wake the processor, so this
     * costs nothing while the device sleeps — it resumes when the device does, and
     * by then the elapsed-realtime gap it measures says exactly how long delivery
     * had stopped. That is also why this cannot be the only recovery path: a
     * frozen process does not run its own timers, which is what the
     * significant-motion trigger is for.
     */
    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        watchdog = scope.launch {
            var tick = 0
            while (true) {
                delay(WATCHDOG_INTERVAL_MILLIS)
                tick++
                // The power answers are binder calls and the sensor ones are not,
                // so the two are refreshed at different rates.
                if (tick % POWER_REFRESH_TICKS == 0) refreshPower()
                supervise()
                publishStatus()
                publishDiagnostics()
            }
        }
    }

    private fun supervise() {
        val current = detector
        when (classify(current)) {
            DetectionStatus.ACTIVE -> noteHealthy()

            DetectionStatus.RECOVERING -> maybeRecover()

            // A sensor that exists but whose listener was refused: rebuilding is
            // the only retry there is, so the same budget and backoff apply.
            DetectionStatus.NO_SENSOR ->
                if (current?.hasAccelerometer == true) maybeRecover() else resetRecovery()

            // Exhausted: leave the budget spent. Real samples still return the
            // status to ACTIVE the moment they arrive, so giving up on rebuilds is
            // not giving up on detection.
            DetectionStatus.STALLED -> Unit

            DetectionStatus.INACTIVE -> resetRecovery()
        }
    }

    private fun classify(current: ShakeDetector?): DetectionStatus = detectionStatus(
        armed = current?.isRunning == true,
        hasSensor = current?.hasAccelerometer == true,
        registrationSucceeded = current?.isRegistrationSucceeded == true,
        hasDeliveredSample = current?.isDeliveringSamples == true,
        millisSinceLastSample = current?.millisSinceLastSample(),
        stallAfterMillis = STALL_AFTER_MILLIS,
        recoveryAttempts = recoveryAttempts,
        maxRecoveryAttempts = MAX_RECOVERY_ATTEMPTS,
    )

    /**
     * Rebuilds the sensor stack, if the budget and the backoff allow it.
     *
     * The backoff is the reason this is a function and not a reflex: the first
     * retry comes after two seconds, and each subsequent one takes twice as long
     * up to five minutes. A transient starvation recovers almost immediately; a
     * device that has decided to stop delivering is not rebuilt every two seconds
     * for the rest of the night.
     */
    private fun maybeRecover() {
        val current = detector ?: return
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) return

        val now = SystemClock.elapsedRealtime()
        if (lastRecoveryAtElapsed != 0L) {
            val backoff = recoveryDelayMillis(
                attempt = recoveryAttempts,
                baseMillis = RECOVERY_BASE_DELAY_MILLIS,
                maxDelayMillis = RECOVERY_MAX_DELAY_MILLIS,
            )
            if (now - lastRecoveryAtElapsed < backoff) return
        }

        recoveryAttempts++
        lastRecoveryAtElapsed = now
        resetDoubleShake()
        val registered = current.recover()
        Log.w(
            TAG,
            "delivery had stopped: rebuilt the sensor stack " +
                "(attempt $recoveryAttempts/$MAX_RECOVERY_ATTEMPTS, registered=$registered)",
        )
    }

    /**
     * Forces a recovery outside the backoff, for moments that are themselves
     * evidence the device is awake and worth another try: the significant-motion
     * trigger firing, or the task being swiped away.
     *
     * One attempt is handed back rather than the whole budget, so a phone being
     * moved around cannot turn this into a rebuild loop.
     */
    private suspend fun recoverNow(because: String) {
        val current = detector
        if (current == null || !current.isRunning) {
            startDetection()
            publishStatus()
            return
        }
        if (classify(current) == DetectionStatus.ACTIVE) return

        // Give delivery a moment to resume on its own: a trigger sensor wakes the
        // processor, and the accelerometer may simply have been mid-batch.
        delay(HARDWARE_WAKE_GRACE_MILLIS)
        if (classify(current) == DetectionStatus.ACTIVE) {
            noteHealthy()
            publishStatus()
            return
        }

        recoveryAttempts = (recoveryAttempts - 1).coerceAtLeast(0)
        lastRecoveryAtElapsed = 0L
        Log.i(TAG, "$because: forcing a sensor recovery")
        maybeRecover()
        publishStatus()
        publishDiagnostics()
    }

    /**
     * The significant-motion trigger fired: hardware woke the processor.
     *
     * Not a shake, and never treated as one — significant motion is far broader
     * than a deliberate shake, and toggling the torch on it would undo everything
     * [com.shakeit.hardware.ShakeAlgorithm] is tuned for. It is a *wake*: proof
     * the device can still deliver, and therefore the right moment to check
     * whether the accelerometer is.
     */
    private fun onHardwareWake() {
        scope.launch { recoverNow("significant motion woke the device") }
    }

    /** Samples are flowing again; after a while, the stall budget is restored. */
    private fun noteHealthy() {
        if (recoveryAttempts == 0) {
            healthySinceElapsed = 0L
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (healthySinceElapsed == 0L) {
            healthySinceElapsed = now
            return
        }
        if (now - healthySinceElapsed >= HEALTHY_FOR_MILLIS) {
            Log.i(TAG, "delivery has been stable; the recovery budget is restored")
            resetRecovery()
        }
    }

    private fun resetRecovery() {
        recoveryAttempts = 0
        lastRecoveryAtElapsed = 0L
        healthySinceElapsed = 0L
    }

    // ------------------------------------------------------------- diagnostics

    /**
     * Re-reads everything that can change while the app is away: the power
     * answers, and Shizuku's. Called on resume, because the system settings screen
     * and the Shizuku app are other apps' activities, and there is no callback for
     * "the user came back".
     */
    fun refreshDiagnostics() {
        shizuku.refresh()
        refreshPower()
        publishStatus()
        publishDiagnostics()
    }

    private fun refreshPower() {
        powerSnapshot = try {
            power.snapshot()
        } catch (error: RuntimeException) {
            Log.w(TAG, "could not read the power diagnostics", error)
            powerSnapshot
        }
    }

    private fun publishStatus() {
        val status = classify(detector)
        val previous = _detectionStatus.value
        _detectionStatus.value = status
        if (previous != status) {
            // The single most useful line for diagnosing a device that stops
            // detecting: what changed, how stale the samples had gone, and how
            // much of the recovery budget is left.
            Log.i(
                TAG,
                "detection $previous -> $status " +
                    "(last sample ${detector?.millisSinceLastSample()}ms ago, " +
                    "recovery $recoveryAttempts/$MAX_RECOVERY_ATTEMPTS)",
            )
        }
    }

    private fun publishDiagnostics() {
        _diagnostics.value = DiagnosticsSnapshot(
            serviceRunning = _serviceRunning.value,
            detectionStatus = _detectionStatus.value,
            sensor = detector?.diagnostics() ?: SensorDiagnostics(),
            wantsDetection = preferences.detectionActive,
            wantsBackgroundService = preferences.runInBackground,
            wantsStartAfterReboot = preferences.startAfterReboot,
            wantsAutoOff = preferences.autoOffAfterFiveMinutes,
            sensitivity = preferences.sensitivity,
            power = powerSnapshot,
            previousExit = previousExit,
            exitHistorySupported = exits.supported,
            processUptimeMillis = exits.processUptimeMillis(),
            shizuku = shizuku.status.value,
            lastPrivilegedAction = _lastPrivilegedAction.value,
            recoveryAttempts = recoveryAttempts,
            maxRecoveryAttempts = MAX_RECOVERY_ATTEMPTS,
        )
    }

    // ------------------------------------------------------------------ Shizuku

    /**
     * Asks Shizuku for authorisation. The answer arrives asynchronously on
     * Shizuku's own listener and lands in [ShizukuController.status]; nothing here
     * claims a result before it has one.
     */
    fun requestShizukuPermission() {
        shizuku.requestPermission()
    }

    /**
     * Opens the Shizuku app, for the two states ShakeIT cannot fix itself: the
     * service is not running, and permission was denied for good.
     */
    fun openShizuku(): Boolean {
        val intent = shizuku.shizukuLaunchIntent() ?: return false
        return launch(intent)
    }

    /**
     * Opens the standard battery-optimisation screen, where the user can set
     * ShakeIT to "Unrestricted" — the power-exemption allowlist, which is also
     * what Low Power Standby exempts from on AOSP-derived builds.
     */
    fun openBatterySettings(): Boolean = power.openBatterySettings()

    /** This app's own system settings page: the doorway to the OEM's own controls. */
    fun openAppSettings(): Boolean = power.openAppSettings()

    /**
     * Adds ShakeIT — and only ShakeIT — to the Doze allowlist through Shizuku,
     * then reads the list back to confirm it took.
     *
     * The read-back is the point. `cmd deviceidle whitelist +pkg` exiting 0 says
     * the command ran, not that an OEM's second list agreed, so the claim is only
     * made when the platform's own list shows the package.
     */
    fun repairDozeAllowlist() {
        scope.launch(Dispatchers.IO) {
            val write = shizuku.addToDozeAllowlist()
            val confirmed = dozeAllowlistContains(
                shizuku.readDozeAllowlist().output,
                context.packageName,
            )
            record(
                succeeded = write.succeeded && confirmed == true,
                description = "Doze allowlist: " +
                    "`cmd deviceidle whitelist +${context.packageName}` " +
                    "exited ${write.exitCode}" +
                    write.errorText.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty() +
                    "; read-back " + when (confirmed) {
                        true -> "lists ShakeIT"
                        false -> "does not list ShakeIT"
                        null -> "could not be parsed"
                    },
            )
            afterPrivilegedAction()
        }
    }

    /**
     * Clears a background restriction the platform wrote for ShakeIT — the
     * `RUN_ANY_IN_BACKGROUND` app-op, which is what "Restrict background activity"
     * sets — and reads the mode back.
     */
    fun repairBackgroundAppOp() {
        scope.launch(Dispatchers.IO) {
            val write = shizuku.allowBackgroundAppOp()
            val mode = parseAppOpMode(shizuku.readBackgroundAppOp().output)
            record(
                // Only a positive read-back counts: an unparseable answer is not
                // evidence the restriction is gone.
                succeeded = write.succeeded && mode == RestrictionState.ALLOWED,
                description = "Background app-op: `cmd appops set ${context.packageName} " +
                    "RUN_ANY_IN_BACKGROUND allow` exited ${write.exitCode}" +
                    write.errorText.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty() +
                    "; read-back " + (mode?.name?.lowercase() ?: "unparseable"),
            )
            afterPrivilegedAction()
        }
    }

    private fun record(succeeded: Boolean, description: String) {
        _lastPrivilegedAction.value = PrivilegedAction(
            description = description,
            succeeded = succeeded,
            atElapsedMillis = SystemClock.elapsedRealtime(),
        )
        Log.i(TAG, "privileged action ${if (succeeded) "succeeded" else "failed"}: $description")
    }

    /**
     * A privileged write can change the power answers, so they are re-read. Runs
     * on the IO dispatcher the command already used: both calls are thread-safe.
     */
    private fun afterPrivilegedAction() {
        refreshPower()
        publishDiagnostics()
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: RuntimeException) {
        Log.w(TAG, "no screen for ${intent.action}", error)
        false
    }

    /**
     * A valid, debounced shake got through the sensor pipeline. Normal Shake is
     * intentionally unchanged. Double Shake adds only a small gate in front of
     * the same toggle path, so sensitivity and hardware reliability stay owned by
     * ShakeDetector exactly as before.
     */
    private fun onShakeDetected() {
        when (preferences.gesture) {
            ShakeGesture.Shake -> completeShakeAction()
            ShakeGesture.DoubleShake -> {
                val completed = doubleShakeGate.accept(SystemClock.elapsedRealtime())
                if (completed) {
                    doubleShakeTimeoutJob?.cancel()
                    doubleShakeTimeoutJob = null
                    completeShakeAction()
                } else {
                    doubleShakeTimeoutJob?.cancel()
                    doubleShakeTimeoutJob = scope.launch {
                        delay(DoubleShakeGate.DEFAULT_TIMEOUT_MILLIS)
                        doubleShakeGate.reset()
                    }
                }
            }
        }
    }

    private fun completeShakeAction() {
        _shakeEvents.update { it + 1 }
        toggleTorch()
    }

    private fun resetDoubleShake() {
        doubleShakeTimeoutJob?.cancel()
        doubleShakeTimeoutJob = null
        doubleShakeGate.reset()
    }

    private companion object {
        const val TAG = "ShakeItEngine"

        /** How often the watchdog checks that samples are still arriving. */
        const val WATCHDOG_INTERVAL_MILLIS = 2_000L

        /** Binder-backed power answers are refreshed this many ticks less often. */
        const val POWER_REFRESH_TICKS = 5

        /**
         * How long without a sample counts as stalled. Armed and healthy, the
         * accelerometer delivers roughly every 20ms, so this leaves a wide margin
         * for scheduling hiccups while still catching a device that has stopped
         * delivering altogether.
         */
        const val STALL_AFTER_MILLIS = 3_000L

        /** Rebuilds allowed per stall before the detector reports STALLED and stops. */
        const val MAX_RECOVERY_ATTEMPTS = 5

        /** First retry delay; each subsequent one doubles. */
        const val RECOVERY_BASE_DELAY_MILLIS = 2_000L

        /** Ceiling on the backoff: five minutes between rebuilds, not five seconds. */
        const val RECOVERY_MAX_DELAY_MILLIS = 300_000L

        /** How long delivery must be stable before the budget is restored. */
        const val HEALTHY_FOR_MILLIS = 30_000L

        /**
         * How long to wait after a hardware wake before rebuilding: the trigger
         * woke the processor, and the accelerometer may simply have been mid-batch.
         */
        const val HARDWARE_WAKE_GRACE_MILLIS = 750L

        /** "Auto-off after 5 min", in milliseconds. */
        const val AUTO_OFF_MILLIS = 5 * 60 * 1000L
    }
}

/**
 * Reaches the process-wide engine from composition, the same way
 * [com.shakeit.state.rememberShakeItState] reaches the state.
 *
 * Read through the [android.app.Application] rather than the activity's context
 * so a configuration change hands back the very same instance — which is what
 * keeps the torch state, and the shake count, continuous across rotations.
 */
@Composable
fun rememberShakeItEngine(): ShakeItEngine {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        (context.applicationContext as ShakeItApplication).engine
    }
}
