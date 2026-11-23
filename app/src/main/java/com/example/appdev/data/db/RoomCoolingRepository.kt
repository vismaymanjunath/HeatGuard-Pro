package com.example.appdev.data.db

import com.example.appdev.domain.cooling.CoolingRepository
import com.example.appdev.domain.cooling.CoolingSample
import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.domain.cooling.SessionOutcome
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCoolingRepository(private val dao: CoolingDao) : CoolingRepository {

    override suspend fun start(session: CoolingSession): Long = dao.insertSession(session.toEntity())

    override suspend fun update(session: CoolingSession) = dao.updateSession(session.toEntity())

    override suspend fun addSample(sample: CoolingSample) = dao.insertSample(sample.toEntity())

    override suspend fun getActive(source: ReadingSource): CoolingSession? = dao.getActive(source.name)?.toDomain()

    override fun observeSessions(source: ReadingSource): Flow<List<CoolingSession>> =
        dao.observeSessions(source.name).map { list -> list.map { it.toDomain() } }

    override fun observeSamples(sessionId: Long): Flow<List<CoolingSample>> =
        dao.observeSamples(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun getSamples(sessionId: Long): List<CoolingSample> =
        dao.getSamples(sessionId).map { it.toDomain() }

    override suspend fun deleteAll(source: ReadingSource) = dao.deleteAll(source.name)
}

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

internal fun CoolingSession.toEntity() = CoolingSessionEntity(
    id = id,
    source = source.name,
    startMillis = startMillis,
    endMillis = endMillis,
    lastSeenMillis = lastSeenMillis,
    startTempC = startTempC,
    lastTempC = lastTempC,
    minTempC = minTempC,
    targetTempC = targetTempC,
    startBatteryPercent = startBatteryPercent,
    startChargeStatus = startChargeStatus.name,
    startPowerSource = startPowerSource.name,
    outcome = outcome?.name,
)

internal fun CoolingSessionEntity.toDomain() = CoolingSession(
    id = id,
    source = enumOrNull<ReadingSource>(source) ?: ReadingSource.DEVICE,
    startMillis = startMillis,
    endMillis = endMillis,
    lastSeenMillis = lastSeenMillis,
    startTempC = startTempC,
    lastTempC = lastTempC,
    minTempC = minTempC,
    targetTempC = targetTempC,
    startBatteryPercent = startBatteryPercent,
    startChargeStatus = enumOrNull<ChargeStatus>(startChargeStatus) ?: ChargeStatus.UNKNOWN,
    startPowerSource = enumOrNull<PowerSource>(startPowerSource) ?: PowerSource.UNKNOWN,
    // An unrecognised stored outcome is treated as interrupted rather than "still running".
    outcome = outcome?.let { enumOrNull<SessionOutcome>(it) ?: SessionOutcome.INTERRUPTED },
)

internal fun CoolingSample.toEntity() = CoolingSampleEntity(
    sessionId = sessionId,
    timestampMillis = timestampMillis,
    temperatureC = temperatureC,
)

internal fun CoolingSampleEntity.toDomain() = CoolingSample(sessionId, timestampMillis, temperatureC)
