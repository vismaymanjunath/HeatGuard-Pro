package com.example.appdev.data.ai

import com.example.appdev.domain.ai.ApiMessage
import com.example.appdev.domain.ai.ChatException
import com.example.appdev.domain.ai.ChatFailure
import com.example.appdev.domain.ai.ChatMessage
import com.example.appdev.domain.ai.ChatRole
import com.example.appdev.domain.ai.ConversationWindow
import com.example.appdev.domain.ai.PhoneContext
import com.example.appdev.domain.ai.PromptBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

enum class AssistantPhase {
    IDLE,

    /** Request sent, no text yet. */
    THINKING,

    /** Text is arriving. */
    STREAMING,
}

data class AssistantState(
    val messages: List<ChatMessage> = emptyList(),
    val phase: AssistantPhase = AssistantPhase.IDLE,
    /** Identifies the active request, so a late fragment from a cancelled one can never be shown. */
    val generation: Int = 0,
) {
    val isGenerating: Boolean get() = phase != AssistantPhase.IDLE
}

enum class KeyResult { SAVED, INVALID_FORMAT }

/**
 * Owns the conversation. It lives for the whole process, so the chat survives navigation and screen
 * rotation, and is never written to disk. One request runs at a time; starting, stopping or clearing
 * always cancels the previous one first, and the whole exchange has a hard deadline so it cannot hang.
 */
class AssistantController(
    private val client: ChatClient,
    private val secrets: SecretStore,
    private val aiSettings: AiSettingsRepository,
    private val contextProvider: suspend (screen: String?) -> PhoneContext,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val overallTimeoutMillis: Long = OVERALL_TIMEOUT_MILLIS,
) {
    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    val hasKey: StateFlow<Boolean> get() = secrets.hasSecret
    val settings: Flow<AiSettings> = aiSettings.settings

    /** True when a key is saved and the user has agreed to how their data is used. */
    val ready: Flow<Boolean> =
        combine(secrets.hasSecret, aiSettings.settings.map { it.consentGiven }) { key, consent -> key && consent }

    private val ids = AtomicLong(1)
    private var job: Job? = null

    // ---- setup ------------------------------------------------------------------------------

    fun saveKey(raw: String): KeyResult {
        if (!ApiKeyFormat.isPlausible(raw)) return KeyResult.INVALID_FORMAT
        secrets.put(ApiKeyFormat.normalize(raw))
        return KeyResult.SAVED
    }

    fun removeKey() {
        stop()
        secrets.clear()
    }

    suspend fun setConsent(given: Boolean) = aiSettings.setConsent(given)

    suspend fun setIncludePhoneData(include: Boolean) = aiSettings.setIncludePhoneData(include)

    /** The exact brief that would accompany a question right now, for the "what the AI sees" panel. */
    suspend fun previewContext(screen: String?): String {
        if (!aiSettings.settings.first().includePhoneData) return PromptBuilder.noContextNote()
        return runCatching { PromptBuilder.contextText(contextProvider(screen)) }
            .getOrElse { PromptBuilder.noContextNote() }
    }

    // ---- conversation -----------------------------------------------------------------------

    @Synchronized
    fun send(text: String, screen: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isGenerating) return
        _state.update { it.copy(messages = it.messages + ChatMessage(ids.getAndIncrement(), ChatRole.USER, trimmed, clock())) }
        start(screen)
    }

    /** Re-asks the last question, discarding whatever failed after it. */
    @Synchronized
    fun retry(screen: String?) {
        if (_state.value.isGenerating) return
        val lastUser = _state.value.messages.indexOfLast { it.role == ChatRole.USER }
        if (lastUser < 0) return
        _state.update { it.copy(messages = it.messages.take(lastUser + 1)) }
        start(screen)
    }

    /** Stops generating. Any text already received stays on screen. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _state.update { s ->
            s.copy(
                phase = AssistantPhase.IDLE,
                generation = s.generation + 1, // invalidates anything still in flight
                messages = s.messages.map { if (it.streaming) it.copy(streaming = false) else it },
            )
        }
    }

    @Synchronized
    fun clear() {
        stop()
        _state.update { it.copy(messages = emptyList()) }
    }

    // ---- internals --------------------------------------------------------------------------

    private fun start(screen: String?) {
        job?.cancel()
        var generation = 0
        _state.update { s ->
            generation = s.generation + 1
            s.copy(generation = generation, phase = AssistantPhase.THINKING)
        }
        val assistantId = ids.getAndIncrement()
        val gen = generation

        job = scope.launch {
            var failure: ChatFailure? = null
            try {
                val key = secrets.get()
                val prefs = aiSettings.settings.first()
                if (key == null || !prefs.consentGiven) throw ChatException(ChatFailure.NotConfigured)

                val brief = if (prefs.includePhoneData) {
                    runCatching { PromptBuilder.contextText(contextProvider(screen)) }
                        .getOrElse { PromptBuilder.noContextNote() }
                } else {
                    PromptBuilder.noContextNote()
                }
                val payload = listOf(ApiMessage("system", PromptBuilder.systemPrompt() + "\n\n" + brief)) +
                    ConversationWindow.select(_state.value.messages)

                val finished = withTimeoutOrNull(overallTimeoutMillis) {
                    client.stream(key, payload).collect { fragment -> append(gen, assistantId, fragment) }
                    true
                }
                if (finished == null) failure = ChatFailure.Timeout
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChatException) {
                failure = e.failure
            } catch (e: Exception) {
                failure = ChatFailure.Unknown
            }
            finish(gen, assistantId, failure)
        }
    }

    private fun append(generation: Int, assistantId: Long, fragment: String) {
        _state.update { s ->
            if (s.generation != generation) return@update s
            val last = s.messages.lastOrNull()
            val messages = if (last != null && last.id == assistantId) {
                s.messages.dropLast(1) + last.copy(text = last.text + fragment, streaming = true)
            } else {
                s.messages + ChatMessage(assistantId, ChatRole.ASSISTANT, fragment, clock(), streaming = true)
            }
            s.copy(messages = messages, phase = AssistantPhase.STREAMING)
        }
    }

    private fun finish(generation: Int, assistantId: Long, failure: ChatFailure?) {
        _state.update { s ->
            if (s.generation != generation) return@update s
            val last = s.messages.lastOrNull()
            val messages = when {
                last != null && last.id == assistantId -> s.messages.dropLast(1) + last.copy(streaming = false, failure = failure)
                failure != null -> s.messages + ChatMessage(assistantId, ChatRole.ASSISTANT, "", clock(), failure = failure)
                else -> s.messages
            }
            s.copy(messages = messages, phase = AssistantPhase.IDLE)
        }
    }

    companion object {
        /** No matter what the network does, a question is answered or failed within this time. */
        const val OVERALL_TIMEOUT_MILLIS = 120_000L
    }
}
