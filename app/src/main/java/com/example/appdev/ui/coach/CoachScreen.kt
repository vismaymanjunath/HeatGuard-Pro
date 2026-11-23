package com.example.appdev.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appdev.R
import com.example.appdev.domain.cooling.Advice
import com.example.appdev.domain.cooling.AdvicePriority
import com.example.appdev.domain.cooling.AdviceType
import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.domain.cooling.RecoveryStats
import com.example.appdev.domain.cooling.SessionOutcome
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.ui.components.ChartReference
import com.example.appdev.ui.components.Durations
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.components.ProfileSelector
import com.example.appdev.ui.components.TemperatureChart
import com.example.appdev.ui.components.rememberDateTimeFormatter
import com.example.appdev.ui.history.StatusChip
import com.example.appdev.ui.theme.HeatColors
import kotlinx.coroutines.delay

@Composable
fun CoachRoute(viewModel: CoachViewModel = viewModel(factory = CoachViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CoachScreen(
        state = state,
        onStart = viewModel::startSession,
        onEnd = viewModel::endSession,
        onDismissResult = viewModel::dismissResult,
        onSelectProfile = viewModel::setProfile,
    )
}

@Composable
fun CoachScreen(
    state: CoachUiState,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onDismissResult: () -> Unit,
    onSelectProfile: (com.example.appdev.domain.profile.ProfileType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = state.assessment.settings.unit
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                stringResource(R.string.coach_title),
                style = MaterialTheme.typography.headlineMedium,
                color = HeatColors.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.coach_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
            )
        }

        ProfileSelector(active = state.assessment.settings.activeProfile, onSelect = onSelectProfile)

        val active = state.active
        when {
            active != null -> ActiveSessionCard(active.session, active.points, unit, onEnd)
            state.lastResult != null -> ResultCard(state.lastResult, unit, onDismissResult)
        }
        if (active == null) StartCard(state, unit, onStart)

        Text(
            stringResource(R.string.coach_recommendations),
            style = MaterialTheme.typography.titleMedium,
            color = HeatColors.TextPrimary,
        )
        state.advice.forEach { AdviceCard(it) }

        PersonalCoolingProfileCard(state.profile, state.assessment.temperatureC, state.targetC, unit)
        StatsCard(state.stats, unit)
        if (state.recent.isNotEmpty()) RecentSessionsCard(state.recent, unit)
        Spacer(Modifier.height(8.dp))
    }
}

// ------------------------------------------------------------------ session cards

@Composable
private fun StartCard(state: CoachUiState, unit: TemperatureUnit, onStart: () -> Unit) {
    val target = Temperatures.format(state.targetC, unit)
    HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(
            stringResource(R.string.session_card_title),
            style = MaterialTheme.typography.titleMedium,
            color = HeatColors.TextPrimary,
        )
        Text(
            stringResource(R.string.session_idle_body, target),
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextSecondary,
        )
        when (state.startBlock) {
            StartBlock.NO_READING -> Text(
                stringResource(R.string.session_waiting_reading),
                style = MaterialTheme.typography.bodySmall,
                color = HeatColors.Elevated,
            )
            StartBlock.ALREADY_COOL -> Text(
                stringResource(R.string.session_cannot_cool, target),
                style = MaterialTheme.typography.bodySmall,
                color = HeatColors.Normal,
            )
            null -> Unit
        }
        Button(
            onClick = onStart,
            enabled = state.startBlock == null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = HeatColors.Accent,
                contentColor = HeatColors.OnAccent,
                disabledContainerColor = HeatColors.SurfaceHighest,
                disabledContentColor = HeatColors.TextTertiary,
            ),
        ) {
            Text(stringResource(R.string.session_start))
        }
    }
}

@Composable
private fun ActiveSessionCard(
    session: CoolingSession,
    points: List<com.example.appdev.domain.thermal.TemperaturePoint>,
    unit: TemperatureUnit,
    onEnd: () -> Unit,
) {
    val elapsed by rememberElapsed(session.startMillis)
    HeatCard(Modifier.fillMaxWidth(), spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.session_card_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = HeatColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            StatusChip(stringResource(R.string.outcome_running), HeatColors.Accent)
        }

        Column {
            Text(
                stringResource(R.string.session_timer_label),
                style = MaterialTheme.typography.bodySmall,
                color = HeatColors.TextSecondary,
            )
            Text(
                Durations.clock(elapsed),
                style = MaterialTheme.typography.displayLarge,
                color = HeatColors.TextPrimary,
            )
        }

        Row(Modifier.fillMaxWidth()) {
            LabeledValue(stringResource(R.string.session_start_label), Temperatures.format(session.startTempC, unit), Modifier.weight(1f))
            LabeledValue(stringResource(R.string.session_now_label), Temperatures.format(session.lastTempC, unit), Modifier.weight(1f), HeatColors.Accent)
            LabeledValue(stringResource(R.string.session_target_label), Temperatures.format(session.targetTempC, unit), Modifier.weight(1f), HeatColors.Normal)
        }

        val percent = (session.progress * 100).toInt()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(HeatColors.SurfaceHighest),
            ) {
                Box(
                    Modifier.fillMaxWidth(session.progress.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(HeatColors.Normal),
                )
            }
            Text(
                stringResource(R.string.session_progress, "$percent%"),
                style = MaterialTheme.typography.bodySmall,
                color = HeatColors.TextSecondary,
            )
        }

        TemperatureChart(
            points = points,
            unit = unit,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            references = listOf(ChartReference(session.targetTempC, HeatColors.Normal)),
            lineColor = HeatColors.Accent,
            startLabel = "0:00",
            endLabel = Durations.clock(elapsed),
            emptyText = stringResource(R.string.session_chart_collecting),
        )

        Text(
            stringResource(R.string.session_keep_monitoring),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
        )
        OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.session_end))
        }
    }
}

@Composable
private fun rememberElapsed(startMillis: Long): State<Long> = produceState(0L, startMillis) {
    while (true) {
        value = System.currentTimeMillis() - startMillis
        delay(1000)
    }
}

@Composable
private fun ResultCard(session: CoolingSession, unit: TemperatureUnit, onDismiss: () -> Unit) {
    val duration = Durations.format(session.durationMillis)
    val (title, detail, color) = when (session.outcome) {
        SessionOutcome.RECOVERED -> Triple(
            stringResource(R.string.result_recovered, duration),
            stringResource(
                R.string.result_recovered_detail,
                Temperatures.formatDelta(session.dropC, unit),
                Temperatures.format(session.lastTempC, unit),
            ),
            HeatColors.Normal,
        )
        SessionOutcome.STOPPED -> Triple(
            stringResource(R.string.result_stopped, duration),
            stringResource(
                R.string.result_stopped_detail,
                Temperatures.format(session.lastTempC, unit),
                Temperatures.format(session.targetTempC, unit),
            ),
            HeatColors.Elevated,
        )
        else -> Triple(
            stringResource(R.string.result_interrupted),
            stringResource(R.string.result_interrupted_detail),
            HeatColors.Unknown,
        )
    }
    HeatCard(
        Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.10f),
        border = color.copy(alpha = 0.5f),
        spacing = 6.dp,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = color)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.result_dismiss)) }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, color: Color = HeatColors.TextPrimary) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HeatColors.TextTertiary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

// ------------------------------------------------------------------ advice

@Composable
private fun AdviceCard(advice: Advice) {
    val (title, body) = adviceText(advice.type)
    val accent = when (advice.priority) {
        AdvicePriority.HIGH -> HeatColors.Critical
        AdvicePriority.MEDIUM -> HeatColors.Elevated
        AdvicePriority.LOW -> HeatColors.Accent
    }
    val priorityLabel = when (advice.priority) {
        AdvicePriority.HIGH -> R.string.priority_high
        AdvicePriority.MEDIUM -> R.string.priority_medium
        AdvicePriority.LOW -> R.string.priority_low
    }
    HeatCard(Modifier.fillMaxWidth(), contentPadding = 0.dp, spacing = 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(priorityLabel).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
            }
        }
    }
}

private fun adviceText(type: AdviceType): Pair<Int, Int> = when (type) {
    AdviceType.UNPLUG_CHARGER -> R.string.advice_unplug_title to R.string.advice_unplug_body
    AdviceType.SWITCH_TO_WIRED_CHARGING -> R.string.advice_wired_title to R.string.advice_wired_body
    AdviceType.STOP_HEAVY_USE -> R.string.advice_stop_title to R.string.advice_stop_body
    AdviceType.REMOVE_CASE -> R.string.advice_case_title to R.string.advice_case_body
    AdviceType.MOVE_FROM_HEAT -> R.string.advice_shade_title to R.string.advice_shade_body
    AdviceType.REDUCE_BRIGHTNESS -> R.string.advice_brightness_title to R.string.advice_brightness_body
    AdviceType.NO_FREEZER -> R.string.advice_freezer_title to R.string.advice_freezer_body
    AdviceType.AIRPLANE_MODE_WEAK_SIGNAL -> R.string.advice_airplane_title to R.string.advice_airplane_body
    AdviceType.SYSTEM_THROTTLING -> R.string.advice_system_title to R.string.advice_system_body
    AdviceType.PREVENT_HEATING -> R.string.advice_prevent_title to R.string.advice_prevent_body
    AdviceType.CHARGING_WARMS -> R.string.advice_charging_warms_title to R.string.advice_charging_warms_body
    AdviceType.KEEP_RESTING -> R.string.advice_resting_title to R.string.advice_resting_body
    AdviceType.PROFILE_WORK -> R.string.advice_work_title to R.string.advice_work_body
    AdviceType.PROFILE_NAVIGATION -> R.string.advice_nav_title to R.string.advice_nav_body
    AdviceType.PROFILE_GAMING -> R.string.advice_gaming_title to R.string.advice_gaming_body
    AdviceType.PROFILE_CHARGING -> R.string.advice_charging_profile_title to R.string.advice_charging_profile_body
    AdviceType.ALL_GOOD -> R.string.advice_good_title to R.string.advice_good_body
    AdviceType.SENSOR_UNAVAILABLE -> R.string.advice_sensor_title to R.string.advice_sensor_body
}

// ------------------------------------------------------------------ statistics

@Composable
private fun StatsCard(stats: RecoveryStats, unit: TemperatureUnit) {
    HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(
            stringResource(R.string.stats_title),
            style = MaterialTheme.typography.titleMedium,
            color = HeatColors.TextPrimary,
        )
        if (stats.totalSessions == 0) {
            Text(
                stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
            )
            return@HeatCard
        }
        val none = stringResource(R.string.detail_unavailable)
        StatRow(R.string.stats_recovered, stringResource(R.string.stats_recovered_value, stats.recoveredCount, stats.totalSessions))
        HorizontalDivider(color = HeatColors.OutlineSubtle)
        StatRow(R.string.stats_avg_time, stats.averageRecoveryMillis?.let { Durations.format(it) } ?: none)
        StatRow(R.string.stats_fastest, stats.fastestRecoveryMillis?.let { Durations.format(it) } ?: none)
        StatRow(R.string.stats_avg_drop, stats.averageDropC?.let { Temperatures.formatDelta(it, unit) } ?: none)
        StatRow(
            R.string.stats_avg_rate,
            stats.averageRateCPerMin?.let { stringResource(R.string.stats_rate_value, Temperatures.formatDelta(it, unit)) } ?: none,
        )
    }
}

@Composable
private fun StatRow(labelRes: Int, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextPrimary, textAlign = TextAlign.End)
    }
}

@Composable
private fun RecentSessionsCard(sessions: List<CoolingSession>, unit: TemperatureUnit) {
    val formatDateTime = rememberDateTimeFormatter()
    HeatCard(Modifier.fillMaxWidth(), spacing = 12.dp) {
        Text(
            stringResource(R.string.recent_title),
            style = MaterialTheme.typography.titleMedium,
            color = HeatColors.TextPrimary,
        )
        sessions.forEachIndexed { index, s ->
            if (index > 0) HorizontalDivider(color = HeatColors.OutlineSubtle)
            val (label, color) = when (s.outcome) {
                SessionOutcome.RECOVERED -> R.string.outcome_recovered to HeatColors.Normal
                SessionOutcome.STOPPED -> R.string.outcome_stopped to HeatColors.Elevated
                SessionOutcome.INTERRUPTED -> R.string.outcome_interrupted to HeatColors.Unknown
                null -> R.string.outcome_running to HeatColors.Accent
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDateTime(s.startMillis), style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextPrimary)
                    Text(
                        stringResource(R.string.recent_line, Durations.format(s.durationMillis), Temperatures.formatDelta(s.dropC, unit)),
                        style = MaterialTheme.typography.bodySmall,
                        color = HeatColors.TextSecondary,
                    )
                }
                StatusChip(stringResource(label), color)
            }
        }
    }
}
