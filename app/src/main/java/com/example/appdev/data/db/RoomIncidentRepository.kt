package com.example.appdev.data.db

import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.incident.IncidentRepository
import com.example.appdev.domain.incident.IncidentSample
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomIncidentRepository(private val dao: IncidentDao) : IncidentRepository {

    override suspend fun start(incident: Incident): Long = dao.insertIncident(incident.toEntity())

    override suspend fun update(incident: Incident) = dao.updateIncident(incident.toEntity())

    override suspend fun addSample(sample: IncidentSample) = dao.insertSample(sample.toEntity())

    override suspend fun getActive(source: ReadingSource): Incident? = dao.getActive(source.name)?.toDomain()

    override fun observeIncidents(source: ReadingSource): Flow<List<Incident>> =
        dao.observeIncidents(source.name).map { list -> list.map { it.toDomain() } }

    override fun observeIncident(id: Long): Flow<Incident?> = dao.observeIncident(id).map { it?.toDomain() }

    override fun observeSamples(incidentId: Long): Flow<List<IncidentSample>> =
        dao.observeSamples(incidentId).map { list -> list.map { it.toDomain() } }

    override suspend fun deleteAll(source: ReadingSource) = dao.deleteAll(source.name)
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

internal fun Incident.toEntity() = IncidentEntity(
    id = id,
    source = source.name,
    startMillis = startMillis,
    endMillis = endMillis,
    lastSeenMillis = lastSeenMillis,
    initialTempC = initialTempC,
    peakTempC = peakTempC,
    peakMillis = peakMillis,
    endTempC = endTempC,
    thresholdC = thresholdC,
    startBatteryPercent = startBatteryPercent,
    endBatteryPercent = endBatteryPercent,
    startChargeStatus = startChargeStatus.name,
    startPowerSource = startPowerSource.name,
)

internal fun IncidentEntity.toDomain() = Incident(
    id = id,
    source = enumOrDefault(source, ReadingSource.DEVICE),
    startMillis = startMillis,
    endMillis = endMillis,
    lastSeenMillis = lastSeenMillis,
    initialTempC = initialTempC,
    peakTempC = peakTempC,
    peakMillis = peakMillis,
    endTempC = endTempC,
    thresholdC = thresholdC,
    startBatteryPercent = startBatteryPercent,
    endBatteryPercent = endBatteryPercent,
    startChargeStatus = enumOrDefault(startChargeStatus, ChargeStatus.UNKNOWN),
    startPowerSource = enumOrDefault(startPowerSource, PowerSource.UNKNOWN),
)

internal fun IncidentSample.toEntity() = IncidentSampleEntity(
    incidentId = incidentId,
    timestampMillis = timestampMillis,
    temperatureC = temperatureC,
    batteryPercent = batteryPercent,
    chargeStatus = chargeStatus.name,
    powerSource = powerSource.name,
)

internal fun IncidentSampleEntity.toDomain() = IncidentSample(
    incidentId = incidentId,
    timestampMillis = timestampMillis,
    temperatureC = temperatureC,
    batteryPercent = batteryPercent,
    chargeStatus = enumOrDefault(chargeStatus, ChargeStatus.UNKNOWN),
    powerSource = enumOrDefault(powerSource, PowerSource.UNKNOWN),
)
