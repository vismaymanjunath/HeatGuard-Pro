package com.example.appdev.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Starts and stops [MonitoringService] and exposes whether it is actually running. */
class MonitoringController(context: Context) {

    private val context = context.applicationContext
    private val _running = MutableStateFlow(false)

    /** True only while the service is alive, regardless of what the user's preference says. */
    val isRunning: StateFlow<Boolean> = _running.asStateFlow()

    internal fun setRunning(running: Boolean) {
        _running.value = running
    }

    /** Returns false if Android refused to start the service (e.g. app not in a startable state). */
    fun start(): Boolean = runCatching {
        ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
    }.isSuccess

    fun stop() {
        context.stopService(Intent(context, MonitoringService::class.java))
    }
}
