package com.example.appdev.domain.incident

import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource

enum class TimelineMarker { START, PEAK, RESOLVED }

data class TimelineEntry(
    val timestampMillis: Long,
    val temperatureC: Float,
    val batteryPercent: Int?,
    val chargeStatus: ChargeStatus,
    val powerSource: PowerSource,
    val marker: TimelineMarker?,
)

/** Builds the ordered, annotated list of readings shown on the incident detail screen. */
object IncidentTimeline {

    fun build(incident: Incident, samples: List<IncidentSample>): List<TimelineEntry> {
        val ordered = samples.sortedBy { it.timestampMillis }
        if (ordered.isEmpty()) return emptyList()
        val lastIndex = ordered.lastIndex
        val peakIndex = ordered.indexOfFirst { it.timestampMillis == incident.peakMillis }

        return ordered.mapIndexed { index, sample ->
            val marker = when {
                index == 0 -> TimelineMarker.START
                index == lastIndex && !incident.isActive -> TimelineMarker.RESOLVED
                index == peakIndex -> TimelineMarker.PEAK
                else -> null
            }
            TimelineEntry(
                timestampMillis = sample.timestampMillis,
                temperatureC = sample.temperatureC,
                batteryPercent = sample.batteryPercent,
                chargeStatus = sample.chargeStatus,
                powerSource = sample.powerSource,
                marker = marker,
            )
        }
    }
}
