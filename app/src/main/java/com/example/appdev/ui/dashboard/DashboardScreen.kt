package com.example.appdev.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appdev.R
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.thermal.SessionStats
import com.example.appdev.domain.thermal.TemperatureLevel
import com.example.appdev.domain.thermal.ThermalAssessment
import com.example.appdev.ui.components.HeatCard
import com.example.appdev.ui.components.TemperatureGauge
import com.example.appdev.ui.components.labelRes
import com.example.appdev.ui.components.rememberTimeFormatter
import com.example.appdev.ui.theme.HeatColors
import com.example.appdev.ui.theme.color
import java.util.Locale
import kotlin.math.abs

@Composable
fun DashboardRoute(
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Monitoring starts whether or not the user grants notifications; the card explains the
    // consequence of denying them.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        viewModel.setMonitoring(true)
    }

    DashboardScreen(
        state = state,
        notificationsAllowed = notificationsAllowed,
        onOpenSettings = onOpenSettings,
        onSelectProfile = viewModel::setProfile,
        onToggleMonitoring = { enable ->
            val needsPermission = enable &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setMonitoring(enable)
            }
        },
        onOpenNotificationSettings = { openNotificationSettings(context) },
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    notificationsAllowed: Boolean,
    onOpenSettings: () -> Unit,
    onSelectProfile: (com.example.appdev.domain.profile.ProfileType?) -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val assessment = state.assessment
    val unit = assessment.settings.unit
    val snapshot = assessment.snapshot

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DashboardHeader(lastUpdateMillis = snapshot?.timestampMillis)

        AnimatedVisibility(
            visible = assessment.level == TemperatureLevel.CRITICAL,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            OverheatBanner()
        }

        TemperatureHeroCard(state)

        PreCallReadinessCard(state.readiness, state.system, assessment.settings.unit)

        ProfileCard(assessment.settings, onSelectProfile)

        PredictionCard(state.analysis.prediction, assessment.settings)
        RiskIndexCard(state.analysis.risk, state.analysis, assessment)
        SystemThermalCard(state.system)

        MonitoringCard(
            state = state,
            notificationsAllowed = notificationsAllowed,
            onToggle = onToggleMonitoring,
            onOpenNotificationSettings = onOpenNotificationSettings,
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricTile(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = R.drawable.ic_thermostat,
                iconTint = HeatColors.Critical,
                label = stringResource(R.string.metric_threshold),
                value = Temperatures.format(assessment.settings.thresholdC, unit),
                caption = stringResource(R.string.metric_threshold_caption),
                onClick = onOpenSettings,
            )
            MetricTile(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = R.drawable.ic_battery,
                iconTint = HeatColors.Accent,
                label = stringResource(R.string.metric_battery),
                value = snapshot?.levelPercent?.let { "$it%" } ?: stringResource(R.string.not_reported),
                caption = snapshot?.let {
                    stringResource(R.string.health_caption, stringResource(it.health.labelRes()))
                } ?: "",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricTile(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = R.drawable.ic_bolt,
                iconTint = if (snapshot?.isPluggedIn == true) HeatColors.Elevated else HeatColors.TextSecondary,
                label = stringResource(R.string.metric_power),
                value = snapshot?.let { stringResource(it.chargeStatus.labelRes()) } ?: "—",
                caption = snapshot?.let { stringResource(it.powerSource.labelRes()) } ?: "",
            )
            MetricTile(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = R.drawable.ic_speed,
                iconTint = HeatColors.TextSecondary,
                label = stringResource(R.string.metric_voltage),
                value = snapshot?.voltageMillivolts
                    ?.let { String.format(Locale.getDefault(), "%.2f V", it / 1000f) }
                    ?: stringResource(R.string.not_reported),
                caption = snapshot?.technology ?: "",
            )
        }

        SessionCard(session = state.session, unit = unit)

        Text(
            text = stringResource(R.string.source_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextTertiary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DashboardHeader(lastUpdateMillis: Long?) {
    val formatTime = rememberTimeFormatter()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = HeatColors.TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
            )
        }
        LivePill(
            text = if (lastUpdateMillis == null) {
                stringResource(R.string.live_waiting)
            } else {
                stringResource(R.string.live_updated, formatTime(lastUpdateMillis))
            },
            active = lastUpdateMillis != null,
        )
    }
}

@Composable
private fun LivePill(text: String, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "livePulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "livePulseAlpha",
    )
    Row(
        modifier = Modifier
            .background(HeatColors.SurfaceRaised, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (active) pulse else 1f)
                .background(if (active) HeatColors.Normal else HeatColors.Unknown, CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = HeatColors.TextSecondary,
        )
    }
}

@Composable
private fun OverheatBanner() {
    HeatCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        color = HeatColors.Critical.copy(alpha = 0.12f),
        border = HeatColors.Critical.copy(alpha = 0.55f),
        contentPadding = 16.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = HeatColors.Critical,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.overheat_warning),
                style = MaterialTheme.typography.titleMedium,
                color = HeatColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun TemperatureHeroCard(state: DashboardUiState) {
    val assessment = state.assessment
    val unit = assessment.settings.unit
    val temperatureC = assessment.temperatureC
    val levelColor by animateColorAsState(assessment.level.color(), tween(600), label = "levelColor")

    val heroDescription = stringResource(
        R.string.widget_content_description,
        temperatureC?.let { Temperatures.format(it, unit) } ?: stringResource(R.string.detail_unavailable),
        stringResource(assessment.level.labelRes()),
    )
    HeatCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = heroDescription },
        spacing = 4.dp,
    ) {
        TemperatureGauge(
            temperatureC = temperatureC,
            thresholdC = assessment.settings.thresholdC,
            warningMarginC = assessment.settings.warningMarginC,
            color = levelColor,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.battery_temperature).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HeatColors.TextSecondary,
                )
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = temperatureC?.let { Temperatures.formatValue(it, unit) } ?: "--",
                        style = MaterialTheme.typography.displayLarge,
                        color = HeatColors.TextPrimary,
                    )
                    Text(
                        text = unit.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        color = HeatColors.TextSecondary,
                        modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                    )
                }
                val secondaryUnit = if (unit == TemperatureUnit.CELSIUS) {
                    TemperatureUnit.FAHRENHEIT
                } else {
                    TemperatureUnit.CELSIUS
                }
                Text(
                    text = temperatureC?.let { Temperatures.format(it, secondaryUnit) } ?: " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeatColors.TextSecondary,
                )
            }
        }

        if (assessment.snapshot?.source == com.example.appdev.domain.model.ReadingSource.DEMO) {
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                com.example.appdev.ui.history.StatusChip(stringResource(R.string.demo_simulated_chip), HeatColors.Accent)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).background(levelColor, CircleShape))
            Text(
                text = if (state.isWaitingForFirstReading) {
                    stringResource(R.string.live_waiting)
                } else {
                    stringResource(assessment.level.labelRes())
                },
                style = MaterialTheme.typography.titleMedium,
                color = levelColor,
            )
        }
        if (!state.isWaitingForFirstReading) {
            Text(
                text = levelDetail(assessment),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun levelDetail(assessment: ThermalAssessment): String {
    val unit = assessment.settings.unit
    val threshold = Temperatures.format(assessment.settings.thresholdC, unit)
    val headroom = assessment.headroomC ?: return stringResource(R.string.level_unknown_detail)
    return when (assessment.level) {
        TemperatureLevel.NORMAL ->
            stringResource(R.string.level_normal_detail, Temperatures.formatDelta(headroom, unit), threshold)
        TemperatureLevel.ELEVATED ->
            stringResource(R.string.level_elevated_detail, Temperatures.formatDelta(headroom, unit), threshold)
        TemperatureLevel.CRITICAL ->
            if (abs(headroom) < 0.05f) {
                stringResource(R.string.level_critical_at_detail, threshold)
            } else {
                stringResource(R.string.level_critical_detail, Temperatures.formatDelta(headroom, unit), threshold)
            }
        TemperatureLevel.UNKNOWN -> stringResource(R.string.level_unknown_detail)
    }
}

@Composable
private fun MetricTile(
    @DrawableRes icon: Int,
    iconTint: Color,
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    HeatCard(modifier = modifier, onClick = onClick, contentPadding = 16.dp, spacing = 6.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = HeatColors.TextSecondary,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = HeatColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption.isNotEmpty()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = HeatColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionCard(session: SessionStats, unit: TemperatureUnit) {
    val formatTime = rememberTimeFormatter()
    HeatCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.session_title),
                style = MaterialTheme.typography.titleMedium,
                color = HeatColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            session.firstSampleMillis?.let {
                Text(
                    text = stringResource(R.string.session_since, formatTime(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = HeatColors.TextSecondary,
                )
            }
        }
        if (session.sampleCount == 0) {
            Text(
                text = stringResource(R.string.session_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = HeatColors.TextSecondary,
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                SessionStat(
                    label = stringResource(R.string.session_min),
                    value = session.minC?.let { Temperatures.format(it, unit) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                SessionStat(
                    label = stringResource(R.string.session_max),
                    value = session.maxC?.let { Temperatures.format(it, unit) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                SessionStat(
                    label = stringResource(R.string.session_samples),
                    value = session.sampleCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SessionStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = HeatColors.TextTertiary,
        )
        Spacer(Modifier.width(0.dp).height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = HeatColors.TextPrimary,
        )
    }
}

@Composable
private fun MonitoringCard(
    state: DashboardUiState,
    notificationsAllowed: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val active = state.monitoringRunning
    val starting = state.monitoringWanted && !state.monitoringRunning
    HeatCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (active) HeatColors.Normal else HeatColors.Unknown, CircleShape),
            )
            Text(
                text = stringResource(R.string.monitoring_title),
                style = MaterialTheme.typography.titleMedium,
                color = HeatColors.TextPrimary,
            )
        }
        Text(
            text = when {
                state.monitoringStartFailed -> stringResource(R.string.monitoring_failed)
                starting -> stringResource(R.string.monitoring_starting)
                active -> stringResource(R.string.monitoring_on)
                else -> stringResource(R.string.monitoring_off)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.monitoringStartFailed) HeatColors.Critical else HeatColors.TextSecondary,
        )
        if (active && !notificationsAllowed) {
            Text(
                text = stringResource(R.string.monitoring_notifications_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = HeatColors.Elevated,
            )
            TextButton(onClick = onOpenNotificationSettings) {
                Text(stringResource(R.string.monitoring_open_notification_settings))
            }
        }
        if (active || starting) {
            OutlinedButton(onClick = { onToggle(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.monitoring_stop))
            }
        } else {
            Button(
                onClick = { onToggle(true) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HeatColors.Accent,
                    contentColor = HeatColors.OnAccent,
                ),
            ) {
                Text(stringResource(R.string.monitoring_start))
            }
        }
    }
}

@Composable
private fun ProfileCard(settings: com.example.appdev.domain.model.UserSettings, onSelect: (com.example.appdev.domain.profile.ProfileType?) -> Unit) {
    val unit = settings.unit
    HeatCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        Text(
            stringResource(R.string.profile_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = HeatColors.TextSecondary,
        )
        com.example.appdev.ui.components.ProfileSelector(active = settings.activeProfile, onSelect = onSelect)
        val profile = settings.activeProfile
        Text(
            text = if (profile == null) {
                stringResource(R.string.profile_hint_custom)
            } else {
                stringResource(
                    R.string.profile_hint_active,
                    stringResource(profile.labelRes()),
                    Temperatures.format(settings.thresholdC, unit),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = HeatColors.TextSecondary,
        )
    }
}

/**
 * Opens the app's notification settings. The dedicated screen exists from Android 8; earlier
 * versions (and any device that cannot resolve it) fall back to the general app details page.
 */
private fun openNotificationSettings(context: android.content.Context) {
    val dedicated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        null
    }
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
    for (intent in listOfNotNull(dedicated, fallback)) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: android.content.ActivityNotFoundException) {
            // Try the next option.
        }
    }
}
