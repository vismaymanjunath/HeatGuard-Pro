package com.example.appdev.domain.analytics

import com.example.appdev.domain.thermal.TemperaturePoint

/** Summary of the readings inside the visible chart window. */
data class WindowStats(
    val sampleCount: Int,
    val minC: Float,
    val maxC: Float,
    /** Arithmetic mean of the readings in the window. */
    val avgC: Float,
    val latestC: Float,
    /** Time spent at or above the threshold, counting only gaps short enough to be continuous. */
    val aboveThresholdMillis: Long,
    val spanMillis: Long,
)

object LiveAnalytics {
    val WINDOWS_MINUTES = listOf(5, 15, 30)

    /** Intervals longer than this between readings are treated as a gap, not as time above threshold. */
    const val MAX_CONTINUOUS_GAP_MILLIS = 60_000L

    /** Points within [windowMillis] of the newest point. */
    fun window(points: List<TemperaturePoint>, windowMillis: Long): List<TemperaturePoint> {
        val latest = points.lastOrNull()?.timestampMillis ?: return emptyList()
        return points.filter { latest - it.timestampMillis <= windowMillis }
    }

    fun stats(points: List<TemperaturePoint>, thresholdC: Float): WindowStats? {
        if (points.isEmpty()) return null
        var above = 0L
        for (i in 0 until points.lastIndex) {
            val gap = points[i + 1].timestampMillis - points[i].timestampMillis
            if (points[i].temperatureC >= thresholdC && gap in 0..MAX_CONTINUOUS_GAP_MILLIS) above += gap
        }
        val temps = points.map { it.temperatureC }
        return WindowStats(
            sampleCount = points.size,
            minC = temps.min(),
            maxC = temps.max(),
            avgC = temps.average().toFloat(),
            latestC = temps.last(),
            aboveThresholdMillis = above,
            spanMillis = points.last().timestampMillis - points.first().timestampMillis,
        )
    }
}
