package com.example.appdev.domain

import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TemperaturesTest {

    @Test
    fun `celsius to fahrenheit uses standard reference points`() {
        assertEquals(32f, Temperatures.celsiusToFahrenheit(0f), 0.001f)
        assertEquals(212f, Temperatures.celsiusToFahrenheit(100f), 0.001f)
        assertEquals(104f, Temperatures.celsiusToFahrenheit(40f), 0.001f)
        assertEquals(-40f, Temperatures.celsiusToFahrenheit(-40f), 0.001f)
    }

    @Test
    fun `fahrenheit to celsius is the inverse`() {
        listOf(-20f, 0f, 37.5f, 40f, 55f).forEach { c ->
            val roundTrip = Temperatures.fahrenheitToCelsius(Temperatures.celsiusToFahrenheit(c))
            assertEquals(c, roundTrip, 0.001f)
        }
    }

    @Test
    fun `deltas convert without the 32 degree offset`() {
        assertEquals(5f, Temperatures.deltaFromCelsius(5f, TemperatureUnit.CELSIUS), 0.001f)
        assertEquals(9f, Temperatures.deltaFromCelsius(5f, TemperatureUnit.FAHRENHEIT), 0.001f)
    }

    @Test
    fun `formatting uses one decimal and the unit symbol`() {
        assertEquals("38.4°C", Temperatures.format(38.4f, TemperatureUnit.CELSIUS, Locale.US))
        assertEquals("104.0°F", Temperatures.format(40f, TemperatureUnit.FAHRENHEIT, Locale.US))
        assertEquals("38.4", Temperatures.formatValue(38.4f, TemperatureUnit.CELSIUS, Locale.US))
    }

    @Test
    fun `delta formatting is always a positive magnitude`() {
        assertEquals("2.5°", Temperatures.formatDelta(-2.5f, TemperatureUnit.CELSIUS, Locale.US))
        assertEquals("4.5°", Temperatures.formatDelta(2.5f, TemperatureUnit.FAHRENHEIT, Locale.US))
    }
}
