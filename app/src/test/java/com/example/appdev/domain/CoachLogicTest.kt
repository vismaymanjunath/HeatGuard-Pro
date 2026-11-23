package com.example.appdev.domain

import com.example.appdev.domain.cooling.AdviceContext
import com.example.appdev.domain.cooling.AdvicePriority
import com.example.appdev.domain.cooling.AdviceType
import com.example.appdev.domain.cooling.CoolingAdvisor
import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.domain.cooling.RecoveryStats
import com.example.appdev.domain.cooling.SessionOutcome
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.profile.ProfileDefaults
import com.example.appdev.domain.profile.ProfileType
import com.example.appdev.domain.thermal.Confidence
import com.example.appdev.domain.thermal.Prediction
import com.example.appdev.domain.thermal.TemperatureLevel
import com.example.appdev.domain.thermal.ThermalStatus
import com.example.appdev.ui.components.ChartScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachLogicTest {

    private fun ctx(
        level: TemperatureLevel = TemperatureLevel.NORMAL,
        charging: Boolean = false,
        source: PowerSource = PowerSource.NONE,
        prediction: Prediction = Prediction.Stable(0f),
        risk: Int? = 10,
        status: ThermalStatus? = null,
        profile: ProfileType? = null,
        session: Boolean = false,
    ) = AdviceContext(level, charging, source, prediction, risk, status, profile, session)

    private fun types(c: AdviceContext) = CoolingAdvisor.advise(c).map { it.type }

    // ------------------------------------------------------------ advisor

    @Test
    fun `hot and charging puts unplugging first`() {
        val advice = CoolingAdvisor.advise(ctx(TemperatureLevel.CRITICAL, charging = true, source = PowerSource.AC, risk = 80))
        assertEquals(AdviceType.UNPLUG_CHARGER, advice.first().type)
        assertEquals(AdvicePriority.HIGH, advice.first().priority)
        assertTrue(AdviceType.STOP_HEAVY_USE in advice.map { it.type })
    }

    @Test
    fun `hot and not charging does not mention the charger`() {
        val t = types(ctx(TemperatureLevel.CRITICAL, risk = 80))
        assertFalse(AdviceType.UNPLUG_CHARGER in t)
        assertFalse(AdviceType.SWITCH_TO_WIRED_CHARGING in t)
        assertEquals(AdviceType.STOP_HEAVY_USE, t.first())
    }

    @Test
    fun `wireless charging suggests switching to a cable`() {
        val t = types(ctx(TemperatureLevel.CRITICAL, charging = true, source = PowerSource.WIRELESS, risk = 80))
        assertTrue(AdviceType.SWITCH_TO_WIRED_CHARGING in t)
    }

    @Test
    fun `warm and heating soon is a high priority prevention tip`() {
        val advice = CoolingAdvisor.advise(
            ctx(TemperatureLevel.ELEVATED, prediction = Prediction.Heating(0.5f, 4f, Confidence.HIGH), risk = 40),
        )
        assertEquals(AdviceType.PREVENT_HEATING, advice.first().type)
        assertEquals(AdvicePriority.HIGH, advice.first().priority)
    }

    @Test
    fun `warm while charging explains that charging adds heat`() {
        assertTrue(AdviceType.CHARGING_WARMS in types(ctx(TemperatureLevel.ELEVATED, charging = true, source = PowerSource.USB)))
    }

    @Test
    fun `normal temperature gives reassurance`() {
        assertEquals(listOf(AdviceType.ALL_GOOD), types(ctx()))
    }

    @Test
    fun `unknown temperature only reports the sensor problem`() {
        assertEquals(listOf(AdviceType.SENSOR_UNAVAILABLE), types(ctx(TemperatureLevel.UNKNOWN, risk = null)))
    }

    @Test
    fun `severe system throttling is surfaced even when the battery is cool`() {
        val advice = CoolingAdvisor.advise(ctx(status = ThermalStatus.SEVERE))
        assertEquals(AdviceType.SYSTEM_THROTTLING, advice.first().type)
    }

    @Test
    fun `an active session asks the user to rest the phone`() {
        assertTrue(AdviceType.KEEP_RESTING in types(ctx(TemperatureLevel.ELEVATED, session = true)))
    }

    @Test
    fun `profiles add their own tip and the list is capped`() {
        assertTrue(AdviceType.PROFILE_GAMING in types(ctx(profile = ProfileType.GAMING)))
        val busy = CoolingAdvisor.advise(
            ctx(TemperatureLevel.CRITICAL, charging = true, source = PowerSource.WIRELESS, status = ThermalStatus.SEVERE, profile = ProfileType.NAVIGATION, risk = 90),
        )
        assertEquals(CoolingAdvisor.MAX_ADVICE, busy.size)
        assertEquals(busy.map { it.priority.ordinal }, busy.map { it.priority.ordinal }.sorted())
    }

    @Test
    fun `advice never repeats a type`() {
        val t = types(ctx(TemperatureLevel.CRITICAL, charging = true, source = PowerSource.WIRELESS, risk = 90))
        assertEquals(t.size, t.toSet().size)
    }

    // ------------------------------------------------------------ recovery stats

    private fun session(
        outcome: SessionOutcome?,
        durationSec: Long = 120,
        start: Float = 41f,
        end: Float = 38f,
    ) = CoolingSession(
        id = 1, source = ReadingSource.DEVICE, startMillis = 0,
        endMillis = if (outcome == null) null else durationSec * 1000,
        lastSeenMillis = durationSec * 1000, startTempC = start, lastTempC = end, minTempC = end,
        targetTempC = 38f, startBatteryPercent = 70, startChargeStatus = ChargeStatus.DISCHARGING,
        startPowerSource = PowerSource.NONE, outcome = outcome,
    )

    @Test
    fun `stats average only recovered sessions`() {
        val stats = RecoveryStats.compute(
            listOf(
                session(SessionOutcome.RECOVERED, durationSec = 120, start = 41f, end = 38f),
                session(SessionOutcome.RECOVERED, durationSec = 240, start = 43f, end = 38f),
                session(SessionOutcome.STOPPED, durationSec = 30),
                session(SessionOutcome.INTERRUPTED, durationSec = 10),
                session(null), // still running: ignored
            ),
        )
        assertEquals(4, stats.totalSessions)
        assertEquals(2, stats.recoveredCount)
        assertEquals(1, stats.stoppedCount)
        assertEquals(1, stats.interruptedCount)
        assertEquals(180_000L, stats.averageRecoveryMillis)
        assertEquals(120_000L, stats.fastestRecoveryMillis)
        assertEquals(4f, stats.averageDropC!!, 0.001f)
        // 3 deg / 2 min = 1.5 and 5 deg / 4 min = 1.25 -> mean 1.375
        assertEquals(1.375f, stats.averageRateCPerMin!!, 0.001f)
    }

    @Test
    fun `stats with no recovered sessions have no timings`() {
        val stats = RecoveryStats.compute(listOf(session(SessionOutcome.STOPPED)))
        assertEquals(1, stats.totalSessions)
        assertNull(stats.averageRecoveryMillis)
        assertNull(stats.averageDropC)
        assertEquals(0, RecoveryStats.compute(emptyList()).totalSessions)
    }

    // ------------------------------------------------------------ profiles

    @Test
    fun `profile defaults are valid and ordered sensibly`() {
        ProfileType.entries.forEach { p ->
            val c = ProfileDefaults.forProfile(p)
            assertEquals("$p", c, c.sanitized())
        }
        assertTrue(ProfileDefaults.forProfile(ProfileType.CHARGING).thresholdC < ProfileDefaults.forProfile(ProfileType.WORK).thresholdC)
        assertTrue(ProfileDefaults.forProfile(ProfileType.GAMING).thresholdC > ProfileDefaults.forProfile(ProfileType.WORK).thresholdC)
    }

    // ------------------------------------------------------------ chart scale

    @Test
    fun `axis contains every value and reference with nice ticks`() {
        val axis = ChartScale.yAxis(listOf(33.2f, 36.8f, 35f), references = listOf(40f))
        assertTrue(axis.min <= 33.2f)
        assertTrue(axis.max >= 40f)
        assertTrue(axis.ticks.size in 3..8)
        assertEquals(axis.min, axis.ticks.first(), 0.0001f)
        assertEquals(axis.max, axis.ticks.last(), 0.0001f)
    }

    @Test
    fun `axis never collapses to a tiny span`() {
        val axis = ChartScale.yAxis(listOf(35.0f, 35.1f))
        assertTrue(axis.max - axis.min >= 4f)
        assertTrue(axis.fraction(35.05f) in 0.3f..0.7f)
    }

    @Test
    fun `empty axis falls back to a sane default and fraction is clamped`() {
        val axis = ChartScale.yAxis(emptyList())
        assertTrue(axis.min < axis.max)
        assertEquals(0f, axis.fraction(axis.min - 100), 0f)
        assertEquals(1f, axis.fraction(axis.max + 100), 0f)
        assertFalse(ChartScale.yAxis(listOf(Float.NaN)).ticks.isEmpty())
    }
}
