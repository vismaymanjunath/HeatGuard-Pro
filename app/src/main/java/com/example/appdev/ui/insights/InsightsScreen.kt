package com.example.appdev.ui.insights

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.domain.analytics.ChargingCorrelation
import com.example.appdev.domain.analytics.InsightRecommendation
import com.example.appdev.domain.analytics.InsightsEngine
import com.example.appdev.domain.analytics.InsightsReport
import com.example.appdev.domain.analytics.TimeOfDay
import com.example.appdev.domain.cooling.CoolingRepository
import com.example.appdev.domain.incident.IncidentRepository
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.ui.components.Bar
import com.example.appdev.ui.components.BarChart
import com.example.appdev.ui.components.Durations
import com.example.appdev.ui.components.ExportCard
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.platform.Sharing
import com.example.appdev.ui.components.labelRes
import com.example.appdev.ui.components.rememberCsvExport
import com.example.appdev.ui.components.rememberDateTimeFormatter
import com.example.appdev.ui.theme.HeatColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** `report == null` means still loading. */
data class InsightsUiState(
    val report: InsightsReport? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    incidents: IncidentRepository,
    sessions: CoolingRepository,
    settings: SettingsRepository,
    activeSource: StateFlow<ReadingSource>,
) : ViewModel() {

    val uiState: StateFlow<InsightsUiState> = combine(
        activeSource.flatMapLatest { incidents.observeIncidents(it) },
        activeSource.flatMapLatest { sessions.observeSessions(it) },
        settings.settings.map { it.unit },
    ) { incidentList, sessionList, unit ->
        InsightsUiState(InsightsEngine.compute(incidentList, sessionList, System.currentTimeMillis()), unit)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as HeatGuardApp).container
                InsightsViewModel(c.incidentRepository, c.coolingRepository, c.settingsRepository, c.demoController.activeSource)
            }
        }
    }
}

@Composable
fun InsightsRoute(viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsScreen(state)
}

@Composable
fun InsightsScreen(state: InsightsUiState, modifier: Modifier = Modifier) {
    val report = state.report
    val unit = state.unit
    val context = LocalContext.current
    val export = rememberCsvExport()
    val chooser = stringResource(R.string.export_report_chooser)
    val subject = stringResource(R.string.report_header)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                stringResource(R.string.insights_title),
                style = MaterialTheme.typography.headlineMedium,
                color = HeatColors.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.insights_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
            )
        }

        if (report == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HeatColors.Accent)
            }
            return@Column
        }

        if (report.totalIncidents == 0 && report.recovery.totalSessions == 0) {
            HeatCard(Modifier.fillMaxWidth(), spacing = 6.dp) {
                Text(stringResource(R.string.insights_empty_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
                Text(stringResource(R.string.insights_empty_body), style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
            }
            return@Column
        }

        WeekCard(report)
        TimeOfDayCard(report)
        ChargingCard(report.charging, unit)
        SummaryCard(report, unit)
        if (report.recovery.totalSessions > 0) RecoveryCard(report, unit)

        Text(
            stringResource(R.string.insights_recs_title),
            style = MaterialTheme.typography.titleMedium,
            color = HeatColors.TextPrimary,
        )
        report.recommendations.forEach { RecommendationCard(it, unit) }

        ExportCard(
            state = export,
            onShareReport = {
                Sharing.shareText(context, subject, InsightText.buildReport(context, report, unit), chooser)
            },
        )

        Text(
            stringResource(R.string.insights_footer),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WeekCard(report: InsightsReport) {
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    val dayFormat = remember(locale) { SimpleDateFormat("EEE", locale) }
    HeatCard(Modifier.fillMaxWidth(), spacing = 12.dp) {
        Text(stringResource(R.string.insights_week_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        Text(
            stringResource(R.string.insights_week_compare, report.last7Days, report.previous7Days),
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextSecondary,
        )
        BarChart(
            bars = report.days.mapIndexed { index, day ->
                Bar(day.count, dayFormat.format(Date(day.startMillis)), highlighted = index == report.days.lastIndex)
            },
        )
    }
}

@Composable
private fun TimeOfDayCard(report: InsightsReport) {
    val context = LocalContext.current
    HeatCard(Modifier.fillMaxWidth(), spacing = 12.dp) {
        Text(stringResource(R.string.insights_tod_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        BarChart(
            bars = TimeOfDay.entries.map { Bar(report.timeOfDay[it] ?: 0, InsightText.timeOfDayLabel(context, it)) },
            color = HeatColors.Elevated,
        )
        Text(stringResource(R.string.insights_tod_hint), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextTertiary)
    }
}

@Composable
private fun ChargingCard(c: ChargingCorrelation, unit: TemperatureUnit) {
    HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(stringResource(R.string.insights_charging_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        if (c.total == 0) {
            Text(stringResource(R.string.insights_none_yet), style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
            return@HeatCard
        }
        val percent = ((c.chargingShare ?: 0f) * 100).toInt()
        Text(
            pluralStringResource(R.plurals.insights_charging_line, c.total, c.whileCharging, c.total, percent),
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextPrimary,
        )
        ShareBar(fraction = c.chargingShare ?: 0f)
        if (c.byPowerSource.isNotEmpty()) {
            val sorted = c.byPowerSource.entries.sortedByDescending { it.value }
            val labels = sorted.map { stringResource(it.key.labelRes()) }
            val parts = sorted.indices.joinToString(", ") { i -> "${labels[i]} ${sorted[i].value}" }
            Text(stringResource(R.string.insights_charging_sources, parts), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
        }
        val charging = c.avgPeakChargingC
        val battery = c.avgPeakOnBatteryC
        if (charging != null && battery != null) {
            Text(
                stringResource(R.string.insights_charging_peaks, Temperatures.format(charging, unit), Temperatures.format(battery, unit)),
                style = MaterialTheme.typography.bodySmall,
                color = HeatColors.TextSecondary,
            )
        }
        Text(stringResource(R.string.insights_charging_caveat), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextTertiary)
    }
}

@Composable
private fun ShareBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(HeatColors.SurfaceHighest, CircleShape)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .background(HeatColors.Elevated, CircleShape),
        )
    }
}

@Composable
private fun SummaryCard(report: InsightsReport, unit: TemperatureUnit) {
    val formatDateTime = rememberDateTimeFormatter()
    val none = stringResource(R.string.detail_unavailable)
    HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(stringResource(R.string.insights_summary_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        LabeledRow(report.totalIncidents.toString(), R.string.insights_total)
        HorizontalDivider(color = HeatColors.OutlineSubtle)
        LabeledRow(report.avgPeakC?.let { Temperatures.format(it, unit) } ?: none, R.string.insights_avg_peak)
        LabeledRow(
            report.maxPeakC?.let { Temperatures.format(it, unit) + " · " + formatDateTime(report.maxPeakMillis ?: 0) } ?: none,
            R.string.insights_max_peak,
        )
        LabeledRow(report.avgDurationMillis?.let { Durations.format(it) } ?: none, R.string.insights_avg_duration)
        LabeledRow(report.longestDurationMillis?.let { Durations.format(it) } ?: none, R.string.insights_longest)
    }
}

@Composable
private fun RecoveryCard(report: InsightsReport, unit: TemperatureUnit) {
    val r = report.recovery
    val none = stringResource(R.string.detail_unavailable)
    HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(stringResource(R.string.insights_recovery_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        LabeledRow(stringResource(R.string.stats_recovered_value, r.recoveredCount, r.totalSessions), R.string.stats_recovered)
        LabeledRow(r.averageRecoveryMillis?.let { Durations.format(it) } ?: none, R.string.stats_avg_time)
        LabeledRow(r.fastestRecoveryMillis?.let { Durations.format(it) } ?: none, R.string.stats_fastest)
        LabeledRow(r.averageDropC?.let { Temperatures.formatDelta(it, unit) } ?: none, R.string.stats_avg_drop)
    }
}

@Composable
private fun LabeledRow(value: String, labelRes: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.3f),
        )
    }
}

@Composable
private fun RecommendationCard(rec: InsightRecommendation, unit: TemperatureUnit) {
    val context = LocalContext.current
    val (title, body) = remember(rec, unit) { InsightText.describe(context, rec, unit) }
    HeatCard(Modifier.fillMaxWidth(), contentPadding = 0.dp, spacing = 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(HeatColors.Accent))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
            }
        }
    }
}
