package com.example.appdev.platform

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.appdev.HeatGuardApp
import com.example.appdev.MainActivity
import com.example.appdev.R
import com.example.appdev.domain.surface.SurfaceModels
import com.example.appdev.ui.components.labelRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile showing the battery temperature. The tile is "on" while background
 * monitoring runs; tapping it starts or stops monitoring.
 */
class HeatTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val container get() = (application as HeatGuardApp).container

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = scope.launch {
            val c = container
            combine(
                c.thermalRepository.latestSnapshot,
                c.settingsRepository.settings,
                c.monitoringController.isRunning,
                c.demoController.enabled,
            ) { snapshot, settings, running, demo ->
                // Before the first broadcast arrives, show the live battery value rather than a blank tile.
                val reading = snapshot ?: if (demo) null else c.batteryMonitor.readNow()
                SurfaceModels.tile(reading, settings, running)
            }.collect { model -> render(model) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(model: com.example.appdev.domain.surface.TileModel) {
        val tile = qsTile ?: return
        val status = getString(model.level.labelRes())
        tile.label = if (model.isDemo) getString(R.string.demo_prefix, model.label) else model.label
        tile.icon = Icon.createWithResource(this, R.drawable.ic_thermostat)
        tile.state = if (model.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (model.active) R.string.tile_sub_on else R.string.tile_sub_off, status)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val c = container
        scope.launch {
            if (c.monitoringController.isRunning.value) {
                c.settingsRepository.setMonitoringEnabled(false)
                c.monitoringController.stop()
                return@launch
            }
            // Starting needs the notification permission to be useful; the app handles asking for it.
            val canNotify = c.alertNotifier.notificationsAllowed()
            val started = canNotify && run {
                c.settingsRepository.setMonitoringEnabled(true)
                c.monitoringController.start()
            }
            if (!started) {
                c.settingsRepository.setMonitoringEnabled(false)
                openApp()
            }
        }
    }

    // The Intent overload is the only one before Android 14, where the PendingIntent overload appears.
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
