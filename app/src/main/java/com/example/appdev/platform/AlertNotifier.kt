package com.example.appdev.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.appdev.MainActivity
import com.example.appdev.R
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.model.UserSettings

/**
 * Builds and posts HeatGuard notifications and triggers alert vibration.
 *
 * Vibration is done explicitly (not through the channel) because notification channel settings
 * cannot be changed after creation, and the user can toggle vibration at any time.
 */
class AlertNotifier(context: Context) : AlertPresenter {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    init {
        createChannels()
    }

    fun notificationsAllowed(): Boolean = manager.areNotificationsEnabled()

    /** The persistent notification that keeps the monitoring service in the foreground. */
    fun buildMonitoringNotification(
        snapshot: BatterySnapshot?,
        settings: UserSettings?,
        overheating: Boolean,
    ): Notification {
        // Without settings (the very first notification, posted before they are loaded) nothing
        // about units or thresholds is guessed; the next reading replaces this with the real values.
        val temperature = if (settings != null) snapshot?.temperatureC?.let { Temperatures.format(it, settings.unit) } else null
        val plainTitle = when {
            overheating && temperature != null -> context.getString(R.string.notif_monitor_title_hot, temperature)
            temperature != null -> context.getString(R.string.notif_monitor_title, temperature)
            else -> context.getString(R.string.notif_monitor_title_waiting)
        }
        val title = demoLabelled(plainTitle, snapshot)
        val text = when {
            settings == null -> context.getString(R.string.notif_monitor_text_generic)
            overheating -> context.getString(R.string.notif_monitor_text_hot, Temperatures.format(settings.thresholdC, settings.unit))
            else -> context.getString(R.string.notif_monitor_text, Temperatures.format(settings.thresholdC, settings.unit))
        }
        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_STOP,
            Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_thermostat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    /** Refreshes the persistent notification in place. Silently skipped if notifications are off. */
    @SuppressLint("MissingPermission")
    override fun updateMonitoring(snapshot: BatterySnapshot?, settings: UserSettings, overheating: Boolean) {
        if (!canPost()) return
        manager.notify(ID_MONITOR, buildMonitoringNotification(snapshot, settings, overheating))
    }

    /** Posts the high-priority overheating alert with the exact required wording. */
    @SuppressLint("MissingPermission")
    override fun showOverheatAlert(snapshot: BatterySnapshot, settings: UserSettings) {
        if (!settings.notificationsEnabled || !canPost()) return
        val temperature = snapshot.temperatureC?.let { Temperatures.format(it, settings.unit) }.orEmpty()
        val message = context.getString(R.string.overheat_warning)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(demoLabelled(context.getString(R.string.notif_alert_title, temperature), snapshot))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        manager.notify(ID_ALERT, notification)
    }

    override fun cancelOverheatAlert() {
        manager.cancel(ID_ALERT)
    }

    /** Short buzz pattern for an overheating alert. No-op on devices without a vibrator. */
    @Suppress("DEPRECATION")
    override fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
        } else {
            vibrator.vibrate(VIBRATION_PATTERN, -1)
        }
    }

    /** Notifications about simulated readings are always labelled so they cannot be mistaken for real ones. */
    private fun demoLabelled(title: String, snapshot: BatterySnapshot?): String =
        if (snapshot?.source == ReadingSource.DEMO) context.getString(R.string.demo_prefix, title) else title

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR,
                context.getString(R.string.channel_monitor_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_monitor_desc)
                setShowBadge(false)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alert_desc)
                enableVibration(false) // handled explicitly so the in-app toggle is honoured
            },
        )
    }

    companion object {
        const val CHANNEL_MONITOR = "monitoring"
        const val CHANNEL_ALERT = "overheat_alerts"
        const val ID_MONITOR = 1001
        const val ID_ALERT = 1002
        private const val REQUEST_OPEN = 1
        private const val REQUEST_STOP = 2
        private val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 400)
    }
}
