package com.example.appdev.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.appdev.HeatGuardApp
import com.example.appdev.R
import com.example.appdev.data.demo.DemoModeManager
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.domain.demo.CyclePhase
import com.example.appdev.domain.demo.DemoControls
import com.example.appdev.domain.demo.DemoScenario
import com.example.appdev.domain.demo.DemoSimulator
import com.example.appdev.domain.demo.DemoState
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.thermal.TemperatureClassifier
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.components.labelRes
import com.example.appdev.ui.theme.HeatColors
import com.example.appdev.ui.theme.color
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DemoUiState(
    val enabled: Boolean = false,
    val controls: DemoControls = DemoControls(),
    val simState: DemoState? = null,
    val snapshot: BatterySnapshot? = null,
    val settings: UserSettings = UserSettings(),
)

class DemoViewModel(
    private val demoMode: DemoModeManager,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val controller = demoMode.controller

    private val demo = combine(controller.enabled, controller.controls, controller.state, controller.current) { e, c, s, snap ->
        listOf(e, c, s, snap)
    }

    val uiState: StateFlow<DemoUiState> = combine(demo, settingsRepository.settings) { d, settings ->
        DemoUiState(
            enabled = d[0] as Boolean,
            controls = d[1] as DemoControls,
            simState = d[2] as DemoState?,
            snapshot = d[3] as BatterySnapshot?,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DemoUiState())

    fun setEnabled(on: Boolean) = demoMode.setEnabled(on)
    fun resetData() = demoMode.resetData()
    fun setScenario(s: DemoScenario) = controller.update { it.copy(scenario = s) }
    fun setManualTemp(c: Float) = controller.update { it.copy(manualTempC = c.coerceIn(DemoSimulator.MIN_TEMP_C, DemoSimulator.MAX_TEMP_C)) }
    fun setCharging(on: Boolean) = controller.update { it.copy(charging = on) }
    fun setPowerSource(p: PowerSource) = controller.update { it.copy(powerSource = p) }
    fun setBattery(percent: Int) = controller.update { it.copy(batteryPercent = percent.coerceIn(0, 100)) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as HeatGuardApp).container
                DemoViewModel(c.demoMode, c.settingsRepository)
            }
        }
    }
}

@Composable
fun DemoRoute(onBack: () -> Unit, viewModel: DemoViewModel = viewModel(factory = DemoViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DemoScreen(
        state = state,
        onBack = onBack,
        onToggle = viewModel::setEnabled,
        onScenario = viewModel::setScenario,
        onManualTemp = viewModel::setManualTemp,
        onCharging = viewModel::setCharging,
        onPowerSource = viewModel::setPowerSource,
        onBattery = viewModel::setBattery,
        onReset = viewModel::resetData,
    )
}

@Composable
fun DemoScreen(
    state: DemoUiState,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onScenario: (DemoScenario) -> Unit,
    onManualTemp: (Float) -> Unit,
    onCharging: (Boolean) -> Unit,
    onPowerSource: (PowerSource) -> Unit,
    onBattery: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val unit = state.settings.unit
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back), tint = HeatColors.TextPrimary)
            }
            Column {
                Text(stringResource(R.string.demo_title), style = MaterialTheme.typography.titleLarge, color = HeatColors.TextPrimary)
                Text(stringResource(R.string.demo_subtitle), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeatCard(Modifier.fillMaxWidth(), spacing = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.demo_toggle_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = HeatColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = HeatColors.OnAccent,
                            checkedTrackColor = HeatColors.Accent,
                            uncheckedThumbColor = HeatColors.TextSecondary,
                            uncheckedTrackColor = HeatColors.SurfaceHighest,
                            uncheckedBorderColor = HeatColors.Outline,
                        ),
                    )
                }
                Text(stringResource(R.string.demo_toggle_body), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
            }

            if (!state.enabled) {
                Text(
                    stringResource(R.string.demo_off_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeatColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                return@Column
            }

            ReadoutCard(state)
            ScenarioCard(state, onScenario)
            TemperatureCard(state, onManualTemp)
            PowerCard(state, onCharging, onPowerSource, onBattery)

            HeatCard(Modifier.fillMaxWidth(), spacing = 8.dp) {
                Text(stringResource(R.string.demo_data_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
                Text(stringResource(R.string.demo_data_body), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.demo_reset)) }
                OutlinedButton(onClick = { onToggle(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.demo_exit)) }
            }
        }
    }
}

@Composable
private fun ReadoutCard(state: DemoUiState) {
    val unit = state.settings.unit
    val temp = state.snapshot?.temperatureC
    val level = TemperatureClassifier.classify(temp, state.settings.thresholdC, state.settings.warningMarginC)
    HeatCard(Modifier.fillMaxWidth(), spacing = 6.dp) {
        Text(stringResource(R.string.demo_readout_title).uppercase(), style = MaterialTheme.typography.labelSmall, color = HeatColors.TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(12.dp).background(level.color(), CircleShape))
            Text(
                temp?.let { Temperatures.format(it, unit) } ?: "--",
                style = MaterialTheme.typography.headlineMedium,
                color = HeatColors.TextPrimary,
            )
            Text(stringResource(level.labelRes()), style = MaterialTheme.typography.titleMedium, color = level.color())
        }
        phaseText(state.simState)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
        }
    }
}

@Composable
private fun phaseText(sim: DemoState?): String? {
    if (sim == null || sim.scenario != DemoScenario.FULL_CYCLE) return null
    return when (sim.phase) {
        CyclePhase.WARMING -> stringResource(R.string.demo_phase_warming)
        CyclePhase.HOLDING -> stringResource(R.string.demo_phase_holding, sim.holdRemainingSec.toInt())
        CyclePhase.COOLING -> stringResource(R.string.demo_phase_cooling)
        CyclePhase.DONE -> stringResource(R.string.demo_phase_done)
    }
}

private fun DemoScenario.titleRes() = when (this) {
    DemoScenario.MANUAL -> R.string.demo_scenario_manual
    DemoScenario.HEAT_UP -> R.string.demo_scenario_heat_up
    DemoScenario.COOL_DOWN -> R.string.demo_scenario_cool_down
    DemoScenario.FULL_CYCLE -> R.string.demo_scenario_full_cycle
    DemoScenario.CHARGING_HEAT -> R.string.demo_scenario_charging_heat
}

private fun DemoScenario.bodyRes() = when (this) {
    DemoScenario.MANUAL -> R.string.demo_scenario_manual_body
    DemoScenario.HEAT_UP -> R.string.demo_scenario_heat_up_body
    DemoScenario.COOL_DOWN -> R.string.demo_scenario_cool_down_body
    DemoScenario.FULL_CYCLE -> R.string.demo_scenario_full_cycle_body
    DemoScenario.CHARGING_HEAT -> R.string.demo_scenario_charging_heat_body
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = HeatColors.Accent,
    selectedLabelColor = HeatColors.OnAccent,
    containerColor = HeatColors.SurfaceRaised,
    labelColor = HeatColors.TextSecondary,
)

@Composable
private fun ScenarioCard(state: DemoUiState, onScenario: (DemoScenario) -> Unit) {
    HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(stringResource(R.string.demo_scenario_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoScenario.entries.forEach { scenario ->
                FilterChip(
                    selected = scenario == state.controls.scenario,
                    onClick = { onScenario(scenario) },
                    label = { Text(stringResource(scenario.titleRes())) },
                    colors = chipColors(),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = scenario == state.controls.scenario,
                        borderColor = HeatColors.Outline,
                        selectedBorderColor = HeatColors.Accent,
                    ),
                )
            }
        }
        Text(stringResource(state.controls.scenario.bodyRes()), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
    }
}

@Composable
private fun TemperatureCard(state: DemoUiState, onManualTemp: (Float) -> Unit) {
    val unit = state.settings.unit
    val manual = state.controls.scenario == DemoScenario.MANUAL
    val value = state.controls.manualTempC
    HeatCard(Modifier.fillMaxWidth(), spacing = 8.dp) {
        Text(stringResource(R.string.demo_temp_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onManualTemp(value - 0.5f) }, enabled = manual) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(
                Temperatures.format(value, unit),
                style = MaterialTheme.typography.headlineMedium,
                color = if (manual) HeatColors.Accent else HeatColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = { onManualTemp(value + 0.5f) }, enabled = manual) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
        Slider(
            value = value,
            onValueChange = onManualTemp,
            enabled = manual,
            valueRange = DemoSimulator.MIN_TEMP_C..DemoSimulator.MAX_TEMP_C,
            steps = ((DemoSimulator.MAX_TEMP_C - DemoSimulator.MIN_TEMP_C) / 0.5f).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = HeatColors.Accent,
                activeTrackColor = HeatColors.Accent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                inactiveTrackColor = HeatColors.SurfaceHighest,
            ),
        )
        if (!manual) {
            Text(stringResource(R.string.demo_temp_auto), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextTertiary)
        }
    }
}

@Composable
private fun PowerCard(
    state: DemoUiState,
    onCharging: (Boolean) -> Unit,
    onPowerSource: (PowerSource) -> Unit,
    onBattery: (Int) -> Unit,
) {
    val controls = state.controls
    val chargingForced = controls.scenario == DemoScenario.CHARGING_HEAT
    HeatCard(Modifier.fillMaxWidth(), spacing = 8.dp) {
        Text(stringResource(R.string.demo_power_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.demo_charging), style = MaterialTheme.typography.bodyLarge, color = HeatColors.TextPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = controls.effectiveCharging,
                onCheckedChange = onCharging,
                enabled = !chargingForced,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = HeatColors.OnAccent,
                    checkedTrackColor = HeatColors.Accent,
                    uncheckedThumbColor = HeatColors.TextSecondary,
                    uncheckedTrackColor = HeatColors.SurfaceHighest,
                    uncheckedBorderColor = HeatColors.Outline,
                ),
            )
        }
        if (controls.effectiveCharging) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PowerSource.USB, PowerSource.AC, PowerSource.WIRELESS).forEach { source ->
                    FilterChip(
                        selected = controls.powerSource == source,
                        onClick = { onPowerSource(source) },
                        label = { Text(stringResource(source.labelRes())) },
                        colors = chipColors(),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = controls.powerSource == source,
                            borderColor = HeatColors.Outline,
                            selectedBorderColor = HeatColors.Accent,
                        ),
                    )
                }
            }
        }
        Text(stringResource(R.string.demo_battery_level, controls.batteryPercent), style = MaterialTheme.typography.bodyMedium, color = HeatColors.TextSecondary)
        Slider(
            value = controls.batteryPercent.toFloat(),
            onValueChange = { onBattery(it.toInt()) },
            valueRange = 1f..100f,
            colors = SliderDefaults.colors(
                thumbColor = HeatColors.Accent,
                activeTrackColor = HeatColors.Accent,
                inactiveTrackColor = HeatColors.SurfaceHighest,
            ),
        )
    }
}
