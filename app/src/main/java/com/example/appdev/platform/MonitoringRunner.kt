package com.example.appdev.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs the [MonitoringCoordinator] while at least one owner needs it (the foreground service, or
 * Demo Mode). A single shared loop avoids two coordinators processing the same reading twice.
 */
class MonitoringRunner(
    private val scope: CoroutineScope,
    private val coordinator: () -> MonitoringCoordinator,
) {
    private val owners = mutableSetOf<String>()
    private var job: Job? = null

    @Synchronized
    fun acquire(owner: String) {
        owners += owner
        if (job == null) job = scope.launch { coordinator().run() }
    }

    @Synchronized
    fun release(owner: String) {
        owners -= owner
        if (owners.isEmpty()) {
            job?.cancel()
            job = null
        }
    }

    @Synchronized
    fun isHeldBy(owner: String): Boolean = owner in owners

    companion object {
        const val OWNER_SERVICE = "service"
        const val OWNER_DEMO = "demo"
    }
}
