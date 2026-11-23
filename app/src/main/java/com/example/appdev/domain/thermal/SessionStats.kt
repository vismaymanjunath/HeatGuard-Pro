package com.example.appdev.domain.thermal

import com.example.appdev.domain.model.BatterySnapshot

/**
 * Running statistics over the readings observed in the current app process.
 * Snapshots without a valid temperature are ignored.
 */
data class SessionStats(
    val sampleCount: Int = 0,
    val minC: Float? = null,
    val maxC: Float? = null,
    val firstSampleMillis: Long? = null,
    val lastSampleMillis: Long? = null,
) {
    fun record(snapshot: BatterySnapshot): SessionStats {
        val t = snapshot.temperatureC ?: return this
        if (!t.isFinite()) return this
        return SessionStats(
            sampleCount = sampleCount + 1,
            minC = minC?.let { minOf(it, t) } ?: t,
            maxC = maxC?.let { maxOf(it, t) } ?: t,
            firstSampleMillis = firstSampleMillis ?: snapshot.timestampMillis,
            lastSampleMillis = snapshot.timestampMillis,
        )
    }
}
