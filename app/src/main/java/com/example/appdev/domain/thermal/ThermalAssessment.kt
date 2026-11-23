package com.example.appdev.domain.thermal

import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.UserSettings

/** Green / orange / red indicator state, plus UNKNOWN when no temperature is available. */
enum class TemperatureLevel { UNKNOWN, NORMAL, ELEVATED, CRITICAL }

object TemperatureClassifier {

    /**
     * - CRITICAL: temperature is at or above the threshold.
     * - ELEVATED: within [warningMarginC] below the threshold.
     * - NORMAL:   below the warning band.
     * - UNKNOWN:  no valid reading.
     */
    fun classify(temperatureC: Float?, thresholdC: Float, warningMarginC: Float): TemperatureLevel {
        if (temperatureC == null || !temperatureC.isFinite()) return TemperatureLevel.UNKNOWN
        return when {
            temperatureC >= thresholdC -> TemperatureLevel.CRITICAL
            temperatureC >= thresholdC - warningMarginC -> TemperatureLevel.ELEVATED
            else -> TemperatureLevel.NORMAL
        }
    }
}

/** A reading evaluated against the user's settings. */
data class ThermalAssessment(
    val snapshot: BatterySnapshot?,
    val settings: UserSettings,
    val level: TemperatureLevel,
) {
    val temperatureC: Float? get() = snapshot?.temperatureC

    /** Degrees remaining before the threshold; negative when already above it. */
    val headroomC: Float? get() = temperatureC?.let { settings.thresholdC - it }

    companion object {
        fun evaluate(snapshot: BatterySnapshot?, settings: UserSettings): ThermalAssessment =
            ThermalAssessment(
                snapshot = snapshot,
                settings = settings,
                level = TemperatureClassifier.classify(
                    snapshot?.temperatureC,
                    settings.thresholdC,
                    settings.warningMarginC,
                ),
            )
    }
}
