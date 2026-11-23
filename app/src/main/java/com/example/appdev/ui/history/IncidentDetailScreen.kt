package com.example.appdev.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appdev.R
import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.incident.TimelineEntry
import com.example.appdev.domain.incident.TimelineMarker
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.thermal.TemperaturePoint
import com.example.appdev.ui.components.ChartReference
import com.example.appdev.ui.components.Durations
import com.example.appdev.ui.components.TemperatureChart
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.components.labelRes
import com.example.appdev.ui.components.rememberDateTimeFormatter
import com.example.appdev.ui.components.rememberTimeFormatter
import com.example.appdev.ui.theme.HeatColors

@Composable
fun IncidentDetailRoute(
    onBack: () -> Unit,
    viewModel: IncidentDetailViewModel = viewModel(factory = IncidentDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back),
                    tint = HeatColors.TextPrimary,
                )
            }
            Text(
                stringResource(R.string.detail_title),
                style = MaterialTheme.typography.titleLarge,
                color = HeatColors.TextPrimary,
            )
        }
        when (val s = state) {
            DetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HeatColors.Accent)
            }
            DetailUiState.NotFound -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.incident_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = HeatColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            is DetailUiState.Loaded -> IncidentDetailContent(s.incident, s.timeline, s.unit)
        }
    }
}

@Composable
private fun IncidentDetailContent(incident: Incident, timeline: List<TimelineEntry>, unit: TemperatureUnit) {
    val formatDateTime = rememberDateTimeFormatter()
    val formatTime = rememberTimeFormatter()
    val unavailable = stringResource(R.string.detail_unavailable)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Temperatures.format(incident.peakTempC, unit),
                        style = MaterialTheme.typography.headlineMedium,
                        color = HeatColors.Critical,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(
                        stringResource(if (incident.isActive) R.string.incident_ongoing else R.string.incident_resolved),
                        if (incident.isActive) HeatColors.Critical else HeatColors.Normal,
                    )
                }
                Text(
                    stringResource(R.string.detail_peak_time) + " · " + formatDateTime(incident.peakMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = HeatColors.TextSecondary,
                )
                HorizontalDivider(color = HeatColors.OutlineSubtle)
                DetailRow(R.string.detail_started, formatDateTime(incident.startMillis))
                if (incident.endMillis != null) {
                    DetailRow(R.string.detail_resolved_at, formatDateTime(incident.endMillis))
                } else {
                    DetailRow(R.string.detail_last_seen, formatDateTime(incident.lastSeenMillis))
                }
                DetailRow(R.string.detail_duration, Durations.format(incident.durationMillis))
                DetailRow(R.string.detail_initial, Temperatures.format(incident.initialTempC, unit))
                DetailRow(
                    R.string.detail_end_temp,
                    incident.endTempC?.let { Temperatures.format(it, unit) } ?: unavailable,
                )
                DetailRow(R.string.detail_threshold, Temperatures.format(incident.thresholdC, unit))
                DetailRow(
                    R.string.detail_battery,
                    stringResource(
                        R.string.detail_battery_range,
                        incident.startBatteryPercent?.let { "$it%" } ?: unavailable,
                        if (incident.isActive) "…" else incident.endBatteryPercent?.let { "$it%" } ?: unavailable,
                    ),
                )
                DetailRow(
                    R.string.detail_power_at_start,
                    stringResource(incident.startChargeStatus.labelRes()) + " · " +
                        stringResource(incident.startPowerSource.labelRes()),
                )
                if (!incident.isActive && incident.endTempC == null) {
                    Text(
                        stringResource(R.string.detail_gap_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = HeatColors.Elevated,
                    )
                }
            }
        }
        if (timeline.size >= 2) {
            item {
                HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
                    Text(
                        stringResource(R.string.incident_chart_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = HeatColors.TextSecondary,
                    )
                    TemperatureChart(
                        points = timeline.map { TemperaturePoint(it.timestampMillis, it.temperatureC) },
                        unit = unit,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        references = listOf(ChartReference(incident.thresholdC, HeatColors.Critical)),
                        lineColor = HeatColors.Critical,
                        startLabel = formatTime(timeline.first().timestampMillis),
                        endLabel = formatTime(timeline.last().timestampMillis),
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.detail_timeline),
                style = MaterialTheme.typography.titleMedium,
                color = HeatColors.TextPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(timeline, key = { it.timestampMillis }) { entry ->
            TimelineRow(entry, unit, formatTime(entry.timestampMillis))
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun DetailRow(labelRes: Int, value: String) {
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
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry, unit: TemperatureUnit, time: String) {
    val (markerText, markerColor) = when (entry.marker) {
        TimelineMarker.START -> stringResource(R.string.timeline_start) to HeatColors.Elevated
        TimelineMarker.PEAK -> stringResource(R.string.timeline_peak) to HeatColors.Critical
        TimelineMarker.RESOLVED -> stringResource(R.string.timeline_resolved) to HeatColors.Normal
        null -> null to Color.Unspecified
    }
    HeatCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, spacing = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time, style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary, modifier = Modifier.weight(1f))
            if (markerText != null) {
                StatusChip(markerText, markerColor)
                Spacer(Modifier.padding(start = 8.dp))
            }
            Text(
                Temperatures.format(entry.temperatureC, unit),
                style = MaterialTheme.typography.titleMedium,
                color = HeatColors.TextPrimary,
            )
        }
        val battery = entry.batteryPercent?.let { "$it%" } ?: stringResource(R.string.detail_unavailable)
        Text(
            "$battery · ${stringResource(entry.chargeStatus.labelRes())}",
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
        )
    }
}
