package com.example.appdev.platform

import com.example.appdev.domain.incident.IncidentTracker
import com.example.appdev.domain.incident.TrackerEvent
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Feeds every reading through the incident tracker for its source and turns the resulting events
 * into alerts. Real and demo readings take exactly the same path; each has its own tracker, so
 * demo incidents are stored as demo data and never touch real history.
 */
class MonitoringCoordinator(
    private val snapshots: Flow<BatterySnapshot?>,
    private val settings: Flow<UserSettings>,
    private val trackers: Map<ReadingSource, IncidentTracker>,
    private val presenter: AlertPresenter,
    /** The persistent notification is only updated while the foreground service owns it. */
    private val isServiceRunning: () -> Boolean,
    /** Called after each reading, e.g. to refresh the home-screen widget. */
    private val onProcessed: (BatterySnapshot, UserSettings) -> Unit = { _, _ -> },
) {
    private val overheating = mutableMapOf<ReadingSource, Boolean>()

    /** Suspends until cancelled. */
    suspend fun run() {
        snapshots.filterNotNull().collect { snapshot -> handle(snapshot, settings.first()) }
    }

    internal suspend fun handle(snapshot: BatterySnapshot, settings: UserSettings) {
        val source = snapshot.source
        when (trackers[source]?.onReading(snapshot, settings)) {
            is TrackerEvent.Triggered -> {
                overheating[source] = true
                presenter.showOverheatAlert(snapshot, settings)
                if (settings.vibrationEnabled) presenter.vibrate()
            }
            is TrackerEvent.Resolved -> {
                overheating[source] = false
                presenter.cancelOverheatAlert()
            }
            null -> Unit
        }
        if (isServiceRunning()) presenter.updateMonitoring(snapshot, settings, overheating[source] == true)
        onProcessed(snapshot, settings)
    }
}
