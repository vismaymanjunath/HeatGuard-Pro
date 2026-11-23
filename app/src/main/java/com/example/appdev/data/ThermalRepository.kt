package com.example.appdev.data

import com.example.appdev.data.battery.BatterySnapshotSource
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.data.thermal.NoThermalSystemSource
import com.example.appdev.data.thermal.ThermalSystemSource
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.thermal.SessionStats
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.TemperatureHistory
import com.example.appdev.domain.thermal.TemperaturePoint
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalAssessment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The single processing pipeline for temperature readings. Every consumer (UI now; alerts,
 * incidents, widget and demo mode in later milestones) reads from here so behaviour is identical.
 */
class ThermalRepository(
    source: BatterySnapshotSource,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    systemSource: ThermalSystemSource = NoThermalSystemSource,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
) {
    private val _sessionStats = MutableStateFlow(SessionStats())
    private val _history = MutableStateFlow<List<TemperaturePoint>>(emptyList())
    private var lastSource: ReadingSource? = null

    /** Statistics accumulated for the lifetime of this process. */
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    /** Rolling 30-minute window of valid readings observed in this process. */
    val history: StateFlow<List<TemperaturePoint>> = _history.asStateFlow()

    /** Latest reading; null until the first broadcast arrives. */
    val latestSnapshot: StateFlow<BatterySnapshot?> = source.snapshots()
        .onEach { snapshot ->
            // Real and demo readings must never share a graph or statistics, so a change of
            // source (entering or leaving Demo Mode) starts from a clean slate.
            if (lastSource != null && lastSource != snapshot.source) {
                _history.value = emptyList()
                _sessionStats.value = SessionStats()
            }
            lastSource = snapshot.source
            _sessionStats.update { it.record(snapshot) }
            snapshot.temperatureC?.takeIf { it.isFinite() }?.let { t ->
                _history.update { TemperatureHistory.append(it, TemperaturePoint(snapshot.timestampMillis, t)) }
            }
        }
        .stateIn(scope, sharingStarted, initialValue = null)

    /** What Android's own thermal APIs report; fields are null where unsupported. */
    val systemState: StateFlow<SystemThermalState> = systemSource.state()
        .stateIn(scope, sharingStarted, SystemThermalState.Unknown)

    val assessment: Flow<ThermalAssessment> =
        combine(latestSnapshot, settingsRepository.settings, ThermalAssessment::evaluate)

    /** Prediction and risk index derived from the latest reading, history and system state. */
    val analysis: Flow<ThermalAnalysis> = combine(
        latestSnapshot,
        _history,
        systemState,
        settingsRepository.settings,
    ) { snapshot, history, system, settings ->
        ThermalAnalysis.analyze(snapshot, history, settings.thresholdC, system)
    }

    companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
