package com.example.appdev.data.demo

import com.example.appdev.data.cooling.CoolingSessionManager
import com.example.appdev.domain.cooling.CoolingRepository
import com.example.appdev.domain.incident.IncidentRepository
import com.example.appdev.domain.incident.IncidentTracker
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.platform.MonitoringRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns Demo Mode on and off and keeps its data apart from real data.
 *
 * Demo readings, incidents and cooling sessions are stored with a DEMO source tag and are removed
 * whenever Demo Mode starts or ends, so a presentation always begins clean and leaves nothing
 * behind. Real data is never read, changed or deleted here.
 */
class DemoModeManager(
    val controller: DemoController,
    private val incidents: IncidentRepository,
    private val cooling: CoolingRepository,
    private val incidentTracker: IncidentTracker,
    private val coolingManager: CoolingSessionManager,
    private val runner: MonitoringRunner,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    fun setEnabled(on: Boolean) {
        scope.launch {
            mutex.withLock {
                if (on == controller.enabled.value) return@withLock
                if (on) {
                    clearDemoData()
                    controller.setEnabled(true)
                    runner.acquire(MonitoringRunner.OWNER_DEMO)
                } else {
                    runner.release(MonitoringRunner.OWNER_DEMO)
                    controller.setEnabled(false)
                    clearDemoData()
                }
            }
        }
    }

    /** Deletes demo incidents and cooling sessions (e.g. to restart a presentation). */
    fun resetData() {
        scope.launch { mutex.withLock { clearDemoData() } }
    }

    /** Removes demo rows left behind if the process ended during a demo. Safe to call at startup. */
    fun cleanUpStale() {
        scope.launch {
            mutex.withLock {
                if (!controller.enabled.value) clearDemoData()
            }
        }
    }

    private suspend fun clearDemoData() {
        coolingManager.reset()
        incidentTracker.reset()
        incidents.deleteAll(ReadingSource.DEMO)
        cooling.deleteAll(ReadingSource.DEMO)
    }
}
