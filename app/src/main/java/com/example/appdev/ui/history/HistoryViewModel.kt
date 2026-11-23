package com.example.appdev.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.appdev.HeatGuardApp
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.incident.IncidentRepository
import com.example.appdev.domain.incident.IncidentTimeline
import com.example.appdev.domain.incident.TimelineEntry
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.TemperatureUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** `incidents == null` means still loading, so the empty state is never flashed prematurely. */
data class HistoryUiState(
    val incidents: List<Incident>? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** True while showing simulated demo incidents. */
    val isDemo: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: IncidentRepository,
    settingsRepository: SettingsRepository,
    private val activeSource: StateFlow<ReadingSource>,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        activeSource.flatMapLatest { repository.observeIncidents(it) },
        settingsRepository.settings.map { it.unit },
        activeSource,
    ) { incidents, unit, source -> HistoryUiState(incidents, unit, isDemo = source == ReadingSource.DEMO) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** Clears the incidents currently shown: real history normally, demo data while in Demo Mode. */
    fun clearHistory() {
        viewModelScope.launch { repository.deleteAll(activeSource.value) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as HeatGuardApp).container
                HistoryViewModel(c.incidentRepository, c.settingsRepository, c.demoController.activeSource)
            }
        }
    }
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(
        val incident: Incident,
        val timeline: List<TimelineEntry>,
        val unit: TemperatureUnit,
    ) : DetailUiState
}

class IncidentDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: IncidentRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val incidentId: Long = savedStateHandle.get<Long>(ARG_ID) ?: -1L

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeIncident(incidentId),
        repository.observeSamples(incidentId),
        settingsRepository.settings.map { it.unit },
    ) { incident, samples, unit ->
        if (incident == null) {
            DetailUiState.NotFound
        } else {
            DetailUiState.Loaded(incident, IncidentTimeline.build(incident, samples), unit)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    companion object {
        const val ARG_ID = "id"

        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as HeatGuardApp).container
                IncidentDetailViewModel(createSavedStateHandle(), c.incidentRepository, c.settingsRepository)
            }
        }
    }
}
