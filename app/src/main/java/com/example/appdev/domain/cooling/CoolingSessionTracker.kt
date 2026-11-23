package com.example.appdev.domain.cooling

import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ReadingSource

sealed interface StartResult {
    data class Started(val session: CoolingSession) : StartResult

    /** The battery is already at or below the recovery target, so there is nothing to recover from. */
    data object AlreadyCool : StartResult

    /** No valid temperature reading is available yet. */
    data object NoReading : StartResult

    data object AlreadyActive : StartResult
}

sealed interface CoolingEvent {
    data class Recovered(val session: CoolingSession) : CoolingEvent
    data class Interrupted(val session: CoolingSession) : CoolingEvent
}

/**
 * Drives one cooling session at a time. Only readings from [source] are processed, so real and
 * demo data stay separate. Not thread-safe; use from a single coroutine.
 */
class CoolingSessionTracker(
    private val repository: CoolingRepository,
    private val source: ReadingSource = ReadingSource.DEVICE,
    private val sampleIntervalMillis: Long = DEFAULT_SAMPLE_INTERVAL_MILLIS,
    private val maxGapMillis: Long = DEFAULT_MAX_GAP_MILLIS,
) {
    private var current: CoolingSession? = null
    private var lastSampleWriteMillis = 0L

    val active: CoolingSession? get() = current

    /** Forgets any in-memory state, e.g. after the stored sessions were deleted. */
    fun reset() {
        current = null
        lastSampleWriteMillis = 0L
    }

    /** Loads a session left open by a previous process, if any. */
    suspend fun resume(): CoolingSession? {
        current = repository.getActive(source)
        lastSampleWriteMillis = current?.lastSeenMillis ?: 0L
        return current
    }

    suspend fun start(snapshot: BatterySnapshot, targetC: Float): StartResult {
        if (snapshot.source != source) return StartResult.NoReading
        if (current != null) return StartResult.AlreadyActive
        val temperature = snapshot.temperatureC?.takeIf { it.isFinite() } ?: return StartResult.NoReading
        if (temperature <= targetC) return StartResult.AlreadyCool

        val ts = snapshot.timestampMillis
        val draft = CoolingSession(
            source = source,
            startMillis = ts,
            endMillis = null,
            lastSeenMillis = ts,
            startTempC = temperature,
            lastTempC = temperature,
            minTempC = temperature,
            targetTempC = targetC,
            startBatteryPercent = snapshot.levelPercent,
            startChargeStatus = snapshot.chargeStatus,
            startPowerSource = snapshot.powerSource,
            outcome = null,
        )
        val saved = draft.copy(id = repository.start(draft))
        repository.addSample(CoolingSample(saved.id, ts, temperature))
        current = saved
        lastSampleWriteMillis = ts
        return StartResult.Started(saved)
    }

    suspend fun onReading(snapshot: BatterySnapshot): CoolingEvent? {
        if (snapshot.source != source) return null
        val session = current ?: return null
        val temperature = snapshot.temperatureC?.takeIf { it.isFinite() } ?: return null
        val ts = snapshot.timestampMillis
        if (ts <= session.lastSeenMillis) return null

        // Long silence: we cannot know what happened, so close at the last reading we saw.
        if (ts - session.lastSeenMillis > maxGapMillis) {
            val closed = session.copy(endMillis = session.lastSeenMillis, outcome = SessionOutcome.INTERRUPTED)
            repository.update(closed)
            current = null
            return CoolingEvent.Interrupted(closed)
        }

        val updated = session.copy(
            lastSeenMillis = ts,
            lastTempC = temperature,
            minTempC = minOf(session.minTempC, temperature),
        )

        if (temperature <= session.targetTempC) {
            val done = updated.copy(endMillis = ts, outcome = SessionOutcome.RECOVERED)
            repository.update(done)
            repository.addSample(CoolingSample(done.id, ts, temperature))
            current = null
            return CoolingEvent.Recovered(done)
        }

        if (ts - lastSampleWriteMillis >= sampleIntervalMillis) {
            repository.update(updated)
            repository.addSample(CoolingSample(updated.id, ts, temperature))
            lastSampleWriteMillis = ts
        }
        current = updated
        return null
    }

    /** User ends the session early. Returns the closed session, or null if none was running. */
    suspend fun stop(nowMillis: Long): CoolingSession? {
        val session = current ?: return null
        val end = maxOf(nowMillis, session.lastSeenMillis)
        val closed = session.copy(endMillis = end, lastSeenMillis = end, outcome = SessionOutcome.STOPPED)
        repository.update(closed)
        repository.addSample(CoolingSample(closed.id, end, closed.lastTempC))
        current = null
        return closed
    }

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MILLIS = 5_000L
        const val DEFAULT_MAX_GAP_MILLIS = 5 * 60_000L
    }
}
