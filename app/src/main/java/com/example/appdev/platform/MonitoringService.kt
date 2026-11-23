package com.example.appdev.platform

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.appdev.HeatGuardApp
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps battery temperature monitoring alive while the app is closed.
 * It is only ever started by the user (from the dashboard), which is what Android requires for
 * starting a foreground service, and it can always be stopped from its notification.
 */
class MonitoringService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as HeatGuardApp).container

        if (intent?.action == ACTION_STOP) {
            container.applicationScope.launch { container.settingsRepository.setMonitoringEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }

        // Must be called promptly after startForegroundService().
        val initial = container.alertNotifier.buildMonitoringNotification(
            snapshot = null,
            settings = null,
            overheating = false,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, AlertNotifier.ID_MONITOR, initial, type)

        container.monitoringController.setRunning(true)
        container.monitoringRunner.acquire(MonitoringRunner.OWNER_SERVICE)
        return START_STICKY
    }

    override fun onDestroy() {
        val container = (application as HeatGuardApp).container
        container.monitoringRunner.release(MonitoringRunner.OWNER_SERVICE)
        container.monitoringController.setRunning(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.appdev.action.STOP_MONITORING"
    }
}
