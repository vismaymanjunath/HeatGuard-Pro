package com.example.appdev.domain

import com.example.appdev.domain.demo.CyclePhase
import com.example.appdev.domain.demo.DemoControls
import com.example.appdev.domain.demo.DemoScenario
import com.example.appdev.domain.demo.DemoSimulator
import com.example.appdev.domain.demo.DemoState
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.surface.SurfaceModels
import com.example.appdev.domain.thermal.TemperatureLevel
import com.example.appdev.domain.thermal.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DemoAndSurfaceTest {

    private val threshold = 40f

    private fun run(
        controls: DemoControls,
        seconds: Int,
        start: DemoState = DemoSimulator.initial(controls),
    ): DemoState = (1..seconds).fold(start) { s, _ -> DemoSimulator.step(s, controls, 1f, threshold) }

    // ------------------------------------------------------------ simulator

    @Test
    fun `manual temperature follows the slider at a limited rate`() {
        val controls = DemoControls(scenario = DemoScenario.MANUAL, manualTempC = 34f)
        val up = controls.copy(manualTempC = 45f)
        val afterOne = DemoSimulator.step(DemoSimulator.initial(controls), up, 1f, threshold)
        assertEquals(36f, afterOne.tempC, 0.001f) // 2 degrees per second at most
        assertEquals(45f, run(up, 10, DemoSimulator.initial(controls)).tempC, 0.001f)
    }

    @Test
    fun `manual temperature is clamped to the supported range`() {
        val low = run(DemoControls(manualTempC = 5f), 5)
        assertEquals(DemoSimulator.MIN_TEMP_C, low.tempC, 0f)
        val high = run(DemoControls(manualTempC = 99f), 30, DemoSimulator.initial(DemoControls(manualTempC = 30f)))
        assertEquals(DemoSimulator.MAX_TEMP_C, high.tempC, 0f)
    }

    @Test
    fun `heat up rises steadily past the threshold and then holds`() {
        val controls = DemoControls(scenario = DemoScenario.HEAT_UP, manualTempC = 34f)
        val after10 = run(controls, 10)
        assertEquals(34f + DemoSimulator.HEAT_RATE_C_PER_SEC * 10, after10.tempC, 0.01f)
        val settled = run(controls, 200)
        assertEquals(threshold + 5f, settled.tempC, 0.001f)
        assertEquals(settled.tempC, run(controls, 300).tempC, 0f)
    }

    @Test
    fun `the forecast has a visible window before the alert in the default demo`() {
        // The trend estimator needs about a minute of readings (see TrendEstimator.MIN_SPAN_MILLIS).
        val controls = DemoControls(scenario = DemoScenario.FULL_CYCLE) // starts from the default temperature
        val start = DemoSimulator.initial(controls)
        val secondsToThreshold = (threshold - start.tempC) / DemoSimulator.HEAT_RATE_C_PER_SEC
        val forecastNeeds = com.example.appdev.domain.thermal.TrendEstimator.MIN_SPAN_MILLIS / 1000f
        assertTrue(
            "threshold reached after ${secondsToThreshold}s; forecast needs ${forecastNeeds}s plus time to be seen",
            secondsToThreshold >= forecastNeeds + 45f,
        )
        // And it really crosses the threshold in about that time.
        var s = start
        var t = 0
        while (s.tempC < threshold && t < 1000) { s = DemoSimulator.step(s, controls, 1f, threshold); t++ }
        assertEquals(secondsToThreshold, t.toFloat(), 2f)
    }

    @Test
    fun `cool down falls to a comfortable temperature and stays there`() {
        val controls = DemoControls(scenario = DemoScenario.COOL_DOWN, manualTempC = 44f)
        assertEquals(44f - 1.2f, run(controls, 10).tempC, 0.01f)
        assertEquals(30f, run(controls, 300).tempC, 0f)
    }

    @Test
    fun `full cycle warms holds cools and finishes`() {
        val controls = DemoControls(scenario = DemoScenario.FULL_CYCLE, manualTempC = 34f)
        var s = DemoSimulator.initial(controls)
        val seen = mutableListOf(s.phase)
        var maxTemp = s.tempC
        repeat(400) {
            s = DemoSimulator.step(s, controls, 1f, threshold)
            maxTemp = maxOf(maxTemp, s.tempC)
            if (seen.last() != s.phase) seen += s.phase
        }
        assertEquals(listOf(CyclePhase.WARMING, CyclePhase.HOLDING, CyclePhase.COOLING, CyclePhase.DONE), seen)
        assertEquals(threshold + 4f, maxTemp, 0.001f)
        assertEquals(DemoSimulator.COOL_FLOOR_C, s.tempC, 0f)
        assertTrue("it must cross the threshold to exercise alerts", maxTemp > threshold)
    }

    @Test
    fun `full cycle holds at the peak for the configured time`() {
        val controls = DemoControls(scenario = DemoScenario.FULL_CYCLE, manualTempC = 44f) // already above the peak
        var s = DemoSimulator.initial(controls)
        s = DemoSimulator.step(s, controls, 1f, threshold) // reaches the peak and begins holding
        assertEquals(CyclePhase.HOLDING, s.phase)
        assertEquals(threshold + 4f, s.tempC, 0.001f)
        var holdSteps = 0
        while (s.phase == CyclePhase.HOLDING) { s = DemoSimulator.step(s, controls, 1f, threshold); holdSteps++ }
        assertEquals(DemoSimulator.HOLD_SECONDS.toInt(), holdSteps)
        assertEquals(CyclePhase.COOLING, s.phase)
    }

    @Test
    fun `charging heat implies charging and heats towards threshold plus three`() {
        val controls = DemoControls(scenario = DemoScenario.CHARGING_HEAT, manualTempC = 33f, charging = false)
        assertTrue(controls.effectiveCharging)
        assertEquals(threshold + 3f, run(controls, 300).tempC, 0.001f)
        val snap = DemoSimulator.toSnapshot(run(controls, 5), controls, 1_000)
        assertEquals(ChargeStatus.CHARGING, snap.chargeStatus)
        assertEquals(PowerSource.USB, snap.powerSource)
    }

    @Test
    fun `changing scenario restarts its sequence from the current temperature`() {
        val cycle = DemoControls(scenario = DemoScenario.FULL_CYCLE, manualTempC = 34f)
        val done = run(cycle, 400)
        assertEquals(CyclePhase.DONE, done.phase)
        val again = DemoSimulator.step(done, cycle.copy(scenario = DemoScenario.HEAT_UP), 1f, threshold)
        assertEquals(DemoScenario.HEAT_UP, again.scenario)
        assertTrue(again.tempC > done.tempC)
    }

    @Test
    fun `demo snapshots are tagged as demo and look like ordinary readings`() {
        val controls = DemoControls(manualTempC = 37.46f, charging = true, powerSource = PowerSource.WIRELESS, batteryPercent = 150)
        val snap = DemoSimulator.toSnapshot(DemoSimulator.initial(controls), controls, 123L)
        assertEquals(ReadingSource.DEMO, snap.source)
        assertEquals(37.5f, snap.temperatureC!!, 0.0001f)
        assertEquals(100, snap.levelPercent)
        assertEquals(PowerSource.WIRELESS, snap.powerSource)
        assertEquals(123L, snap.timestampMillis)
        assertEquals("Simulated", snap.technology)
    }

    @Test
    fun `not charging means no power source`() {
        val snap = DemoSimulator.toSnapshot(DemoSimulator.initial(DemoControls()), DemoControls(charging = false), 0)
        assertEquals(ChargeStatus.DISCHARGING, snap.chargeStatus)
        assertEquals(PowerSource.NONE, snap.powerSource)
    }

    @Test
    fun `simulated system state rises with temperature`() {
        assertEquals(ThermalStatus.NONE, DemoSimulator.systemStateFor(30f).status)
        assertEquals(ThermalStatus.LIGHT, DemoSimulator.systemStateFor(39f).status)
        assertEquals(ThermalStatus.MODERATE, DemoSimulator.systemStateFor(42f).status)
        assertEquals(ThermalStatus.SEVERE, DemoSimulator.systemStateFor(45f).status)
        assertEquals(ThermalStatus.CRITICAL, DemoSimulator.systemStateFor(49f).status)
        val a = DemoSimulator.systemStateFor(35f).headroomNow!!
        val b = DemoSimulator.systemStateFor(45f).headroomNow!!
        assertTrue(b > a)
        assertEquals(0f, DemoSimulator.systemStateFor(20f).headroomNow!!, 0f)
        assertNull(DemoSimulator.systemStateFor(null).status)
    }

    // ------------------------------------------------------------ widget and tile

    @Test
    fun `widget model formats temperature in the chosen unit and classifies it`() {
        val settings = UserSettings(thresholdC = 40f, warningMarginC = 3f)
        val normal = SurfaceModels.widget(snapshot(35f, timestampMillis = 9), settings, Locale.US)
        assertEquals("35.0°C", normal.temperatureText)
        assertEquals(TemperatureLevel.NORMAL, normal.level)
        assertEquals(9L, normal.updatedMillis)
        assertFalse(normal.isDemo)

        assertEquals(TemperatureLevel.ELEVATED, SurfaceModels.widget(snapshot(38f), settings, Locale.US).level)
        assertEquals(TemperatureLevel.CRITICAL, SurfaceModels.widget(snapshot(40f), settings, Locale.US).level)

        val f = SurfaceModels.widget(snapshot(40f), settings.copy(unit = TemperatureUnit.FAHRENHEIT), Locale.US)
        assertEquals("104.0°F", f.temperatureText)
    }

    @Test
    fun `widget without a valid reading never shows a number`() {
        val none = SurfaceModels.widget(null, UserSettings(), Locale.US)
        assertEquals(SurfaceModels.NO_VALUE, none.temperatureText)
        assertEquals(TemperatureLevel.UNKNOWN, none.level)
        assertNull(none.updatedMillis)
        assertEquals(SurfaceModels.NO_VALUE, SurfaceModels.widget(snapshot(null), UserSettings(), Locale.US).temperatureText)
    }

    @Test
    fun `widget and tile are marked when showing demo data`() {
        val demo = snapshot(41f).copy(source = ReadingSource.DEMO)
        assertTrue(SurfaceModels.widget(demo, UserSettings(), Locale.US).isDemo)
        assertTrue(SurfaceModels.tile(demo, UserSettings(), monitoringRunning = true, Locale.US).isDemo)
    }

    @Test
    fun `tile reflects whether monitoring is running`() {
        val on = SurfaceModels.tile(snapshot(36f), UserSettings(), monitoringRunning = true, Locale.US)
        val off = SurfaceModels.tile(snapshot(36f), UserSettings(), monitoringRunning = false, Locale.US)
        assertTrue(on.active)
        assertFalse(off.active)
        assertEquals("36.0°C", on.label)
    }
}
