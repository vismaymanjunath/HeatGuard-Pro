package com.example.appdev.domain

import com.example.appdev.domain.analytics.CsvExporter
import com.example.appdev.domain.analytics.InsightType
import com.example.appdev.domain.analytics.InsightsEngine
import com.example.appdev.domain.analytics.LiveAnalytics
import com.example.appdev.domain.analytics.TimeOfDay
import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.domain.cooling.SessionOutcome
import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.incident.IncidentSample
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.thermal.TemperaturePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class AnalyticsTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = at(2026, 11, 18, 12)
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        GregorianCalendar(utc).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    private fun incident(
        start: Long,
        peak: Float = 41f,
        durationMin: Long = 5,
        charging: Boolean = false,
        power: PowerSource = if (charging) PowerSource.USB else PowerSource.NONE,
        active: Boolean = false,
        threshold: Float = 40f,
    ) = Incident(
        id = start, source = ReadingSource.DEVICE, startMillis = start,
        endMillis = if (active) null else start + durationMin * minute,
        lastSeenMillis = start + durationMin * minute,
        initialTempC = 40f, peakTempC = peak, peakMillis = start + minute,
        endTempC = if (active) null else 37f, thresholdC = threshold,
        startBatteryPercent = 80, endBatteryPercent = if (active) null else 79,
        startChargeStatus = if (charging) ChargeStatus.CHARGING else ChargeStatus.DISCHARGING,
        startPowerSource = power,
    )

    private fun report(vararg incidents: Incident) = InsightsEngine.compute(incidents.toList(), emptyList(), now, utc)

    private fun types(r: com.example.appdev.domain.analytics.InsightsReport) = r.recommendations.map { it.type }

    // ---------------------------------------------------------------- live analytics

    private fun pts(vararg pairs: Pair<Long, Float>) = pairs.map { TemperaturePoint(it.first * 1000, it.second) }

    @Test
    fun `window keeps only points within the requested span of the newest point`() {
        val p = pts(0L to 30f, 100L to 31f, 400L to 32f, 600L to 33f)
        val w = LiveAnalytics.window(p, 5 * minute)
        // Newest is t=600 s, so a 5-minute window starts at t=300 s: t=0 and t=100 s fall outside.
        assertEquals(listOf(32f, 33f), w.map { it.temperatureC })
        assertEquals(listOf(400_000L, 600_000L), w.map { it.timestampMillis })
        assertTrue(LiveAnalytics.window(emptyList(), minute).isEmpty())
    }

    @Test
    fun `stats summarise min max average latest and time above threshold`() {
        // Threshold 40: above from t=20 to t=50 (30 s), then falls.
        val p = pts(0L to 38f, 10L to 39f, 20L to 41f, 35L to 42f, 50L to 39f, 60L to 38f)
        val s = LiveAnalytics.stats(p, 40f)!!
        assertEquals(6, s.sampleCount)
        assertEquals(38f, s.minC, 0f)
        assertEquals(42f, s.maxC, 0f)
        assertEquals(39.5f, s.avgC, 0.001f)
        assertEquals(38f, s.latestC, 0f)
        assertEquals(30_000L, s.aboveThresholdMillis)
        assertEquals(60_000L, s.spanMillis)
        assertNull(LiveAnalytics.stats(emptyList(), 40f))
    }

    @Test
    fun `long gaps are not counted as time above threshold`() {
        val p = pts(0L to 41f, 10L to 41f, 600L to 41f)
        assertEquals(10_000L, LiveAnalytics.stats(p, 40f)!!.aboveThresholdMillis)
    }

    // ---------------------------------------------------------------- insights

    @Test
    fun `daily buckets cover seven days ending today`() {
        val r = report(
            incident(at(2026, 11, 18, 9)),
            incident(at(2026, 11, 18, 15)),
            incident(at(2026, 11, 16, 10), peak = 44f),
            incident(at(2026, 11, 11, 10)), // 7 days ago: outside the window
        )
        assertEquals(7, r.days.size)
        assertEquals(at(2026, 11, 18, 0), r.days.last().startMillis)
        assertEquals(at(2026, 11, 12, 0), r.days.first().startMillis)
        assertEquals(2, r.days.last().count)
        assertEquals(1, r.days[4].count) // 16 Nov
        assertEquals(44f, r.days[4].maxPeakC!!, 0f)
        assertEquals(0, r.days[5].count)
        assertEquals(3, r.last7Days)
        assertEquals(1, r.previous7Days)
        assertEquals(4, r.totalIncidents)
    }

    @Test
    fun `hours map to times of day`() {
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.fromHour(0))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.fromHour(5))
        assertEquals(TimeOfDay.MORNING, TimeOfDay.fromHour(6))
        assertEquals(TimeOfDay.AFTERNOON, TimeOfDay.fromHour(12))
        assertEquals(TimeOfDay.EVENING, TimeOfDay.fromHour(18))
        assertEquals(TimeOfDay.EVENING, TimeOfDay.fromHour(23))
        val r = report(incident(at(2026, 11, 17, 20)), incident(at(2026, 11, 17, 3)), incident(at(2026, 11, 16, 21)))
        assertEquals(2, r.timeOfDay[TimeOfDay.EVENING])
        assertEquals(1, r.timeOfDay[TimeOfDay.NIGHT])
        assertEquals(0, r.timeOfDay[TimeOfDay.MORNING])
    }

    @Test
    fun `peaks and durations are aggregated from resolved incidents`() {
        val r = report(
            incident(at(2026, 11, 17, 10), peak = 41f, durationMin = 10),
            incident(at(2026, 11, 16, 10), peak = 45f, durationMin = 30),
            incident(at(2026, 11, 15, 10), peak = 43f, active = true),
        )
        assertEquals(3, r.totalIncidents)
        assertEquals(2, r.resolvedIncidents)
        assertEquals(45f, r.maxPeakC!!, 0f)
        assertEquals(43f, r.avgPeakC!!, 0.001f)
        assertEquals(20 * minute, r.avgDurationMillis)
        assertEquals(30 * minute, r.longestDurationMillis)
    }

    @Test
    fun `charging correlation counts incidents that began while charging`() {
        val r = report(
            incident(at(2026, 11, 17, 10), charging = true, power = PowerSource.WIRELESS, peak = 44f),
            incident(at(2026, 11, 16, 10), charging = true, power = PowerSource.AC, peak = 42f),
            incident(at(2026, 11, 15, 10), charging = false, peak = 41f),
            incident(at(2026, 11, 14, 10), charging = false, peak = 41f),
        )
        val c = r.charging
        assertEquals(4, c.total)
        assertEquals(2, c.whileCharging)
        assertEquals(2, c.onBattery)
        assertEquals(0.5f, c.chargingShare!!, 0f)
        assertEquals(1, c.byPowerSource[PowerSource.WIRELESS])
        assertEquals(1, c.byPowerSource[PowerSource.AC])
        assertEquals(43f, c.avgPeakChargingC!!, 0.001f)
        assertEquals(41f, c.avgPeakOnBatteryC!!, 0.001f)
    }

    @Test
    fun `no incidents gives a single no-incidents insight`() {
        val r = report()
        assertEquals(listOf(InsightType.NO_INCIDENTS), types(r))
        assertNull(r.avgPeakC)
        assertNull(r.charging.chargingShare)
    }

    @Test
    fun `too few incidents never claims a pattern`() {
        val r = report(incident(at(2026, 11, 17, 20), charging = true), incident(at(2026, 11, 16, 20), charging = true))
        assertEquals(listOf(InsightType.NOT_ENOUGH_DATA), types(r))
        assertEquals(2, r.recommendations.single().count)
    }

    @Test
    fun `charging heavy and wireless heavy are reported with percentages`() {
        val r = report(
            incident(at(2026, 11, 17, 10), charging = true, power = PowerSource.WIRELESS),
            incident(at(2026, 11, 16, 10), charging = true, power = PowerSource.WIRELESS),
            incident(at(2026, 11, 15, 10), charging = true, power = PowerSource.AC),
            incident(at(2026, 11, 14, 10), charging = false),
        )
        val charging = r.recommendations.single { it.type == InsightType.CHARGING_HEAVY }
        assertEquals(75, charging.percent)
        val wireless = r.recommendations.single { it.type == InsightType.WIRELESS_HEAVY }
        assertEquals(66, wireless.percent)
    }

    @Test
    fun `a dominant time of day is detected`() {
        val r = report(
            incident(at(2026, 11, 17, 20)), incident(at(2026, 11, 16, 21)),
            incident(at(2026, 11, 15, 19)), incident(at(2026, 11, 14, 9)),
        )
        val rec = r.recommendations.single { it.type == InsightType.TIME_OF_DAY_PEAK }
        assertEquals(TimeOfDay.EVENING, rec.timeOfDay)
        assertEquals(75, rec.percent)
    }

    @Test
    fun `long incidents and high peaks are flagged`() {
        val r = report(
            incident(at(2026, 11, 17, 10), peak = 44f, durationMin = 25),
            incident(at(2026, 11, 16, 10), peak = 45f, durationMin = 20),
            incident(at(2026, 11, 15, 10), peak = 43f, durationMin = 30),
        )
        assertTrue(InsightType.LONG_INCIDENTS in types(r))
        val high = r.recommendations.single { it.type == InsightType.HIGH_PEAKS }
        assertEquals(4f, high.degreesC!!, 0.001f)
    }

    @Test
    fun `week over week change needs a difference of at least two`() {
        val worse = report(
            incident(at(2026, 11, 18, 9)), incident(at(2026, 11, 17, 9)), incident(at(2026, 11, 16, 9)),
            incident(at(2026, 11, 10, 9)),
        )
        val w = worse.recommendations.single { it.type == InsightType.WORSENING }
        assertEquals(3, w.count)
        assertEquals(1, w.previousCount)

        val better = report(
            incident(at(2026, 11, 18, 9)),
            incident(at(2026, 11, 10, 9)), incident(at(2026, 11, 9, 9)), incident(at(2026, 11, 8, 9)),
        )
        assertTrue(InsightType.IMPROVING in types(better))

        val flat = report(
            incident(at(2026, 11, 18, 9)), incident(at(2026, 11, 17, 9)),
            incident(at(2026, 11, 10, 9)), incident(at(2026, 11, 9, 9)),
        )
        assertFalse(InsightType.WORSENING in types(flat))
        assertFalse(InsightType.IMPROVING in types(flat))
    }

    @Test
    fun `a quiet recent week is acknowledged`() {
        val r = report(
            incident(at(2026, 10, 1, 9)), incident(at(2026, 10, 2, 9)), incident(at(2026, 10, 3, 9)),
        )
        assertEquals(0, r.last7Days)
        assertTrue(InsightType.QUIET_WEEK in types(r))
    }

    @Test
    fun `cooling sessions feed the recovery stats`() {
        val s = CoolingSession(
            id = 1, source = ReadingSource.DEVICE, startMillis = 0, endMillis = 120_000, lastSeenMillis = 120_000,
            startTempC = 41f, lastTempC = 38f, minTempC = 38f, targetTempC = 38f, startBatteryPercent = 70,
            startChargeStatus = ChargeStatus.DISCHARGING, startPowerSource = PowerSource.NONE,
            outcome = SessionOutcome.RECOVERED,
        )
        val r = InsightsEngine.compute(emptyList(), listOf(s), now, utc)
        assertEquals(1, r.recovery.recoveredCount)
        assertEquals(120_000L, r.recovery.averageRecoveryMillis)
    }

    // ---------------------------------------------------------------- CSV

    @Test
    fun `escape quotes only when needed and doubles embedded quotes`() {
        assertEquals("plain", CsvExporter.escape("plain"))
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExporter.escape("line1\nline2"))
    }

    @Test
    fun `iso timestamps are UTC`() {
        assertEquals("2026-11-18T12:00:00Z", CsvExporter.iso(now))
        assertEquals("1970-01-01T00:00:00Z", CsvExporter.iso(0))
    }

    @Test
    fun `incident csv has a header and one row per incident in time order`() {
        val csv = CsvExporter.incidents(
            listOf(
                incident(at(2026, 11, 18, 9), peak = 42.5f, durationMin = 10, charging = true, power = PowerSource.AC),
                incident(at(2026, 11, 17, 9), peak = 41f, active = true),
            ),
        )
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(CsvExporter.INCIDENT_HEADER.joinToString(","), lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains("ongoing")) // 17 Nov sorts first
        assertTrue(lines[2].contains("resolved"))
        assertTrue(lines[2].contains("2026-11-18T09:00:00Z"))
        assertTrue(lines[2].contains(",600,")) // duration in seconds
        assertTrue(lines[2].contains(",42.5,"))
        assertTrue(lines[2].endsWith("CHARGING,AC"))
        // Ongoing: empty end time, end temperature and end battery.
        assertEquals(CsvExporter.INCIDENT_HEADER.size, lines[1].split(",").size)
        assertTrue(lines[1].contains(",,"))
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test
    fun `numbers use a dot decimal regardless of the device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("38.4", CsvExporter.num(38.4f))
            val csv = CsvExporter.incidents(listOf(incident(now, peak = 41.7f)))
            assertTrue(csv.contains(",41.7,"))
            assertFalse(csv.contains("41,7"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `readings csv is ordered by incident then time`() {
        val s = { id: Long, t: Long -> IncidentSample(id, t, 40f, 70, ChargeStatus.DISCHARGING, PowerSource.NONE) }
        val csv = CsvExporter.readings(listOf(s(2, 5_000), s(1, 9_000), s(1, 1_000)))
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(4, lines.size)
        assertTrue(lines[1].startsWith("1,1970-01-01T00:00:01Z"))
        assertTrue(lines[2].startsWith("1,1970-01-01T00:00:09Z"))
        assertTrue(lines[3].startsWith("2,"))
        assertNotNull(lines[0])
    }

    @Test
    fun `sessions csv records outcome and drop`() {
        val s = CoolingSession(
            id = 7, source = ReadingSource.DEVICE, startMillis = 0, endMillis = 180_000, lastSeenMillis = 180_000,
            startTempC = 41f, lastTempC = 37.9f, minTempC = 37.9f, targetTempC = 38f, startBatteryPercent = null,
            startChargeStatus = ChargeStatus.UNKNOWN, startPowerSource = PowerSource.UNKNOWN,
            outcome = SessionOutcome.RECOVERED,
        )
        val line = CsvExporter.sessions(listOf(s)).trimEnd().split("\r\n")[1]
        assertTrue(line.startsWith("7,recovered,"))
        assertTrue(line.contains(",180,"))
        assertTrue(line.contains(",3.1,"))
        assertEquals(CsvExporter.SESSION_HEADER.size, line.split(",").size)
    }
}
