package com.example.appdev.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.appdev.data.battery.BatterySnapshotSource
import com.example.appdev.data.cooling.CoolingSessionManager
import com.example.appdev.data.demo.DemoAwareBatterySource
import com.example.appdev.data.demo.DemoController
import com.example.appdev.data.demo.DemoModeManager
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.domain.FakeCoolingRepository
import com.example.appdev.domain.FakeIncidentRepository
import com.example.appdev.domain.cooling.CoolingSessionTracker
import com.example.appdev.domain.demo.DemoScenario
import com.example.appdev.domain.incident.IncidentTracker
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.snapshot
import com.example.appdev.platform.AlertPresenter
import com.example.appdev.platform.MonitoringCoordinator
import com.example.appdev.platform.MonitoringRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Called from the coordinator's thread and read from the test thread, so everything here is thread-safe. */
class FakePresenter : AlertPresenter {
    val alerts = java.util.concurrent.CopyOnWriteArrayList<BatterySnapshot>()
    private val cancelCount = java.util.concurrent.atomic.AtomicInteger()
    private val vibrationCount = java.util.concurrent.atomic.AtomicInteger()
    private val updateCount = java.util.concurrent.atomic.AtomicInteger()

    val cancels: Int get() = cancelCount.get()
    val vibrations: Int get() = vibrationCount.get()
    val monitoringUpdates: Int get() = updateCount.get()

    override fun showOverheatAlert(snapshot: BatterySnapshot, settings: UserSettings) { alerts += snapshot }
    override fun cancelOverheatAlert() { cancelCount.incrementAndGet() }
    override fun vibrate() { vibrationCount.incrementAndGet() }
    override fun updateMonitoring(snapshot: BatterySnapshot?, settings: UserSettings, overheating: Boolean) { updateCount.incrementAndGet() }
}

/**
 * Exercises Demo Mode end to end against the real pipeline classes (repository, coordinator,
 * trackers, runner), with only storage and notifications faked.
 */
class DemoPipelineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsRepository
    private val incidents = FakeIncidentRepository()
    private val cooling = FakeCoolingRepository()
    private val presenter = FakePresenter()
    private val realReadings = MutableSharedFlow<BatterySnapshot>(extraBufferCapacity = 16)
    private var serviceRunning = false

    private lateinit var demoController: DemoController
    private lateinit var thermalRepository: ThermalRepository
    private lateinit var demoMode: DemoModeManager

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "demo.preferences_pb") }
        settings = SettingsRepository(dataStore)

        // One simulated second per 10 ms tick, so a full heating cycle takes a couple of seconds.
        demoController = DemoController(settings, clock = System::currentTimeMillis, tickMillis = 10, secondsPerTick = 1f)
        val real = BatterySnapshotSource { realReadings }
        thermalRepository = ThermalRepository(
            source = DemoAwareBatterySource(real, demoController),
            settingsRepository = settings,
            scope = scope,
            sharingStarted = SharingStarted.Eagerly,
        )

        val deviceTracker = IncidentTracker(incidents, ReadingSource.DEVICE)
        val demoTracker = IncidentTracker(incidents, ReadingSource.DEMO)
        val demoCooling = CoolingSessionManager(
            ReadingSource.DEMO, CoolingSessionTracker(cooling, ReadingSource.DEMO), cooling, thermalRepository, scope,
        )
        val coordinator = MonitoringCoordinator(
            snapshots = thermalRepository.latestSnapshot,
            settings = settings.settings,
            trackers = mapOf(ReadingSource.DEVICE to deviceTracker, ReadingSource.DEMO to demoTracker),
            presenter = presenter,
            isServiceRunning = { serviceRunning },
        )
        val runner = MonitoringRunner(scope) { coordinator }
        demoMode = DemoModeManager(demoController, incidents, cooling, demoTracker, demoCooling, runner, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun awaitUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        withTimeout(timeoutMs) { while (!condition()) delay(20) }
    }

    @Test
    fun `demo controller emits simulated readings tagged as demo that follow the scenario`() = runBlocking {
        demoController.update { it.copy(scenario = DemoScenario.HEAT_UP, manualTempC = 34f) }
        val readings = withTimeout(10_000) { demoController.snapshots().take(5).toList() }
        assertTrue(readings.all { it.source == ReadingSource.DEMO })
        val temps = readings.map { it.temperatureC!! }
        assertEquals(temps.sorted(), temps) // warming
        assertTrue(temps.last() > temps.first())
    }

    @Test
    fun `a full demo cycle records a demo incident and alerts without touching real data`() = runBlocking {
        demoController.update { it.copy(scenario = DemoScenario.FULL_CYCLE, manualTempC = 34f) }
        demoMode.setEnabled(true)

        awaitUntil { presenter.alerts.isNotEmpty() }
        awaitUntil { incidents.allIncidents.any { !it.isActive } } // resolved after cooling
        // The coordinator stores the resolution first and then clears the notification, so wait for that too.
        awaitUntil { presenter.cancels >= 1 }

        assertEquals(1, presenter.alerts.size)
        assertEquals(ReadingSource.DEMO, presenter.alerts.single().source)
        assertTrue(presenter.cancels >= 1)
        assertTrue(presenter.vibrations >= 1)

        val stored = incidents.allIncidents
        assertEquals(1, stored.size)
        assertEquals(ReadingSource.DEMO, stored.single().source)
        assertEquals(1, incidents.observeIncidents(ReadingSource.DEMO).first().size)
        assertTrue(incidents.observeIncidents(ReadingSource.DEVICE).first().isEmpty())
        assertTrue(incidents.getActive(ReadingSource.DEVICE) == null)
        assertTrue(stored.single().peakTempC > UserSettings.DEFAULT_THRESHOLD_C)
    }

    @Test
    fun `leaving demo mode deletes demo data and leaves real data alone`() = runBlocking {
        // A real incident already in history.
        val realTracker = IncidentTracker(incidents, ReadingSource.DEVICE)
        realTracker.onReading(snapshot(41f, timestampMillis = 1_000), UserSettings())
        assertEquals(1, incidents.allIncidents.size)

        demoController.update { it.copy(scenario = DemoScenario.HEAT_UP, manualTempC = 38f) }
        demoMode.setEnabled(true)
        awaitUntil { incidents.allIncidents.any { it.source == ReadingSource.DEMO } }
        assertEquals(ReadingSource.DEMO, demoController.activeSource.value)

        demoMode.setEnabled(false)
        awaitUntil { incidents.allIncidents.none { it.source == ReadingSource.DEMO } }

        assertEquals(ReadingSource.DEVICE, demoController.activeSource.value)
        assertFalse(demoController.enabled.value)
        assertEquals(1, incidents.allIncidents.size)
        assertEquals(ReadingSource.DEVICE, incidents.allIncidents.single().source)
        assertEquals(1_000L, incidents.allIncidents.single().startMillis)
    }

    @Test
    fun `entering or leaving demo mode starts the graph and statistics from a clean slate`() = runBlocking {
        realReadings.emit(snapshot(33f, timestampMillis = 1_000))
        realReadings.emit(snapshot(34f, timestampMillis = 2_000))
        awaitUntil { thermalRepository.history.value.size == 2 }
        assertEquals(2, thermalRepository.sessionStats.value.sampleCount)

        demoController.update { it.copy(scenario = DemoScenario.MANUAL, manualTempC = 36f) }
        demoMode.setEnabled(true)
        awaitUntil { thermalRepository.latestSnapshot.value?.source == ReadingSource.DEMO }

        // No real readings remain in the demo history.
        awaitUntil { thermalRepository.history.value.isNotEmpty() }
        assertTrue(thermalRepository.history.value.all { it.timestampMillis > 100_000 })
        assertTrue(thermalRepository.sessionStats.value.minC!! >= 25f)
    }

    @Test
    fun `real readings keep flowing through the same coordinator when demo is off`() = runBlocking {
        val realTracker = IncidentTracker(incidents, ReadingSource.DEVICE)
        val coordinator = MonitoringCoordinator(
            snapshots = thermalRepository.latestSnapshot,
            settings = settings.settings,
            trackers = mapOf(ReadingSource.DEVICE to realTracker),
            presenter = presenter,
            isServiceRunning = { true },
        )
        coordinator.handle(snapshot(41f, timestampMillis = 5_000), UserSettings())
        assertEquals(1, presenter.alerts.size)
        assertEquals(ReadingSource.DEVICE, presenter.alerts.single().source)
        assertEquals(1, presenter.monitoringUpdates) // service running -> persistent notification refreshed
        assertEquals(ReadingSource.DEVICE, incidents.allIncidents.single().source)
    }

    @Test
    fun `the persistent notification is not touched when the service is not running`() = runBlocking {
        val coordinator = MonitoringCoordinator(
            snapshots = thermalRepository.latestSnapshot,
            settings = settings.settings,
            trackers = mapOf(ReadingSource.DEVICE to IncidentTracker(incidents, ReadingSource.DEVICE)),
            presenter = presenter,
            isServiceRunning = { false },
        )
        coordinator.handle(snapshot(36f, timestampMillis = 1_000), UserSettings())
        assertEquals(0, presenter.monitoringUpdates)
    }

    @Test
    fun `vibration respects the user setting`() = runBlocking {
        val coordinator = MonitoringCoordinator(
            snapshots = thermalRepository.latestSnapshot,
            settings = settings.settings,
            trackers = mapOf(ReadingSource.DEVICE to IncidentTracker(incidents, ReadingSource.DEVICE)),
            presenter = presenter,
            isServiceRunning = { false },
        )
        coordinator.handle(snapshot(41f, timestampMillis = 1_000), UserSettings(vibrationEnabled = false))
        assertEquals(1, presenter.alerts.size)
        assertEquals(0, presenter.vibrations)
    }
}
