package com.example.appdev.data.ai

import com.example.appdev.data.ThermalRepository
import com.example.appdev.data.battery.BatteryMonitor
import com.example.appdev.data.cooling.CoolingSessionManager
import com.example.appdev.data.cooling.PersonalCoolingProfileProvider
import com.example.appdev.data.demo.DemoController
import com.example.appdev.data.settings.SettingsRepository
import com.example.appdev.domain.ai.PhoneContext
import com.example.appdev.domain.analytics.InsightsEngine
import com.example.appdev.domain.cooling.CoolingRepository
import com.example.appdev.domain.incident.IncidentRepository
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.readiness.ReadinessEvaluator
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalAssessment
import com.example.appdev.platform.MonitoringController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.TimeZone

/**
 * Gathers the assistant's view of the phone at the moment a question is asked, reusing the same
 * repositories and calculations as the rest of the app, so the assistant never disagrees with the screens.
 * The clock is read once, so every age in the brief is measured against the same "now".
 */
class PhoneContextCollector(
    private val thermalRepository: ThermalRepository,
    private val settingsRepository: SettingsRepository,
    private val batteryMonitor: BatteryMonitor,
    private val demoController: DemoController,
    private val incidents: IncidentRepository,
    private val cooling: CoolingRepository,
    private val profiles: PersonalCoolingProfileProvider,
    private val monitoring: MonitoringController,
    private val coolingManagerFor: (ReadingSource) -> CoolingSessionManager,
    private val metricsReader: PhoneMetricsReader,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun collect(screen: String?): PhoneContext = withContext(Dispatchers.Default) {
        val now = clock()
        val settings = settingsRepository.settings.first()
        val demo = demoController.enabled.value
        val source = if (demo) ReadingSource.DEMO else ReadingSource.DEVICE

        // A fresh read of the live battery, not whatever was last cached, so the "age" is honest.
        val snapshot = if (demo) demoController.current.value else batteryMonitor.readNow()
        val system = thermalRepository.systemState.value
        val history = thermalRepository.history.value

        val assessment = ThermalAssessment.evaluate(snapshot, settings)
        val analysis = ThermalAnalysis.analyze(snapshot, history, settings.thresholdC, system)
        val profile = profiles.profile.first()
        val incidentList = incidents.observeIncidents(source).first()
        val sessionList = cooling.observeSessions(source).first()

        PhoneContext(
            nowMillis = now,
            timeZone = TimeZone.getDefault(),
            screen = screen,
            simulated = demo,
            assessment = assessment,
            analysis = analysis,
            system = system,
            readiness = ReadinessEvaluator.evaluate(settings, analysis, system, profile, assessment.temperatureC),
            profile = profile,
            insights = InsightsEngine.compute(incidentList, sessionList, now),
            recentIncidents = incidentList.take(RECENT_INCIDENTS),
            sessionStats = thermalRepository.sessionStats.value,
            // In Demo Mode the whole brief is simulated, so real device details are left out.
            device = if (demo) null else metricsReader.deviceInfo(),
            metrics = if (demo) null else metricsReader.metrics(),
            monitoringRunning = monitoring.isRunning.value,
            coolingSessionActive = coolingManagerFor(source).active.value != null,
        )
    }

    private companion object {
        const val RECENT_INCIDENTS = 5
    }
}
