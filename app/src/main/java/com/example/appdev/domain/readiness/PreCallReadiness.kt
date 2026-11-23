package com.example.appdev.domain.readiness

import com.example.appdev.domain.cooling.PersonalCoolingProfile
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.thermal.Prediction
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.TemperatureClassifier
import com.example.appdev.domain.thermal.TemperatureLevel
import com.example.appdev.domain.thermal.TemperaturePredictor
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalStatus

enum class ReadinessStatus {
    READY,
    CAUTION,
    COOL_DOWN_FIRST,

    /** No valid temperature reading, so readiness cannot be judged. */
    UNAVAILABLE,
}

enum class TrendDirection { RISING, FALLING, STEADY;

    companion object {
        fun fromSlope(slopeCPerMin: Float): TrendDirection = when {
            slopeCPerMin > TemperaturePredictor.STABLE_SLOPE_C_PER_MIN -> RISING
            slopeCPerMin < -TemperaturePredictor.STABLE_SLOPE_C_PER_MIN -> FALLING
            else -> STEADY
        }
    }
}

/** Why the status is what it is. Shown to the user so the result is never a black box. */
sealed interface ReadinessReason {
    data class AboveThreshold(val overByC: Float) : ReadinessReason
    data object InWarningBand : ReadinessReason
    data class SystemThermal(val status: ThermalStatus) : ReadinessReason
    data class HeatingSoon(val minutes: Float) : ReadinessReason
    data object CoolingDown : ReadinessReason
    data object AllClear : ReadinessReason
}

enum class EstimateBasis {
    /** The user's average cooling rate from past recoveries. */
    PERSONAL_HISTORY,

    /** The cooling rate measured over the last few minutes. */
    CURRENT_TREND,
}

sealed interface CoolingEstimate {
    /** The battery is already below the temperature needed to be ready. */
    data object NotNeeded : CoolingEstimate

    data class Estimated(val millis: Long, val basis: EstimateBasis, val targetC: Float, val basedOnEvents: Int) : CoolingEstimate

    /** Neither enough history nor a current cooling trend to estimate from. */
    data object InsufficientData : CoolingEstimate
}

data class PreCallReadiness(
    val status: ReadinessStatus,
    val reasons: List<ReadinessReason>,
    val temperatureC: Float?,
    /** Null while there are too few readings to fit a trend. */
    val trend: TrendDirection?,
    val trendCPerMin: Float?,
    /** Null when the device or Android version does not report it. */
    val thermalStatus: ThermalStatus?,
    val cooling: CoolingEstimate,
)

/**
 * Judges whether the phone is in a good state to start a call, from real readings only. It
 * looks at the battery temperature against the user's threshold, the short-term trend, Android's
 * own thermal status where available, and estimates how long cooling would take. It makes no
 * claim about how long a call can last.
 */
object ReadinessEvaluator {
    /** Warming that would reach the threshold within this many minutes means cool down first. */
    const val COOL_DOWN_IF_ETA_MINUTES = 5f

    /** Warming that would reach the threshold within this many minutes calls for caution. */
    const val CAUTION_IF_ETA_MINUTES = 15f

    fun evaluate(
        settings: UserSettings,
        analysis: ThermalAnalysis,
        system: SystemThermalState,
        profile: PersonalCoolingProfile,
        temperatureC: Float?,
    ): PreCallReadiness {
        val temp = temperatureC?.takeIf { it.isFinite() }
        val trend = analysis.trend
        val direction = trend?.let { TrendDirection.fromSlope(it.slopeCPerMin) }

        if (temp == null) {
            return PreCallReadiness(
                status = ReadinessStatus.UNAVAILABLE,
                reasons = emptyList(),
                temperatureC = null,
                trend = direction,
                trendCPerMin = trend?.slopeCPerMin,
                thermalStatus = system.status,
                cooling = CoolingEstimate.InsufficientData,
            )
        }

        val level = TemperatureClassifier.classify(temp, settings.thresholdC, settings.warningMarginC)
        val heating = analysis.prediction as? Prediction.Heating
        val sysStatus = system.status

        val mustCoolDown = level == TemperatureLevel.CRITICAL ||
            sysStatus?.isSevere == true ||
            (heating != null && heating.minutesToThreshold <= COOL_DOWN_IF_ETA_MINUTES)
        val caution = level == TemperatureLevel.ELEVATED ||
            (sysStatus != null && sysStatus >= ThermalStatus.MODERATE) ||
            (heating != null && heating.minutesToThreshold <= CAUTION_IF_ETA_MINUTES)

        val status = when {
            mustCoolDown -> ReadinessStatus.COOL_DOWN_FIRST
            caution -> ReadinessStatus.CAUTION
            else -> ReadinessStatus.READY
        }

        val reasons = buildList {
            if (level == TemperatureLevel.CRITICAL) add(ReadinessReason.AboveThreshold(temp - settings.thresholdC))
            if (sysStatus != null && sysStatus >= ThermalStatus.MODERATE) add(ReadinessReason.SystemThermal(sysStatus))
            if (heating != null && heating.minutesToThreshold <= CAUTION_IF_ETA_MINUTES) {
                add(ReadinessReason.HeatingSoon(heating.minutesToThreshold))
            }
            if (level == TemperatureLevel.ELEVATED) add(ReadinessReason.InWarningBand)
            if (status == ReadinessStatus.READY) {
                add(ReadinessReason.AllClear)
            } else if (direction == TrendDirection.FALLING) {
                add(ReadinessReason.CoolingDown)
            }
        }

        return PreCallReadiness(
            status = status,
            reasons = reasons,
            temperatureC = temp,
            trend = direction,
            trendCPerMin = trend?.slopeCPerMin,
            thermalStatus = sysStatus,
            cooling = coolingEstimate(temp, settings.warningStartC, trend?.slopeCPerMin, profile),
        )
    }

    /**
     * Time to cool to [targetC], the temperature below which the warning colour clears. Prefers the
     * user's own history; otherwise uses the cooling rate being measured right now; otherwise there
     * is honestly nothing to base an estimate on.
     */
    private fun coolingEstimate(
        temp: Float,
        targetC: Float,
        slopeCPerMin: Float?,
        profile: PersonalCoolingProfile,
    ): CoolingEstimate {
        if (temp < targetC) return CoolingEstimate.NotNeeded

        profile.estimateRecoveryMillis(temp, targetC)?.let { millis ->
            return CoolingEstimate.Estimated(millis, EstimateBasis.PERSONAL_HISTORY, targetC, profile.eventCount)
        }
        if (slopeCPerMin != null && TrendDirection.fromSlope(slopeCPerMin) == TrendDirection.FALLING) {
            val millis = ((temp - targetC) / -slopeCPerMin * 60_000f).toLong()
            return CoolingEstimate.Estimated(millis, EstimateBasis.CURRENT_TREND, targetC, 0)
        }
        return CoolingEstimate.InsufficientData
    }
}
