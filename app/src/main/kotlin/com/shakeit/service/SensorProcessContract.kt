package com.shakeit.service

/** Explicit, package-private commands sent from the UI process to the sensor process. */
object SensorProcessContract {
    const val ACTION_COMMAND = "com.shakeit.action.SENSOR_COMMAND"
    const val ACTION_STATUS = "com.shakeit.action.SENSOR_STATUS"
    const val EXTRA_STATUS = "status"
    const val EXTRA_SERVICE_RUNNING = "service_running"
    const val EXTRA_SENSOR_DIAGNOSTICS = "sensor_diagnostics"
    const val EXTRA_ACCELEROMETER_AVAILABLE = "accelerometer_available"
    const val EXTRA_ACCELEROMETER_WAKE_UP = "accelerometer_wake_up"
    const val EXTRA_REGISTRATION_SUCCEEDED = "registration_succeeded"
    const val EXTRA_WAKE_LOCK_REQUIRED = "wake_lock_required"
    const val EXTRA_WAKE_LOCK_HELD = "wake_lock_held"
    const val EXTRA_HAS_DELIVERED_SAMPLE = "has_delivered_sample"
    const val EXTRA_MILLIS_SINCE_SAMPLE = "millis_since_sample"
    const val EXTRA_RECOVERIES = "recoveries"
    const val EXTRA_SENSITIVITY = "sensitivity"
    const val EXTRA_GESTURE = "gesture"
    const val EXTRA_DETECTION_ACTIVE = "detection_active"
}
