package com.example.appdev.domain.cooling

import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource
import kotlinx.coroutines.flow.Flow

enum class SessionOutcome {
    /** The battery reached the recovery target. */
    RECOVERED,

    /** The user ended the session before the target was reached. */
    STOPPED,

    /** Readings stopped arriving (app killed or monitoring interrupted), so the end is unknown. */
    INTERRUPTED,
}

/** A user-started period of cooling down, tracked until the battery reaches [targetTempC]. */
data class CoolingSession(
    val id: Long = 0,
    val source: ReadingSource,
    val startMillis: Long,
    val endMillis: Long?,
    val lastSeenMillis: Long,
    val startTempC: Float,
    val lastTempC: Float,
    val minTempC: Float,
    val targetTempC: Float,
    val startBatteryPercent: Int?,
    val startChargeStatus: ChargeStatus,
    val startPowerSource: PowerSource,
    /** Null while the session is running. */
    val outcome: SessionOutcome?,
) {
    val isActive: Boolean get() = outcome == null

    val durationMillis: Long get() = ((endMillis ?: lastSeenMillis) - startMillis).coerceAtLeast(0L)

    /** Degrees lost between the start and the last reading. Never negative. */
    val dropC: Float get() = (startTempC - lastTempC).coerceAtLeast(0f)

    /** 0..1 progress from the starting temperature to the target. */
    val progress: Float
        get() {
            val span = startTempC - targetTempC
            if (span <= 0f) return 1f
            return ((startTempC - lastTempC) / span).coerceIn(0f, 1f)
        }
}

data class CoolingSample(
    val sessionId: Long,
    val timestampMillis: Long,
    val temperatureC: Float,
)

interface CoolingRepository {
    suspend fun start(session: CoolingSession): Long
    suspend fun update(session: CoolingSession)
    suspend fun addSample(sample: CoolingSample)
    suspend fun getActive(source: ReadingSource): CoolingSession?

    fun observeSessions(source: ReadingSource): Flow<List<CoolingSession>>
    fun observeSamples(sessionId: Long): Flow<List<CoolingSample>>
    suspend fun getSamples(sessionId: Long): List<CoolingSample>

    suspend fun deleteAll(source: ReadingSource)
}
