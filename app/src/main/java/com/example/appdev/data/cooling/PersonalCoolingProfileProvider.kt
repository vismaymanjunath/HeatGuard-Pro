package com.example.appdev.data.cooling

import com.example.appdev.domain.cooling.CoolingRepository
import com.example.appdev.domain.cooling.PersonalCoolingProfile
import com.example.appdev.domain.cooling.PersonalCoolingProfileCalculator
import com.example.appdev.domain.incident.IncidentRepository
import com.example.appdev.domain.model.ReadingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn

/**
 * The user's personal cooling profile, derived live from the cooling sessions and incidents already
 * stored in Room, so it updates the moment a recovery finishes and survives restarts. It follows the
 * active data source: real history normally, demo data (clearly separate) while Demo Mode is on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonalCoolingProfileProvider(
    incidents: IncidentRepository,
    sessions: CoolingRepository,
    activeSource: StateFlow<ReadingSource>,
) {
    val profile: Flow<PersonalCoolingProfile> = activeSource
        .flatMapLatest { source ->
            combine(incidents.observeIncidents(source), sessions.observeSessions(source)) { incidentList, sessionList ->
                PersonalCoolingProfileCalculator.compute(sessionList, incidentList)
            }
        }
        .flowOn(Dispatchers.Default)
}
