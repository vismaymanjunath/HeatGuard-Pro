package com.example.appdev.data.ai

import com.example.appdev.domain.ai.ApiMessage
import com.example.appdev.domain.ai.ChatFailure
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One parsed line of a Groq server-sent-event stream. */
sealed interface SseEvent {
    data class Delta(val text: String) : SseEvent
    data object Done : SseEvent
    data class Error(val message: String) : SseEvent
    data object Ignore : SseEvent
}

object SseParser {
    fun parse(line: String): SseEvent {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":") || !trimmed.startsWith("data:")) return SseEvent.Ignore
        val payload = trimmed.removePrefix("data:").trim()
        if (payload == "[DONE]") return SseEvent.Done
        return try {
            val root = JSONObject(payload)
            root.optJSONObject("error")?.let { return SseEvent.Error(it.optString("message", "Unknown error")) }
            val delta = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: return SseEvent.Ignore
            if (delta.isNull("content")) return SseEvent.Ignore
            val text = delta.optString("content", "")
            if (text.isEmpty()) SseEvent.Ignore else SseEvent.Delta(text)
        } catch (_: JSONException) {
            SseEvent.Ignore
        }
    }
}

/** Builds the JSON request body for the Groq chat completions endpoint. */
object GroqRequest {
    const val MODEL = "openai/gpt-oss-120b"
    const val MAX_COMPLETION_TOKENS = 1_200
    const val TEMPERATURE = 0.4

    /**
     * [lean] drops the optional reasoning parameters. It is used as a one-time fallback if the service
     * rejects them, so a change on the provider's side cannot break the assistant.
     */
    fun body(messages: List<ApiMessage>, lean: Boolean = false): String {
        val array = JSONArray()
        messages.forEach { array.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val root = JSONObject()
            .put("model", MODEL)
            .put("stream", true)
            .put("max_completion_tokens", MAX_COMPLETION_TOKENS)
            .put("temperature", TEMPERATURE)
            .put("messages", array)
        if (!lean) {
            root.put("reasoning_effort", "low") // fast answers; the questions here do not need deep reasoning
            root.put("include_reasoning", false) // never stream the model's internal reasoning to the screen
        }
        return root.toString()
    }
}

/** Turns an HTTP error from Groq into a [ChatFailure]. */
object GroqErrors {
    fun classify(code: Int, body: String?, retryAfterHeader: String?): ChatFailure = when (code) {
        401, 403 -> ChatFailure.InvalidKey
        429 -> ChatFailure.RateLimited(retryAfterHeader?.trim()?.toDoubleOrNull()?.toInt())
        408 -> ChatFailure.Timeout
        in 500..599 -> ChatFailure.Server(code)
        else -> ChatFailure.Rejected(extractMessage(body) ?: "HTTP $code")
    }

    /** Errors worth trying again: rate limits, timeouts and server-side problems. */
    fun isRetryable(code: Int): Boolean = code == 429 || code == 408 || code in listOf(500, 502, 503, 504)

    /** True when a 400 is about the optional reasoning parameters, so a lean retry may succeed. */
    fun isReasoningParameterError(code: Int, body: String?): Boolean =
        code == 400 && extractMessage(body)?.contains("reasoning", ignoreCase = true) == true

    fun extractMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (_: JSONException) {
            null
        }
    }
}
