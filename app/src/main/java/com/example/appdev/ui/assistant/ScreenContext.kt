package com.example.appdev.ui.assistant

import androidx.annotation.StringRes
import com.example.appdev.R

/** Where the user was when they opened the assistant, so it can answer in context. */
object ScreenContext {
    const val ASSISTANT_ROUTE = "assistant"

    /** The English screen name given to the model. */
    fun label(route: String?): String? = when {
        route == null -> null
        route == "dashboard" -> "Dashboard"
        route == "analytics" -> "Live Analytics"
        route == "coach" -> "Cooling Coach"
        route == "history" -> "Incident History"
        route.startsWith("incident") -> "Incident details"
        route == "insights" -> "Insights"
        route == "settings" -> "Settings"
        route == "demo" -> "Demo controls"
        else -> null
    }

    data class Suggestion(@StringRes val label: Int, @StringRes val prompt: Int)

    private val healthCheck = Suggestion(R.string.ai_s_health_label, R.string.ai_s_health_prompt)

    /** Starter questions that make sense for the screen the user came from. */
    fun suggestions(route: String?): List<Suggestion> = when {
        route == "dashboard" -> listOf(
            healthCheck,
            Suggestion(R.string.ai_s_why_warm_label, R.string.ai_s_why_warm_prompt),
            Suggestion(R.string.ai_s_call_label, R.string.ai_s_call_prompt),
        )
        route == "analytics" -> listOf(
            Suggestion(R.string.ai_s_graph_label, R.string.ai_s_graph_prompt),
            Suggestion(R.string.ai_s_trend_label, R.string.ai_s_trend_prompt),
            healthCheck,
        )
        route == "coach" -> listOf(
            Suggestion(R.string.ai_s_plan_label, R.string.ai_s_plan_prompt),
            Suggestion(R.string.ai_s_fastest_label, R.string.ai_s_fastest_prompt),
            healthCheck,
        )
        route == "history" || route?.startsWith("incident") == true -> listOf(
            Suggestion(R.string.ai_s_last_incident_label, R.string.ai_s_last_incident_prompt),
            Suggestion(R.string.ai_s_patterns_label, R.string.ai_s_patterns_prompt),
            healthCheck,
        )
        route == "insights" -> listOf(
            Suggestion(R.string.ai_s_week_label, R.string.ai_s_week_prompt),
            Suggestion(R.string.ai_s_charging_label, R.string.ai_s_charging_prompt),
            healthCheck,
        )
        route == "settings" -> listOf(
            Suggestion(R.string.ai_s_threshold_label, R.string.ai_s_threshold_prompt),
            Suggestion(R.string.ai_s_profile_label, R.string.ai_s_profile_prompt),
            healthCheck,
        )
        else -> listOf(healthCheck, Suggestion(R.string.ai_s_why_warm_label, R.string.ai_s_why_warm_prompt))
    }
}
