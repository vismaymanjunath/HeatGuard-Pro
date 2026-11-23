package com.example.appdev.data.battery

import android.os.BatteryManager
import com.example.appdev.domain.model.BatteryHealth
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import kotlin.math.roundToInt

/**
 * Raw extras from an ACTION_BATTERY_CHANGED intent. `null` means the extra was absent.
 * Kept free of Android types so parsing can be unit-tested on the JVM.
 */
data class RawBatteryExtras(
    val temperatureTenthsC: Int?,
    val level: Int?,
    val scale: Int?,
    val status: Int?,
    val plugged: Int?,
    val voltage: Int?,
    val health: Int?,
    val technology: String?,
    val present: Boolean?,
)

/** Converts raw battery extras into a validated [BatterySnapshot]. Never invents values. */
object BatteryParser {

    /** Readings outside this range are treated as sensor errors rather than shown to the user. */
    const val MIN_PLAUSIBLE_C = -40f
    const val MAX_PLAUSIBLE_C = 100f

    fun parse(raw: RawBatteryExtras, timestampMillis: Long): BatterySnapshot {
        val batteryPresent = raw.present != false
        return BatterySnapshot(
            timestampMillis = timestampMillis,
            temperatureC = if (batteryPresent) parseTemperature(raw.temperatureTenthsC) else null,
            levelPercent = if (batteryPresent) parseLevel(raw.level, raw.scale) else null,
            chargeStatus = parseStatus(raw.status),
            powerSource = parsePowerSource(raw.plugged),
            voltageMillivolts = if (batteryPresent) parseVoltage(raw.voltage) else null,
            health = parseHealth(raw.health),
            technology = raw.technology?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** EXTRA_TEMPERATURE is reported in tenths of a degree Celsius. */
    fun parseTemperature(tenthsC: Int?): Float? {
        if (tenthsC == null) return null
        val celsius = tenthsC / 10f
        return celsius.takeIf { it in MIN_PLAUSIBLE_C..MAX_PLAUSIBLE_C }
    }

    fun parseLevel(level: Int?, scale: Int?): Int? {
        if (level == null || scale == null || level < 0 || scale <= 0) return null
        return (level * 100f / scale).roundToInt().coerceIn(0, 100)
    }

    /** Most devices report millivolts; a few report volts. Normalise to millivolts. */
    fun parseVoltage(voltage: Int?): Int? = when {
        voltage == null || voltage <= 0 -> null
        voltage < 100 -> voltage * 1000
        else -> voltage
    }

    fun parseStatus(status: Int?): ChargeStatus = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
        else -> ChargeStatus.UNKNOWN
    }

    /** EXTRA_PLUGGED is a bit field; 0 means on battery. */
    fun parsePowerSource(plugged: Int?): PowerSource = when {
        plugged == null || plugged < 0 -> PowerSource.UNKNOWN
        plugged == 0 -> PowerSource.NONE
        plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> PowerSource.AC
        plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> PowerSource.USB
        plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> PowerSource.WIRELESS
        plugged and BATTERY_PLUGGED_DOCK != 0 -> PowerSource.DOCK
        else -> PowerSource.UNKNOWN
    }

    fun parseHealth(health: Int?): BatteryHealth = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
        else -> BatteryHealth.UNKNOWN
    }

    /** BatteryManager.BATTERY_PLUGGED_DOCK (API 33); duplicated so it is usable on minSdk 24. */
    internal const val BATTERY_PLUGGED_DOCK = 8
}
