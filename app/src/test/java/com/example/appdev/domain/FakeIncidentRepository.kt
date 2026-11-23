package com.example.appdev.domain

import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.incident.IncidentRepository
import com.example.appdev.domain.incident.IncidentSample
import com.example.appdev.domain.model.ReadingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeIncidentRepository : IncidentRepository {
    private val incidents = MutableStateFlow<List<Incident>>(emptyList())
    private val samples = MutableStateFlow<List<IncidentSample>>(emptyList())
    private var nextId = 1L

    val allIncidents: List<Incident> get() = incidents.value
    val allSamples: List<IncidentSample> get() = samples.value

    override suspend fun start(incident: Incident): Long {
        val id = nextId++
        incidents.value = incidents.value + incident.copy(id = id)
        return id
    }

    override suspend fun update(incident: Incident) {
        incidents.value = incidents.value.map { if (it.id == incident.id) incident else it }
    }

    override suspend fun addSample(sample: IncidentSample) {
        samples.value = samples.value + sample
    }

    override suspend fun getActive(source: ReadingSource): Incident? =
        incidents.value.filter { it.source == source && it.endMillis == null }.maxByOrNull { it.startMillis }

    override fun observeIncidents(source: ReadingSource): Flow<List<Incident>> =
        incidents.map { list -> list.filter { it.source == source }.sortedByDescending { it.startMillis } }

    override fun observeIncident(id: Long): Flow<Incident?> = incidents.map { list -> list.firstOrNull { it.id == id } }

    override fun observeSamples(incidentId: Long): Flow<List<IncidentSample>> =
        samples.map { list -> list.filter { it.incidentId == incidentId } }

    override suspend fun deleteAll(source: ReadingSource) {
        val ids = incidents.value.filter { it.source == source }.map { it.id }.toSet()
        incidents.value = incidents.value.filterNot { it.id in ids }
        samples.value = samples.value.filterNot { it.incidentId in ids }
    }
}
