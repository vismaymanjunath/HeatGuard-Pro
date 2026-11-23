package com.example.appdev.domain.cooling

import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.profile.ProfileType
import com.example.appdev.domain.thermal.Prediction
import com.example.appdev.domain.thermal.TemperatureLevel
import com.example.appdev.domain.thermal.ThermalStatus

enum class AdvicePriority { HIGH, MEDIUM, LOW }

/**
 * Recommendations are things the *user* can do. HeatGuard cannot control charging, close other
 * apps or identify which app is producing heat, so no advice claims otherwise.
 */
enum class AdviceType {
    UNPLUG_CHARGER,
    SWITCH_TO_WIRED_CHARGING,
    STOP_HEAVY_USE,
    REMOVE_CASE,
    MOVE_FROM_HEAT,
    REDUCE_BRIGHTNESS,
    NO_FREEZER,
    AIRPLANE_MODE_WEAK_SIGNAL,
    SYSTEM_THROTTLING,
    PREVENT_HEATING,
    CHARGING_WARMS,
    KEEP_RESTING,
    PROFILE_WORK,
    PROFILE_NAVIGATION,
    PROFILE_GAMING,
    PROFILE_CHARGING,
    ALL_GOOD,
    SENSOR_UNAVAILABLE,
}

data class Advice(val type: AdviceType, val priority: AdvicePriority)

/** The situation the advisor reasons about. */
data class AdviceContext(
    val level: TemperatureLevel,
    val isCharging: Boolean,
    val powerSource: PowerSource,
    val prediction: Prediction,
    val riskIndex: Int?,
    val systemStatus: ThermalStatus?,
    val profile: ProfileType?,
    val sessionActive: Boolean,
)

object CoolingAdvisor {
    const val MAX_ADVICE = 5

    /** A projected threshold crossing within this many minutes counts as "heating soon". */
    private const val HEATING_SOON_MINUTES = 10f
    private const val HIGH_RISK_INDEX = 75

    fun advise(ctx: AdviceContext): List<Advice> {
        if (ctx.level == TemperatureLevel.UNKNOWN) {
            return listOf(Advice(AdviceType.SENSOR_UNAVAILABLE, AdvicePriority.MEDIUM))
        }

        val hot = ctx.level == TemperatureLevel.CRITICAL || (ctx.riskIndex ?: 0) >= HIGH_RISK_INDEX
        val warm = ctx.level == TemperatureLevel.ELEVATED
        val heatingSoon = ctx.prediction is Prediction.Heating &&
            ctx.prediction.minutesToThreshold <= HEATING_SOON_MINUTES
        val wireless = ctx.isCharging && ctx.powerSource == PowerSource.WIRELESS

        val advice = mutableListOf<Advice>()
        fun add(type: AdviceType, priority: AdvicePriority) {
            if (advice.none { it.type == type }) advice += Advice(type, priority)
        }

        if (ctx.systemStatus?.isSevere == true) add(AdviceType.SYSTEM_THROTTLING, AdvicePriority.HIGH)

        when {
            hot -> {
                if (ctx.isCharging) add(AdviceType.UNPLUG_CHARGER, AdvicePriority.HIGH)
                add(AdviceType.STOP_HEAVY_USE, AdvicePriority.HIGH)
                if (wireless) add(AdviceType.SWITCH_TO_WIRED_CHARGING, AdvicePriority.MEDIUM)
                add(AdviceType.REMOVE_CASE, AdvicePriority.MEDIUM)
                add(AdviceType.MOVE_FROM_HEAT, AdvicePriority.MEDIUM)
                add(AdviceType.REDUCE_BRIGHTNESS, AdvicePriority.MEDIUM)
                add(AdviceType.NO_FREEZER, AdvicePriority.MEDIUM)
                add(AdviceType.AIRPLANE_MODE_WEAK_SIGNAL, AdvicePriority.LOW)
            }
            warm || heatingSoon -> {
                add(AdviceType.PREVENT_HEATING, if (heatingSoon) AdvicePriority.HIGH else AdvicePriority.MEDIUM)
                if (ctx.isCharging) add(AdviceType.CHARGING_WARMS, AdvicePriority.MEDIUM)
                if (wireless) add(AdviceType.SWITCH_TO_WIRED_CHARGING, AdvicePriority.MEDIUM)
                add(AdviceType.REDUCE_BRIGHTNESS, AdvicePriority.LOW)
                add(AdviceType.REMOVE_CASE, AdvicePriority.LOW)
            }
            else -> add(AdviceType.ALL_GOOD, AdvicePriority.LOW)
        }

        if (ctx.sessionActive && !hot) add(AdviceType.KEEP_RESTING, AdvicePriority.HIGH)

        profileTip(ctx.profile)?.let { add(it, if (hot || warm) AdvicePriority.MEDIUM else AdvicePriority.LOW) }

        return advice
            .sortedBy { it.priority.ordinal } // stable: keeps insertion order within a priority
            .take(MAX_ADVICE)
    }

    private fun profileTip(profile: ProfileType?): AdviceType? = when (profile) {
        ProfileType.WORK -> AdviceType.PROFILE_WORK
        ProfileType.NAVIGATION -> AdviceType.PROFILE_NAVIGATION
        ProfileType.GAMING -> AdviceType.PROFILE_GAMING
        ProfileType.CHARGING -> AdviceType.PROFILE_CHARGING
        null -> null
    }
}
