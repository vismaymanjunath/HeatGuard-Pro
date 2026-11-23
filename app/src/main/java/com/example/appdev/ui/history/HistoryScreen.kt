package com.example.appdev.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appdev.R
import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.ui.components.Durations
import com.example.appdev.ui.components.ExportCard
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.components.rememberCsvExport
import com.example.appdev.ui.components.labelRes
import com.example.appdev.ui.components.rememberDateTimeFormatter
import com.example.appdev.ui.theme.HeatColors

@Composable
fun HistoryRoute(
    onOpenIncident: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state = state, onOpenIncident = onOpenIncident, onClear = viewModel::clearHistory)
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOpenIncident: (Long) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val incidents = state.incidents
    val export = rememberCsvExport()

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = HeatColors.TextPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(if (state.isDemo) R.string.demo_history_note else R.string.history_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isDemo) HeatColors.Accent else HeatColors.TextSecondary,
                )
            }
            if (!incidents.isNullOrEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.history_clear),
                        tint = HeatColors.TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            incidents == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HeatColors.Accent)
            }
            incidents.isEmpty() -> EmptyHistory()
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(incidents, key = { it.id }) { incident ->
                    IncidentCard(incident, state.unit, onClick = { onOpenIncident(incident.id) })
                }
                item { ExportCard(state = export) }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) {
                    Text(stringResource(R.string.history_clear_confirm), color = HeatColors.Critical)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_history),
            contentDescription = null,
            tint = HeatColors.TextTertiary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = HeatColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IncidentCard(incident: Incident, unit: TemperatureUnit, onClick: () -> Unit) {
    val formatDateTime = rememberDateTimeFormatter()
    val statusColor = if (incident.isActive) HeatColors.Critical else HeatColors.Normal
    HeatCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatDateTime(incident.startMillis),
                    style = MaterialTheme.typography.titleMedium,
                    color = HeatColors.TextPrimary,
                )
                Text(
                    stringResource(R.string.incident_peak, Temperatures.format(incident.peakTempC, unit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeatColors.Critical,
                )
            }
            StatusChip(
                text = stringResource(if (incident.isActive) R.string.incident_ongoing else R.string.incident_resolved),
                color = statusColor,
            )
        }
        val battery = incident.startBatteryPercent?.let { "$it%" } ?: stringResource(R.string.detail_unavailable)
        Text(
            stringResource(
                R.string.incident_summary_line,
                Temperatures.format(incident.initialTempC, unit),
                battery,
                stringResource(incident.startChargeStatus.labelRes()),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextSecondary,
        )
        Text(
            stringResource(R.string.incident_duration, Durations.format(incident.durationMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
        )
    }
}

@Composable
internal fun StatusChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
