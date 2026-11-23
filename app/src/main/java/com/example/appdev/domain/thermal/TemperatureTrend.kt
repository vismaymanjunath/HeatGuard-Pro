package com.example.appdev.domain.thermal

/** A single battery-temperature reading in the rolling history. */
data class TemperaturePoint(val timestampMillis: Long, val temperatureC: Float)

/** Rolling in-memory window of recent readings, used for trend estimation and live graphs. */
object TemperatureHistory {
    const val WINDOW_MILLIS = 30 * 60_000L

    /**
     * Appends [point] and drops anything older than [windowMillis] relative to the newest point.
     * Points that are not newer than the last one are ignored (clock changes, duplicates).
     */
    fun append(
        history: List<TemperaturePoint>,
        point: TemperaturePoint,
        windowMillis: Long = WINDOW_MILLIS,
    ): List<TemperaturePoint> {
        val last = history.lastOrNull()
        if (last != null && point.timestampMillis <= last.timestampMillis) return history
        val cutoff = point.timestampMillis - windowMillis
        return history.dropWhile { it.timestampMillis < cutoff } + point
    }
}

/** Least-squares line through recent readings. */
data class TrendEstimate(
    /** Change in temperature in °C per minute; positive means warming. */
    val slopeCPerMin: Float,
    /** Goodness of fit, 0..1. Low values mean the readings are noisy or non-linear. */
    val rSquared: Float,
    val sampleCount: Int,
    val spanMillis: Long,
)

object TrendEstimator {
    const val WINDOW_MILLIS = 5 * 60_000L
    const val MIN_SAMPLES = 4
    const val MIN_SPAN_MILLIS = 60_000L

    /** Returns null until there are enough readings spanning enough time to fit a trend. */
    fun estimate(points: List<TemperaturePoint>): TrendEstimate? {
        val latest = points.lastOrNull()?.timestampMillis ?: return null
        val window = points.filter { latest - it.timestampMillis <= WINDOW_MILLIS }
        if (window.size < MIN_SAMPLES) return null
        val origin = window.first().timestampMillis
        val span = latest - origin
        if (span < MIN_SPAN_MILLIS) return null

        val xs = window.map { (it.timestampMillis - origin) / 60_000.0 }
        val ys = window.map { it.temperatureC.toDouble() }
        val n = window.size
        val meanX = xs.sum() / n
        val meanY = ys.sum() / n
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            sxx += dx * dx
            sxy += dx * dy
            syy += dy * dy
        }
        if (sxx == 0.0) return null
        val slope = sxy / sxx
        // A perfectly flat series has no variance to explain; it is a perfect (zero-slope) fit.
        val r2 = if (syy == 0.0) 1.0 else (sxy * sxy) / (sxx * syy)
        return TrendEstimate(slope.toFloat(), r2.toFloat().coerceIn(0f, 1f), n, span)
    }
}

enum class Confidence { LOW, MEDIUM, HIGH;

    companion object {
        fun fromRSquared(r2: Float): Confidence = when {
            r2 >= 0.8f -> HIGH
            r2 >= 0.5f -> MEDIUM
            else -> LOW
        }
    }
}

/** What the recent trend implies for the temperature threshold. */
sealed interface Prediction {
    /** No valid temperature reading is available. */
    data object Unavailable : Prediction

    /** Not enough readings yet to estimate a trend. */
    data class CollectingData(val sampleCount: Int, val spanMillis: Long) : Prediction

    data class AlreadyOverThreshold(val overByC: Float) : Prediction

    data class Stable(val slopeCPerMin: Float) : Prediction

    data class Cooling(val slopeCPerMin: Float) : Prediction

    /** Warming, and projected to reach the threshold within [TemperaturePredictor.HORIZON_MINUTES]. */
    data class Heating(
        val slopeCPerMin: Float,
        val minutesToThreshold: Float,
        val confidence: Confidence,
    ) : Prediction

    /** Warming, but not projected to reach the threshold within the prediction horizon. */
    data class HeatingSlowly(val slopeCPerMin: Float) : Prediction
}

object TemperaturePredictor {
    /** Slopes within ±this (°C/min, i.e. 3 °C per hour) count as stable. */
    const val STABLE_SLOPE_C_PER_MIN = 0.05f

    /** Projections further out than this are reported as "not within the horizon". */
    const val HORIZON_MINUTES = 60f

    fun predict(temperatureC: Float?, thresholdC: Float, trend: TrendEstimate?, history: List<TemperaturePoint> = emptyList()): Prediction {
        if (temperatureC == null || !temperatureC.isFinite()) return Prediction.Unavailable
        if (temperatureC >= thresholdC) return Prediction.AlreadyOverThreshold(temperatureC - thresholdC)
        if (trend == null) {
            val span = if (history.size >= 2) history.last().timestampMillis - history.first().timestampMillis else 0L
            return Prediction.CollectingData(history.size, span)
        }
        val slope = trend.slopeCPerMin
        return when {
            slope > STABLE_SLOPE_C_PER_MIN -> {
                val minutes = (thresholdC - temperatureC) / slope
                if (minutes > HORIZON_MINUTES) {
                    Prediction.HeatingSlowly(slope)
                } else {
                    Prediction.Heating(slope, minutes, Confidence.fromRSquared(trend.rSquared))
                }
            }
            slope < -STABLE_SLOPE_C_PER_MIN -> Prediction.Cooling(slope)
            else -> Prediction.Stable(slope)
        }
    }
}
