package com.example.appdev.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.appdev.data.battery.BatterySnapshotSource
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.snapshot
import com.example.appdev.domain.thermal.TemperatureLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsAndThermalRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            java.io.File(tmp.root, "test.preferences_pb")
        }
        settings = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `settings default to spec values`() = runBlocking {
        assertEquals(UserSettings(), settings.settings.first())
    }

    @Test
    fun `settings persist and are sanitised on write`() = runBlocking {
        settings.setThreshold(43.2f)
        settings.setWarningMargin(50f)
        settings.setUnit(TemperatureUnit.FAHRENHEIT)

        val stored = settings.settings.first()
        assertEquals(43f, stored.thresholdC, 0f)
        assertEquals(UserSettings.MAX_WARNING_MARGIN_C, stored.warningMarginC, 0f)
        assertEquals(TemperatureUnit.FAHRENHEIT, stored.unit)
    }

    @Test
    fun `alert preferences persist and are sanitised`() = runBlocking {
        settings.setHysteresis(3.3f)
        settings.setNotificationsEnabled(false)
        settings.setVibrationEnabled(false)
        settings.setMonitoringEnabled(true)

        val stored = settings.settings.first()
        assertEquals(3.5f, stored.hysteresisC, 0f)
        assertEquals(false, stored.notificationsEnabled)
        assertEquals(false, stored.vibrationEnabled)
        assertEquals(true, stored.monitoringEnabled)
        assertEquals(36.5f, stored.clearBelowC, 0f)

        settings.setHysteresis(99f)
        assertEquals(UserSettings.MAX_HYSTERESIS_C, settings.settings.first().hysteresisC, 0f)
    }

    @Test
    fun `selecting a profile applies its values and edits go to that profile only`() = runBlocking {
        settings.setThreshold(41f) // Custom value

        settings.setActiveProfile(com.example.appdev.domain.profile.ProfileType.GAMING)
        var s = settings.settings.first()
        assertEquals(com.example.appdev.domain.profile.ProfileType.GAMING, s.activeProfile)
        assertEquals(43f, s.thresholdC, 0f) // gaming default, not the custom 41

        settings.setThreshold(45f)
        settings.setVibrationEnabled(false)
        s = settings.settings.first()
        assertEquals(45f, s.thresholdC, 0f)
        assertEquals(false, s.vibrationEnabled)

        settings.setActiveProfile(com.example.appdev.domain.profile.ProfileType.CHARGING)
        assertEquals(38f, settings.settings.first().thresholdC, 0f) // untouched by gaming edits

        settings.setActiveProfile(null)
        s = settings.settings.first()
        assertEquals(null, s.activeProfile)
        assertEquals(41f, s.thresholdC, 0f) // custom value preserved
        assertEquals(true, s.vibrationEnabled)

        settings.setActiveProfile(com.example.appdev.domain.profile.ProfileType.GAMING)
        assertEquals(45f, settings.settings.first().thresholdC, 0f) // gaming edit persisted
    }

    @Test
    fun `reset clears profile edits and returns to custom`() = runBlocking {
        settings.setActiveProfile(com.example.appdev.domain.profile.ProfileType.WORK)
        settings.setThreshold(50f)
        settings.resetToDefaults()
        assertEquals(UserSettings(), settings.settings.first())
        settings.setActiveProfile(com.example.appdev.domain.profile.ProfileType.WORK)
        assertEquals(40f, settings.settings.first().thresholdC, 0f)
    }

    @Test
    fun `reset keeps the monitoring preference`() = runBlocking {
        settings.setMonitoringEnabled(true)
        settings.setHysteresis(4f)
        settings.resetToDefaults()
        val stored = settings.settings.first()
        assertEquals(UserSettings.DEFAULT_HYSTERESIS_C, stored.hysteresisC, 0f)
        assertEquals(true, stored.monitoringEnabled)
    }

    @Test
    fun `reset restores defaults`() = runBlocking {
        settings.setThreshold(35f)
        settings.setUnit(TemperatureUnit.FAHRENHEIT)
        settings.resetToDefaults()
        assertEquals(UserSettings(), settings.settings.first())
    }

    @Test
    fun `repository classifies readings against current settings and tracks session`() = runBlocking {
        val readings = MutableSharedFlow<com.example.appdev.domain.model.BatterySnapshot>(replay = 0)
        val source = BatterySnapshotSource { readings }
        val repository = ThermalRepository(source, settings, scope, sharingStarted = SharingStarted.Eagerly)
        settings.setThreshold(38f)

        withTimeout(5_000) {
            // Wait for the eager collector to subscribe before emitting.
            readings.subscriptionCount.first { it > 0 }
            readings.emit(snapshot(36f, timestampMillis = 1))
            val elevated = repository.assessment.first { it.temperatureC == 36f && it.settings.thresholdC == 38f }
            assertEquals(TemperatureLevel.ELEVATED, elevated.level)

            readings.emit(snapshot(39.5f, timestampMillis = 2))
            val critical = repository.assessment.first { it.temperatureC == 39.5f }
            assertEquals(TemperatureLevel.CRITICAL, critical.level)

            val stats = repository.sessionStats.first { it.sampleCount == 2 }
            assertEquals(36f, stats.minC!!, 0f)
            assertEquals(39.5f, stats.maxC!!, 0f)
        }
    }
}
