package com.example.appdev.data.demo

import com.example.appdev.data.battery.BatterySnapshotSource
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.data.thermal.ThermalSystemSource
import com.example.appdev.domain.demo.DemoControls
import com.example.appdev.domain.demo.DemoSimulator
import com.example.appdev.domain.demo.DemoState
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.thermal.SystemThermalState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds Demo Mode state and produces simulated readings. It is deliberately passive: switching
 * the mode on or off, and cleaning up demo data, is orchestrated by [DemoModeManager].
 */
class DemoController(
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickMillis: Long = TICK_MILLIS,
    /** Simulated seconds advanced per tick. Normally real time; tests raise it to run quickly. */
    private val secondsPerTick: Float = tickMillis / 1000f,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Which data the app should show and record: demo readings while Demo Mode is on, else real ones. */
    private val _activeSource = MutableStateFlow(ReadingSource.DEVICE)
    val activeSource: StateFlow<ReadingSource> = _activeSource.asStateFlow()

    private val _controls = MutableStateFlow(DemoControls())
    val controls: StateFlow<DemoControls> = _controls.asStateFlow()

    private val _state = MutableStateFlow<DemoState?>(null)

    /** The simulator's internal state (scenario phase etc.), for display on the demo screen. */
    val state: StateFlow<DemoState?> = _state.asStateFlow()

    private val _current = MutableStateFlow<BatterySnapshot?>(null)

    /** Most recent simulated reading; null when Demo Mode is off. */
    val current: StateFlow<BatterySnapshot?> = _current.asStateFlow()

    internal fun setEnabled(on: Boolean) {
        if (!on) {
            _state.value = null
            _current.value = null
        }
        _enabled.value = on
        _activeSource.value = if (on) ReadingSource.DEMO else ReadingSource.DEVICE
    }

    fun update(transform: (DemoControls) -> DemoControls) {
        _controls.update(transform)
    }

    /** Emits one simulated reading immediately and then one per tick, for as long as it is collected. */
    fun snapshots(): Flow<BatterySnapshot> = flow {
        var state = DemoSimulator.initial(_controls.value)
        var threshold = settingsRepository.settings.first().thresholdC
        coroutineScope {
            launch { settingsRepository.settings.collect { threshold = it.thresholdC } }
            while (true) {
                val controls = _controls.value
                state = DemoSimulator.step(state, controls, secondsPerTick, threshold)
                val snapshot = DemoSimulator.toSnapshot(state, controls, clock())
                _state.value = state
                _current.value = snapshot
                emit(snapshot)
                delay(tickMillis)
            }
        }
    }

    /** Simulated Android thermal APIs derived from the current simulated temperature. */
    fun systemState(): Flow<SystemThermalState> =
        _current.filterNotNull().map { DemoSimulator.systemStateFor(it.temperatureC) }

    companion object {
        const val TICK_MILLIS = 1_000L
    }
}

/** Real battery readings normally; simulated ones while Demo Mode is on. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoAwareBatterySource(
    private val real: BatterySnapshotSource,
    private val demo: DemoController,
) : BatterySnapshotSource {
    override fun snapshots(): Flow<BatterySnapshot> =
        demo.enabled.flatMapLatest { on -> if (on) demo.snapshots() else real.snapshots() }
}

/** Android's thermal APIs normally; simulated ones while Demo Mode is on. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoAwareSystemSource(
    private val real: ThermalSystemSource,
    private val demo: DemoController,
) : ThermalSystemSource {
    override fun state(): Flow<SystemThermalState> =
        demo.enabled.flatMapLatest { on -> if (on) demo.systemState() else real.state() }
}
