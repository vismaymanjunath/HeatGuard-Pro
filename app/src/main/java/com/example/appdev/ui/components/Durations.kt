package com.example.appdev.ui.components

/** Compact human-readable durations: "42 s", "5 min 07 s", "1 h 05 min". */
object Durations {
    fun format(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "$hours h ${minutes.toString().padStart(2, '0')} min"
            minutes > 0 -> "$minutes min ${seconds.toString().padStart(2, '0')} s"
            else -> "$seconds s"
        }
    }

    /** Stopwatch style: "04:12", or "1:04:12" once past an hour. */
    fun clock(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val mm = m.toString().padStart(2, '0')
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
    }
}
