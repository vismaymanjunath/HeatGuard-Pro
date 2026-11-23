package com.example.appdev.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.appdev.HeatGuardApp
import com.example.appdev.R
import com.example.appdev.data.ThermalRepository
import com.example.appdev.domain.analytics.LiveAnalytics
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.thermal.TemperaturePoint
import com.example.appdev.domain.thermal.TemperatureHistory
import com.example.appdev.domain.thermal.TemperaturePredictor
import com.example.appdev.domain.thermal.ThermalAssessment
import com.example.appdev.domain.thermal.TrendEstimate
import com.example.appdev.ui.components.ChartReference
import com.example.appdev.ui.components.Durations
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.components.TemperatureChart
import com.example.appdev.ui.theme.HeatColors
import com.example.appdev.ui.theme.color
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalyticsUiState(
    /** Up to 30 minutes of readings, oldest first. */
    val points: List<TemperaturePoint> = emptyList(),
    val assessment: ThermalAssessment = ThermalAssessment.evaluate(null, com.example.appdev.domain.model.UserSettings()),
    val trend: TrendEstimate? = null,
)

class AnalyticsViewModel(repository: ThermalRepository) : ViewModel() {

    val uiState: StateFlow<AnalyticsUiState> = combine(
        repository.history,
        repository.assessment,
        repository.analysis,
    ) { points, assessment, analysis -> AnalyticsUiState(points, assessment, analysis.trend) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                AnalyticsViewModel((this[APPLICATION_KEY] as HeatGuardApp).container.thermalRepository)
            }
        }
    }
}

@Composable
fun AnalyticsRoute(viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsScreen(state)
}

@Composable
fun AnalyticsScreen(state: AnalyticsUiState, modifier: Modifier = Modifier) {
    var windowIndex by rememberSaveable { mutableIntStateOf(LiveAnalytics.WINDOWS_MINUTES.lastIndex) }
    val windowMinutes = LiveAnalytics.WINDOWS_MINUTES[windowIndex]
    val settings = state.assessment.settings
    val unit = settings.unit

    val visible = remember(state.points, windowMinutes) { LiveAnalytics.window(state.points, windowMinutes * 60_000L) }
    val stats = remember(visible, settings.thresholdC) { LiveAnalytics.stats(visible, settings.thresholdC) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                stringResource(R.string.analytics_title),
                style = MaterialTheme.typography.headlineMedium,
                color = HeatColors.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.analytics_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveAnalytics.WINDOWS_MINUTES.forEachIndexed { index, minutes ->
                FilterChip(
                    selected = index == windowIndex,
                    onClick = { windowIndex = index },
                    label = { Text(stringResource(R.string.analytics_window, minutes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HeatColors.Accent,
                        selectedLabelColor = HeatColors.OnAccent,
                        containerColor = HeatColors.SurfaceRaised,
                        labelColor = HeatColors.TextSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = index == windowIndex,
                        borderColor = HeatColors.Outline,
                        selectedBorderColor = HeatColors.Accent,
                    ),
                )
            }
        }

        HeatCard(Modifier.fillMaxWidth(), spacing = 12.dp) {
            Text(
                stringResource(R.string.analytics_chart_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = HeatColors.TextSecondary,
            )
            val span = stats?.spanMillis ?: 0L
            TemperatureChart(
                points = visible,
                unit = unit,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                references = listOf(
                    ChartReference(settings.thresholdC, HeatColors.Critical),
                    ChartReference(settings.warningStartC, HeatColors.Elevated),
                ),
                lineColor = state.assessment.level.color(),
                startLabel = if (visible.size >= 2) stringResource(R.string.analytics_x_ago, Durations.clock(span)) else "",
                endLabel = if (visible.size >= 2) stringResource(R.string.analytics_x_now) else "",
                emptyText = stringResource(R.string.analytics_empty),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(HeatColors.Critical, stringResource(R.string.analytics_legend_threshold, Temperatures.format(settings.thresholdC, unit)))
                Legend(HeatColors.Elevated, stringResource(R.string.analytics_legend_warning, Temperatures.format(settings.warningStartC, unit)))
            }
        }

        HeatCard(Modifier.fillMaxWidth(), spacing = 14.dp) {
            Row(Modifier.fillMaxWidth()) {
                Stat(stringResource(R.string.analytics_stat_now), stats?.latestC?.let { Temperatures.format(it, unit) } ?: "—", Modifier.weight(1f), state.assessment.level.color())
                Stat(stringResource(R.string.analytics_stat_min), stats?.minC?.let { Temperatures.format(it, unit) } ?: "—", Modifier.weight(1f))
                Stat(stringResource(R.string.analytics_stat_max), stats?.maxC?.let { Temperatures.format(it, unit) } ?: "—", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Stat(stringResource(R.string.analytics_stat_avg), stats?.avgC?.let { Temperatures.format(it, unit) } ?: "—", Modifier.weight(1f))
                Stat(stringResource(R.string.analytics_stat_readings), stats?.sampleCount?.toString() ?: "—", Modifier.weight(1f))
                Stat(
                    stringResource(R.string.analytics_stat_above),
                    stats?.let { Durations.format(it.aboveThresholdMillis) } ?: "—",
                    Modifier.weight(1f),
                    if ((stats?.aboveThresholdMillis ?: 0L) > 0) HeatColors.Critical else HeatColors.TextPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.analytics_stat_trend).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HeatColors.TextTertiary,
                )
                Text(trendText(state.trend, unit), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
            }
        }

        Text(
            stringResource(R.string.analytics_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun trendText(trend: TrendEstimate?, unit: com.example.appdev.domain.model.TemperatureUnit): String {
    if (trend == null) return stringResource(R.string.analytics_trend_none)
    val slope = trend.slopeCPerMin
    return when {
        slope > TemperaturePredictor.STABLE_SLOPE_C_PER_MIN ->
            stringResource(R.string.analytics_trend_rising, Temperatures.formatDelta(slope, unit))
        slope < -TemperaturePredictor.STABLE_SLOPE_C_PER_MIN ->
            stringResource(R.string.analytics_trend_falling, Temperatures.formatDelta(slope, unit))
        else -> stringResource(R.string.analytics_trend_steady)
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(14.dp).height(3.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = HeatColors.TextPrimary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HeatColors.TextTertiary, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1)
    }
}
