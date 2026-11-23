package com.example.appdev.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.appdev.HeatGuardApp
import com.example.appdev.data.ThermalRepository
import com.example.appdev.data.cooling.ActiveCoolingSession
import com.example.appdev.data.cooling.CoolingSessionManager
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.domain.cooling.Advice
import com.example.appdev.domain.cooling.AdviceContext
import com.example.appdev.domain.cooling.CoolingAdvisor
import com.example.appdev.domain.cooling.CoolingRepository
import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.data.cooling.PersonalCoolingProfileProvider
import com.example.appdev.domain.cooling.PersonalCoolingProfile
import com.example.appdev.domain.cooling.RecoveryStats
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.profile.ProfileType
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalAssessment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CoachUiState(
    val assessment: ThermalAssessment = ThermalAssessment.evaluate(null, com.example.appdev.domain.model.UserSettings()),
    val advice: List<Advice> = emptyList(),
    val active: ActiveCoolingSession? = null,
    val lastResult: CoolingSession? = null,
    /** Finished sessions, newest first. */
    val recent: List<CoolingSession> = emptyList(),
    val stats: RecoveryStats = RecoveryStats.compute(emptyList()),
    val profile: PersonalCoolingProfile = PersonalCoolingProfile.Empty,
) {
    val targetC: Float get() = assessment.settings.clearBelowC

    /** Why a session cannot be started right now, or null if it can. */
    val startBlock: StartBlock?
        get() {
            val t = assessment.temperatureC ?: return StartBlock.NO_READING
            return if (t <= targetC) StartBlock.ALREADY_COOL else null
        }
}

enum class StartBlock { NO_READING, ALREADY_COOL }

@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModel(
    repository: ThermalRepository,
    private val settingsRepository: SettingsRepository,
    private val managerFor: (ReadingSource) -> CoolingSessionManager,
    coolingRepository: CoolingRepository,
    private val activeSource: StateFlow<ReadingSource>,
    profileProvider: PersonalCoolingProfileProvider,
) : ViewModel() {

    private val manager get() = managerFor(activeSource.value)

    private val thermal = combine(repository.assessment, repository.analysis, repository.systemState) { a, an, sys ->
        Triple(a, an, sys)
    }

    // Real and demo cooling sessions live in separate managers and tables; follow whichever is active.
    private val sessions = activeSource.flatMapLatest { source ->
        val m = managerFor(source)
        combine(m.active, m.lastResult, coolingRepository.observeSessions(source)) { active, last, all ->
            Triple(active, last, all)
        }
    }

    val uiState: StateFlow<CoachUiState> = combine(thermal, sessions, profileProvider.profile) { (assessment, analysis, system), (active, last, all), profile ->
        val finished = all.filter { !it.isActive }
        CoachUiState(
            assessment = assessment,
            advice = CoolingAdvisor.advise(adviceContext(assessment, analysis, system.status, active != null)),
            active = active,
            lastResult = last,
            recent = finished.take(RECENT_LIMIT),
            stats = RecoveryStats.compute(finished),
            profile = profile,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoachUiState())

    fun startSession() {
        viewModelScope.launch {
            val target = settingsRepository.settings.first().clearBelowC
            manager.start(target)
        }
    }

    fun endSession() {
        viewModelScope.launch { manager.stop() }
    }

    fun dismissResult() = manager.dismissResult()

    fun setProfile(profile: ProfileType?) {
        viewModelScope.launch { settingsRepository.setActiveProfile(profile) }
    }

    private fun adviceContext(
        assessment: ThermalAssessment,
        analysis: ThermalAnalysis,
        status: com.example.appdev.domain.thermal.ThermalStatus?,
        sessionActive: Boolean,
    ): AdviceContext {
        val snapshot = assessment.snapshot
        return AdviceContext(
            level = assessment.level,
            isCharging = snapshot?.isCharging == true,
            powerSource = snapshot?.powerSource ?: com.example.appdev.domain.model.PowerSource.UNKNOWN,
            prediction = analysis.prediction,
            riskIndex = analysis.risk?.index,
            systemStatus = status,
            profile = assessment.settings.activeProfile,
            sessionActive = sessionActive,
        )
    }

    companion object {
        private const val RECENT_LIMIT = 5

        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as HeatGuardApp).container
                CoachViewModel(c.thermalRepository, c.settingsRepository, c::coolingManager, c.coolingRepository, c.demoController.activeSource, c.personalCoolingProfile)
            }
        }
    }
}
