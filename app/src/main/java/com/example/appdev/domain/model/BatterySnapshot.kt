package com.example.appdev.domain.model

/** Where a reading originated. Demo readings must never be mixed into real device history. */
enum class ReadingSource { DEVICE, DEMO }

enum class ChargeStatus { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }

enum class PowerSource { AC, USB, WIRELESS, DOCK, NONE, UNKNOWN }

enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, FAILURE, COLD, UNKNOWN }

/**
 * A single battery reading as reported by Android. Every hardware-derived field is nullable:
 * `null` means the device did not report a usable value, and the UI must say so rather than
 * substituting a number.
 */
data class BatterySnapshot(
    val timestampMillis: Long,
    val temperatureC: Float?,
    val levelPercent: Int?,
    val chargeStatus: ChargeStatus,
    val powerSource: PowerSource,
    val voltageMillivolts: Int?,
    val health: BatteryHealth,
    val technology: String?,
    val source: ReadingSource = ReadingSource.DEVICE,
) {
    /** True when an external power source is connected, regardless of whether it is charging. */
    val isPluggedIn: Boolean
        get() = powerSource != PowerSource.NONE && powerSource != PowerSource.UNKNOWN

    val isCharging: Boolean
        get() = chargeStatus == ChargeStatus.CHARGING
}
