package com.example.appdev.domain.cooling

import com.example.appdev.domain.incident.Incident

/** Where a recovery measurement came from. Both are real readings recorded on this device. */
enum class RecoverySource {
    /** A cooling session the user started and that reached its target. */
    COOLING_SESSION,

    /** The cool-down between an incident's peak and its resolution. */
    INCIDENT,
}

/** One measured cool-down: how far the battery fell and how long that took. */
data class RecoveryEvent(
    val source: RecoverySource,
    val startMillis: Long,
    val durationMillis: Long,
    val fromTempC: Float,
    val toTempC: Float,
) {
    val dropC: Float get() = fromTempC - toTempC

    /** Cooling rate in °C per minute. */
    val rateCPerMin: Float get() = dropC / (durationMillis / 60_000f)
}

/**
 * The user's own cooling behaviour, derived only from stored history: recovered cooling sessions
 * and resolved incidents. Statistics are `null` until there are [MIN_EVENTS] recoveries, so
 * nothing is ever extrapolated from too little data.
 */
data class PersonalCoolingProfile(
    /** Every usable recovery, newest first. */
    val events: List<RecoveryEvent>,
    val avgRateCPerMin: Float?,
    val avgRecoveryMillis: Long?,
    val avgDropC: Float?,
    val fastestRecovery: RecoveryEvent?,
) {
    val eventCount: Int get() = events.size
    val sessionCount: Int get() = events.count { it.source == RecoverySource.COOLING_SESSION }
    val incidentCount: Int get() = events.count { it.source == RecoverySource.INCIDENT }

    /** True when enough recoveries exist for the averages to mean something. */
    val hasEnoughData: Boolean get() = eventCount >= MIN_EVENTS

    /**
     * Estimated time to cool from [currentTempC] to [targetC] at this user's average cooling rate.
     * Returns 0 when already at or below the target and null when there is not enough history.
     * An estimate of typical behaviour, not a promise.
     */
    fun estimateRecoveryMillis(currentTempC: Float, targetC: Float): Long? {
        val rate = avgRateCPerMin
        if (!hasEnoughData || rate == null || rate <= 0f) return null
        if (currentTempC <= targetC) return 0L
        return ((currentTempC - targetC) / rate * 60_000f).toLong()
    }

    companion object {
        /** Recoveries needed before averages are shown. */
        const val MIN_EVENTS = 3

        /** Shorter or smaller cool-downs are measurement noise, not recoveries. */
        const val MIN_DURATION_MILLIS = 30_000L
        const val MIN_DROP_C = 0.5f

        val Empty = PersonalCoolingProfile(emptyList(), null, null, null, null)
    }
}

object PersonalCoolingProfileCalculator {

    fun compute(sessions: List<CoolingSession>, incidents: List<Incident>): PersonalCoolingProfile {
        val events = (sessions.mapNotNull(::fromSession) + incidents.mapNotNull(::fromIncident))
            .sortedByDescending { it.startMillis }
        if (events.size < PersonalCoolingProfile.MIN_EVENTS) {
            return PersonalCoolingProfile.Empty.copy(events = events)
        }
        return PersonalCoolingProfile(
            events = events,
            avgRateCPerMin = events.map { it.rateCPerMin }.average().toFloat(),
            avgRecoveryMillis = events.map { it.durationMillis }.average().toLong(),
            avgDropC = events.map { it.dropC }.average().toFloat(),
            fastestRecovery = events.minByOrNull { it.durationMillis },
        )
    }

    /** Only sessions that actually reached their target count; stopped or interrupted ones do not. */
    private fun fromSession(s: CoolingSession): RecoveryEvent? {
        if (s.outcome != SessionOutcome.RECOVERED) return null
        return usable(RecoveryEvent(RecoverySource.COOLING_SESSION, s.startMillis, s.durationMillis, s.startTempC, s.lastTempC))
    }

    /**
     * The cool-down from an incident's peak to the moment it resolved. Incidents closed after a gap
     * (no end temperature) or still open are skipped because their end is unknown.
     */
    private fun fromIncident(i: Incident): RecoveryEvent? {
        val end = i.endMillis ?: return null
        val endTemp = i.endTempC ?: return null
        return usable(RecoveryEvent(RecoverySource.INCIDENT, i.peakMillis, end - i.peakMillis, i.peakTempC, endTemp))
    }

    private fun usable(e: RecoveryEvent): RecoveryEvent? =
        e.takeIf { it.durationMillis >= PersonalCoolingProfile.MIN_DURATION_MILLIS && it.dropC >= PersonalCoolingProfile.MIN_DROP_C }
}
