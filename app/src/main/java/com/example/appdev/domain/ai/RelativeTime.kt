package com.example.appdev.domain.ai

/** Compact relative durations for the model and the UI ("42 s ago", "3 h 12 min ago"). */
object RelativeTime {
    /** A reading older than this is described as stale rather than live. */
    const val STALE_AFTER_MILLIS = 30_000L

    fun span(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val d = totalSeconds / 86_400
        val h = (totalSeconds % 86_400) / 3_600
        val m = (totalSeconds % 3_600) / 60
        val s = totalSeconds % 60
        return when {
            d > 0 -> if (h > 0) "$d d $h h" else "$d d"
            h > 0 -> if (m > 0) "$h h $m min" else "$h h"
            m > 0 -> "$m min"
            else -> "$s s"
        }
    }

    /**
     * How long before [nowMillis] something happened. A timestamp in the future (the device clock was
     * changed) is reported as "just now" instead of a nonsense negative time.
     */
    fun ago(eventMillis: Long, nowMillis: Long): String {
        val diff = nowMillis - eventMillis
        return if (diff < 2_000) "just now" else span(diff) + " ago"
    }
}
