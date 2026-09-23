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
 * hand the accelerometer to [ShakeItEngine], and keep the notification in step
 * with the hardware. The engine, and therefore the torch state, lives in the
 * [ShakeItApplication] scope so the UI and this service can never disagree.
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

        // Idempotent, and called again on every start so a restart by the system
        // re-arms detection instead of running an empty foreground notification.
        engine.startDetection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        observeHardware()

        // A null intent means the system restarted the service after killing it
        // for memory; detection should come back either way.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (::engine.isInitialized) engine.stopDetection()
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
     * Rewrites the notification whenever the torch or the pocket state changes,
     * which is what makes the notification a truthful status line rather than a
     * "service is running" placeholder.
     */
    private fun observeHardware() {
        if (stateJob?.isActive == true) return
        val manager = getSystemService<NotificationManager>() ?: return
        stateJob = scope.launch {
            combine(engine.torch.torchOn, engine.covered) { torchOn, covered ->
                torchOn to covered
            }.collect { (torchOn, covered) ->
                manager.notify(
                    ShakeItNotification.ID,
                    ShakeItNotification.build(
                        context = this@ShakeItService,
                        torchOn = torchOn,
                        covered = covered,
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
