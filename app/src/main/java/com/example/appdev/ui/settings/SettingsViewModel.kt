package com.example.appdev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.appdev.HeatGuardApp
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val demoMode: com.example.appdev.data.demo.DemoModeManager,
) : ViewModel() {

    val demoEnabled: StateFlow<Boolean> = demoMode.controller.enabled

    fun setDemoEnabled(on: Boolean) = demoMode.setEnabled(on)


    /** Null until the stored settings have been loaded, so controls never flash default values. */
    val settings: StateFlow<UserSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setThreshold(celsius: Float) {
        viewModelScope.launch { repository.setThreshold(celsius) }
    }

    fun setWarningMargin(celsius: Float) {
        viewModelScope.launch { repository.setWarningMargin(celsius) }
    }

    fun setHysteresis(celsius: Float) {
        viewModelScope.launch { repository.setHysteresis(celsius) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setVibrationEnabled(enabled) }
    }

    fun setProfile(profile: com.example.appdev.domain.profile.ProfileType?) {
        viewModelScope.launch { repository.setActiveProfile(profile) }
    }

    fun setUnit(unit: TemperatureUnit) {
        viewModelScope.launch { repository.setUnit(unit) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { repository.resetToDefaults() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HeatGuardApp
                SettingsViewModel(app.container.settingsRepository, app.container.demoMode)
            }
        }
    }
}
