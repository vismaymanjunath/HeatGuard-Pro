package com.example.appdev.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.appdev.domain.analytics.CsvExporter
import com.example.appdev.domain.cooling.CoolingRepository
import com.example.appdev.domain.incident.IncidentRepository
import com.example.appdev.domain.model.ReadingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExportResult {
    data class Ready(val uris: List<Uri>) : ExportResult
    data object NothingToExport : ExportResult
}

/**
 * Writes CSV exports of real (device) data to the app's cache and exposes them through a
 * FileProvider content URI. Demo data is never exported. Files only leave the app when the user
 * picks a share target.
 */
class ExportManager(
    context: Context,
    private val incidents: IncidentRepository,
    private val sessions: CoolingRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val context = context.applicationContext

    suspend fun createCsvExport(): ExportResult = withContext(Dispatchers.IO) {
        val incidentList = incidents.observeIncidents(ReadingSource.DEVICE).first()
        val sessionList = sessions.observeSessions(ReadingSource.DEVICE).first()
        if (incidentList.isEmpty() && sessionList.isEmpty()) return@withContext ExportResult.NothingToExport

        val samples = incidentList.flatMap { incidents.observeSamples(it.id).first() }

        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only the latest export is kept
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date(clock()))

        val files = listOf(
            File(dir, "heatguard_incidents_$stamp.csv").also { it.writeText(CsvExporter.incidents(incidentList)) },
            File(dir, "heatguard_incident_readings_$stamp.csv").also { it.writeText(CsvExporter.readings(samples)) },
            File(dir, "heatguard_cooling_sessions_$stamp.csv").also { it.writeText(CsvExporter.sessions(sessionList)) },
        )
        ExportResult.Ready(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
    }

    companion object {
        private const val EXPORT_DIR = "exports"
    }
}

/** Opens the system share sheet. Launched from a user tap only. */
object Sharing {

    fun shareCsvFiles(context: Context, uris: List<Uri>, chooserTitle: String) {
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(context: Context, subject: String, text: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
