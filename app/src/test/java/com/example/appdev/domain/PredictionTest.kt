package com.example.appdev.domain

import com.example.appdev.domain.thermal.Confidence
import com.example.appdev.domain.thermal.Prediction
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.TemperatureHistory
import com.example.appdev.domain.thermal.TemperaturePoint
import com.example.appdev.domain.thermal.TemperaturePredictor
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalStatus
import com.example.appdev.domain.thermal.TrendEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionTest {

    private val minute = 60_000L

    /** Points every [stepSeconds] starting at t=0 along a straight line. */
    private fun line(startC: Float, slopeCPerMin: Float, minutes: Int, stepSeconds: Int = 15): List<TemperaturePoint> =
        (0..(minutes * 60 / stepSeconds)).map { i ->
            val tSec = i * stepSeconds
            TemperaturePoint(tSec * 1000L, startC + slopeCPerMin * tSec / 60f)
        }

    @Test
    fun `slope of a straight line is recovered exactly`() {
        val trend = TrendEstimator.estimate(line(30f, 0.4f, minutes = 4))!!
        assertEquals(0.4f, trend.slopeCPerMin, 0.001f)
        assertEquals(1f, trend.rSquared, 0.001f)
    }

    @Test
    fun `cooling line gives a negative slope`() {
        val trend = TrendEstimator.estimate(line(42f, -0.3f, minutes = 4))!!
        assertEquals(-0.3f, trend.slopeCPerMin, 0.001f)
    }

    @Test
    fun `flat series is a perfect zero-slope fit`() {
        val trend = TrendEstimator.estimate(line(35f, 0f, minutes = 3))!!
        assertEquals(0f, trend.slopeCPerMin, 0.0001f)
        assertEquals(1f, trend.rSquared, 0f)
    }

    @Test
    fun `too few samples or too short a span yields no trend`() {
        assertNull(TrendEstimator.estimate(emptyList()))
        assertNull(TrendEstimator.estimate(line(30f, 0.5f, minutes = 1, stepSeconds = 30).take(3)))
        // Many samples but only 30 seconds apart in total.
        assertNull(TrendEstimator.estimate((0..10).map { TemperaturePoint(it * 3_000L, 30f + it * 0.1f) }))
    }

    @Test
    fun `only the last five minutes are used`() {
        // Long cooling phase followed by a recent warming phase.
        val cooling = (0 until 20).map { TemperaturePoint(it * 30_000L, 45f - it * 0.2f) }
        val start = cooling.last().timestampMillis + 30_000L
        val warming = (0..10).map { TemperaturePoint(start + it * 30_000L, 38f + it * 0.2f) }
        val trend = TrendEstimator.estimate(cooling + warming)!!
        assertTrue("slope was ${trend.slopeCPerMin}", trend.slopeCPerMin > 0.3f)
    }

    @Test
    fun `heating prediction estimates minutes to threshold`() {
        val history = line(36f, 0.5f, minutes = 4) // ends at 38 °C, warming 0.5 °C/min
        val temp = history.last().temperatureC
        val trend = TrendEstimator.estimate(history)
        val p = TemperaturePredictor.predict(temp, 40f, trend, history)
        assertTrue(p is Prediction.Heating)
        p as Prediction.Heating
        assertEquals(4f, p.minutesToThreshold, 0.05f)
        assertEquals(Confidence.HIGH, p.confidence)
    }

    @Test
    fun `slow warming beyond an hour is not projected`() {
        val history = line(30f, 0.1f, minutes = 4) // needs ~99 minutes
        val trend = TrendEstimator.estimate(history)
        val p = TemperaturePredictor.predict(history.last().temperatureC, 40f, trend, history)
        assertTrue(p is Prediction.HeatingSlowly)
    }

    @Test
    fun `stable and cooling are distinguished`() {
        val stable = line(35f, 0.02f, minutes = 4)
        assertTrue(
            TemperaturePredictor.predict(stable.last().temperatureC, 40f, TrendEstimator.estimate(stable), stable)
                is Prediction.Stable,
        )
        val cooling = line(39f, -0.2f, minutes = 4)
        assertTrue(
            TemperaturePredictor.predict(cooling.last().temperatureC, 40f, TrendEstimator.estimate(cooling), cooling)
                is Prediction.Cooling,
        )
    }

    @Test
    fun `at or above the threshold is reported as already over`() {
        val p = TemperaturePredictor.predict(41.5f, 40f, null)
        assertTrue(p is Prediction.AlreadyOverThreshold)
        assertEquals(1.5f, (p as Prediction.AlreadyOverThreshold).overByC, 0.0001f)
    }

    @Test
    fun `missing temperature is unavailable and no trend means collecting data`() {
        assertEquals(Prediction.Unavailable, TemperaturePredictor.predict(null, 40f, null))
        val history = line(30f, 0.1f, minutes = 0).take(1)
        val p = TemperaturePredictor.predict(30f, 40f, null, history)
        assertTrue(p is Prediction.CollectingData)
        assertEquals(1, (p as Prediction.CollectingData).sampleCount)
    }

    @Test
    fun `noisy readings lower confidence`() {
        val noise = floatArrayOf(0.6f, -0.6f, 0.5f, -0.7f, 0.6f, -0.5f, 0.7f, -0.6f, 0.5f, -0.6f, 0.6f, -0.5f, 0.7f)
        val history = noise.mapIndexed { i, n -> TemperaturePoint(i * 20_000L, 36f + i * 0.02f + n) }
        val trend = TrendEstimator.estimate(history)!!
        assertTrue("r2 was ${trend.rSquared}", trend.rSquared < 0.5f)
        assertEquals(Confidence.LOW, Confidence.fromRSquared(trend.rSquared))
    }

    @Test
    fun `history keeps a rolling 30 minute window and ignores stale points`() {
        var h = emptyList<TemperaturePoint>()
        for (i in 0..40) h = TemperatureHistory.append(h, TemperaturePoint(i * minute, 30f))
        assertEquals(40 * minute, h.last().timestampMillis)
        assertEquals(10 * minute, h.first().timestampMillis)
        assertEquals(31, h.size)

        val unchanged = TemperatureHistory.append(h, TemperaturePoint(39 * minute, 99f))
        assertEquals(h, unchanged)
        val duplicate = TemperatureHistory.append(h, TemperaturePoint(40 * minute, 99f))
        assertEquals(h, duplicate)
    }

    @Test
    fun `analysis combines prediction trend and risk`() {
        val history = line(36f, 0.5f, minutes = 4)
        val snap = snapshot(history.last().temperatureC, timestampMillis = history.last().timestampMillis)
        val a = ThermalAnalysis.analyze(snap, history, 40f, SystemThermalState.Unknown)
        assertTrue(a.prediction is Prediction.Heating)
        assertNotNull(a.trend)
        assertNotNull(a.risk)
    }

    @Test
    fun `android thermal status values map and unknown values are rejected`() {
        assertEquals(ThermalStatus.NONE, ThermalStatus.fromAndroid(0))
        assertEquals(ThermalStatus.SEVERE, ThermalStatus.fromAndroid(3))
        assertEquals(ThermalStatus.SHUTDOWN, ThermalStatus.fromAndroid(6))
        assertNull(ThermalStatus.fromAndroid(7))
        assertNull(ThermalStatus.fromAndroid(-1))
        assertTrue(ThermalStatus.CRITICAL.isSevere)
        assertTrue(!ThermalStatus.MODERATE.isSevere)
    }
}
