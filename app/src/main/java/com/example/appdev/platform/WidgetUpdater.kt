package com.example.appdev.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.example.appdev.HeatGuardApp
import com.example.appdev.MainActivity
import com.example.appdev.R
import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.surface.SurfaceModels
import com.example.appdev.domain.surface.WidgetModel
import com.example.appdev.domain.thermal.TemperatureLevel
import com.example.appdev.ui.components.labelRes
import com.example.appdev.ui.theme.color
import kotlinx.coroutines.flow.first
import java.util.Date

/** Builds and pushes the home-screen widget. Uses the same reading and settings as the app. */
object WidgetUpdater {
    private const val MIN_PUSH_INTERVAL_MILLIS = 15_000L

    private var lastPushMillis = 0L
    private var lastLevel: TemperatureLevel? = null
    private var lastDemo = false

    private fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, TemperatureWidgetProvider::class.java))

    /**
     * Called for every processed reading while monitoring (or Demo Mode) runs. Pushes at most once
     * every 15 seconds, or immediately when the colour level or demo state changes.
     */
    @Synchronized
    fun onReading(context: Context, snapshot: BatterySnapshot, settings: UserSettings) {
        val widgetIds = ids(context)
        if (widgetIds.isEmpty()) return
        val model = SurfaceModels.widget(snapshot, settings)
        val now = System.currentTimeMillis()
        if (now - lastPushMillis < MIN_PUSH_INTERVAL_MILLIS && model.level == lastLevel && model.isDemo == lastDemo) return
        push(context, widgetIds, model)
    }

    /** Refreshes from the best current source: the demo reading in Demo Mode, else the live battery. */
    suspend fun refresh(context: Context) {
        val widgetIds = ids(context)
        if (widgetIds.isEmpty()) return
        val container = (context.applicationContext as HeatGuardApp).container
        val settings = container.settingsRepository.settings.first()
        val snapshot = if (container.demoController.enabled.value) {
            container.demoController.current.value
        } else {
            container.batteryMonitor.readNow()
        }
        push(context, widgetIds, SurfaceModels.widget(snapshot, settings))
    }

    @Synchronized
    private fun push(context: Context, widgetIds: IntArray, model: WidgetModel) {
        AppWidgetManager.getInstance(context).updateAppWidget(widgetIds, buildViews(context, model))
        lastPushMillis = System.currentTimeMillis()
        lastLevel = model.level
        lastDemo = model.isDemo
    }

    fun buildViews(context: Context, model: WidgetModel): RemoteViews {
        val levelColor = model.level.color().toArgb()
        val statusText = context.getString(model.level.labelRes())
        val metaText = when {
            model.isDemo -> context.getString(R.string.widget_demo_meta)
            model.updatedMillis != null ->
                context.getString(R.string.widget_updated, DateFormat.getTimeFormat(context).format(Date(model.updatedMillis)))
            else -> ""
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteViews(context.packageName, R.layout.widget_temperature).apply {
            setTextViewText(R.id.widget_temperature, model.temperatureText)
            setTextViewText(R.id.widget_status, if (model.isDemo) context.getString(R.string.demo_prefix, statusText) else statusText)
            setTextColor(R.id.widget_status, levelColor)
            setTextViewText(R.id.widget_meta, metaText)
            setInt(R.id.widget_indicator, "setBackgroundColor", levelColor)
            setContentDescription(
                R.id.widget_root,
                context.getString(R.string.widget_content_description, model.temperatureText, statusText),
            )
            setOnClickPendingIntent(R.id.widget_root, open)
        }
    }
}
