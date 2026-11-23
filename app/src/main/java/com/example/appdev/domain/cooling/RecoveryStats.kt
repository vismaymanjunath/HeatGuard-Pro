package com.example.appdev.domain.cooling

/** Aggregates over completed cooling sessions. Only RECOVERED sessions count towards timings. */
data class RecoveryStats(
    val totalSessions: Int,
    val recoveredCount: Int,
    val stoppedCount: Int,
    val interruptedCount: Int,
    val averageRecoveryMillis: Long?,
    val fastestRecoveryMillis: Long?,
    val averageDropC: Float?,
    /** Mean cooling rate in °C per minute across recovered sessions. */
    val averageRateCPerMin: Float?,
) {
    companion object {
        fun compute(sessions: List<CoolingSession>): RecoveryStats {
            val finished = sessions.filter { !it.isActive }
            val recovered = finished.filter { it.outcome == SessionOutcome.RECOVERED }
            val durations = recovered.map { it.durationMillis }

            val rates = recovered.mapNotNull { s ->
                val minutes = s.durationMillis / 60_000f
                if (minutes > 0f) s.dropC / minutes else null
            }

            return RecoveryStats(
                totalSessions = finished.size,
                recoveredCount = recovered.size,
                stoppedCount = finished.count { it.outcome == SessionOutcome.STOPPED },
                interruptedCount = finished.count { it.outcome == SessionOutcome.INTERRUPTED },
                averageRecoveryMillis = durations.takeIf { it.isNotEmpty() }?.average()?.toLong(),
                fastestRecoveryMillis = durations.minOrNull(),
                averageDropC = recovered.takeIf { it.isNotEmpty() }?.map { it.dropC }?.average()?.toFloat(),
                averageRateCPerMin = rates.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            )
        }
    }
}
