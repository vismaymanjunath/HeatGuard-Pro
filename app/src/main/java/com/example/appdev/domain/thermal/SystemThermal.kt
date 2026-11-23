package com.example.appdev.domain.thermal

/** Android's PowerManager thermal status, ordered from coolest to most severe. */
enum class ThermalStatus {
    NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN;

    val isSevere: Boolean get() = this >= SEVERE

    companion object {
        /** Maps PowerManager.THERMAL_STATUS_* (0..6). Unknown values are treated as not reported. */
        fun fromAndroid(value: Int): ThermalStatus? = entries.getOrNull(value)
    }
}

/**
 * What the Android system itself reports about thermal state. Each value is null when the device
 * or OS version does not provide it; the `*ApiAvailable` flags say whether that is because the
 * Android version is too old (false) or the device simply did not report a value (true).
 */
data class SystemThermalState(
    val statusApiAvailable: Boolean = false,
    val status: ThermalStatus? = null,
    val headroomApiAvailable: Boolean = false,
    /** Current headroom: 0 = cool, 1.0 = at the device's throttling threshold. */
    val headroomNow: Float? = null,
    /** Forecast headroom a few seconds ahead, when the device supports forecasts. */
    val headroomForecast: Float? = null,
) {
    companion object {
        val Unknown = SystemThermalState()
    }
}
