package com.shakeit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shakeit.ShakeItApplication
import com.shakeit.state.DefaultShakeItSnapshot
import com.shakeit.state.ShakeItStore

/**
 * Brings detection back after the device restarts, if the user asked for it.
 *
 * This is the only real boot path the app has, and it is deliberately conditional:
 * **Start after reboot** is a switch in Settings, so a user who turned it off is
 * not handed a foreground service and a notification on every boot. The same gate
 * applies to **Detection active** and **Run in background** — starting a service
 * to keep alive a detector the user switched off would be the switch lying.
 *
 * Two platform rules shape the rest:
 *
 *  * `BOOT_COMPLETED` is an explicit exemption from the background-start
 *    restriction on foreground services, so `startForegroundService` is allowed
 *    here even though the app has no visible UI. The service still has to reach
 *    `startForeground` promptly, which it does first thing in `onStartCommand`.
 *  * The type matters. From Android 15, only a restricted set of foreground
 *    service types may be started from `BOOT_COMPLETED` — and `specialUse` is not
 *    one of them. This app targets 34, where the rule does not apply; if the
 *    target ever moves to 35+, this receiver becomes the thing to revisit, and a
 *    refused start is caught rather than crashing at boot.
 *
 * `MY_PACKAGE_REPLACED` is handled for the same reason a reboot is: an app update
 * kills the process, the service does not come back on its own, and from the
 * user's point of view "it stopped working after an update" is the same failure.
 *
 * An app that has never been launched sits in the stopped state and does not
 * receive either broadcast, so nothing here can run before first use.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val app = context.applicationContext as? ShakeItApplication
        if (app == null) {
            Log.w(TAG, "no application to hand the restart to")
            return
        }
        val preferences = ShakeItStore(context).read(DefaultShakeItSnapshot)
        if (!preferences.startAfterReboot) {
            Log.i(TAG, "$action: start-after-reboot is off, leaving detection stopped")
            return
        }
        if (!preferences.detectionActive || !preferences.runInBackground) {
            Log.i(TAG, "$action: detection or background running is off, nothing to restart")
            return
        }
        Log.i(TAG, "$action: restarting detection")
        // startService() catches the refusals the platform can hand out here
        // (ForegroundServiceStartNotAllowedException, SecurityException) and logs
        // them, so a boot on a hostile device degrades to "not running" instead of
        // a crash in a receiver the user never sees.
        app.engine.startService()
    }

    private companion object {
        const val TAG = "ShakeItBoot"
    }
}
