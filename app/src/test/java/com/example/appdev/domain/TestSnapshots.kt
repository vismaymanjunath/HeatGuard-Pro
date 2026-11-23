package com.example.appdev.domain

import com.example.appdev.domain.model.BatteryHealth
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource

fun snapshot(
    temperatureC: Float?,
    timestampMillis: Long = 0L,
    chargeStatus: ChargeStatus = ChargeStatus.DISCHARGING,
    powerSource: PowerSource = PowerSource.NONE,
) = BatterySnapshot(
    timestampMillis = timestampMillis,
    temperatureC = temperatureC,
    levelPercent = 80,
    chargeStatus = chargeStatus,
    powerSource = powerSource,
    voltageMillivolts = 4100,
    health = BatteryHealth.GOOD,
    technology = "Li-ion",
)
