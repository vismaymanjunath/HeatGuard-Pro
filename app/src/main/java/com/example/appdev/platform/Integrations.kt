package com.example.appdev.platform

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.appdev.R

enum class PinWidgetResult { REQUESTED, UNSUPPORTED }

enum class AddTileResult { ADDED, ALREADY_PRESENT, DECLINED, MANUAL }

/** Asks Android to place the widget or tile for the user, where the OS offers a way to do so. */
object Integrations {

    /** Pinning from inside an app needs Android 8+; earlier versions add widgets from the launcher only. */
    fun requestPinWidget(context: Context): PinWidgetResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return PinWidgetResult.UNSUPPORTED
        return requestPinWidgetApi26(context)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun requestPinWidgetApi26(context: Context): PinWidgetResult {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return PinWidgetResult.UNSUPPORTED
        val requested = manager.requestPinAppWidget(ComponentName(context, TemperatureWidgetProvider::class.java), null, null)
        return if (requested) PinWidgetResult.REQUESTED else PinWidgetResult.UNSUPPORTED
    }

    /** On Android 13+ shows the system's "add tile" prompt; earlier versions must add it by hand. */
    fun requestAddTile(context: Context, onResult: (AddTileResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(AddTileResult.MANUAL)
            return
        }
        val manager = context.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            onResult(AddTileResult.MANUAL)
            return
        }
        manager.requestAddTileService(
            ComponentName(context, HeatTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_thermostat),
            ContextCompat.getMainExecutor(context),
        ) { code ->
            onResult(
                when (code) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> AddTileResult.ADDED
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> AddTileResult.ALREADY_PRESENT
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> AddTileResult.DECLINED
                    else -> AddTileResult.MANUAL
                },
            )
        }
    }
}
