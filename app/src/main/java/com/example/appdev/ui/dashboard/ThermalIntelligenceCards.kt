package com.example.appdev.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.appdev.R
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.thermal.Confidence
import com.example.appdev.domain.thermal.FloorReason
import com.example.appdev.domain.thermal.Prediction
import com.example.appdev.domain.thermal.RiskComponent
import com.example.appdev.domain.thermal.RiskFactor
import com.example.appdev.domain.thermal.RiskLevel
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalAssessment
import com.example.appdev.domain.thermal.ThermalRisk
import com.example.appdev.domain.thermal.ThermalStatus
import com.example.appdev.ui.components.Durations
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.components.labelRes
import com.example.appdev.ui.theme.HeatColors
import com.example.appdev.ui.theme.color
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Prediction

@Composable
fun PredictionCard(prediction: Prediction, settings: UserSettings) {
    val unit = settings.unit
    val (headline, detail, accent) = describe(prediction, unit)
    HeatCard(modifier = Modifier.fillMaxWidth(), spacing = 6.dp) {
        Text(
            stringResource(R.string.prediction_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = HeatColors.TextSecondary,
        )
        Text(headline, style = MaterialTheme.typography.titleLarge, color = accent)
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
        }
        Text(
            stringResource(R.string.prediction_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private data class PredictionText(val headline: String, val detail: String, val accent: Color)

@Composable
private fun describe(prediction: Prediction, unit: TemperatureUnit): PredictionText {
    @Composable fun rate(slope: Float) = stringResource(R.string.prediction_rate, Temperatures.formatDelta(slope, unit))
    return when (prediction) {
        Prediction.Unavailable -> PredictionText(
            stringResource(R.string.level_unknown), stringResource(R.string.prediction_unavailable), HeatColors.Unknown,
        )
        is Prediction.CollectingData -> PredictionText(
            stringResource(R.string.live_waiting), stringResource(R.string.prediction_collecting), HeatColors.TextPrimary,
        )
        is Prediction.AlreadyOverThreshold -> PredictionText(
            stringResource(R.string.level_critical),
            stringResource(R.string.prediction_over, Temperatures.formatDelta(prediction.overByC, unit)),
            HeatColors.Critical,
        )
        is Prediction.Stable -> PredictionText(
            stringResource(R.string.prediction_stable_headline),
            stringResource(R.string.prediction_stable, rate(prediction.slopeCPerMin)),
            HeatColors.Normal,
        )
        is Prediction.Cooling -> PredictionText(
            stringResource(R.string.prediction_cooling_headline),
            stringResource(R.string.prediction_cooling, rate(prediction.slopeCPerMin)),
            HeatColors.Normal,
        )
        is Prediction.Heating -> PredictionText(
            stringResource(R.string.prediction_heating_headline, formatEta(prediction.minutesToThreshold)),
            stringResource(
                R.string.prediction_heating_detail,
                rate(prediction.slopeCPerMin),
                stringResource(prediction.confidence.labelRes()),
            ),
            if (prediction.minutesToThreshold <= 5f) HeatColors.Critical else HeatColors.Elevated,
        )
        is Prediction.HeatingSlowly -> PredictionText(
            stringResource(R.string.prediction_slow_headline),
            stringResource(R.string.prediction_slow_detail, rate(prediction.slopeCPerMin)),
            HeatColors.Elevated,
        )
    }
}

private fun Confidence.labelRes(): Int = when (this) {
    Confidence.LOW -> R.string.confidence_low
    Confidence.MEDIUM -> R.string.confidence_medium
    Confidence.HIGH -> R.string.confidence_high
}

@Composable
internal fun formatEta(minutes: Float): String {
    if (minutes < 1f) return stringResource(R.string.prediction_less_than_minute)
    return Durations.format(ceil(minutes).toLong() * 60_000L).substringBefore(" 00 s")
}

// ---------------------------------------------------------------- Risk index

@Composable
fun RiskIndexCard(risk: ThermalRisk?, analysis: ThermalAnalysis, assessment: ThermalAssessment) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val level = risk?.level
    val color by animateColorAsState(level?.color() ?: HeatColors.Unknown, tween(500), label = "riskColor")

    HeatCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.risk_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = HeatColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (level != null) {
                Row(
                    modifier = Modifier.background(color.copy(alpha = 0.14f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(6.dp).background(color, CircleShape))
                    Text(stringResource(level.labelRes()), style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }

        if (risk == null) {
            Text(
                stringResource(R.string.risk_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
            )
            return@HeatCard
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(risk.index.toString(), style = MaterialTheme.typography.displayLarge, color = HeatColors.TextPrimary)
            Text(
                stringResource(R.string.risk_out_of),
                style = MaterialTheme.typography.titleMedium,
                color = HeatColors.TextSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        ProgressBar(fraction = risk.index / 100f, color = color, height = 8.dp)

        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 0.dp)) {
            Text(stringResource(if (expanded) R.string.risk_hide else R.string.risk_how))
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.risk_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = HeatColors.TextSecondary,
                )
                HorizontalDivider(color = HeatColors.OutlineSubtle)
                risk.components.forEach { component ->
                    ComponentRow(component, analysis, assessment, color)
                }
                HorizontalDivider(color = HeatColors.OutlineSubtle)
                if (risk.floors.isNotEmpty()) {
                    Text(
                        stringResource(R.string.risk_weighted, risk.weightedIndex.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = HeatColors.TextSecondary,
                    )
                    risk.floors.forEach { reason ->
                        Text(
                            stringResource(
                                if (reason == FloorReason.AT_THRESHOLD) R.string.risk_floor_threshold else R.string.risk_floor_system,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = HeatColors.Elevated,
                        )
                    }
                }
            }
        }
    }
}

private fun RiskLevel.labelRes(): Int = when (this) {
    RiskLevel.LOW -> R.string.risk_level_low
    RiskLevel.MODERATE -> R.string.risk_level_moderate
    RiskLevel.HIGH -> R.string.risk_level_high
    RiskLevel.CRITICAL -> R.string.risk_level_critical
}

@Composable
private fun ComponentRow(component: RiskComponent, analysis: ThermalAnalysis, assessment: ThermalAssessment, color: Color) {
    val unit = assessment.settings.unit
    val title = when (component.factor) {
        RiskFactor.TEMPERATURE -> R.string.factor_temperature
        RiskFactor.WARMING_RATE -> R.string.factor_warming_rate
        RiskFactor.TIME_TO_THRESHOLD -> R.string.factor_time
        RiskFactor.SYSTEM_STATUS -> R.string.factor_status
        RiskFactor.HEADROOM -> R.string.factor_headroom
        RiskFactor.CHARGING -> R.string.factor_charging
    }
    val input: String? = if (!component.available) {
        null
    } else {
        when (component.factor) {
            RiskFactor.TEMPERATURE -> assessment.temperatureC?.let {
                stringResource(
                    R.string.factor_temperature_input,
                    Temperatures.format(it, unit),
                    Temperatures.format(assessment.settings.thresholdC, unit),
                )
            }
            RiskFactor.WARMING_RATE -> analysis.trend?.let {
                val sign = if (it.slopeCPerMin >= 0) "+" else "−"
                sign + stringResource(R.string.factor_warming_rate_input, Temperatures.formatDelta(it.slopeCPerMin, unit))
            }
            RiskFactor.TIME_TO_THRESHOLD -> when (val p = analysis.prediction) {
                is Prediction.AlreadyOverThreshold -> stringResource(R.string.factor_time_input_over)
                is Prediction.Heating -> stringResource(R.string.factor_time_input, formatEta(p.minutesToThreshold))
                else -> stringResource(R.string.factor_time_input_none)
            }
            RiskFactor.CHARGING -> assessment.snapshot?.let {
                stringResource(it.chargeStatus.labelRes()) + " · " + stringResource(it.powerSource.labelRes())
            }
            // System-derived factors are explained on the Android thermal card below.
            RiskFactor.SYSTEM_STATUS, RiskFactor.HEADROOM -> null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.bodyMedium,
                color = if (component.available) HeatColors.TextPrimary else HeatColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (component.available) {
                    stringResource(
                        R.string.risk_points,
                        String.format(androidx.compose.ui.text.intl.Locale.current.platformLocale, "%.1f", component.points),
                        component.maxPoints.roundToInt().toString(),
                    )
                } else {
                    stringResource(R.string.risk_not_available)
                },
                style = MaterialTheme.typography.bodySmall,
                color = HeatColors.TextSecondary,
                textAlign = TextAlign.End,
            )
        }
        ProgressBar(
            fraction = if (component.available) component.points / component.maxPoints else 0f,
            color = color,
            height = 4.dp,
        )
        if (input != null) {
            Text(input, style = MaterialTheme.typography.bodySmall, color = HeatColors.TextTertiary)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(HeatColors.SurfaceHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(color),
        )
    }
}

// ---------------------------------------------------------------- System thermal

@Composable
fun SystemThermalCard(system: SystemThermalState) {
    HeatCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(
            stringResource(R.string.system_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = HeatColors.TextSecondary,
        )

        val notReported = stringResource(R.string.system_not_reported)
        val statusText = when {
            !system.statusApiAvailable -> stringResource(R.string.system_requires_10)
            system.status == null -> notReported
            else -> stringResource(system.status.labelRes())
        }
        SystemRow(
            label = stringResource(R.string.system_status_label),
            value = statusText,
            valueColor = system.status.color(),
            available = system.status != null,
        )

        val headroomText = when {
            !system.headroomApiAvailable -> stringResource(R.string.system_requires_11)
            system.headroomNow == null -> notReported
            else -> stringResource(R.string.system_headroom_value, (system.headroomNow * 100).roundToInt().toString())
        }
        SystemRow(
            label = stringResource(R.string.system_headroom_label),
            value = headroomText,
            valueColor = HeatColors.TextPrimary,
            available = system.headroomNow != null,
        )
        system.headroomNow?.let { ProgressBar(it, headroomColor(it), 4.dp) }
        system.headroomForecast?.let {
            SystemRow(
                label = stringResource(R.string.system_forecast_label),
                value = stringResource(R.string.system_headroom_value, (it * 100).roundToInt().toString()),
                valueColor = HeatColors.TextPrimary,
                available = true,
            )
        }
        Text(
            stringResource(R.string.system_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
        )
    }
}

@Composable
private fun SystemRow(label: String, value: String, valueColor: Color, available: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (available) valueColor else HeatColors.TextTertiary,
            textAlign = TextAlign.End,
        )
    }
}

internal fun ThermalStatus.labelRes(): Int = when (this) {
    ThermalStatus.NONE -> R.string.thermal_status_none
    ThermalStatus.LIGHT -> R.string.thermal_status_light
    ThermalStatus.MODERATE -> R.string.thermal_status_moderate
    ThermalStatus.SEVERE -> R.string.thermal_status_severe
    ThermalStatus.CRITICAL -> R.string.thermal_status_critical
    ThermalStatus.EMERGENCY -> R.string.thermal_status_emergency
    ThermalStatus.SHUTDOWN -> R.string.thermal_status_shutdown
}

internal fun ThermalStatus?.color(): Color = when (this) {
    null -> HeatColors.Unknown
    ThermalStatus.NONE -> HeatColors.Normal
    ThermalStatus.LIGHT -> HeatColors.Normal
    ThermalStatus.MODERATE -> HeatColors.Elevated
    ThermalStatus.SEVERE -> HeatColors.High
    ThermalStatus.CRITICAL, ThermalStatus.EMERGENCY, ThermalStatus.SHUTDOWN -> HeatColors.Critical
}

private fun headroomColor(value: Float): Color = when {
    value >= 1f -> HeatColors.Critical
    value >= 0.8f -> HeatColors.High
    value >= 0.5f -> HeatColors.Elevated
    else -> HeatColors.Normal
}
