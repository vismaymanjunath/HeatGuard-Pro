package com.example.appdev.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.appdev.R
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.readiness.CoolingEstimate
import com.example.appdev.domain.readiness.EstimateBasis
import com.example.appdev.domain.readiness.PreCallReadiness
import com.example.appdev.domain.readiness.ReadinessReason
import com.example.appdev.domain.readiness.ReadinessStatus
import com.example.appdev.domain.readiness.TrendDirection
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.history.StatusChip
import com.example.appdev.ui.theme.HeatColors

private fun ReadinessStatus.color(): Color = when (this) {
    ReadinessStatus.READY -> HeatColors.Normal
    ReadinessStatus.CAUTION -> HeatColors.Elevated
    ReadinessStatus.COOL_DOWN_FIRST -> HeatColors.Critical
    ReadinessStatus.UNAVAILABLE -> HeatColors.Unknown
}

private fun ReadinessStatus.labelRes(): Int = when (this) {
    ReadinessStatus.READY -> R.string.readiness_ready
    ReadinessStatus.CAUTION -> R.string.readiness_caution
    ReadinessStatus.COOL_DOWN_FIRST -> R.string.readiness_cool_down
    ReadinessStatus.UNAVAILABLE -> R.string.readiness_unavailable
}

private fun ReadinessStatus.headlineRes(): Int = when (this) {
    ReadinessStatus.READY -> R.string.readiness_headline_ready
    ReadinessStatus.CAUTION -> R.string.readiness_headline_caution
    ReadinessStatus.COOL_DOWN_FIRST -> R.string.readiness_headline_cool
    ReadinessStatus.UNAVAILABLE -> R.string.readiness_headline_unavailable
}

/**
 * "Can I start a call now?" in one glance. Built from the app's real readings, trend, Android thermal
 * status and the user's own cooling history; it never promises a call duration.
 */
@Composable
fun PreCallReadinessCard(readiness: PreCallReadiness, system: SystemThermalState, unit: TemperatureUnit) {
    val color = readiness.status.color()
    HeatCard(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.07f),
        border = color.copy(alpha = 0.45f),
        spacing = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.readiness_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = HeatColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            StatusChip(stringResource(readiness.status.labelRes()), color)
        }
        Text(stringResource(readiness.status.headlineRes()), style = MaterialTheme.typography.titleLarge, color = color)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                label = stringResource(R.string.readiness_tile_temperature),
                value = readiness.temperatureC?.let { Temperatures.format(it, unit) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            Tile(
                label = stringResource(R.string.readiness_tile_trend),
                value = trendText(readiness, unit),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                label = stringResource(R.string.readiness_tile_status),
                value = androidStatusText(readiness, system),
                valueColor = readiness.thermalStatus.color().takeIf { readiness.thermalStatus != null } ?: HeatColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            CoolingTile(readiness.cooling, unit, Modifier.weight(1f))
        }

        if (readiness.reasons.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                readiness.reasons.forEach { reason ->
                    Text(
                        "•  " + reasonText(reason, unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeatColors.TextSecondary,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.readiness_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
        )
    }
}

@Composable
private fun trendText(readiness: PreCallReadiness, unit: TemperatureUnit): String {
    val slope = readiness.trendCPerMin
    return when (readiness.trend) {
        null -> stringResource(R.string.analytics_trend_none)
        TrendDirection.RISING -> stringResource(R.string.analytics_trend_rising, Temperatures.formatDelta(slope ?: 0f, unit))
        TrendDirection.FALLING -> stringResource(R.string.analytics_trend_falling, Temperatures.formatDelta(slope ?: 0f, unit))
        TrendDirection.STEADY -> stringResource(R.string.analytics_trend_steady)
    }
}

@Composable
private fun androidStatusText(readiness: PreCallReadiness, system: SystemThermalState): String = when {
    readiness.thermalStatus != null -> stringResource(readiness.thermalStatus.labelRes())
    !system.statusApiAvailable -> stringResource(R.string.system_requires_10)
    else -> stringResource(R.string.system_not_reported)
}

@Composable
private fun CoolingTile(cooling: CoolingEstimate, unit: TemperatureUnit, modifier: Modifier) {
    val label = stringResource(R.string.readiness_tile_cooling)
    when (cooling) {
        CoolingEstimate.NotNeeded -> Tile(label, stringResource(R.string.readiness_cooling_not_needed), modifier)
        CoolingEstimate.InsufficientData -> Tile(
            label, stringResource(R.string.readiness_cooling_insufficient), modifier, valueColor = HeatColors.TextTertiary,
        )
        is CoolingEstimate.Estimated -> Tile(
            label = label,
            value = "~" + formatEta(cooling.millis / 60_000f),
            modifier = modifier,
            caption = stringResource(R.string.readiness_cooling_to, Temperatures.format(cooling.targetC, unit)) + " · " +
                when (cooling.basis) {
                    EstimateBasis.PERSONAL_HISTORY -> stringResource(R.string.readiness_basis_personal, cooling.basedOnEvents)
                    EstimateBasis.CURRENT_TREND -> stringResource(R.string.readiness_basis_trend)
                },
        )
    }
}

@Composable
private fun reasonText(reason: ReadinessReason, unit: TemperatureUnit): String = when (reason) {
    is ReadinessReason.AboveThreshold -> stringResource(R.string.readiness_reason_over, Temperatures.formatDelta(reason.overByC, unit))
    ReadinessReason.InWarningBand -> stringResource(R.string.readiness_reason_warning)
    is ReadinessReason.SystemThermal -> stringResource(R.string.readiness_reason_system, stringResource(reason.status.labelRes()))
    is ReadinessReason.HeatingSoon -> stringResource(R.string.readiness_reason_heating, formatEta(reason.minutes))
    ReadinessReason.CoolingDown -> stringResource(R.string.readiness_reason_cooling)
    ReadinessReason.AllClear -> stringResource(R.string.readiness_reason_clear)
}

@Composable
private fun Tile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = HeatColors.TextPrimary,
    caption: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HeatColors.TextTertiary, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
    }
}
