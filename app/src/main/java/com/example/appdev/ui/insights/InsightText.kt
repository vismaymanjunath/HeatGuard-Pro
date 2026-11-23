package com.example.appdev.ui.insights

import android.content.Context
import com.example.appdev.R
import com.example.appdev.domain.analytics.InsightRecommendation
import com.example.appdev.domain.analytics.InsightType
import com.example.appdev.domain.analytics.InsightsReport
import com.example.appdev.domain.analytics.TimeOfDay
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.ui.components.Durations
import java.text.DateFormat
import java.util.Date

/** Turns insight data into user-facing text. Shared by the Insights screen and the shared report. */
object InsightText {

    fun timeOfDayName(context: Context, tod: TimeOfDay): String = context.getString(
        when (tod) {
            TimeOfDay.NIGHT -> R.string.tod_night_name
            TimeOfDay.MORNING -> R.string.tod_morning_name
            TimeOfDay.AFTERNOON -> R.string.tod_afternoon_name
            TimeOfDay.EVENING -> R.string.tod_evening_name
        },
    )

    fun timeOfDayLabel(context: Context, tod: TimeOfDay): String = context.getString(
        when (tod) {
            TimeOfDay.NIGHT -> R.string.tod_night
            TimeOfDay.MORNING -> R.string.tod_morning
            TimeOfDay.AFTERNOON -> R.string.tod_afternoon
            TimeOfDay.EVENING -> R.string.tod_evening
        },
    )

    /** Returns (title, body). */
    fun describe(context: Context, rec: InsightRecommendation, unit: TemperatureUnit): Pair<String, String> {
        fun s(id: Int, vararg args: Any) = context.getString(id, *args)
        val percent = rec.percent ?: 0
        return when (rec.type) {
            InsightType.NO_INCIDENTS -> s(R.string.insight_none_title) to s(R.string.insight_none_body)
            InsightType.NOT_ENOUGH_DATA ->
                s(R.string.insight_few_title) to s(R.string.insight_few_body, rec.count ?: 0)
            InsightType.CHARGING_HEAVY ->
                s(R.string.insight_charging_title) to s(R.string.insight_charging_body, percent)
            InsightType.WIRELESS_HEAVY ->
                s(R.string.insight_wireless_title) to s(R.string.insight_wireless_body, percent)
            InsightType.TIME_OF_DAY_PEAK -> {
                val name = rec.timeOfDay?.let { timeOfDayName(context, it) }.orEmpty()
                s(R.string.insight_time_title, name) to s(R.string.insight_time_body, name, percent)
            }
            InsightType.LONG_INCIDENTS ->
                s(R.string.insight_long_title) to s(R.string.insight_long_body, Durations.format(rec.durationMillis ?: 0))
            InsightType.WORSENING ->
                s(R.string.insight_worse_title) to s(R.string.insight_worse_body, rec.count ?: 0, rec.previousCount ?: 0)
            InsightType.IMPROVING ->
                s(R.string.insight_better_title) to s(R.string.insight_better_body, rec.count ?: 0, rec.previousCount ?: 0)
            InsightType.HIGH_PEAKS ->
                s(R.string.insight_peaks_title) to s(R.string.insight_peaks_body, Temperatures.formatDelta(rec.degreesC ?: 0f, unit))
            InsightType.QUIET_WEEK -> s(R.string.insight_quiet_title) to s(R.string.insight_quiet_body)
        }
    }

    /** Plain-text report suitable for sharing as a message or email. */
    fun buildReport(context: Context, report: InsightsReport, unit: TemperatureUnit): String {
        fun s(id: Int, vararg args: Any) = context.getString(id, *args)
        val dateTime = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val unavailable = s(R.string.detail_unavailable)

        return buildString {
            appendLine(s(R.string.report_header))
            appendLine(s(R.string.report_generated, dateTime.format(Date(report.generatedAtMillis))))
            appendLine()
            appendLine(s(R.string.report_incidents, report.totalIncidents, report.last7Days, report.previous7Days))
            report.maxPeakC?.let { peak ->
                appendLine(s(R.string.report_peak, Temperatures.format(peak, unit), dateTime.format(Date(report.maxPeakMillis ?: 0))))
            }
            appendLine(s(R.string.report_avg_peak, report.avgPeakC?.let { Temperatures.format(it, unit) } ?: unavailable))
            appendLine(s(R.string.report_avg_duration, report.avgDurationMillis?.let { Durations.format(it) } ?: unavailable))
            report.charging.chargingShare?.let { share ->
                appendLine(s(R.string.report_charging, report.charging.whileCharging, report.charging.total, (share * 100).toInt()))
            }
            if (report.recovery.totalSessions > 0) {
                appendLine(
                    s(
                        R.string.report_recovery,
                        report.recovery.recoveredCount,
                        report.recovery.totalSessions,
                        report.recovery.averageRecoveryMillis?.let { Durations.format(it) } ?: unavailable,
                    ),
                )
            }
            appendLine()
            appendLine(s(R.string.report_recs))
            report.recommendations.forEach { rec ->
                val (title, body) = describe(context, rec, unit)
                appendLine("• $title — $body")
            }
            appendLine()
            appendLine(s(R.string.report_footer))
        }
    }
}
