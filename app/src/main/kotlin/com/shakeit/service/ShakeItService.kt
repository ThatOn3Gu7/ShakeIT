package com.shakeit.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.getSystemService
import com.shakeit.ShakeItApplication
import com.shakeit.engine.ShakeItEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps shake detection alive when the activity is gone: screen off, device
 * locked, app swiped to the background.
 *
 * It owns no logic of its own. All it does is promote itself to the foreground —
 * which is the only supported way to keep reading sensors on modern Android —
 * tell the engine it is up, hand over the accelerometer, and keep the
 * notification in step with the hardware. The engine, and therefore the torch
 * state, lives in the [ShakeItApplication] scope so the UI and this service can
 * never disagree.
 *
 * The order of those steps is not incidental. A service started with
 * `startForegroundService` has only a few seconds to call `startForeground`, or
 * the system throws and kills it; every millisecond spent before that call is
 * borrowed from that budget. So the notification goes up first, sensors second.
 *
 * Declared `specialUse` because none of the platform's foreground service types
 * describe "listen to the accelerometer while the screen is off"; the subtype
 * property in the manifest states the reason.
 */
class ShakeItService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var engine: ShakeItEngine
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        engine = (application as ShakeItApplication).engine
        ShakeItNotification.createChannel(this)
        // Detection is armed from onStartCommand, which always follows onCreate
        // for a started service — one place, so it cannot drift.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Foreground, before anything else — see the class documentation.
        engine.onServiceStarted()
        goForeground()

        // 2. Then arm detection. Idempotent, and repeated on every start: if
        // anything ever stopped the listener while the service lived on, the next
        // start re-arms it rather than leaving a foreground notification over a
        // dead sensor.
        engine.startDetection()

        // 3. Then keep the notification honest, which corrects the first line of
        // text as soon as the detector reports what it managed to register.
        observeHardware()

        // A null intent means the system restarted the service after killing it
        // for memory; detection should come back either way.
        return START_STICKY
    }

    /**
     * The user swiped the task away from Recents.
     *
     * Nothing here stops the service: `stopWithTask` is false and `onStartCommand`
     * returns [START_STICKY], so on stock Android the foreground service outlives
     * its task, which is the behaviour the app is designed around — the activity is
     * disposable, this is not. What must not happen is treating the surviving
     * listener as automatically healthy: some OEM power managers read a removed
     * task as "freeze the package", which stops sample delivery while leaving
     * everything looking started. So the task removal is handed to the engine as a
     * recovery point rather than swallowed.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (::engine.isInitialized) engine.onTaskRemoved()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (::engine.isInitialized) {
            engine.stopDetection()
            engine.onServiceStopped()
        }
        super.onDestroy()
    }

    /** No binding: nothing talks to this service except the engine it shares. */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Promotes the service and posts the notification. On Android 14+ the type
     * has to be passed as well as declared, or the call throws.
     */
    private fun goForeground() {
        val notification = ShakeItNotification.build(
            context = this,
            torchOn = engine.torch.torchOn.value,
            covered = engine.covered.value,
            status = engine.detectionStatus.value,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ShakeItNotification.ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(ShakeItNotification.ID, notification)
        }
    }

    /**
     * Rewrites the notification whenever the torch, the pocket state or the
     * detector's health changes, which is what makes the notification a truthful
     * status line rather than a "service is running" placeholder.
     */
    private fun observeHardware() {
        if (stateJob?.isActive == true) return
        val manager = getSystemService<NotificationManager>() ?: return
        stateJob = scope.launch {
            combine(
                engine.torch.torchOn,
                engine.covered,
                engine.detectionStatus,
            ) { torchOn, covered, status ->
                Triple(torchOn, covered, status)
            }.collect { (torchOn, covered, status) ->
                manager.notify(
                    ShakeItNotification.ID,
                    ShakeItNotification.build(
                        context = this@ShakeItService,
                        torchOn = torchOn,
                        covered = covered,
                        status = status,
                    ),
                )
            }
        }
    }

    companion object {
        /** An explicit intent for this service, used both to start and to stop it. */
        fun intent(context: Context): Intent = Intent(context, ShakeItService::class.java)
    }
}
