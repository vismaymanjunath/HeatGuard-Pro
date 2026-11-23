package com.example.appdev.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.appdev.HeatGuardApp
import com.example.appdev.data.ThermalRepository
import com.example.appdev.data.ai.AssistantController
import com.example.appdev.data.ai.AssistantPhase
import com.example.appdev.data.ai.KeyResult
import com.example.appdev.domain.ai.ChatMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val phase: AssistantPhase = AssistantPhase.IDLE,
    /** Key saved and consent given. */
    val ready: Boolean = false,
    val hasKey: Boolean = false,
    val consent: Boolean = false,
    val includePhoneData: Boolean = true,
) {
    val isGenerating: Boolean get() = phase != AssistantPhase.IDLE
}

class AssistantViewModel(
    private val controller: AssistantController,
    thermalRepository: ThermalRepository,
) : ViewModel() {

    val uiState: StateFlow<AssistantUiState> = combine(
        controller.state,
        controller.ready,
        controller.hasKey,
        controller.settings,
    ) { chat, ready, hasKey, settings ->
        AssistantUiState(chat.messages, chat.phase, ready, hasKey, settings.consentGiven, settings.includePhoneData)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

    init {
        // While the chat is open, keep the live battery and Android thermal feeds running so every
        // question is answered from fresh readings rather than whatever was last cached.
        viewModelScope.launch { thermalRepository.latestSnapshot.collect { } }
        viewModelScope.launch { thermalRepository.systemState.collect { } }
    }

    fun send(text: String, screen: String?) = controller.send(text, screen)

    fun retry(screen: String?) = controller.retry(screen)

    fun stop() = controller.stop()

    fun clear() = controller.clear()

    /**
     * Finishes setup. Returns false (and changes nothing) if a key was typed but is not shaped like a
     * Groq key, so the screen can show the problem straight away.
     */
    fun completeSetup(rawKey: String, includePhoneData: Boolean): Boolean {
        if (rawKey.isNotBlank() && controller.saveKey(rawKey) != KeyResult.SAVED) return false
        viewModelScope.launch {
            controller.setIncludePhoneData(includePhoneData)
            controller.setConsent(true)
        }
        return true
    }

    suspend fun preview(screen: String?): String = controller.previewContext(screen)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as HeatGuardApp).container
                AssistantViewModel(c.assistant, c.thermalRepository)
            }
        }
    }
}

/** Small view model for the AI card in Settings. */
class AssistantSettingsViewModel(private val controller: AssistantController) : ViewModel() {

    val uiState: StateFlow<AssistantUiState> = combine(
        controller.state, controller.ready, controller.hasKey, controller.settings,
    ) { chat, ready, hasKey, settings ->
        AssistantUiState(chat.messages, chat.phase, ready, hasKey, settings.consentGiven, settings.includePhoneData)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

    fun setIncludePhoneData(include: Boolean) {
        viewModelScope.launch { controller.setIncludePhoneData(include) }
    }

    /** Forgets the key, withdraws consent and clears the chat. */
    fun removeEverything() {
        controller.removeKey()
        controller.clear()
        viewModelScope.launch { controller.setConsent(false) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                AssistantSettingsViewModel((this[APPLICATION_KEY] as HeatGuardApp).container.assistant)
            }
        }
    }
}
