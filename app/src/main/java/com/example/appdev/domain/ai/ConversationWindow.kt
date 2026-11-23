package com.example.appdev.domain.ai

/**
 * Chooses which part of the conversation is sent with each request. Keeping it bounded keeps
 * requests fast, cheap and well inside the model's limits, however long the chat becomes.
 */
object ConversationWindow {
    const val MAX_MESSAGES = 12
    const val MAX_CHARS = 8_000

    fun select(history: List<ChatMessage>): List<ApiMessage> {
        // Failed or empty assistant messages carry nothing worth sending.
        val usable = history.filter { it.failure == null && it.text.isNotBlank() }
        var window = usable.takeLast(MAX_MESSAGES)

        // Drop the oldest messages until the character budget fits, but never the latest one.
        while (window.size > 1 && window.sumOf { it.text.length } > MAX_CHARS) window = window.drop(1)

        // A conversation must begin with the user's turn.
        window = window.dropWhile { it.role != ChatRole.USER }

        return window.map { ApiMessage(it.role.apiName, it.text.take(MAX_CHARS)) }
    }
}
