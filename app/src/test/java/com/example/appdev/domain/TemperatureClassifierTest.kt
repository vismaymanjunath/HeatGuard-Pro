package com.example.appdev.domain

import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.thermal.TemperatureClassifier
import com.example.appdev.domain.thermal.TemperatureLevel
import com.example.appdev.domain.thermal.ThermalAssessment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemperatureClassifierTest {

    private fun classify(t: Float?) = TemperatureClassifier.classify(t, thresholdC = 40f, warningMarginC = 3f)

    @Test
    fun `below warning band is normal`() {
        assertEquals(TemperatureLevel.NORMAL, classify(25f))
        assertEquals(TemperatureLevel.NORMAL, classify(36.9f))
    }

    @Test
    fun `warning band starts exactly at threshold minus margin`() {
        assertEquals(TemperatureLevel.ELEVATED, classify(37f))
        assertEquals(TemperatureLevel.ELEVATED, classify(39.9f))
    }

    @Test
    fun `threshold itself is critical`() {
        assertEquals(TemperatureLevel.CRITICAL, classify(40f))
        assertEquals(TemperatureLevel.CRITICAL, classify(47.3f))
    }

    @Test
    fun `missing or invalid reading is unknown, never normal`() {
        assertEquals(TemperatureLevel.UNKNOWN, classify(null))
        assertEquals(TemperatureLevel.UNKNOWN, classify(Float.NaN))
        assertEquals(TemperatureLevel.UNKNOWN, classify(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `custom threshold moves all bands`() {
        assertEquals(
            TemperatureLevel.CRITICAL,
            TemperatureClassifier.classify(36f, thresholdC = 35f, warningMarginC = 2f),
        )
        assertEquals(
            TemperatureLevel.ELEVATED,
            TemperatureClassifier.classify(33.5f, thresholdC = 35f, warningMarginC = 2f),
        )
        assertEquals(
            TemperatureLevel.NORMAL,
            TemperatureClassifier.classify(32.9f, thresholdC = 35f, warningMarginC = 2f),
        )
    }

    @Test
    fun `assessment reports headroom relative to threshold`() {
        val settings = UserSettings(thresholdC = 40f)
        val below = ThermalAssessment.evaluate(snapshot(36.5f), settings)
        assertEquals(3.5f, below.headroomC!!, 0.001f)
        assertEquals(TemperatureLevel.NORMAL, below.level)

        val above = ThermalAssessment.evaluate(snapshot(42f), settings)
        assertEquals(-2f, above.headroomC!!, 0.001f)
        assertEquals(TemperatureLevel.CRITICAL, above.level)
    }

    @Test
    fun `assessment without a snapshot is unknown with no headroom`() {
        val assessment = ThermalAssessment.evaluate(null, UserSettings())
        assertEquals(TemperatureLevel.UNKNOWN, assessment.level)
        assertNull(assessment.headroomC)
    }
}
