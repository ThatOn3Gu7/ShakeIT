package com.shakeit.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the Stop action on the foreground notification.
 *
 * A receiver rather than a service `PendingIntent` on purpose: stopping a service
 * is allowed from any app state, while starting one from the background is not,
 * so the Stop button keeps working hours after the screen went off.
 *
 * The intent targeting it is explicit and the receiver is not exported, so this
 * is reachable only from ShakeIT's own notification.
 */
class StopDetectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        context.stopService(ShakeItService.intent(context))
    }
}
