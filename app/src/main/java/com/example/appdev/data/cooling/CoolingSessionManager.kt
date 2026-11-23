package com.example.appdev.data.cooling

import com.example.appdev.data.ThermalRepository
import com.example.appdev.domain.cooling.CoolingEvent
import com.example.appdev.domain.cooling.CoolingRepository
import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.domain.cooling.CoolingSessionTracker
import com.example.appdev.domain.cooling.StartResult
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.thermal.TemperaturePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A running session together with the readings collected so far, for live display. */
data class ActiveCoolingSession(val session: CoolingSession, val points: List<TemperaturePoint>)

/**
 * Owns the cooling session for one [source] (real or demo). While a session is active it subscribes
 * to live readings, which also keeps the battery receiver running as long as the process lives.
 * Readings from the other source are ignored, so a real session is never fed demo data.
 */
class CoolingSessionManager(
    private val source: ReadingSource,
    private val tracker: CoolingSessionTracker,
    private val repository: CoolingRepository,
    private val thermalRepository: ThermalRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var job: Job? = null

    private val _active = MutableStateFlow<ActiveCoolingSession?>(null)
    val active: StateFlow<ActiveCoolingSession?> = _active.asStateFlow()

    /** The session that most recently finished, kept so the UI can show a result until dismissed. */
    private val _lastResult = MutableStateFlow<CoolingSession?>(null)
    val lastResult: StateFlow<CoolingSession?> = _lastResult.asStateFlow()

    /** Picks up a session left open by an earlier process, if any. */
    suspend fun resumeIfActive() = mutex.withLock {
        val session = tracker.resume() ?: return@withLock
        val points = repository.getSamples(session.id).map { TemperaturePoint(it.timestampMillis, it.temperatureC) }
        _active.value = ActiveCoolingSession(session, points)
        collect()
    }

    suspend fun start(targetC: Float): StartResult = mutex.withLock {
        val snapshot = thermalRepository.latestSnapshot.value
            ?.takeIf { it.source == source }
            ?: return@withLock StartResult.NoReading
        val result = tracker.start(snapshot, targetC)
        if (result is StartResult.Started) {
            _lastResult.value = null
            val t = snapshot.temperatureC ?: result.session.startTempC
            _active.value = ActiveCoolingSession(result.session, listOf(TemperaturePoint(snapshot.timestampMillis, t)))
            collect()
        }
        result
    }

    suspend fun stop() = mutex.withLock {
        val closed = tracker.stop(clock()) ?: return@withLock
        finish(closed)
    }

    /** Drops all in-memory state, e.g. after the stored sessions for this source were deleted. */
    suspend fun reset() = mutex.withLock {
        job?.cancel()
        job = null
        tracker.reset()
        _active.value = null
        _lastResult.value = null
    }

    fun dismissResult() {
        _lastResult.value = null
    }

    private fun collect() {
        job?.cancel()
        job = scope.launch {
            thermalRepository.latestSnapshot.filterNotNull().collect { snapshot ->
                if (snapshot.source != source) return@collect
                mutex.withLock {
                    val event = tracker.onReading(snapshot)
                    val current = tracker.active
                    val temp = snapshot.temperatureC
                    when {
                        event is CoolingEvent.Recovered -> finish(event.session)
                        event is CoolingEvent.Interrupted -> finish(event.session)
                        current != null && temp != null && temp.isFinite() -> {
                            val prev = _active.value
                            val points = (prev?.points.orEmpty() + TemperaturePoint(snapshot.timestampMillis, temp))
                                .distinctBy { it.timestampMillis }
                            _active.value = ActiveCoolingSession(current, points)
                        }
                    }
                }
            }
        }
    }

    private fun finish(session: CoolingSession) {
        _active.value = null
        _lastResult.value = session
        job?.cancel()
        job = null
    }
}
