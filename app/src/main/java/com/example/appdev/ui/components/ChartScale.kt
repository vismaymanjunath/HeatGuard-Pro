package com.example.appdev.ui.components

import kotlin.math.ceil
import kotlin.math.floor

/** Vertical axis for a temperature chart: a padded range plus "nice" tick values (in °C). */
data class YAxis(val min: Float, val max: Float, val ticks: List<Float>) {
    fun fraction(value: Float): Float = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0.5f
}

object ChartScale {
    private val NICE_STEPS = floatArrayOf(0.5f, 1f, 2f, 5f, 10f, 20f)
    private const val DEFAULT_MIN = 25f
    private const val DEFAULT_MAX = 45f
    private const val TARGET_TICKS = 4

    /**
     * Builds an axis that contains every value and every reference line (threshold, target),
     * never spans less than [minSpan] degrees (so tiny wobbles do not look like swings), and
     * rounds its ends to tick multiples.
     */
    fun yAxis(values: List<Float>, references: List<Float> = emptyList(), minSpan: Float = 4f): YAxis {
        val all = (values + references).filter { it.isFinite() }
        if (all.isEmpty()) return axis(DEFAULT_MIN, DEFAULT_MAX)

        var lo = all.min()
        var hi = all.max()
        if (hi - lo < minSpan) {
            val mid = (hi + lo) / 2f
            lo = mid - minSpan / 2f
            hi = mid + minSpan / 2f
        }
        val pad = (hi - lo) * 0.08f
        return axis(lo - pad, hi + pad)
    }

    private fun axis(lo: Float, hi: Float): YAxis {
        val step = niceStep((hi - lo) / TARGET_TICKS)
        val min = floor(lo / step) * step
        val max = ceil(hi / step) * step
        val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + step / 1000f }.toList()
        return YAxis(min, max, ticks)
    }

    private fun niceStep(raw: Float): Float = NICE_STEPS.firstOrNull { it >= raw } ?: NICE_STEPS.last()
}
