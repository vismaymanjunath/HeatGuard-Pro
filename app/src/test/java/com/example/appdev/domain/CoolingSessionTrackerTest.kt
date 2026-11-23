package com.example.appdev.domain

import com.example.appdev.domain.cooling.CoolingEvent
import com.example.appdev.domain.cooling.CoolingRepository
import com.example.appdev.domain.cooling.CoolingSample
import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.domain.cooling.CoolingSessionTracker
import com.example.appdev.domain.cooling.SessionOutcome
import com.example.appdev.domain.cooling.StartResult
import com.example.appdev.domain.model.ReadingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCoolingRepository : CoolingRepository {
    private val sessions = MutableStateFlow<List<CoolingSession>>(emptyList())
    private val samples = MutableStateFlow<List<CoolingSample>>(emptyList())
    private var nextId = 1L
    val allSessions get() = sessions.value
    val allSamples get() = samples.value

    override suspend fun start(session: CoolingSession): Long {
        val id = nextId++
        sessions.value = sessions.value + session.copy(id = id)
        return id
    }

    override suspend fun update(session: CoolingSession) {
        sessions.value = sessions.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun addSample(sample: CoolingSample) {
        samples.value = samples.value + sample
    }

    override suspend fun getActive(source: ReadingSource) =
        sessions.value.lastOrNull { it.source == source && it.outcome == null }

    override fun observeSessions(source: ReadingSource): Flow<List<CoolingSession>> =
        sessions.map { l -> l.filter { it.source == source }.sortedByDescending { it.startMillis } }

    override fun observeSamples(sessionId: Long): Flow<List<CoolingSample>> =
        samples.map { l -> l.filter { it.sessionId == sessionId } }

    override suspend fun getSamples(sessionId: Long) = samples.value.filter { it.sessionId == sessionId }

    override suspend fun deleteAll(source: ReadingSource) {
        val ids = sessions.value.filter { it.source == source }.map { it.id }.toSet()
        sessions.value = sessions.value.filterNot { it.id in ids }
        samples.value = samples.value.filterNot { it.sessionId in ids }
    }
}

class CoolingSessionTrackerTest {

    private val repo = FakeCoolingRepository()
    private val target = 38f
    private val base = 5_000_000L

    private fun tracker(source: ReadingSource = ReadingSource.DEVICE) =
        CoolingSessionTracker(repo, source, sampleIntervalMillis = 5_000L, maxGapMillis = 300_000L)

    private fun reading(temp: Float?, sec: Long, source: ReadingSource = ReadingSource.DEVICE) =
        snapshot(temp, timestampMillis = base + sec * 1000).copy(source = source)

    @Test
    fun `starting captures initial state`() = runBlocking {
        val result = tracker().start(reading(41.2f, 0), target)
        assertTrue(result is StartResult.Started)
        val s = (result as StartResult.Started).session
        assertEquals(1L, s.id)
        assertEquals(41.2f, s.startTempC, 0f)
        assertEquals(38f, s.targetTempC, 0f)
        assertTrue(s.isActive)
        assertEquals(1, repo.allSamples.size)
    }

    @Test
    fun `cannot start when already cool, without a reading, or twice`() = runBlocking {
        val t = tracker()
        assertEquals(StartResult.AlreadyCool, t.start(reading(37.9f, 0), target))
        assertEquals(StartResult.AlreadyCool, t.start(reading(38f, 0), target))
        assertEquals(StartResult.NoReading, t.start(reading(null, 0), target))
        assertEquals(StartResult.NoReading, t.start(reading(41f, 0, ReadingSource.DEMO), target))
        assertTrue(t.start(reading(41f, 0), target) is StartResult.Started)
        assertEquals(StartResult.AlreadyActive, t.start(reading(41f, 1), target))
        assertEquals(1, repo.allSessions.size)
    }

    @Test
    fun `reaching the target completes the session as recovered`() = runBlocking {
        val t = tracker()
        t.start(reading(41f, 0), target)
        assertNull(t.onReading(reading(40f, 60)))
        assertNull(t.onReading(reading(38.5f, 120)))
        val event = t.onReading(reading(37.9f, 180))

        assertTrue(event is CoolingEvent.Recovered)
        val done = repo.allSessions.single()
        assertEquals(SessionOutcome.RECOVERED, done.outcome)
        assertEquals(180_000L, done.durationMillis)
        assertEquals(3.1f, done.dropC, 0.001f)
        assertEquals(1f, done.progress, 0f)
        assertNull(t.active)
    }

    @Test
    fun `progress and minimum track the readings`() = runBlocking {
        val t = tracker()
        t.start(reading(42f, 0), target) // 4 degrees to go
        t.onReading(reading(40f, 10))
        val s = t.active!!
        assertEquals(0.5f, s.progress, 0.001f)
        assertEquals(40f, s.minTempC, 0f)
        t.onReading(reading(41f, 20)) // reheating
        assertEquals(40f, t.active!!.minTempC, 0f)
        assertEquals(41f, t.active!!.lastTempC, 0f)
    }

    @Test
    fun `samples are throttled`() = runBlocking {
        val t = tracker()
        t.start(reading(41f, 0), target)
        t.onReading(reading(40.9f, 2))
        t.onReading(reading(40.8f, 4))
        assertEquals(1, repo.allSamples.size)
        t.onReading(reading(40.7f, 6))
        assertEquals(2, repo.allSamples.size)
    }

    @Test
    fun `user stop ends the session with the stopped outcome`() = runBlocking {
        val t = tracker()
        t.start(reading(41f, 0), target)
        t.onReading(reading(40f, 30))
        val closed = t.stop(base + 45_000)!!
        assertEquals(SessionOutcome.STOPPED, closed.outcome)
        assertEquals(45_000L, closed.durationMillis)
        assertNull(t.stop(base + 50_000))
        assertFalse(repo.allSessions.single().isActive)
    }

    @Test
    fun `a long silence interrupts the session at the last reading seen`() = runBlocking {
        val t = tracker()
        t.start(reading(41f, 0), target)
        t.onReading(reading(40.5f, 60))
        val event = t.onReading(reading(30f, 3600)) // cooled, but we cannot know when

        assertTrue(event is CoolingEvent.Interrupted)
        val s = repo.allSessions.single()
        assertEquals(SessionOutcome.INTERRUPTED, s.outcome)
        assertEquals(base + 60_000, s.endMillis)
        assertEquals(60_000L, s.durationMillis)
    }

    @Test
    fun `resume restores an open session and continues it`() = runBlocking {
        tracker().start(reading(41f, 0), target)
        val restarted = tracker()
        assertNotNull(restarted.resume())
        val event = restarted.onReading(reading(37f, 100))
        assertTrue(event is CoolingEvent.Recovered)
        assertEquals(1, repo.allSessions.size)
    }

    @Test
    fun `readings from another source are ignored`() = runBlocking {
        val t = tracker()
        t.start(reading(41f, 0), target)
        assertNull(t.onReading(reading(30f, 10, ReadingSource.DEMO)))
        assertTrue(t.active!!.isActive)
    }

    @Test
    fun `stale or missing readings do nothing`() = runBlocking {
        val t = tracker()
        t.start(reading(41f, 10), target)
        assertNull(t.onReading(reading(null, 20)))
        assertNull(t.onReading(reading(30f, 5))) // older than the last reading seen
        assertEquals(41f, t.active!!.lastTempC, 0f)
    }
}
