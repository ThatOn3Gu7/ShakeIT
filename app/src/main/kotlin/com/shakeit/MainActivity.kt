package com.shakeit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
     * Android 13+ hides the foreground notification until this is granted, and
     * asking first is not just tidier: a notification posted before the grant is
     * dropped outright, which would leave a running service the user cannot see.
     *
     * The answer is never worth blocking on — detection is armed either way.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            engine.startService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShakeItApp()
        }

        // Started from the foreground, which is when the platform allows it, and
        // from here the service outlives this activity.
        if (notificationsVisible()) {
            engine.startService()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the system battery screen is the only way this answer
        // changes, and resume is exactly when we arrive back from it.
        engine.refreshBatteryRestrictions()
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
}
