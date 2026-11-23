package com.example.appdev.domain

import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.incident.IncidentTimeline
import com.example.appdev.domain.incident.IncidentTracker
import com.example.appdev.domain.incident.TimelineMarker
import com.example.appdev.domain.incident.TrackerEvent
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.ui.components.Durations
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentTrackerTest {

    private val settings = UserSettings(thresholdC = 40f, hysteresisC = 2f)
    private val repo = FakeIncidentRepository()

    private fun tracker(
        source: ReadingSource = ReadingSource.DEVICE,
        interval: Long = 10_000L,
    ) = IncidentTracker(repo, source, sampleIntervalMillis = interval, maxGapMillis = 120_000L)

    private fun reading(
        temp: Float,
        secondsFromStart: Long,
        charging: Boolean = false,
        source: ReadingSource = ReadingSource.DEVICE,
    ) = snapshot(
        temperatureC = temp,
        timestampMillis = 1_000_000L + secondsFromStart * 1000,
        chargeStatus = if (charging) ChargeStatus.CHARGING else ChargeStatus.DISCHARGING,
        powerSource = if (charging) PowerSource.USB else PowerSource.NONE,
    ).copy(source = source)

    @Test
    fun `incident opens at threshold with initial state captured`() = runBlocking {
        val t = tracker()
        assertNull(t.onReading(reading(39f, 0), settings))
        val event = t.onReading(reading(40.2f, 5, charging = true), settings)

        assertTrue(event is TrackerEvent.Triggered)
        val incident = (event as TrackerEvent.Triggered).incident
        assertEquals(1L, incident.id)
        assertEquals(40.2f, incident.initialTempC, 0f)
        assertEquals(40.2f, incident.peakTempC, 0f)
        assertEquals(40f, incident.thresholdC, 0f)
        assertEquals(80, incident.startBatteryPercent)
        assertEquals(ChargeStatus.CHARGING, incident.startChargeStatus)
        assertEquals(PowerSource.USB, incident.startPowerSource)
        assertTrue(incident.isActive)
        assertEquals(1, repo.allIncidents.size)
        assertEquals(1, repo.allSamples.size)
    }

    @Test
    fun `peak is tracked and a single incident covers a noisy crossing`() = runBlocking {
        val t = tracker()
        val events = listOf(
            reading(40.0f, 0), reading(40.8f, 5), reading(39.7f, 10),
            reading(41.5f, 15), reading(40.2f, 20), reading(39.9f, 25),
        ).mapNotNull { t.onReading(it, settings) }

        assertEquals(1, events.size)
        val stored = repo.allIncidents.single()
        assertEquals(41.5f, stored.peakTempC, 0f)
        assertEquals(1_000_000L + 15_000, stored.peakMillis)
        assertTrue(stored.isActive)
    }

    @Test
    fun `resolution records end time temperature and duration`() = runBlocking {
        val t = tracker()
        t.onReading(reading(40.5f, 0), settings)
        t.onReading(reading(42f, 30), settings)
        val event = t.onReading(reading(37.5f, 90), settings)

        assertTrue(event is TrackerEvent.Resolved)
        val resolved = repo.allIncidents.single()
        assertFalse(resolved.isActive)
        assertEquals(1_000_000L + 90_000, resolved.endMillis)
        assertEquals(37.5f, resolved.endTempC!!, 0f)
        assertEquals(42f, resolved.peakTempC, 0f)
        assertEquals(90_000L, resolved.durationMillis)
    }

    @Test
    fun `samples are throttled to the interval but peaks are always recorded`() = runBlocking {
        val t = tracker(interval = 10_000L)
        t.onReading(reading(40f, 0), settings) // start sample
        t.onReading(reading(40f, 3), settings) // not due, no new peak
        t.onReading(reading(40f, 6), settings) // not due, no new peak
        assertEquals(1, repo.allSamples.size)
        t.onReading(reading(40.4f, 7), settings) // new peak -> recorded
        assertEquals(2, repo.allSamples.size)
        t.onReading(reading(40.3f, 12), settings) // not due (5 s since last write)
        assertEquals(2, repo.allSamples.size)
        t.onReading(reading(40.3f, 18), settings) // due
        assertEquals(3, repo.allSamples.size)
    }

    @Test
    fun `a second incident can start after the first resolves`() = runBlocking {
        val t = tracker()
        t.onReading(reading(41f, 0), settings)
        t.onReading(reading(37f, 60), settings)
        val second = t.onReading(reading(40.5f, 600), settings)
        assertTrue(second is TrackerEvent.Triggered)
        assertEquals(2, repo.allIncidents.size)
        assertEquals(2L, (second as TrackerEvent.Triggered).incident.id)
    }

    @Test
    fun `readings from another source are ignored`() = runBlocking {
        val t = tracker(source = ReadingSource.DEVICE)
        assertNull(t.onReading(reading(45f, 0, source = ReadingSource.DEMO), settings))
        assertTrue(repo.allIncidents.isEmpty())
    }

    @Test
    fun `missing temperature never creates or resolves incidents`() = runBlocking {
        val t = tracker()
        assertNull(t.onReading(snapshot(null, timestampMillis = 1), settings))
        t.onReading(reading(41f, 0), settings)
        assertNull(t.onReading(snapshot(null, timestampMillis = 2_000_000L), settings))
        assertTrue(repo.allIncidents.single().isActive)
    }

    @Test
    fun `open incident is resumed after restart when still hot`() = runBlocking {
        tracker().onReading(reading(41f, 0), settings)
        val restarted = tracker()
        val event = restarted.onReading(reading(41.5f, 30), settings)

        assertNull(event) // no second alert
        assertEquals(1, repo.allIncidents.size)
        assertEquals(41.5f, repo.allIncidents.single().peakTempC, 0f)
    }

    @Test
    fun `stale open incident is closed at last seen time when phone cooled during the gap`() = runBlocking {
        tracker().onReading(reading(42f, 0), settings)
        val restarted = tracker()
        val event = restarted.onReading(reading(30f, 3600), settings)

        assertNull(event)
        val closed = repo.allIncidents.single()
        assertFalse(closed.isActive)
        assertEquals(1_000_000L, closed.endMillis) // last seen, not an hour later
        assertNull(closed.endTempC)
        assertEquals(0L, closed.durationMillis)
    }

    @Test
    fun `demo tracker stores demo incidents only and real queries stay clean`() = runBlocking {
        val demo = tracker(source = ReadingSource.DEMO)
        demo.onReading(reading(44f, 0, source = ReadingSource.DEMO), settings)
        assertEquals(1, repo.allIncidents.size)
        assertEquals(ReadingSource.DEMO, repo.allIncidents.single().source)
        assertNull(repo.getActive(ReadingSource.DEVICE))
        assertNotNull(repo.getActive(ReadingSource.DEMO))
    }

    @Test
    fun `timeline marks start peak and resolution`() = runBlocking {
        val t = tracker(interval = 1_000L)
        t.onReading(reading(40.1f, 0), settings)
        t.onReading(reading(41.9f, 5), settings)
        t.onReading(reading(40.5f, 10), settings)
        t.onReading(reading(37.8f, 15), settings)

        val incident: Incident = repo.allIncidents.single()
        val timeline = IncidentTimeline.build(incident, repo.allSamples)

        assertEquals(TimelineMarker.START, timeline.first().marker)
        assertEquals(TimelineMarker.RESOLVED, timeline.last().marker)
        assertEquals(41.9f, timeline.single { it.marker == TimelineMarker.PEAK }.temperatureC, 0f)
        assertEquals(timeline.map { it.timestampMillis }.sorted(), timeline.map { it.timestampMillis })
    }

    @Test
    fun `durations are formatted compactly`() {
        assertEquals("0 s", Durations.format(-5))
        assertEquals("42 s", Durations.format(42_000))
        assertEquals("5 min 07 s", Durations.format(307_000))
        assertEquals("1 h 05 min", Durations.format(3_900_000))
    }
}
