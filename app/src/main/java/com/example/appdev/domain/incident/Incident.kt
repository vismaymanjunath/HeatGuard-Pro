package com.example.appdev.domain.incident

import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource

/** One continuous period during which the battery temperature was at or above the threshold. */
data class Incident(
    val id: Long = 0,
    val source: ReadingSource,
    val startMillis: Long,
    /** Null while the incident is still open. */
    val endMillis: Long?,
    /** Timestamp of the last reading recorded for this incident. */
    val lastSeenMillis: Long,
    val initialTempC: Float,
    val peakTempC: Float,
    val peakMillis: Long,
    /** Temperature at the moment the incident was resolved; null if open or closed after a gap. */
    val endTempC: Float?,
    val thresholdC: Float,
    val startBatteryPercent: Int?,
    val endBatteryPercent: Int?,
    val startChargeStatus: ChargeStatus,
    val startPowerSource: PowerSource,
) {
    val isActive: Boolean get() = endMillis == null

    /** Duration in ms; for an open incident this runs to the last reading actually recorded. */
    val durationMillis: Long get() = ((endMillis ?: lastSeenMillis) - startMillis).coerceAtLeast(0L)

    val startedWhileCharging: Boolean
        get() = startChargeStatus == ChargeStatus.CHARGING
}

/** A reading captured while an incident was open. */
data class IncidentSample(
    val incidentId: Long,
    val timestampMillis: Long,
    val temperatureC: Float,
    val batteryPercent: Int?,
    val chargeStatus: ChargeStatus,
    val powerSource: PowerSource,
)
