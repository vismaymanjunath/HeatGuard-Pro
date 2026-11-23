package com.example.appdev.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.appdev.HeatGuardApp
import com.example.appdev.R
import com.example.appdev.platform.ExportResult
import com.example.appdev.platform.Sharing
import com.example.appdev.ui.theme.HeatColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class ExportStatus { IDLE, WORKING, NOTHING, FAILED }

/** Runs a CSV export and opens the share sheet. Only ever started by a user tap. */
class CsvExportState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val chooserTitle: String,
) {
    var status by mutableStateOf(ExportStatus.IDLE)
        private set

    fun start() {
        if (status == ExportStatus.WORKING) return
        status = ExportStatus.WORKING
        scope.launch {
            status = try {
                val container = (context.applicationContext as HeatGuardApp).container
                when (val result = container.exportManager.createCsvExport()) {
                    is ExportResult.Ready -> {
                        Sharing.shareCsvFiles(context, result.uris, chooserTitle)
                        ExportStatus.IDLE
                    }
                    ExportResult.NothingToExport -> ExportStatus.NOTHING
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportStatus.FAILED
            }
        }
    }
}

@Composable
fun rememberCsvExport(): CsvExportState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.export_chooser)
    return remember(context, scope, title) { CsvExportState(context, scope, title) }
}

/** Card with an "Export CSV" button, an optional "Share report" button and a status line. */
@Composable
fun ExportCard(
    state: CsvExportState,
    modifier: Modifier = Modifier,
    onShareReport: (() -> Unit)? = null,
) {
    HeatCard(modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(
            stringResource(R.string.export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextSecondary,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = state::start,
                enabled = state.status != ExportStatus.WORKING,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = HeatColors.Accent, contentColor = HeatColors.OnAccent),
            ) {
                Text(stringResource(if (state.status == ExportStatus.WORKING) R.string.export_working else R.string.export_csv))
            }
            if (onShareReport != null) {
                OutlinedButton(onClick = onShareReport, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.export_share_report))
                }
            }
        }
        when (state.status) {
            ExportStatus.NOTHING -> Text(stringResource(R.string.export_nothing), style = MaterialTheme.typography.bodySmall, color = HeatColors.Elevated)
            ExportStatus.FAILED -> Text(stringResource(R.string.export_failed), style = MaterialTheme.typography.bodySmall, color = HeatColors.Critical)
            else -> Unit
        }
    }
}
