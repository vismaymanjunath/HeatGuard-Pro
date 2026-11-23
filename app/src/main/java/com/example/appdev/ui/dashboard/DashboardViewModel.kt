package com.example.appdev.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.appdev.HeatGuardApp
import com.example.appdev.data.ThermalRepository
import com.example.appdev.data.cooling.PersonalCoolingProfileProvider
import com.example.appdev.domain.cooling.PersonalCoolingProfile
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.readiness.PreCallReadiness
import com.example.appdev.domain.readiness.ReadinessEvaluator
import com.example.appdev.domain.thermal.SessionStats
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalAssessment
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.platform.MonitoringController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val assessment: ThermalAssessment = ThermalAssessment.evaluate(null, UserSettings()),
    val session: SessionStats = SessionStats(),
    val analysis: ThermalAnalysis = ThermalAnalysis.Empty,
    val system: SystemThermalState = SystemThermalState.Unknown,
    val profile: PersonalCoolingProfile = PersonalCoolingProfile.Empty,
    val readiness: PreCallReadiness = ReadinessEvaluator.evaluate(
        UserSettings(), ThermalAnalysis.Empty, SystemThermalState.Unknown, PersonalCoolingProfile.Empty, null,
    ),
    /** The user's saved on/off preference. */
    val monitoringWanted: Boolean = false,
    /** Whether the monitoring service is actually alive right now. */
    val monitoringRunning: Boolean = false,
    val monitoringStartFailed: Boolean = false,
) {
    /** True until the first battery broadcast has been received. */
    val isWaitingForFirstReading: Boolean get() = assessment.snapshot == null
}

class DashboardViewModel(
    repository: ThermalRepository,
    private val settingsRepository: SettingsRepository,
    private val controller: MonitoringController,
    profileProvider: PersonalCoolingProfileProvider,
) : ViewModel() {

    private val startFailed = MutableStateFlow(false)

    private class Thermal(
        val assessment: ThermalAssessment,
        val analysis: ThermalAnalysis,
        val system: SystemThermalState,
        val profile: PersonalCoolingProfile,
    )

    private val thermal = combine(
        repository.assessment,
        repository.analysis,
        repository.systemState,
        profileProvider.profile,
    ) { assessment, analysis, system, profile -> Thermal(assessment, analysis, system, profile) }

    val uiState: StateFlow<DashboardUiState> = combine(
        thermal,
        repository.sessionStats,
        controller.isRunning,
        startFailed,
    ) { t, session, running, failed ->
        val assessment = t.assessment
        DashboardUiState(
            assessment = assessment,
            session = session,
            analysis = t.analysis,
            system = t.system,
            profile = t.profile,
            // Readiness reuses the same readings, trend, Android status and history as the rest of the app.
            readiness = ReadinessEvaluator.evaluate(assessment.settings, t.analysis, t.system, t.profile, assessment.temperatureC),
            monitoringWanted = assessment.settings.monitoringEnabled,
            monitoringRunning = running,
            monitoringStartFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setProfile(profile: com.example.appdev.domain.profile.ProfileType?) {
        viewModelScope.launch { settingsRepository.setActiveProfile(profile) }
    }

    fun setMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            startFailed.value = false
            if (enabled) {
                settingsRepository.setMonitoringEnabled(true)
                if (!controller.start()) {
                    settingsRepository.setMonitoringEnabled(false)
                    startFailed.value = true
                }
            } else {
                settingsRepository.setMonitoringEnabled(false)
                controller.stop()
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HeatGuardApp
                DashboardViewModel(
                    app.container.thermalRepository,
                    app.container.settingsRepository,
                    app.container.monitoringController,
                    app.container.personalCoolingProfile,
                )
            }
        }
    }
}
