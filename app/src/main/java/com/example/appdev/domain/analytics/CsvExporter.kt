package com.example.appdev.domain.analytics

import com.example.appdev.domain.cooling.CoolingSession
import com.example.appdev.domain.incident.Incident
import com.example.appdev.domain.incident.IncidentSample
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Builds RFC 4180 CSV text. Everything is locale-independent so files open the same everywhere:
 * temperatures are in °C with a dot decimal, timestamps are ISO 8601 in UTC.
 */
object CsvExporter {
    private const val EOL = "\r\n"

    val INCIDENT_HEADER = listOf(
        "incident_id", "status", "start_utc", "end_utc", "duration_seconds",
        "initial_temp_c", "peak_temp_c", "peak_time_utc", "end_temp_c", "threshold_c",
        "start_battery_percent", "end_battery_percent", "start_charge_status", "start_power_source",
    )
    val READING_HEADER = listOf(
        "incident_id", "timestamp_utc", "temperature_c", "battery_percent", "charge_status", "power_source",
    )
    val SESSION_HEADER = listOf(
        "session_id", "outcome", "start_utc", "end_utc", "duration_seconds", "start_temp_c",
        "last_temp_c", "min_temp_c", "target_temp_c", "drop_c", "start_battery_percent",
        "start_charge_status", "start_power_source",
    )

    fun incidents(incidents: List<Incident>): String = table(
        INCIDENT_HEADER,
        incidents.sortedBy { it.startMillis }.map { i ->
            listOf(
                i.id.toString(),
                if (i.isActive) "ongoing" else "resolved",
                iso(i.startMillis),
                i.endMillis?.let(::iso).orEmpty(),
                (i.durationMillis / 1000).toString(),
                num(i.initialTempC),
                num(i.peakTempC),
                iso(i.peakMillis),
                i.endTempC?.let(::num).orEmpty(),
                num(i.thresholdC),
                i.startBatteryPercent?.toString().orEmpty(),
                i.endBatteryPercent?.toString().orEmpty(),
                i.startChargeStatus.name,
                i.startPowerSource.name,
            )
        },
    )

    fun readings(samples: List<IncidentSample>): String = table(
        READING_HEADER,
        samples.sortedWith(compareBy({ it.incidentId }, { it.timestampMillis })).map { s ->
            listOf(
                s.incidentId.toString(),
                iso(s.timestampMillis),
                num(s.temperatureC),
                s.batteryPercent?.toString().orEmpty(),
                s.chargeStatus.name,
                s.powerSource.name,
            )
        },
    )

    fun sessions(sessions: List<CoolingSession>): String = table(
        SESSION_HEADER,
        sessions.sortedBy { it.startMillis }.map { s ->
            listOf(
                s.id.toString(),
                s.outcome?.name?.lowercase() ?: "running",
                iso(s.startMillis),
                s.endMillis?.let(::iso).orEmpty(),
                (s.durationMillis / 1000).toString(),
                num(s.startTempC),
                num(s.lastTempC),
                num(s.minTempC),
                num(s.targetTempC),
                num(s.dropC),
                s.startBatteryPercent?.toString().orEmpty(),
                s.startChargeStatus.name,
                s.startPowerSource.name,
            )
        },
    )

    internal fun table(header: List<String>, rows: List<List<String>>): String =
        (listOf(header) + rows).joinToString(separator = EOL, postfix = EOL) { row ->
            row.joinToString(",") { escape(it) }
        }

    /** Quotes a field if it contains a comma, quote or line break; doubles embedded quotes. */
    internal fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    internal fun num(value: Float): String = String.format(Locale.ROOT, "%.1f", value)

    internal fun iso(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date(millis))
    }
}
