package com.example.appdev.platform

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen temperature widget. Android refreshes it when it is added and about every 30
 * minutes; while monitoring or Demo Mode runs, the app pushes fresher values as readings arrive.
 */
class TemperatureWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
