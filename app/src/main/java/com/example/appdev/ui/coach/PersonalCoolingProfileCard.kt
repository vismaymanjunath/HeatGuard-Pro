package com.example.appdev.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.appdev.R
import com.example.appdev.domain.cooling.PersonalCoolingProfile
import com.example.appdev.domain.cooling.RecoverySource
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.ui.components.Durations
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.components.rememberDateTimeFormatter
import com.example.appdev.ui.dashboard.formatEta
import com.example.appdev.ui.theme.HeatColors

/**
 * How this particular phone and user cool down, from stored cooling sessions and resolved incidents.
 * Averages appear only once there are enough recoveries; until then the card says "Insufficient data".
 */
@Composable
fun PersonalCoolingProfileCard(
    profile: PersonalCoolingProfile,
    currentTempC: Float?,
    targetC: Float,
    unit: TemperatureUnit,
) {
    HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(
            stringResource(R.string.profile_card_title),
            style = MaterialTheme.typography.titleMedium,
            color = HeatColors.TextPrimary,
        )

        if (!profile.hasEnoughData) {
            Text(
                stringResource(R.string.profile_card_insufficient),
                style = MaterialTheme.typography.titleLarge,
                color = HeatColors.TextSecondary,
            )
            Text(
                stringResource(R.string.profile_card_insufficient_body, PersonalCoolingProfile.MIN_EVENTS, profile.eventCount),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
            )
            return@HeatCard
        }

        Text(
            stringResource(R.string.profile_card_built_from, profile.eventCount, profile.sessionCount, profile.incidentCount),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextSecondary,
        )
        Row(
            profile.avgRateCPerMin?.let { stringResource(R.string.stats_rate_value, Temperatures.formatDelta(it, unit)) },
            R.string.profile_stat_rate,
        )
        Row(profile.avgRecoveryMillis?.let { Durations.format(it) }, R.string.profile_stat_avg_time)
        Row(
            profile.fastestRecovery?.let {
                stringResource(R.string.profile_stat_fastest_value, Durations.format(it.durationMillis), Temperatures.formatDelta(it.dropC, unit))
            },
            R.string.profile_stat_fastest,
        )

        HorizontalDivider(color = HeatColors.OutlineSubtle)
        val estimate = currentTempC?.let { profile.estimateRecoveryMillis(it, targetC) }
        Row(
            when {
                currentTempC == null -> stringResource(R.string.profile_estimate_unavailable)
                estimate == null -> stringResource(R.string.profile_card_insufficient)
                estimate == 0L -> stringResource(R.string.profile_estimate_at_target, Temperatures.format(targetC, unit))
                else -> stringResource(R.string.profile_estimate_value, formatEta(estimate / 60_000f), Temperatures.format(targetC, unit))
            },
            R.string.profile_stat_estimate,
        )
        Text(stringResource(R.string.profile_estimate_note), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextTertiary)

        val formatDateTime = rememberDateTimeFormatter()
        HorizontalDivider(color = HeatColors.OutlineSubtle)
        Text(stringResource(R.string.profile_recent_title), style = MaterialTheme.typography.labelLarge, color = HeatColors.TextPrimary)
        profile.events.take(RECENT).forEach { e ->
            Text(
                formatDateTime(e.startMillis) + "  ·  " + stringResource(
                    R.string.profile_recent_line,
                    Durations.format(e.durationMillis),
                    Temperatures.formatDelta(e.dropC, unit),
                    stringResource(if (e.source == RecoverySource.COOLING_SESSION) R.string.profile_source_session else R.string.profile_source_incident),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = HeatColors.TextSecondary,
            )
        }
    }
}

private const val RECENT = 5

@Composable
private fun Row(value: String?, labelRes: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value ?: stringResource(R.string.detail_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = HeatColors.TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.3f),
        )
    }
}
