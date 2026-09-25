package com.shakeit

import android.app.Application
import com.shakeit.engine.ShakeItEngine

/**
 * Owns the [ShakeItEngine] for the current process. The foreground service is
 * isolated in `:sensor`, so its engine is the authoritative detector owner;
 * the UI process observes hardware state and sends explicit preference commands
 * rather than relying on shared in-memory state.
 */
class ShakeItApplication : Application() {

    /** Process-wide hardware access: the torch, shake detection and haptics. */
    lateinit var engine: ShakeItEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = ShakeItEngine(this)

        // Registering the torch callback costs nothing while it sits idle, and
        // it is what keeps the UI honest when another app drives the flash.
        engine.start()
    }
}
