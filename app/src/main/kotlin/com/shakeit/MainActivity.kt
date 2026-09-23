package com.shakeit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shakeit.ui.ShakeItApp

/**
 * Single-activity host. The app draws behind the system bars (the prototype's
 * `.screen-clip` background runs edge to edge), so the screens inset their own
 * content — see `ShakeItScreen`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShakeItApp()
        }
    }
}
