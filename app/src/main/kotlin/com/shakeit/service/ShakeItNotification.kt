package com.shakeit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.shakeit.MainActivity
import com.shakeit.R

/**
 * The foreground notification for [ShakeItService].
 *
 * It doubles as the only visible sign that shake detection is armed, so its text
 * reports what the detector is doing right now — listening, torch on, or paused
 * because the phone looks covered — and refreshing it is what tells a user with
 * the screen off that a shake actually landed.
 *
 * The framework [NotificationManager] is used directly rather than the AndroidX
 * wrapper: posting is a no-op without `POST_NOTIFICATIONS` on Android 13+ either
 * way, and the service still runs.
 */
internal object ShakeItNotification {

    const val ID = 1001

    private const val CHANNEL_ID = "shake_detection"
    private const val REQUEST_CONTENT = 1
    private const val REQUEST_STOP = 2

    /** Creates the low-importance channel the service posts into. Idempotent. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            // Silent and unobtrusive: this is a status line, not an alert.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds the notification for the current hardware state.
     *
     * @param covered whether the proximity sensor says the phone is in a pocket
     */
    fun build(context: Context, torchOn: Boolean, covered: Boolean): Notification {
        val statusText = when {
            covered -> context.getString(R.string.notification_covered)
            torchOn -> context.getString(R.string.notification_torch_on)
            else -> context.getString(R.string.notification_listening)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_torch)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(statusText)
            .setContentIntent(contentIntent(context))
            .addAction(0, context.getString(R.string.notification_stop), stopIntent(context))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Show it as soon as the service goes foreground instead of waiting
            // for the system to decide the notification is worth surfacing.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            // Rebuilds on every state change; only the first one should be
            // noticeable, and it should not beep or pulse.
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_STOP,
        Intent(context, StopDetectionReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
