package com.example.appdev.ui.components

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.appdev.R
import com.example.appdev.domain.model.BatteryHealth
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.thermal.TemperatureLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@StringRes
fun TemperatureLevel.labelRes(): Int = when (this) {
    TemperatureLevel.NORMAL -> R.string.level_normal
    TemperatureLevel.ELEVATED -> R.string.level_elevated
    TemperatureLevel.CRITICAL -> R.string.level_critical
    TemperatureLevel.UNKNOWN -> R.string.level_unknown
}

@StringRes
fun ChargeStatus.labelRes(): Int = when (this) {
    ChargeStatus.CHARGING -> R.string.status_charging
    ChargeStatus.DISCHARGING -> R.string.status_discharging
    ChargeStatus.FULL -> R.string.status_full
    ChargeStatus.NOT_CHARGING -> R.string.status_not_charging
    ChargeStatus.UNKNOWN -> R.string.status_unknown
}

@StringRes
fun PowerSource.labelRes(): Int = when (this) {
    PowerSource.AC -> R.string.source_ac
    PowerSource.USB -> R.string.source_usb
    PowerSource.WIRELESS -> R.string.source_wireless
    PowerSource.DOCK -> R.string.source_dock
    PowerSource.NONE -> R.string.source_none
    PowerSource.UNKNOWN -> R.string.source_unknown
}

@StringRes
fun BatteryHealth.labelRes(): Int = when (this) {
    BatteryHealth.GOOD -> R.string.health_good
    BatteryHealth.OVERHEAT -> R.string.health_overheat
    BatteryHealth.DEAD -> R.string.health_dead
    BatteryHealth.OVER_VOLTAGE -> R.string.health_over_voltage
    BatteryHealth.FAILURE -> R.string.health_failure
    BatteryHealth.COLD -> R.string.health_cold
    BatteryHealth.UNKNOWN -> R.string.health_unknown
}

/** Formats epoch millis as a time with seconds, honouring the user's 12/24-hour preference. */
@Composable
fun rememberTimeFormatter(): (Long) -> String {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    return remember(is24h) {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, if (is24h) "Hms" else "hmsa")
        val format = SimpleDateFormat(pattern, locale)
        val formatter: (Long) -> String = { millis -> format.format(Date(millis)) }
        formatter
    }
}

/** Formats epoch millis as a short date and time, e.g. "18 Nov, 14:32". */
@Composable
fun rememberDateTimeFormatter(): (Long) -> String {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    return remember(is24h) {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, if (is24h) "dMMMHm" else "dMMMhma")
        val format = SimpleDateFormat(pattern, locale)
        val formatter: (Long) -> String = { millis -> format.format(Date(millis)) }
        formatter
    }
}
