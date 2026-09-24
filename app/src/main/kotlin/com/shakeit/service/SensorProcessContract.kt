package com.shakeit.service

/** Explicit, package-private commands sent from the UI process to the sensor process. */
object SensorProcessContract {
    const val ACTION_COMMAND = "com.shakeit.action.SENSOR_COMMAND"
    const val ACTION_STATUS = "com.shakeit.action.SENSOR_STATUS"
    const val EXTRA_STATUS = "status"
    const val EXTRA_SERVICE_RUNNING = "service_running"
    const val EXTRA_SENSITIVITY = "sensitivity"
    const val EXTRA_GESTURE = "gesture"
    const val EXTRA_DETECTION_ACTIVE = "detection_active"
}
