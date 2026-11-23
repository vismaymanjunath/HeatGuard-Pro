package com.example.appdev.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appdev.R
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.theme.HeatColors

/** AI assistant status and privacy controls, shown in Settings. */
@Composable
fun AiAssistantSettingsCard(
    onOpenAssistant: () -> Unit,
    viewModel: AssistantSettingsViewModel = viewModel(factory = AssistantSettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HeatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(stringResource(R.string.ai_settings_title), style = MaterialTheme.typography.titleMedium, color = HeatColors.TextPrimary)
        Text(
            stringResource(if (state.ready) R.string.ai_settings_status_on else R.string.ai_settings_status_off),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.ready) HeatColors.Normal else HeatColors.TextSecondary,
        )
        if (state.ready) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_include_data), style = MaterialTheme.typography.bodyLarge, color = HeatColors.TextPrimary)
                    Text(stringResource(R.string.ai_include_data_body), style = MaterialTheme.typography.bodySmall, color = HeatColors.TextSecondary)
                }
                Switch(
                    checked = state.includePhoneData,
                    onCheckedChange = viewModel::setIncludePhoneData,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HeatColors.OnAccent,
                        checkedTrackColor = HeatColors.Accent,
                        uncheckedThumbColor = HeatColors.TextSecondary,
                        uncheckedTrackColor = HeatColors.SurfaceHighest,
                        uncheckedBorderColor = HeatColors.Outline,
                    ),
                )
            }
        }
        OutlinedButton(onClick = onOpenAssistant, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ai_settings_open))
        }
        if (state.hasKey || state.consent) {
            OutlinedButton(onClick = viewModel::removeEverything, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_settings_remove_key), color = HeatColors.Critical)
            }
        }
    }
}
