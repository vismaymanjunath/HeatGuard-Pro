package com.example.appdev.domain.incident

import com.example.appdev.domain.alert.AlertTransition
import com.example.appdev.domain.alert.OverheatDetector
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.UserSettings

sealed interface TrackerEvent {
    /** A new overheating incident has just begun; the user should be alerted. */
    data class Triggered(val incident: Incident) : TrackerEvent

    /** The temperature has recovered past the hysteresis level; the incident is closed. */
    data class Resolved(val incident: Incident) : TrackerEvent
}

/**
 * Turns a stream of readings into persisted incidents. Only readings from [source] are
 * processed, so the same class can serve real and demo data without mixing them.
 *
 * The tracker is not thread-safe; feed it from a single coroutine.
 */
class IncidentTracker(
    private val repository: IncidentRepository,
    private val source: ReadingSource = ReadingSource.DEVICE,
    private val sampleIntervalMillis: Long = DEFAULT_SAMPLE_INTERVAL_MILLIS,
    private val maxGapMillis: Long = DEFAULT_MAX_GAP_MILLIS,
) {
    private var detector = OverheatDetector()
    private var current: Incident? = null
    private var restored = false
    private var lastSampleWriteMillis = 0L

    /** Forgets any in-memory state, e.g. after the stored incidents were deleted. */
    fun reset() {
        detector = OverheatDetector()
        current = null
        restored = false
        lastSampleWriteMillis = 0L
    }

    suspend fun onReading(snapshot: BatterySnapshot, settings: UserSettings): TrackerEvent? {
        if (snapshot.source != source) return null
        val temperature = snapshot.temperatureC?.takeIf { it.isFinite() } ?: return null

        if (!restored) {
            restore(snapshot, temperature, settings)
            restored = true
        }

        return when (detector.onReading(temperature, settings.thresholdC, settings.hysteresisC)) {
            AlertTransition.TRIGGERED -> trigger(snapshot, temperature, settings)
            AlertTransition.RESOLVED -> resolve(snapshot, temperature)
            null -> {
                track(snapshot, temperature)
                null
            }
        }
    }

    /**
     * An incident may still be open in storage if the process was killed mid-incident. If we have
     * been away longer than [maxGapMillis] and the phone has since cooled, close it at the last
     * time we actually saw it hot rather than inflating its duration. Otherwise resume it.
     */
    private suspend fun restore(snapshot: BatterySnapshot, temperature: Float, settings: UserSettings) {
        val open = repository.getActive(source) ?: return
        val clearLevel = settings.thresholdC - settings.hysteresisC.coerceAtLeast(0f)
        val gap = snapshot.timestampMillis - open.lastSeenMillis
        if (gap > maxGapMillis && temperature <= clearLevel) {
            repository.update(open.copy(endMillis = open.lastSeenMillis, endTempC = null))
            return
        }
        current = open
        detector.resume()
    }

    private suspend fun trigger(snapshot: BatterySnapshot, temperature: Float, settings: UserSettings): TrackerEvent {
        val ts = snapshot.timestampMillis
        val draft = Incident(
            source = source,
            startMillis = ts,
            endMillis = null,
            lastSeenMillis = ts,
            initialTempC = temperature,
            peakTempC = temperature,
            peakMillis = ts,
            endTempC = null,
            thresholdC = settings.thresholdC,
            startBatteryPercent = snapshot.levelPercent,
            endBatteryPercent = null,
            startChargeStatus = snapshot.chargeStatus,
            startPowerSource = snapshot.powerSource,
        )
        val saved = draft.copy(id = repository.start(draft))
        repository.addSample(sampleOf(saved.id, snapshot, temperature))
        current = saved
        lastSampleWriteMillis = ts
        return TrackerEvent.Triggered(saved)
    }

    private suspend fun track(snapshot: BatterySnapshot, temperature: Float) {
        val incident = current ?: return
        val ts = snapshot.timestampMillis
        val newPeak = temperature > incident.peakTempC
        val sampleDue = ts - lastSampleWriteMillis >= sampleIntervalMillis
        if (!newPeak && !sampleDue) return

        val updated = incident.copy(
            lastSeenMillis = ts,
            peakTempC = if (newPeak) temperature else incident.peakTempC,
            peakMillis = if (newPeak) ts else incident.peakMillis,
        )
        repository.update(updated)
        repository.addSample(sampleOf(updated.id, snapshot, temperature))
        lastSampleWriteMillis = ts
        current = updated
    }

    private suspend fun resolve(snapshot: BatterySnapshot, temperature: Float): TrackerEvent? {
        val incident = current ?: return null
        val ts = snapshot.timestampMillis
        val resolved = incident.copy(
            endMillis = ts,
            lastSeenMillis = ts,
            endTempC = temperature,
            endBatteryPercent = snapshot.levelPercent,
        )
        repository.update(resolved)
        repository.addSample(sampleOf(resolved.id, snapshot, temperature))
        current = null
        return TrackerEvent.Resolved(resolved)
    }

    private fun sampleOf(incidentId: Long, snapshot: BatterySnapshot, temperature: Float) = IncidentSample(
        incidentId = incidentId,
        timestampMillis = snapshot.timestampMillis,
        temperatureC = temperature,
        batteryPercent = snapshot.levelPercent,
        chargeStatus = snapshot.chargeStatus,
        powerSource = snapshot.powerSource,
    )

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MILLIS = 10_000L
        const val DEFAULT_MAX_GAP_MILLIS = 120_000L
    }
}
