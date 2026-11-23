package com.example.appdev.domain.ai

enum class ChatRole(val apiName: String) { USER("user"), ASSISTANT("assistant") }

/** Why a chat request did not produce an answer. Each maps to a plain-language message in the UI. */
sealed interface ChatFailure {
    /** No API key saved, or the user has not agreed to use the assistant. */
    data object NotConfigured : ChatFailure
    data object InvalidKey : ChatFailure
    data class RateLimited(val retryAfterSeconds: Int?) : ChatFailure
    data object Offline : ChatFailure
    data object Timeout : ChatFailure
    data class Server(val code: Int) : ChatFailure
    data class Rejected(val message: String) : ChatFailure
    data object EmptyReply : ChatFailure
    data object Unknown : ChatFailure
}

class ChatException(val failure: ChatFailure, cause: Throwable? = null) : Exception(failure.toString(), cause)

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    /** Device clock time the message was created. */
    val timestampMillis: Long,
    /** Set on an assistant message whose request failed; the UI offers a retry. */
    val failure: ChatFailure? = null,
    val streaming: Boolean = false,
)

/** A message in the wire format. */
data class ApiMessage(val role: String, val content: String)
