package com.example.appdev.data.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import com.example.appdev.domain.ai.DeviceInfo
import com.example.appdev.domain.ai.PhoneMetrics

/**
 * Reads whole-phone signals that affect heat: memory pressure, free storage, power saver, brightness
 * and uptime. None of them need a permission, and none identify the user or the unit.
 */
class PhoneMetricsReader(context: Context) {
    private val context = context.applicationContext

    fun deviceInfo() = DeviceInfo(
        manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() },
        model = Build.MODEL.orEmpty(),
        androidRelease = Build.VERSION.RELEASE.orEmpty(),
        sdkInt = Build.VERSION.SDK_INT,
    )

    fun metrics(): PhoneMetrics {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)

        val storage = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode

        val brightness = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255
        }.getOrNull()

        return PhoneMetrics(
            totalRamBytes = memory.totalMem,
            availRamBytes = memory.availMem,
            lowMemory = memory.lowMemory,
            totalStorageBytes = storage?.totalBytes ?: 0L,
            freeStorageBytes = storage?.availableBytes ?: 0L,
            powerSaveOn = powerSave,
            brightnessPercent = brightness?.coerceIn(0, 100),
            uptimeMillis = SystemClock.elapsedRealtime(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
        )
    }
}
