package com.example.appdev.domain

import com.example.appdev.data.cooling.PersonalCoolingProfileProvider
import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.domain.cooling.PersonalCoolingProfile
import com.example.appdev.domain.cooling.PersonalCoolingProfileCalculator
import com.example.appdev.domain.cooling.RecoverySource
import com.example.appdev.domain.cooling.SessionOutcome
import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.readiness.CoolingEstimate
import com.example.appdev.domain.readiness.EstimateBasis
import com.example.appdev.domain.readiness.ReadinessEvaluator
import com.example.appdev.domain.readiness.ReadinessReason
import com.example.appdev.domain.readiness.ReadinessStatus
import com.example.appdev.domain.readiness.TrendDirection
import com.example.appdev.domain.thermal.Confidence
import com.example.appdev.domain.thermal.Prediction
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalStatus
import com.example.appdev.domain.thermal.TrendEstimate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalProfileAndReadinessTest {

    private val minute = 60_000L

    // ---------------------------------------------------------------- helpers

    private fun session(
        startMin: Long,
        durationSec: Long,
        from: Float,
        to: Float,
        outcome: SessionOutcome? = SessionOutcome.RECOVERED,
        source: ReadingSource = ReadingSource.DEVICE,
    ) = CoolingSession(
        source = source, startMillis = startMin * minute,
        endMillis = if (outcome == null) null else startMin * minute + durationSec * 1000,
        lastSeenMillis = startMin * minute + durationSec * 1000,
        startTempC = from, lastTempC = to, minTempC = to, targetTempC = 38f,
        startBatteryPercent = 70, startChargeStatus = ChargeStatus.DISCHARGING,
        startPowerSource = PowerSource.NONE, outcome = outcome,
    )

    private fun incident(
        peakMin: Long,
        coolSec: Long,
        peak: Float,
        end: Float?,
        resolved: Boolean = true,
        source: ReadingSource = ReadingSource.DEVICE,
    ) = Incident(
        source = source, startMillis = peakMin * minute - minute,
        endMillis = if (resolved) peakMin * minute + coolSec * 1000 else null,
        lastSeenMillis = peakMin * minute + coolSec * 1000,
        initialTempC = 40f, peakTempC = peak, peakMillis = peakMin * minute,
        endTempC = if (resolved) end else null, thresholdC = 40f,
        startBatteryPercent = 80, endBatteryPercent = null,
        startChargeStatus = ChargeStatus.DISCHARGING, startPowerSource = PowerSource.NONE,
    )

    /** Three recovered sessions: rates 1.5, 1.25 and 1.0 °C/min -> mean 1.25. */
    private fun threeSessions() = listOf(
        session(10, 120, 41f, 38f),
        session(20, 240, 43f, 38f),
        session(30, 180, 40f, 37f),
    )

    private val enoughHistory = PersonalCoolingProfileCalculator.compute(threeSessions(), emptyList())

    // ---------------------------------------------------------------- personal profile

    @Test
    fun `statistics are computed from recovered sessions`() {
        val p = enoughHistory
        assertTrue(p.hasEnoughData)
        assertEquals(3, p.eventCount)
        assertEquals(1.25f, p.avgRateCPerMin!!, 0.001f)
        assertEquals(180_000L, p.avgRecoveryMillis)
        assertEquals((3f + 5f + 3f) / 3f, p.avgDropC!!, 0.001f)
        assertEquals(120_000L, p.fastestRecovery!!.durationMillis)
        assertEquals(3, p.sessionCount)
        assertEquals(0, p.incidentCount)
    }

    @Test
    fun `fewer than the minimum recoveries gives no statistics`() {
        val p = PersonalCoolingProfileCalculator.compute(threeSessions().take(2), emptyList())
        assertFalse(p.hasEnoughData)
        assertEquals(2, p.eventCount) // still reported honestly
        assertNull(p.avgRateCPerMin)
        assertNull(p.avgRecoveryMillis)
        assertNull(p.avgDropC)
        assertNull(p.fastestRecovery)
        assertNull(p.estimateRecoveryMillis(42f, 38f))
        assertFalse(PersonalCoolingProfile.Empty.hasEnoughData)
    }

    @Test
    fun `incident cool-downs count alongside sessions`() {
        val p = PersonalCoolingProfileCalculator.compute(
            sessions = listOf(session(10, 120, 41f, 38f), session(20, 240, 43f, 38f)),
            incidents = listOf(incident(peakMin = 30, coolSec = 150, peak = 42f, end = 38f)),
        )
        assertTrue(p.hasEnoughData)
        assertEquals(2, p.sessionCount)
        assertEquals(1, p.incidentCount)
        assertEquals(RecoverySource.INCIDENT, p.events.first().source) // newest first
        // incident: 4 degrees in 2.5 min = 1.6 per minute
        assertEquals((1.5f + 1.25f + 1.6f) / 3f, p.avgRateCPerMin!!, 0.001f)
    }

    @Test
    fun `unfinished or unreliable recoveries are never counted`() {
        val p = PersonalCoolingProfileCalculator.compute(
            sessions = listOf(
                session(1, 120, 41f, 38f, outcome = SessionOutcome.STOPPED),
                session(2, 120, 41f, 38f, outcome = SessionOutcome.INTERRUPTED),
                session(3, 120, 41f, 39f, outcome = null), // still running
                session(4, 20, 41f, 38f), // too short to be meaningful
                session(5, 120, 38.3f, 38f), // drop under half a degree
            ),
            incidents = listOf(
                incident(10, 120, 42f, 38f, resolved = false), // still open
                incident(11, 120, 42f, null), // closed after a monitoring gap: end unknown
                incident(12, 10, 42f, 38f), // too short
            ),
        )
        assertEquals(0, p.eventCount)
        assertFalse(p.hasEnoughData)
    }

    @Test
    fun `events are listed newest first`() {
        val p = PersonalCoolingProfileCalculator.compute(threeSessions().shuffled(), emptyList())
        assertEquals(p.events.map { it.startMillis }.sortedDescending(), p.events.map { it.startMillis })
    }

    @Test
    fun `recovery estimate scales with how far there is to cool`() {
        // Average rate 1.25 C/min.
        assertEquals(192_000L, enoughHistory.estimateRecoveryMillis(42f, 38f)) // 4 / 1.25 = 3.2 min
        assertEquals(96_000L, enoughHistory.estimateRecoveryMillis(40f, 38f)) // 2 / 1.25 = 1.6 min
        assertEquals(0L, enoughHistory.estimateRecoveryMillis(38f, 38f))
        assertEquals(0L, enoughHistory.estimateRecoveryMillis(35f, 38f))
    }

    @Test
    fun `provider builds the profile from stored data and follows the active source`() = runBlocking {
        val incidents = FakeIncidentRepository()
        val sessions = FakeCoolingRepository()
        threeSessions().forEach { sessions.start(it) }
        sessions.start(session(40, 120, 41f, 38f, source = ReadingSource.DEMO)) // must be invisible to real mode
        incidents.start(incident(50, 120, 42f, 38f))

        val active = MutableStateFlow(ReadingSource.DEVICE)
        val provider = PersonalCoolingProfileProvider(incidents, sessions, active)

        val real = provider.profile.first()
        assertEquals(4, real.eventCount) // 3 sessions + 1 incident, no demo
        assertTrue(real.events.none { it.startMillis == 40 * minute })

        active.value = ReadingSource.DEMO
        val demo = provider.profile.first { it.eventCount == 1 }
        assertFalse(demo.hasEnoughData)
        assertEquals(1, demo.sessionCount)
    }

    // ---------------------------------------------------------------- readiness

    private val settings = UserSettings(thresholdC = 40f, warningMarginC = 3f) // warning from 37 C

    private fun trend(slope: Float) = TrendEstimate(slope, rSquared = 0.95f, sampleCount = 10, spanMillis = 240_000)

    private fun analysis(slope: Float? = 0f, prediction: Prediction = Prediction.Stable(0f)) =
        ThermalAnalysis(prediction, slope?.let(::trend), risk = null)

    private fun system(status: ThermalStatus?) =
        SystemThermalState(statusApiAvailable = status != null, status = status)

    private fun evaluate(
        temp: Float?,
        analysis: ThermalAnalysis = analysis(),
        system: SystemThermalState = SystemThermalState.Unknown,
        profile: PersonalCoolingProfile = PersonalCoolingProfile.Empty,
    ) = ReadinessEvaluator.evaluate(settings, analysis, system, profile, temp)

    @Test
    fun `a cool steady phone is ready`() {
        val r = evaluate(33f)
        assertEquals(ReadinessStatus.READY, r.status)
        assertEquals(listOf(ReadinessReason.AllClear), r.reasons)
        assertEquals(CoolingEstimate.NotNeeded, r.cooling)
        assertEquals(TrendDirection.STEADY, r.trend)
    }

    @Test
    fun `inside the warning band means caution`() {
        val r = evaluate(38f)
        assertEquals(ReadinessStatus.CAUTION, r.status)
        assertTrue(ReadinessReason.InWarningBand in r.reasons)
    }

    @Test
    fun `at or above the threshold means cool down first`() {
        val r = evaluate(40f)
        assertEquals(ReadinessStatus.COOL_DOWN_FIRST, r.status)
        assertTrue(r.reasons.first() is ReadinessReason.AboveThreshold)
        assertEquals(ReadinessStatus.COOL_DOWN_FIRST, evaluate(44.5f).status)
    }

    @Test
    fun `android thermal status can override a cool battery reading`() {
        assertEquals(ReadinessStatus.COOL_DOWN_FIRST, evaluate(33f, system = system(ThermalStatus.SEVERE)).status)
        assertEquals(ReadinessStatus.COOL_DOWN_FIRST, evaluate(33f, system = system(ThermalStatus.CRITICAL)).status)
        val moderate = evaluate(33f, system = system(ThermalStatus.MODERATE))
        assertEquals(ReadinessStatus.CAUTION, moderate.status)
        assertTrue(moderate.reasons.any { it is ReadinessReason.SystemThermal })
        assertEquals(ReadinessStatus.READY, evaluate(33f, system = system(ThermalStatus.LIGHT)).status)
        assertEquals(ReadinessStatus.READY, evaluate(33f, system = system(ThermalStatus.NONE)).status)
        assertEquals(ThermalStatus.MODERATE, moderate.thermalStatus)
    }

    @Test
    fun `an unsupported thermal api neither helps nor hurts`() {
        val r = evaluate(33f, system = SystemThermalState.Unknown)
        assertEquals(ReadinessStatus.READY, r.status)
        assertNull(r.thermalStatus)
    }

    @Test
    fun `fast warming towards the threshold lowers readiness`() {
        fun heating(minutes: Float) = analysis(0.5f, Prediction.Heating(0.5f, minutes, Confidence.HIGH))
        assertEquals(ReadinessStatus.COOL_DOWN_FIRST, evaluate(35f, heating(4f)).status)
        assertEquals(ReadinessStatus.COOL_DOWN_FIRST, evaluate(35f, heating(5f)).status)
        val caution = evaluate(35f, heating(10f))
        assertEquals(ReadinessStatus.CAUTION, caution.status)
        assertTrue(caution.reasons.any { it is ReadinessReason.HeatingSoon })
        assertEquals(ReadinessStatus.READY, evaluate(35f, heating(30f)).status)
        assertEquals(TrendDirection.RISING, evaluate(35f, heating(30f)).trend)
    }

    @Test
    fun `no temperature reading means unavailable, not ready`() {
        val r = evaluate(null)
        assertEquals(ReadinessStatus.UNAVAILABLE, r.status)
        assertNull(r.temperatureC)
        assertEquals(CoolingEstimate.InsufficientData, r.cooling)
        assertEquals(ReadinessStatus.UNAVAILABLE, evaluate(Float.NaN).status)
    }

    @Test
    fun `cooling estimate uses personal history when there is enough`() {
        val r = evaluate(41f, analysis(slope = 0.2f), profile = enoughHistory) // target is the 37 C warning start
        val estimate = r.cooling as CoolingEstimate.Estimated
        assertEquals(EstimateBasis.PERSONAL_HISTORY, estimate.basis)
        assertEquals(3, estimate.basedOnEvents)
        assertEquals(37f, estimate.targetC, 0f)
        assertEquals(192_000L, estimate.millis) // 4 C at 1.25 C/min
    }

    @Test
    fun `without history the estimate falls back to the measured cooling trend`() {
        val r = evaluate(39f, analysis(slope = -0.5f))
        val estimate = r.cooling as CoolingEstimate.Estimated
        assertEquals(EstimateBasis.CURRENT_TREND, estimate.basis)
        assertEquals(240_000L, estimate.millis) // 2 C at 0.5 C/min
        assertEquals(0, estimate.basedOnEvents)
        assertTrue(ReadinessReason.CoolingDown in r.reasons)
    }

    @Test
    fun `without history or a cooling trend the estimate is insufficient data`() {
        assertEquals(CoolingEstimate.InsufficientData, evaluate(39f, analysis(slope = 0.3f)).cooling)
        assertEquals(CoolingEstimate.InsufficientData, evaluate(39f, analysis(slope = null)).cooling)
        assertEquals(CoolingEstimate.InsufficientData, evaluate(39f, analysis(slope = 0f)).cooling)
    }

    @Test
    fun `too little history is never used for an estimate`() {
        val twoOnly = PersonalCoolingProfileCalculator.compute(threeSessions().take(2), emptyList())
        assertEquals(CoolingEstimate.InsufficientData, evaluate(41f, analysis(slope = 0.1f), profile = twoOnly).cooling)
    }

    @Test
    fun `trend is reported only when it has been measured`() {
        assertNull(evaluate(35f, analysis(slope = null)).trend)
        assertNull(evaluate(35f, analysis(slope = null)).trendCPerMin)
        assertEquals(TrendDirection.FALLING, evaluate(35f, analysis(slope = -0.4f)).trend)
        assertNotNull(evaluate(35f, analysis(slope = -0.4f)).trendCPerMin)
    }

    @Test
    fun `the evaluation follows the active profile's threshold`() {
        val gaming = settings.copy(thresholdC = 43f) // warning from 40 C
        val r = ReadinessEvaluator.evaluate(gaming, analysis(), SystemThermalState.Unknown, PersonalCoolingProfile.Empty, 39f)
        assertEquals(ReadinessStatus.READY, r.status)
        assertEquals(ReadinessStatus.CAUTION, ReadinessEvaluator.evaluate(gaming, analysis(), SystemThermalState.Unknown, PersonalCoolingProfile.Empty, 41f).status)
    }
}
