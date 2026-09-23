package com.shakeit

import android.app.Application
import com.shakeit.engine.ShakeItEngine

/**
 * Owns the [ShakeItEngine] for the whole process.
 *
 * Hardware access has to outlive any one screen: the service keeps detecting
 * shakes after the activity is destroyed, and the activity has to see torch
 * changes that happened while it was gone. Creating the engine here, once, is
 * what lets both sides read the same state instead of each keeping their own
 * idea of whether the light is on.
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
