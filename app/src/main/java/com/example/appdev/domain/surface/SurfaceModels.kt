package com.example.appdev.domain.surface

import com.example.appdev.domain.model.BatterySnapshot
import com.example.appdev.domain.model.ReadingSource
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.thermal.TemperatureClassifier
import com.example.appdev.domain.thermal.TemperatureLevel
import java.util.Locale

/** What the home-screen widget shows. Built from the same reading and settings as the app. */
data class WidgetModel(
    /** e.g. "38.4°C", or "--" when no valid reading exists. */
    val temperatureText: String,
    val level: TemperatureLevel,
    val updatedMillis: Long?,
    val isDemo: Boolean,
)

/** What the Quick Settings tile shows. */
data class TileModel(
    val label: String,
    val level: TemperatureLevel,
    /** True while background monitoring is running. */
    val active: Boolean,
    val isDemo: Boolean,
)

object SurfaceModels {

    fun widget(snapshot: BatterySnapshot?, settings: UserSettings, locale: Locale = Locale.getDefault()): WidgetModel {
        val temperature = snapshot?.temperatureC?.takeIf { it.isFinite() }
        return WidgetModel(
            temperatureText = temperature?.let { Temperatures.format(it, settings.unit, locale) } ?: NO_VALUE,
            level = TemperatureClassifier.classify(temperature, settings.thresholdC, settings.warningMarginC),
            updatedMillis = snapshot?.timestampMillis,
            isDemo = snapshot?.source == ReadingSource.DEMO,
        )
    }

    fun tile(
        snapshot: BatterySnapshot?,
        settings: UserSettings,
        monitoringRunning: Boolean,
        locale: Locale = Locale.getDefault(),
    ): TileModel {
        val widget = widget(snapshot, settings, locale)
        return TileModel(widget.temperatureText, widget.level, monitoringRunning, widget.isDemo)
    }

    const val NO_VALUE = "--"
}
