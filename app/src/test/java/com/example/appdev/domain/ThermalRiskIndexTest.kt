package com.example.appdev.domain

import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.thermal.Confidence
import com.example.appdev.domain.thermal.FloorReason
import com.example.appdev.domain.thermal.Prediction
import com.example.appdev.domain.thermal.RiskFactor
import com.example.appdev.domain.thermal.RiskLevel
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.ThermalRisk
import com.example.appdev.domain.thermal.ThermalRiskIndex
import com.example.appdev.domain.thermal.ThermalStatus
import com.example.appdev.domain.thermal.TrendEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalRiskIndexTest {

    private val threshold = 40f
    private val fullSystem = SystemThermalState(
        statusApiAvailable = true, status = ThermalStatus.NONE,
        headroomApiAvailable = true, headroomNow = 0f, headroomForecast = 0f,
    )

    private fun trend(slope: Float) = TrendEstimate(slope, rSquared = 1f, sampleCount = 10, spanMillis = 240_000)

    private fun risk(
        temp: Float,
        trend: TrendEstimate? = trend(0f),
        prediction: Prediction = Prediction.Stable(0f),
        system: SystemThermalState = fullSystem,
        charging: Boolean = false,
        source: PowerSource = PowerSource.NONE,
    ): ThermalRisk = ThermalRiskIndex.compute(
        snapshot(
            temp,
            chargeStatus = if (charging) ChargeStatus.CHARGING else ChargeStatus.DISCHARGING,
            powerSource = if (charging) source else PowerSource.NONE,
        ),
        threshold, trend, prediction, system,
    )!!

    private fun ThermalRisk.points(f: RiskFactor) = components.single { it.factor == f }

    @Test
    fun `weights sum to 100`() {
        assertEquals(100f, RiskFactor.entries.sumOf { it.maxPoints.toDouble() }.toFloat(), 0f)
    }

    @Test
    fun `cool idle phone scores zero`() {
        val r = risk(28f)
        assertEquals(0, r.index)
        assertEquals(RiskLevel.LOW, r.level)
        assertTrue(r.floors.isEmpty())
    }

    @Test
    fun `temperature points scale across the 10 degree range below threshold`() {
        assertEquals(0f, risk(30f).points(RiskFactor.TEMPERATURE).points, 0.001f)
        assertEquals(17.5f, risk(35f).points(RiskFactor.TEMPERATURE).points, 0.001f)
        assertEquals(35f, risk(40f).points(RiskFactor.TEMPERATURE).points, 0.001f)
    }

    @Test
    fun `warming rate is capped at one degree per minute and ignores cooling`() {
        assertEquals(10f, risk(36f, trend(0.5f)).points(RiskFactor.WARMING_RATE).points, 0.001f)
        assertEquals(20f, risk(36f, trend(3f)).points(RiskFactor.WARMING_RATE).points, 0.001f)
        assertEquals(0f, risk(36f, trend(-0.5f)).points(RiskFactor.WARMING_RATE).points, 0.001f)
    }

    @Test
    fun `time to threshold earns more points the sooner it is`() {
        fun eta(minutes: Float) =
            risk(38f, trend(0.5f), Prediction.Heating(0.5f, minutes, Confidence.HIGH))
                .points(RiskFactor.TIME_TO_THRESHOLD).points
        assertEquals(15f, eta(1f), 0.001f)
        assertEquals(15f, eta(2f), 0.001f)
        assertEquals(0f, eta(15f), 0.001f)
        assertTrue(eta(5f) > eta(10f))
    }

    @Test
    fun `charging points depend on the charger type and only apply while charging`() {
        assertEquals(10f, risk(30f, charging = true, source = PowerSource.WIRELESS).points(RiskFactor.CHARGING).points, 0f)
        assertEquals(8f, risk(30f, charging = true, source = PowerSource.AC).points(RiskFactor.CHARGING).points, 0f)
        assertEquals(6f, risk(30f, charging = true, source = PowerSource.USB).points(RiskFactor.CHARGING).points, 0f)
        assertEquals(0f, risk(30f, charging = false).points(RiskFactor.CHARGING).points, 0f)
    }

    @Test
    fun `unavailable inputs are excluded and the rest rescaled`() {
        // Only temperature (35) and charging (10) are available: 17.5 of 45 -> 39.
        val r = risk(35f, trend = null, prediction = Prediction.CollectingData(1, 0), system = SystemThermalState.Unknown)
        assertFalse(r.points(RiskFactor.WARMING_RATE).available)
        assertFalse(r.points(RiskFactor.TIME_TO_THRESHOLD).available)
        assertFalse(r.points(RiskFactor.SYSTEM_STATUS).available)
        assertFalse(r.points(RiskFactor.HEADROOM).available)
        assertEquals(39, r.index)
        assertEquals(RiskLevel.MODERATE, r.level)
    }

    @Test
    fun `system status and headroom contribute when reported`() {
        val severe = fullSystem.copy(status = ThermalStatus.MODERATE, headroomNow = 0.6f)
        val r = risk(30f, system = severe)
        assertEquals(5f, r.points(RiskFactor.SYSTEM_STATUS).points, 0f)
        assertEquals(6f, r.points(RiskFactor.HEADROOM).points, 0.001f)
    }

    @Test
    fun `reaching the threshold floors the index at 75`() {
        val r = risk(40f, trend = null, prediction = Prediction.AlreadyOverThreshold(0f), system = SystemThermalState.Unknown)
        assertTrue(r.index >= 75)
        assertEquals(RiskLevel.CRITICAL, r.level)
        assertTrue(FloorReason.AT_THRESHOLD in r.floors)
    }

    @Test
    fun `severe system thermal status floors the index even when the battery is cool`() {
        val r = risk(28f, system = fullSystem.copy(status = ThermalStatus.SEVERE))
        assertEquals(75, r.index)
        assertTrue(r.weightedIndex < 75)
        assertEquals(listOf(FloorReason.SYSTEM_SEVERE), r.floors)
    }

    @Test
    fun `a floor never lowers a higher weighted score`() {
        val hot = fullSystem.copy(status = ThermalStatus.EMERGENCY, headroomNow = 1f)
        val r = risk(45f, trend(2f), Prediction.AlreadyOverThreshold(5f), hot, charging = true, source = PowerSource.WIRELESS)
        assertEquals(100, r.index)
        assertEquals(r.weightedIndex, r.index)
    }

    @Test
    fun `no temperature means no risk index`() {
        assertNull(ThermalRiskIndex.compute(null, threshold, null, Prediction.Unavailable, fullSystem))
        assertNull(ThermalRiskIndex.compute(snapshot(null), threshold, null, Prediction.Unavailable, fullSystem))
    }

    @Test
    fun `levels map from index boundaries`() {
        assertEquals(RiskLevel.LOW, RiskLevel.fromIndex(24))
        assertEquals(RiskLevel.MODERATE, RiskLevel.fromIndex(25))
        assertEquals(RiskLevel.HIGH, RiskLevel.fromIndex(50))
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromIndex(75))
    }

    @Test
    fun `index never leaves 0 to 100`() {
        val extreme = fullSystem.copy(status = ThermalStatus.SHUTDOWN, headroomNow = 5f)
        val r = risk(90f, trend(10f), Prediction.AlreadyOverThreshold(50f), extreme, charging = true, source = PowerSource.WIRELESS)
        assertTrue(r.index in 0..100)
        assertTrue(r.components.all { it.points <= it.maxPoints })
    }
}
