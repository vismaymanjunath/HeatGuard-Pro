package com.example.appdev.ui.components

/**
 * Maps a temperature onto the 0..1 sweep of the dashboard gauge. The scale starts at 20 °C and
 * ends 10 °C above the user's threshold so the threshold always sits at a consistent position.
 */
object GaugeScale {
    const val MIN_C = 20f
    const val SPAN_ABOVE_THRESHOLD_C = 10f

    fun maxC(thresholdC: Float): Float = thresholdC + SPAN_ABOVE_THRESHOLD_C

    fun fraction(temperatureC: Float, thresholdC: Float): Float {
        val max = maxC(thresholdC)
        if (max <= MIN_C || !temperatureC.isFinite()) return 0f
        return ((temperatureC - MIN_C) / (max - MIN_C)).coerceIn(0f, 1f)
    }
}
