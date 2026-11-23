package com.example.appdev.domain.ai

import com.example.appdev.domain.analytics.InsightsReport
import com.example.appdev.domain.cooling.PersonalCoolingProfile
import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.readiness.PreCallReadiness
import com.example.appdev.domain.thermal.SessionStats
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalAssessment
import java.util.TimeZone

/** General device facts. Nothing that identifies a person or the unit. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
)

/** Whole-phone signals that influence heat, read from Android without any special permission. */
data class PhoneMetrics(
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val lowMemory: Boolean,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val powerSaveOn: Boolean?,
    val brightnessPercent: Int?,
    val uptimeMillis: Long,
    val cpuCores: Int,
)

/** Everything the assistant is allowed to know, gathered at the moment a question is asked. */
data class PhoneContext(
    val nowMillis: Long,
    val timeZone: TimeZone,
    /** The screen the user is looking at, e.g. "Insights". */
    val screen: String?,
    /** True in Demo Mode: every value is simulated and must be described that way. */
    val simulated: Boolean,
    val assessment: ThermalAssessment,
    val analysis: ThermalAnalysis,
    val system: SystemThermalState,
    val readiness: PreCallReadiness,
    val profile: PersonalCoolingProfile,
    val insights: InsightsReport?,
    val recentIncidents: List<Incident>,
    val sessionStats: SessionStats,
    val device: DeviceInfo?,
    val metrics: PhoneMetrics?,
    val monitoringRunning: Boolean,
    val coolingSessionActive: Boolean,
)

internal fun ChargeStatus.describe(): String = when (this) {
    ChargeStatus.CHARGING -> "charging"
    ChargeStatus.DISCHARGING -> "on battery"
    ChargeStatus.FULL -> "full"
    ChargeStatus.NOT_CHARGING -> "plugged in, not charging"
    ChargeStatus.UNKNOWN -> "unknown"
}

internal fun PowerSource.describe(): String = when (this) {
    PowerSource.AC -> "AC charger"
    PowerSource.USB -> "USB"
    PowerSource.WIRELESS -> "wireless charger"
    PowerSource.DOCK -> "dock"
    PowerSource.NONE -> "none"
    PowerSource.UNKNOWN -> "unknown source"
}
