package com.shakeit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.shakeit.ui.ShakeItApp

/**
 * Single-activity host. The app draws behind the system bars (the prototype's
 * `.screen-clip` background runs edge to edge), so the screens inset their own
 * content — see `ShakeItScreen`.
 *
 * The activity is deliberately *not* where the app's work happens: it starts the
 * detection service and renders whatever the engine reports, so shaking the phone
 * keeps working after this activity is finished.
 */
class MainActivity : ComponentActivity() {

    private val engine by lazy { (application as ShakeItApplication).engine }

    /**
     * Set by the notification's Diagnostics action, which is the one way into
     * this activity that should land on Settings rather than on the home screen.
     * Held in state rather than read once, because the action can also arrive
     * through [onNewIntent] while the activity is already alive.
     */
    private val openSettingsRequest = mutableStateOf(false)

    /**
     * Android 13+ hides the foreground notification until this is granted, and
     * asking first is not just tidier: a notification posted before the grant is
     * dropped outright, which would leave a running service the user cannot see.
     *
     * The answer is never worth blocking on — detection is armed either way.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            engine.onForegroundStart()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent {
            ShakeItApp(openSettingsOnLaunch = openSettingsRequest.value)
        }

        // Started from the foreground, which is the only moment the platform
        // allows a foreground-service start on the user's behalf. What actually
        // happens next is the engine's decision, from the persisted switches: a
        // background owner when "Run in background" is on, an in-process detector
        // when it is not, and nothing at all when detection is switched off.
        if (notificationsVisible()) {
            engine.onForegroundStart()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // The battery screen and the Shizuku app are other apps' activities, and
        // neither calls back when the user comes home, so resume is the moment
        // every diagnostic answer is re-read — including what happened while the
        // process was away, which is the whole reason the page exists.
        engine.refreshDiagnostics()
    }

    private fun readIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) {
            openSettingsRequest.value = true
        }
    }

    /**
     * Whether the service's notification can actually be seen. Below Android 13
     * there is nothing to ask for; above it, only a grant counts. A refusal that
     * has already been made permanent still delivers a result immediately, so the
     * service is started either way.
     */
    private fun notificationsVisible(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Opens the app on the Settings screen, where the diagnostics live. */
        const val EXTRA_OPEN_SETTINGS = "com.shakeit.extra.OPEN_SETTINGS"
    }
}
