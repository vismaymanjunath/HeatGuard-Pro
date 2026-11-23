package com.example.appdev.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appdev.R
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.components.labelRes
import com.example.appdev.ui.theme.HeatColors
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    onOpenDemo: () -> Unit,
    onOpenAssistant: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val demoEnabled by viewModel.demoEnabled.collectAsStateWithLifecycle()
    val loaded = settings
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HeatColors.Accent)
        }
    } else {
        SettingsScreen(
            settings = loaded,
            onThresholdChange = viewModel::setThreshold,
            onWarningMarginChange = viewModel::setWarningMargin,
            onProfileChange = viewModel::setProfile,
            onHysteresisChange = viewModel::setHysteresis,
            onNotificationsChange = viewModel::setNotificationsEnabled,
            onVibrationChange = viewModel::setVibrationEnabled,
            onUnitChange = viewModel::setUnit,
            onReset = viewModel::resetToDefaults,
            demoEnabled = demoEnabled,
            onDemoToggle = viewModel::setDemoEnabled,
            onOpenDemo = onOpenDemo,
            onOpenAssistant = onOpenAssistant,
        )
    }
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onThresholdChange: (Float) -> Unit,
    onWarningMarginChange: (Float) -> Unit,
    onProfileChange: (com.example.appdev.domain.profile.ProfileType?) -> Unit,
    onHysteresisChange: (Float) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onUnitChange: (TemperatureUnit) -> Unit,
    onReset: () -> Unit,
    demoEnabled: Boolean,
    onDemoToggle: (Boolean) -> Unit,
    onOpenDemo: () -> Unit,
    onOpenAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = settings.unit
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = HeatColors.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
            )
        }

        HeatCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
            SettingHeader(
                stringResource(R.string.settings_profile_title),
                settings.activeProfile?.let {
                    stringResource(
                        R.string.settings_profile_body_active,
                        stringResource(it.labelRes()),
                    )
                } ?: stringResource(R.string.settings_profile_body_custom),
            )
            com.example.appdev.ui.components.ProfileSelector(active = settings.activeProfile, onSelect = onProfileChange)
        }

        StepperSliderSetting(
            title = stringResource(R.string.settings_threshold_title),
            body = stringResource(R.string.settings_threshold_body),
            valueC = settings.thresholdC,
            minC = UserSettings.MIN_THRESHOLD_C,
            maxC = UserSettings.MAX_THRESHOLD_C,
            stepC = UserSettings.THRESHOLD_STEP_C,
            accent = HeatColors.Critical,
            format = { Temperatures.format(it, unit) },
            onCommit = onThresholdChange,
        )

        StepperSliderSetting(
            title = stringResource(R.string.settings_margin_title),
            body = stringResource(R.string.settings_margin_body),
            valueC = settings.warningMarginC,
            minC = UserSettings.MIN_WARNING_MARGIN_C,
            maxC = UserSettings.MAX_WARNING_MARGIN_C,
            stepC = UserSettings.WARNING_MARGIN_STEP_C,
            accent = HeatColors.Elevated,
            format = { Temperatures.formatDelta(it, unit) },
            onCommit = onWarningMarginChange,
        )

        StepperSliderSetting(
            title = stringResource(R.string.settings_hysteresis_title),
            body = stringResource(R.string.settings_hysteresis_body),
            valueC = settings.hysteresisC,
            minC = UserSettings.MIN_HYSTERESIS_C,
            maxC = UserSettings.MAX_HYSTERESIS_C,
            stepC = UserSettings.HYSTERESIS_STEP_C,
            accent = HeatColors.Normal,
            format = { Temperatures.formatDelta(it, unit) },
            onCommit = onHysteresisChange,
        )

        HeatCard(modifier = Modifier.fillMaxWidth(), spacing = 4.dp) {
            Text(
                stringResource(R.string.settings_alerts_title),
                style = MaterialTheme.typography.titleMedium,
                color = HeatColors.TextPrimary,
            )
            SwitchRow(
                title = stringResource(R.string.settings_notifications_title),
                body = stringResource(R.string.settings_notifications_body),
                checked = settings.notificationsEnabled,
                onCheckedChange = onNotificationsChange,
            )
            SwitchRow(
                title = stringResource(R.string.settings_vibration_title),
                body = stringResource(R.string.settings_vibration_body),
                checked = settings.vibrationEnabled,
                onCheckedChange = onVibrationChange,
            )
        }

        UnitSetting(selected = unit, onSelect = onUnitChange)

        BandPreview(settings)

        com.example.appdev.ui.assistant.AiAssistantSettingsCard(onOpenAssistant)

        IntegrationsCard()

        HeatCard(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
            SwitchRow(
                title = stringResource(R.string.demo_settings_title),
                body = stringResource(R.string.demo_settings_body),
                checked = demoEnabled,
                onCheckedChange = onDemoToggle,
            )
            OutlinedButton(onClick = onOpenDemo, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.demo_open_controls))
            }
        }

        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_reset))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = HeatColors.TextPrimary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HeatColors.OnAccent,
                checkedTrackColor = HeatColors.Accent,
                uncheckedThumbColor = HeatColors.TextSecondary,
                uncheckedTrackColor = HeatColors.SurfaceHighest,
                uncheckedBorderColor = HeatColors.Outline,
            ),
        )
    }
}

@Composable
private fun SettingHeader(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        Text(body, style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
    }
}

/**
 * Slider plus ± buttons for precise adjustment. The slider keeps a local draft while dragging
 * and persists only when released, avoiding a DataStore write on every frame.
 */
@Composable
private fun StepperSliderSetting(
    title: String,
    body: String,
    valueC: Float,
    minC: Float,
    maxC: Float,
    stepC: Float,
    accent: Color,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var draft by remember(valueC) { mutableFloatStateOf(valueC) }
    val decreaseLabel = stringResource(R.string.decrease)
    val increaseLabel = stringResource(R.string.increase)

    HeatCard(modifier = Modifier.fillMaxWidth()) {
        SettingHeader(title, body)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onCommit(draft - stepC) },
                enabled = draft > minC,
                modifier = Modifier.semantics { contentDescription = "$decreaseLabel $title" },
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(
                text = format(draft),
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = { onCommit(draft + stepC) },
                enabled = draft < maxC,
                modifier = Modifier.semantics { contentDescription = "$increaseLabel $title" },
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
        Slider(
            modifier = Modifier.semantics { contentDescription = title },
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onCommit(draft) },
            valueRange = minC..maxC,
            steps = ((maxC - minC) / stepC).roundToInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                inactiveTrackColor = HeatColors.SurfaceHighest,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(format(minC), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextTertiary)
            Spacer(Modifier.weight(1f))
            Text(format(maxC), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextTertiary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitSetting(selected: TemperatureUnit, onSelect: (TemperatureUnit) -> Unit) {
    HeatCard(modifier = Modifier.fillMaxWidth()) {
        SettingHeader(
            stringResource(R.string.settings_unit_title),
            stringResource(R.string.settings_unit_body),
        )
        val units = TemperatureUnit.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            units.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = unit == selected,
                    onClick = { onSelect(unit) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = units.size),
                ) {
                    Text(unit.symbol)
                }
            }
        }
    }
}

@Composable
private fun BandPreview(settings: UserSettings) {
    val unit = settings.unit
    val warn = Temperatures.format(settings.warningStartC, unit)
    val threshold = Temperatures.format(settings.thresholdC, unit)
    HeatCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(
            stringResource(R.string.settings_preview_title),
            style = MaterialTheme.typography.titleMedium,
            color = HeatColors.TextPrimary,
        )
        BandRow(HeatColors.Normal, stringResource(R.string.level_normal), stringResource(R.string.settings_band_normal, warn))
        BandRow(
            HeatColors.Elevated,
            stringResource(R.string.level_elevated),
            stringResource(R.string.settings_band_elevated, warn, threshold),
        )
        BandRow(
            HeatColors.Critical,
            stringResource(R.string.level_critical),
            stringResource(R.string.settings_band_critical, threshold),
        )
    }
}

@Composable
private fun BandRow(color: Color, label: String, range: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(range, style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
    }
}

@Composable
private fun IntegrationsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var message by remember { mutableStateOf<Int?>(null) }
    HeatCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        SettingHeader(
            stringResource(R.string.integrations_title),
            stringResource(R.string.integrations_body),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    message = when (com.example.appdev.platform.Integrations.requestPinWidget(context)) {
                        com.example.appdev.platform.PinWidgetResult.REQUESTED -> R.string.integrations_widget_requested
                        com.example.appdev.platform.PinWidgetResult.UNSUPPORTED -> R.string.integrations_widget_manual
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.integrations_add_widget)) }
            OutlinedButton(
                onClick = {
                    com.example.appdev.platform.Integrations.requestAddTile(context) { result ->
                        message = when (result) {
                            com.example.appdev.platform.AddTileResult.ADDED -> R.string.integrations_tile_added
                            com.example.appdev.platform.AddTileResult.ALREADY_PRESENT -> R.string.integrations_tile_present
                            com.example.appdev.platform.AddTileResult.DECLINED -> R.string.integrations_tile_declined
                            com.example.appdev.platform.AddTileResult.MANUAL -> R.string.integrations_tile_manual
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.integrations_add_tile)) }
        }
        message?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
        }
    }
}
