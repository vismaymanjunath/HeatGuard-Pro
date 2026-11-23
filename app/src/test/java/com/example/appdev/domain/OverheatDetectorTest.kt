package com.example.appdev.domain

import com.example.appdev.domain.alert.AlertTransition
import com.example.appdev.domain.alert.OverheatDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverheatDetectorTest {

    private val threshold = 40f
    private val hysteresis = 2f

    private fun OverheatDetector.feed(vararg temps: Float): List<AlertTransition?> =
        temps.map { onReading(it, threshold, hysteresis) }

    @Test
    fun `no alert below threshold`() {
        val d = OverheatDetector()
        assertEquals(listOf<AlertTransition?>(null, null, null), d.feed(30f, 35f, 39.9f))
        assertFalse(d.isActive)
    }

    @Test
    fun `reaching threshold triggers exactly once`() {
        val d = OverheatDetector()
        assertEquals(
            listOf(null, AlertTransition.TRIGGERED, null, null),
            d.feed(39f, 40f, 41f, 43f),
        )
        assertTrue(d.isActive)
    }

    @Test
    fun `hovering around the threshold does not re-alert`() {
        val d = OverheatDetector()
        val result = d.feed(39.9f, 40.0f, 39.8f, 40.1f, 39.5f, 40.2f, 38.5f, 40.0f)
        assertEquals(1, result.count { it == AlertTransition.TRIGGERED })
        assertEquals(0, result.count { it == AlertTransition.RESOLVED })
    }

    @Test
    fun `resolves only at threshold minus hysteresis`() {
        val d = OverheatDetector()
        d.feed(41f)
        assertNull(d.onReading(38.1f, threshold, hysteresis))
        assertEquals(AlertTransition.RESOLVED, d.onReading(38.0f, threshold, hysteresis))
        assertFalse(d.isActive)
    }

    @Test
    fun `can trigger again after resolving`() {
        val d = OverheatDetector()
        val result = d.feed(41f, 37f, 38f, 40f)
        assertEquals(
            listOf(AlertTransition.TRIGGERED, AlertTransition.RESOLVED, null, AlertTransition.TRIGGERED),
            result,
        )
    }

    @Test
    fun `non finite readings are ignored`() {
        val d = OverheatDetector()
        assertNull(d.onReading(Float.NaN, threshold, hysteresis))
        d.feed(41f)
        assertNull(d.onReading(Float.NaN, threshold, hysteresis))
        assertTrue(d.isActive)
    }

    @Test
    fun `resume makes the detector active without re-alerting`() {
        val d = OverheatDetector()
        d.resume()
        assertNull(d.onReading(41f, threshold, hysteresis))
        assertEquals(AlertTransition.RESOLVED, d.onReading(37f, threshold, hysteresis))
    }

    @Test
    fun `negative hysteresis is treated as zero`() {
        val d = OverheatDetector()
        d.onReading(41f, threshold, -3f)
        assertEquals(AlertTransition.RESOLVED, d.onReading(39.9f, threshold, -3f))
    }
}
