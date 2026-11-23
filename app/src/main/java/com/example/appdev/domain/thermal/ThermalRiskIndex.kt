package com.example.appdev.domain.thermal

import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import kotlin.math.roundToInt

enum class RiskLevel { LOW, MODERATE, HIGH, CRITICAL;

    companion object {
        fun fromIndex(index: Int): RiskLevel = when {
            index >= 75 -> CRITICAL
            index >= 50 -> HIGH
            index >= 25 -> MODERATE
            else -> LOW
        }
    }
}

enum class RiskFactor(val maxPoints: Float) {
    TEMPERATURE(35f),
    WARMING_RATE(20f),
    TIME_TO_THRESHOLD(15f),
    SYSTEM_STATUS(10f),
    HEADROOM(10f),
    CHARGING(10f),
}

/** Why the index was raised above its weighted score. */
enum class FloorReason { AT_THRESHOLD, SYSTEM_SEVERE }

data class RiskComponent(
    val factor: RiskFactor,
    /** Points earned, 0..[RiskFactor.maxPoints]. Zero when [available] is false. */
    val points: Float,
    /** False when the device did not supply the input; the factor is then left out of the total. */
    val available: Boolean,
) {
    val maxPoints: Float get() = factor.maxPoints
}

data class ThermalRisk(
    /** Final 0..100 index shown to the user. */
    val index: Int,
    /** Weighted score before any safety floor. */
    val weightedIndex: Int,
    val level: RiskLevel,
    val components: List<RiskComponent>,
    val floors: List<FloorReason>,
)

/**
 * Thermal Risk Index (0–100). A transparent heuristic, not a manufacturer measurement:
 *
 * 1. Each [RiskFactor] earns points up to its weight (weights sum to 100).
 * 2. Factors the device cannot supply are excluded and the rest are rescaled to 100, so a phone
 *    without thermal APIs is not penalised or flattered.
 * 3. Safety floors: at or above the user's threshold, or at SEVERE system thermal status or worse,
 *    the index is never below [FLOOR_INDEX].
 */
object ThermalRiskIndex {
    const val FLOOR_INDEX = 75

    /** Warming at or above this rate (°C/min) earns full trend points. */
    const val FULL_TREND_SLOPE_C_PER_MIN = 1.0f

    /** Temperature points start this far below the threshold. */
    const val TEMPERATURE_RANGE_C = 10f

    private const val ETA_FULL_POINTS_MINUTES = 2f
    private const val ETA_ZERO_POINTS_MINUTES = 15f

    fun compute(
        snapshot: BatterySnapshot?,
        thresholdC: Float,
        trend: TrendEstimate?,
        prediction: Prediction,
        system: SystemThermalState,
    ): ThermalRisk? {
        val temperature = snapshot?.temperatureC?.takeIf { it.isFinite() } ?: return null
        val overThreshold = temperature >= thresholdC

        val components = listOf(
            temperatureComponent(temperature, thresholdC),
            warmingRateComponent(trend),
            etaComponent(overThreshold, trend, prediction),
            statusComponent(system.status),
            headroomComponent(system.headroomNow),
            chargingComponent(snapshot),
        )

        val availableMax = components.filter { it.available }.sumOf { it.maxPoints.toDouble() }
        val earned = components.sumOf { it.points.toDouble() }
        val weighted = if (availableMax > 0) (earned / availableMax * 100).roundToInt().coerceIn(0, 100) else 0

        val floors = buildList {
            if (overThreshold) add(FloorReason.AT_THRESHOLD)
            if (system.status?.isSevere == true) add(FloorReason.SYSTEM_SEVERE)
        }
        val index = if (floors.isNotEmpty()) maxOf(weighted, FLOOR_INDEX) else weighted

        return ThermalRisk(index, weighted, RiskLevel.fromIndex(index), components, floors)
    }

    private fun temperatureComponent(temperature: Float, thresholdC: Float): RiskComponent {
        val fraction = ((temperature - (thresholdC - TEMPERATURE_RANGE_C)) / TEMPERATURE_RANGE_C).coerceIn(0f, 1f)
        return RiskComponent(RiskFactor.TEMPERATURE, RiskFactor.TEMPERATURE.maxPoints * fraction, true)
    }

    private fun warmingRateComponent(trend: TrendEstimate?): RiskComponent {
        if (trend == null) return RiskComponent(RiskFactor.WARMING_RATE, 0f, false)
        val fraction = (trend.slopeCPerMin / FULL_TREND_SLOPE_C_PER_MIN).coerceIn(0f, 1f)
        return RiskComponent(RiskFactor.WARMING_RATE, RiskFactor.WARMING_RATE.maxPoints * fraction, true)
    }

    private fun etaComponent(overThreshold: Boolean, trend: TrendEstimate?, prediction: Prediction): RiskComponent {
        val max = RiskFactor.TIME_TO_THRESHOLD.maxPoints
        if (overThreshold) return RiskComponent(RiskFactor.TIME_TO_THRESHOLD, max, true)
        if (trend == null) return RiskComponent(RiskFactor.TIME_TO_THRESHOLD, 0f, false)
        val fraction = when (prediction) {
            is Prediction.Heating ->
                ((ETA_ZERO_POINTS_MINUTES - prediction.minutesToThreshold) /
                    (ETA_ZERO_POINTS_MINUTES - ETA_FULL_POINTS_MINUTES)).coerceIn(0f, 1f)
            else -> 0f
        }
        return RiskComponent(RiskFactor.TIME_TO_THRESHOLD, max * fraction, true)
    }

    private fun statusComponent(status: ThermalStatus?): RiskComponent {
        if (status == null) return RiskComponent(RiskFactor.SYSTEM_STATUS, 0f, false)
        val points = when (status) {
            ThermalStatus.NONE -> 0f
            ThermalStatus.LIGHT -> 2f
            ThermalStatus.MODERATE -> 5f
            ThermalStatus.SEVERE -> 8f
            ThermalStatus.CRITICAL, ThermalStatus.EMERGENCY, ThermalStatus.SHUTDOWN -> 10f
        }
        return RiskComponent(RiskFactor.SYSTEM_STATUS, points, true)
    }

    private fun headroomComponent(headroom: Float?): RiskComponent {
        if (headroom == null || !headroom.isFinite()) return RiskComponent(RiskFactor.HEADROOM, 0f, false)
        return RiskComponent(RiskFactor.HEADROOM, RiskFactor.HEADROOM.maxPoints * headroom.coerceIn(0f, 1f), true)
    }

    /** Charging adds heat; wireless is typically the least efficient. This is a heuristic weighting. */
    private fun chargingComponent(snapshot: BatterySnapshot): RiskComponent {
        val known = snapshot.chargeStatus != ChargeStatus.UNKNOWN
        if (!known) return RiskComponent(RiskFactor.CHARGING, 0f, false)
        val points = if (snapshot.chargeStatus == ChargeStatus.CHARGING) {
            when (snapshot.powerSource) {
                PowerSource.WIRELESS -> 10f
                PowerSource.AC, PowerSource.DOCK -> 8f
                PowerSource.USB, PowerSource.UNKNOWN, PowerSource.NONE -> 6f
            }
        } else {
            0f
        }
        return RiskComponent(RiskFactor.CHARGING, points, true)
    }
}

/** Everything the dashboard needs to explain the current thermal situation. */
data class ThermalAnalysis(
    val prediction: Prediction,
    val trend: TrendEstimate?,
    val risk: ThermalRisk?,
) {
    companion object {
        val Empty = ThermalAnalysis(Prediction.Unavailable, null, null)

        fun analyze(
            snapshot: BatterySnapshot?,
            history: List<TemperaturePoint>,
            thresholdC: Float,
            system: SystemThermalState,
        ): ThermalAnalysis {
            val trend = TrendEstimator.estimate(history)
            val prediction = TemperaturePredictor.predict(snapshot?.temperatureC, thresholdC, trend, history)
            val risk = ThermalRiskIndex.compute(snapshot, thresholdC, trend, prediction, system)
            return ThermalAnalysis(prediction, trend, risk)
        }
    }
}
