package com.example.appdev.domain.alert

enum class AlertTransition { TRIGGERED, RESOLVED }

/**
 * Hysteresis state machine for overheating alerts.
 *
 * - Becomes active the first time a reading reaches the threshold.
 * - Stays active (no repeat alerts) until the temperature falls to `threshold - hysteresis`.
 *
 * A reading hovering around the threshold (e.g. 39.9 → 40.0 → 39.8 → 40.1) therefore produces
 * exactly one alert, not one per crossing.
 */
class OverheatDetector {

    var isActive: Boolean = false
        private set

    /** Marks an incident as already in progress, e.g. when restoring after a process restart. */
    fun resume() {
        isActive = true
    }

    fun onReading(temperatureC: Float, thresholdC: Float, hysteresisC: Float): AlertTransition? {
        if (!temperatureC.isFinite()) return null
        val clearLevel = thresholdC - hysteresisC.coerceAtLeast(0f)
        return when {
            !isActive && temperatureC >= thresholdC -> {
                isActive = true
                AlertTransition.TRIGGERED
            }
            isActive && temperatureC <= clearLevel -> {
                isActive = false
                AlertTransition.RESOLVED
            }
            else -> null
        }
    }
}
