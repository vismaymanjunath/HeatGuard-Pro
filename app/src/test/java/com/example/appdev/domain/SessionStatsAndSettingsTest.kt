package com.example.appdev.domain

import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.thermal.SessionStats
import com.example.appdev.ui.components.GaugeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStatsAndSettingsTest {

    @Test
    fun `session stats track min max count and time span`() {
        val stats = listOf(
            snapshot(33f, timestampMillis = 1_000),
            snapshot(38.5f, timestampMillis = 2_000),
            snapshot(31.2f, timestampMillis = 3_000),
        ).fold(SessionStats()) { acc, s -> acc.record(s) }

        assertEquals(3, stats.sampleCount)
        assertEquals(31.2f, stats.minC!!, 0.001f)
        assertEquals(38.5f, stats.maxC!!, 0.001f)
        assertEquals(1_000L, stats.firstSampleMillis)
        assertEquals(3_000L, stats.lastSampleMillis)
    }

    @Test
    fun `session stats ignore readings without a temperature`() {
        val stats = SessionStats().record(snapshot(null)).record(snapshot(Float.NaN))
        assertEquals(0, stats.sampleCount)
        assertNull(stats.minC)
        assertNull(stats.firstSampleMillis)
    }

    @Test
    fun `threshold is clamped to range and snapped to half degrees`() {
        assertEquals(40f, UserSettings.sanitizeThreshold(40f), 0f)
        assertEquals(41.5f, UserSettings.sanitizeThreshold(41.4f), 0f)
        assertEquals(UserSettings.MIN_THRESHOLD_C, UserSettings.sanitizeThreshold(10f), 0f)
        assertEquals(UserSettings.MAX_THRESHOLD_C, UserSettings.sanitizeThreshold(90f), 0f)
        assertEquals(UserSettings.DEFAULT_THRESHOLD_C, UserSettings.sanitizeThreshold(Float.NaN), 0f)
    }

    @Test
    fun `warning margin is clamped to range`() {
        assertEquals(UserSettings.MIN_WARNING_MARGIN_C, UserSettings.sanitizeWarningMargin(0f), 0f)
        assertEquals(UserSettings.MAX_WARNING_MARGIN_C, UserSettings.sanitizeWarningMargin(20f), 0f)
        assertEquals(2.5f, UserSettings.sanitizeWarningMargin(2.6f), 0f)
    }

    @Test
    fun `default settings match the product spec`() {
        val defaults = UserSettings()
        assertEquals(40f, defaults.thresholdC, 0f)
        assertEquals(37f, defaults.warningStartC, 0f)
    }

    @Test
    fun `gauge scale maps 20C to start and threshold plus 10 to end`() {
        assertEquals(0f, GaugeScale.fraction(20f, 40f), 0.0001f)
        assertEquals(1f, GaugeScale.fraction(50f, 40f), 0.0001f)
        assertEquals(2f / 3f, GaugeScale.fraction(40f, 40f), 0.0001f)
        assertEquals(0f, GaugeScale.fraction(-5f, 40f), 0.0001f)
        assertEquals(1f, GaugeScale.fraction(80f, 40f), 0.0001f)
        assertEquals(0f, GaugeScale.fraction(Float.NaN, 40f), 0.0001f)
    }
}
