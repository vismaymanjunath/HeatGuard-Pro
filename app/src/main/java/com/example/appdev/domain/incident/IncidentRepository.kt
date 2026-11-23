package com.example.appdev.domain.incident

import com.example.appdev.domain.model.ReadingSource
import kotlinx.coroutines.flow.Flow

/** Storage for incidents. Real and demo data are separated by [ReadingSource] on every query. */
interface IncidentRepository {
    /** Inserts a new incident and returns its generated id. */
    suspend fun start(incident: Incident): Long
    suspend fun update(incident: Incident)
    suspend fun addSample(sample: IncidentSample)
    suspend fun getActive(source: ReadingSource): Incident?

    fun observeIncidents(source: ReadingSource): Flow<List<Incident>>
    fun observeIncident(id: Long): Flow<Incident?>
    fun observeSamples(incidentId: Long): Flow<List<IncidentSample>>

    suspend fun deleteAll(source: ReadingSource)
}
