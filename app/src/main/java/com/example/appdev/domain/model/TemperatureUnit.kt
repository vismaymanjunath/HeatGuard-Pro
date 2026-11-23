package com.example.appdev.domain.model

import java.util.Locale

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F"),
}

/** Temperature conversion and formatting. All values are stored internally in Celsius. */
object Temperatures {

    fun celsiusToFahrenheit(celsius: Float): Float = celsius * 9f / 5f + 32f

    fun fahrenheitToCelsius(fahrenheit: Float): Float = (fahrenheit - 32f) * 5f / 9f

    /** Converts an absolute temperature from Celsius to [unit]. */
    fun fromCelsius(celsius: Float, unit: TemperatureUnit): Float = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsiusToFahrenheit(celsius)
    }

    /** Converts a temperature *difference* (no offset) from Celsius degrees to [unit] degrees. */
    fun deltaFromCelsius(deltaCelsius: Float, unit: TemperatureUnit): Float = when (unit) {
        TemperatureUnit.CELSIUS -> deltaCelsius
        TemperatureUnit.FAHRENHEIT -> deltaCelsius * 9f / 5f
    }

    /** "38.4" — numeric part only, one decimal. */
    fun formatValue(celsius: Float, unit: TemperatureUnit, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", fromCelsius(celsius, unit))

    /** "38.4°C" */
    fun format(celsius: Float, unit: TemperatureUnit, locale: Locale = Locale.getDefault()): String =
        formatValue(celsius, unit, locale) + unit.symbol

    /** "2.5°" — a difference in degrees of [unit]; always positive magnitude. */
    fun formatDelta(deltaCelsius: Float, unit: TemperatureUnit, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f°", kotlin.math.abs(deltaFromCelsius(deltaCelsius, unit)))
}
