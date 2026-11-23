package com.example.appdev.domain.demo

import com.example.appdev.domain.model.BatteryHealth
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.ThermalStatus
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DemoScenario {
    /** Temperature follows the slider. */
    MANUAL,

    /** Warms steadily past the threshold and stays hot. */
    HEAT_UP,

    /** Cools steadily towards a comfortable temperature. */
    COOL_DOWN,

    /** Warms past the threshold, stays hot briefly, then cools: one full incident and recovery. */
    FULL_CYCLE,

    /** Charging with steady heating, as when charging during heavy use. */
    CHARGING_HEAT,
}

enum class CyclePhase { WARMING, HOLDING, COOLING, DONE }

data class DemoControls(
    val scenario: DemoScenario = DemoScenario.MANUAL,
    val manualTempC: Float = 28f,
    val charging: Boolean = false,
    val powerSource: PowerSource = PowerSource.USB,
    val batteryPercent: Int = 64,
) {
    /** Whether the simulated phone is charging, including scenarios that imply charging. */
    val effectiveCharging: Boolean get() = charging || scenario == DemoScenario.CHARGING_HEAT
}

data class DemoState(
    val tempC: Float,
    val scenario: DemoScenario,
    val phase: CyclePhase = CyclePhase.WARMING,
    val holdRemainingSec: Float = 0f,
)

/**
 * Pure, deterministic simulation of a phone's battery temperature. Its output is turned into
 * ordinary [BatterySnapshot]s tagged [ReadingSource.DEMO] and fed through the same pipeline as
 * real readings; nothing downstream knows how a value was produced except that tag.
 */
object DemoSimulator {
    const val MIN_TEMP_C = 25f
    const val MAX_TEMP_C = 50f

    // Rates are faster than a real phone so a demo shows a full story in minutes, but slow enough
    // that the forecast (which needs about a minute of readings) appears well before the alert:
    // from the default 28 C, heating at 0.08 C/s reaches the 40 C threshold after 150 s, and the
    // forecast is visible from about 60 s onwards.
    const val HEAT_RATE_C_PER_SEC = 0.08f
    const val COOL_RATE_C_PER_SEC = 0.12f
    const val CHARGING_HEAT_RATE_C_PER_SEC = 0.06f
    const val MANUAL_MAX_RATE_C_PER_SEC = 2f

    const val HOLD_SECONDS = 20f
    const val COOL_FLOOR_C = 32f
    const val SIMULATED_TECHNOLOGY = "Simulated"

    private const val HEAT_UP_OVER_THRESHOLD_C = 5f
    private const val CYCLE_OVER_THRESHOLD_C = 4f
    private const val CHARGING_OVER_THRESHOLD_C = 3f
    private const val COOL_DOWN_TARGET_C = 30f

    fun initial(controls: DemoControls) =
        DemoState(controls.manualTempC.coerceIn(MIN_TEMP_C, MAX_TEMP_C), controls.scenario)

    fun step(state: DemoState, controls: DemoControls, dtSeconds: Float, thresholdC: Float): DemoState {
        // Switching scenario restarts that scenario's own sequence from the current temperature.
        val s = if (state.scenario != controls.scenario) {
            state.copy(scenario = controls.scenario, phase = CyclePhase.WARMING, holdRemainingSec = 0f)
        } else {
            state
        }
        return when (controls.scenario) {
            DemoScenario.MANUAL -> s.copy(
                tempC = approach(s.tempC, controls.manualTempC.coerceIn(MIN_TEMP_C, MAX_TEMP_C), MANUAL_MAX_RATE_C_PER_SEC * dtSeconds),
            )
            DemoScenario.HEAT_UP -> s.copy(
                tempC = approach(s.tempC, (thresholdC + HEAT_UP_OVER_THRESHOLD_C).coerceAtMost(MAX_TEMP_C), HEAT_RATE_C_PER_SEC * dtSeconds),
            )
            DemoScenario.COOL_DOWN -> s.copy(
                tempC = approach(s.tempC, COOL_DOWN_TARGET_C, COOL_RATE_C_PER_SEC * dtSeconds),
            )
            DemoScenario.CHARGING_HEAT -> s.copy(
                tempC = approach(s.tempC, (thresholdC + CHARGING_OVER_THRESHOLD_C).coerceAtMost(MAX_TEMP_C), CHARGING_HEAT_RATE_C_PER_SEC * dtSeconds),
            )
            DemoScenario.FULL_CYCLE -> cycle(s, dtSeconds, thresholdC)
        }
    }

    private fun cycle(s: DemoState, dt: Float, thresholdC: Float): DemoState {
        val peak = (thresholdC + CYCLE_OVER_THRESHOLD_C).coerceAtMost(MAX_TEMP_C)
        return when (s.phase) {
            CyclePhase.WARMING -> {
                val t = approach(s.tempC, peak, HEAT_RATE_C_PER_SEC * dt)
                if (t == peak) s.copy(tempC = t, phase = CyclePhase.HOLDING, holdRemainingSec = HOLD_SECONDS) else s.copy(tempC = t)
            }
            CyclePhase.HOLDING -> {
                val remaining = s.holdRemainingSec - dt
                if (remaining <= 0f) s.copy(phase = CyclePhase.COOLING, holdRemainingSec = 0f) else s.copy(holdRemainingSec = remaining)
            }
            CyclePhase.COOLING -> {
                val t = approach(s.tempC, COOL_FLOOR_C, COOL_RATE_C_PER_SEC * dt)
                if (t == COOL_FLOOR_C) s.copy(tempC = t, phase = CyclePhase.DONE) else s.copy(tempC = t)
            }
            CyclePhase.DONE -> s
        }
    }

    private fun approach(current: Float, target: Float, maxDelta: Float): Float =
        if (abs(target - current) <= maxDelta) target else if (target > current) current + maxDelta else current - maxDelta

    /** The reading a phone in this state would report, tagged as demo data. */
    fun toSnapshot(state: DemoState, controls: DemoControls, nowMillis: Long): BatterySnapshot {
        val charging = controls.effectiveCharging
        val power = controls.powerSource.takeIf { it != PowerSource.NONE && it != PowerSource.UNKNOWN } ?: PowerSource.USB
        return BatterySnapshot(
            timestampMillis = nowMillis,
            temperatureC = (state.tempC * 10).roundToInt() / 10f,
            levelPercent = controls.batteryPercent.coerceIn(0, 100),
            chargeStatus = if (charging) ChargeStatus.CHARGING else ChargeStatus.DISCHARGING,
            powerSource = if (charging) power else PowerSource.NONE,
            voltageMillivolts = 3_900,
            health = BatteryHealth.GOOD,
            technology = SIMULATED_TECHNOLOGY,
            source = ReadingSource.DEMO,
        )
    }

    /** Simulated Android thermal APIs, derived from temperature so demos show the risk index reacting. */
    fun systemStateFor(tempC: Float?): SystemThermalState {
        if (tempC == null) return SystemThermalState(statusApiAvailable = true, headroomApiAvailable = true)
        val status = when {
            tempC < 38f -> ThermalStatus.NONE
            tempC < 41f -> ThermalStatus.LIGHT
            tempC < 44f -> ThermalStatus.MODERATE
            tempC < 47f -> ThermalStatus.SEVERE
            else -> ThermalStatus.CRITICAL
        }
        val headroom = ((tempC - 30f) / 17f).coerceIn(0f, 1.2f)
        return SystemThermalState(
            statusApiAvailable = true,
            status = status,
            headroomApiAvailable = true,
            headroomNow = headroom,
            headroomForecast = (headroom + 0.05f).coerceAtMost(1.2f),
        )
    }
}
