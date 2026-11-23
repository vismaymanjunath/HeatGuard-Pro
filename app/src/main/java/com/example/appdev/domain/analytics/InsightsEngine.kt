package com.example.appdev.domain.analytics

import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.domain.cooling.RecoveryStats
import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.model.PowerSource
import java.util.Calendar
import java.util.TimeZone

enum class TimeOfDay { NIGHT, MORNING, AFTERNOON, EVENING;

    companion object {
        fun fromHour(hour: Int): TimeOfDay = when (hour) {
            in 6..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..23 -> EVENING
            else -> NIGHT
        }
    }
}

data class DayBucket(val startMillis: Long, val count: Int, val maxPeakC: Float?)

/**
 * How often incidents began while charging. This counts *when incidents started*; HeatGuard does
 * not record how long the phone spends charging overall, so it cannot show that charging causes
 * heat, only whether incidents and charging tend to coincide.
 */
data class ChargingCorrelation(
    val total: Int,
    val whileCharging: Int,
    val byPowerSource: Map<PowerSource, Int>,
    val avgPeakChargingC: Float?,
    val avgPeakOnBatteryC: Float?,
    val avgDurationChargingMillis: Long?,
    val avgDurationOnBatteryMillis: Long?,
) {
    val onBattery: Int get() = total - whileCharging
    val chargingShare: Float? get() = if (total > 0) whileCharging.toFloat() / total else null
}

enum class InsightType {
    NO_INCIDENTS,
    NOT_ENOUGH_DATA,
    CHARGING_HEAVY,
    WIRELESS_HEAVY,
    TIME_OF_DAY_PEAK,
    LONG_INCIDENTS,
    WORSENING,
    IMPROVING,
    HIGH_PEAKS,
    QUIET_WEEK,
}

data class InsightRecommendation(
    val type: InsightType,
    val timeOfDay: TimeOfDay? = null,
    val percent: Int? = null,
    val count: Int? = null,
    val previousCount: Int? = null,
    val durationMillis: Long? = null,
    val degreesC: Float? = null,
)

data class InsightsReport(
    val generatedAtMillis: Long,
    val totalIncidents: Int,
    val resolvedIncidents: Int,
    val last7Days: Int,
    val previous7Days: Int,
    /** Oldest first, ending with today. */
    val days: List<DayBucket>,
    val timeOfDay: Map<TimeOfDay, Int>,
    val avgPeakC: Float?,
    val maxPeakC: Float?,
    val maxPeakMillis: Long?,
    val avgDurationMillis: Long?,
    val longestDurationMillis: Long?,
    val charging: ChargingCorrelation,
    val recovery: RecoveryStats,
    val recommendations: List<InsightRecommendation>,
)

/** Derives personalised statistics and recommendations from locally stored data only. */
object InsightsEngine {
    const val MIN_INCIDENTS_FOR_PATTERNS = 3
    private const val MIN_INCIDENTS_FOR_TIME_PATTERN = 4
    private const val SHARE_THRESHOLD = 0.5f
    private const val LONG_INCIDENT_MILLIS = 15 * 60_000L
    private const val WEEK_CHANGE_MIN_DIFF = 2
    private const val HIGH_PEAK_OVER_THRESHOLD_C = 2f

    fun compute(
        incidents: List<Incident>,
        sessions: List<CoolingSession>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): InsightsReport {
        val days = (6 downTo 0).map { back ->
            val start = startOfDay(nowMillis, timeZone, back)
            val end = startOfDay(nowMillis, timeZone, back - 1)
            val inDay = incidents.filter { it.startMillis in start until end }
            DayBucket(start, inDay.size, inDay.maxOfOrNull { it.peakTempC })
        }
        val weekStart = days.first().startMillis
        val weekEnd = startOfDay(nowMillis, timeZone, -1)
        val previousStart = startOfDay(nowMillis, timeZone, 13)
        val last7 = incidents.count { it.startMillis in weekStart until weekEnd }
        val previous7 = incidents.count { it.startMillis in previousStart until weekStart }

        val timeOfDay = TimeOfDay.entries.associateWith { bucket ->
            incidents.count { TimeOfDay.fromHour(hourOf(it.startMillis, timeZone)) == bucket }
        }

        val resolved = incidents.filter { !it.isActive }
        val durations = resolved.map { it.durationMillis }
        val peak = incidents.maxByOrNull { it.peakTempC }

        val report = InsightsReport(
            generatedAtMillis = nowMillis,
            totalIncidents = incidents.size,
            resolvedIncidents = resolved.size,
            last7Days = last7,
            previous7Days = previous7,
            days = days,
            timeOfDay = timeOfDay,
            avgPeakC = incidents.takeIf { it.isNotEmpty() }?.map { it.peakTempC }?.average()?.toFloat(),
            maxPeakC = peak?.peakTempC,
            maxPeakMillis = peak?.peakMillis,
            avgDurationMillis = durations.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            longestDurationMillis = durations.maxOrNull(),
            charging = chargingCorrelation(incidents),
            recovery = RecoveryStats.compute(sessions),
            recommendations = emptyList(),
        )
        return report.copy(recommendations = recommend(report, incidents))
    }

    private fun chargingCorrelation(incidents: List<Incident>): ChargingCorrelation {
        val charging = incidents.filter { it.startedWhileCharging }
        val onBattery = incidents.filterNot { it.startedWhileCharging }
        fun avgPeak(list: List<Incident>) = list.takeIf { it.isNotEmpty() }?.map { it.peakTempC }?.average()?.toFloat()
        fun avgDuration(list: List<Incident>) = list.filter { !it.isActive }.map { it.durationMillis }
            .takeIf { it.isNotEmpty() }?.average()?.toLong()
        return ChargingCorrelation(
            total = incidents.size,
            whileCharging = charging.size,
            byPowerSource = charging.groupingBy { it.startPowerSource }.eachCount(),
            avgPeakChargingC = avgPeak(charging),
            avgPeakOnBatteryC = avgPeak(onBattery),
            avgDurationChargingMillis = avgDuration(charging),
            avgDurationOnBatteryMillis = avgDuration(onBattery),
        )
    }

    private fun recommend(report: InsightsReport, incidents: List<Incident>): List<InsightRecommendation> {
        val n = report.totalIncidents
        if (n == 0) return listOf(InsightRecommendation(InsightType.NO_INCIDENTS))
        if (n < MIN_INCIDENTS_FOR_PATTERNS) return listOf(InsightRecommendation(InsightType.NOT_ENOUGH_DATA, count = n))

        val out = mutableListOf<InsightRecommendation>()

        report.charging.chargingShare?.let { share ->
            if (share >= SHARE_THRESHOLD) {
                out += InsightRecommendation(InsightType.CHARGING_HEAVY, percent = (share * 100).toInt())
            }
        }
        val wireless = report.charging.byPowerSource[PowerSource.WIRELESS] ?: 0
        if (report.charging.whileCharging >= 2 && wireless.toFloat() / report.charging.whileCharging >= SHARE_THRESHOLD) {
            out += InsightRecommendation(InsightType.WIRELESS_HEAVY, percent = wireless * 100 / report.charging.whileCharging)
        }
        if (n >= MIN_INCIDENTS_FOR_TIME_PATTERN) {
            val top = report.timeOfDay.maxByOrNull { it.value }
            if (top != null && top.value.toFloat() / n >= SHARE_THRESHOLD) {
                out += InsightRecommendation(InsightType.TIME_OF_DAY_PEAK, timeOfDay = top.key, percent = top.value * 100 / n)
            }
        }
        val avgDuration = report.avgDurationMillis
        if (report.resolvedIncidents >= 2 && avgDuration != null && avgDuration > LONG_INCIDENT_MILLIS) {
            out += InsightRecommendation(InsightType.LONG_INCIDENTS, durationMillis = avgDuration)
        }
        val over = incidents.map { it.peakTempC - it.thresholdC }.average().toFloat()
        if (over >= HIGH_PEAK_OVER_THRESHOLD_C) {
            out += InsightRecommendation(InsightType.HIGH_PEAKS, degreesC = over)
        }
        when {
            report.last7Days - report.previous7Days >= WEEK_CHANGE_MIN_DIFF ->
                out += InsightRecommendation(InsightType.WORSENING, count = report.last7Days, previousCount = report.previous7Days)
            report.previous7Days - report.last7Days >= WEEK_CHANGE_MIN_DIFF ->
                out += InsightRecommendation(InsightType.IMPROVING, count = report.last7Days, previousCount = report.previous7Days)
        }
        if (report.last7Days == 0) out += InsightRecommendation(InsightType.QUIET_WEEK)
        return out
    }

    /** Midnight at the start of the day [daysBack] days before [millis] (negative = in the future). */
    internal fun startOfDay(millis: Long, tz: TimeZone, daysBack: Int): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -daysBack)
        return c.timeInMillis
    }

    private fun hourOf(millis: Long, tz: TimeZone): Int {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = millis
        return c.get(Calendar.HOUR_OF_DAY)
    }
}
