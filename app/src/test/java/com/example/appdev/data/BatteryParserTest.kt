package com.example.appdev.data

import com.example.appdev.data.battery.BatteryParser
import com.example.appdev.data.battery.RawBatteryExtras
import com.example.appdev.domain.model.BatteryHealth
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Constant values below are the documented android.os.BatteryManager values. */
class BatteryParserTest {

    private val typical = RawBatteryExtras(
        temperatureTenthsC = 384,
        level = 76,
        scale = 100,
        status = 2, // CHARGING
        plugged = 1, // AC
        voltage = 4123,
        health = 2, // GOOD
        technology = "Li-ion",
        present = true,
    )

    @Test
    fun `parses a typical charging reading`() {
        val s = BatteryParser.parse(typical, timestampMillis = 42L)
        assertEquals(38.4f, s.temperatureC!!, 0.0001f)
        assertEquals(76, s.levelPercent)
        assertEquals(ChargeStatus.CHARGING, s.chargeStatus)
        assertEquals(PowerSource.AC, s.powerSource)
        assertEquals(4123, s.voltageMillivolts)
        assertEquals(BatteryHealth.GOOD, s.health)
        assertEquals("Li-ion", s.technology)
        assertEquals(42L, s.timestampMillis)
        assertTrue(s.isCharging)
        assertTrue(s.isPluggedIn)
    }

    @Test
    fun `temperature is tenths of a degree`() {
        assertEquals(25.0f, BatteryParser.parseTemperature(250)!!, 0.0001f)
        assertEquals(-5.5f, BatteryParser.parseTemperature(-55)!!, 0.0001f)
    }

    @Test
    fun `missing or implausible temperature is null, never a fabricated value`() {
        assertNull(BatteryParser.parseTemperature(null))
        assertNull(BatteryParser.parseTemperature(1500)) // 150 °C
        assertNull(BatteryParser.parseTemperature(-1000)) // -100 °C
    }

    @Test
    fun `absent battery yields no hardware readings`() {
        val s = BatteryParser.parse(typical.copy(present = false), 0L)
        assertNull(s.temperatureC)
        assertNull(s.levelPercent)
        assertNull(s.voltageMillivolts)
    }

    @Test
    fun `missing present extra is treated as present`() {
        val s = BatteryParser.parse(typical.copy(present = null), 0L)
        assertEquals(38.4f, s.temperatureC!!, 0.0001f)
    }

    @Test
    fun `level is scaled to percent`() {
        assertEquals(50, BatteryParser.parseLevel(50, 100))
        assertEquals(50, BatteryParser.parseLevel(128, 256))
        assertEquals(100, BatteryParser.parseLevel(300, 255))
        assertNull(BatteryParser.parseLevel(-1, 100))
        assertNull(BatteryParser.parseLevel(50, 0))
        assertNull(BatteryParser.parseLevel(null, 100))
    }

    @Test
    fun `voltage reported in volts is normalised to millivolts`() {
        assertEquals(4000, BatteryParser.parseVoltage(4))
        assertEquals(3850, BatteryParser.parseVoltage(3850))
        assertNull(BatteryParser.parseVoltage(0))
        assertNull(BatteryParser.parseVoltage(null))
    }

    @Test
    fun `plug bit field maps to power source`() {
        assertEquals(PowerSource.NONE, BatteryParser.parsePowerSource(0))
        assertEquals(PowerSource.AC, BatteryParser.parsePowerSource(1))
        assertEquals(PowerSource.USB, BatteryParser.parsePowerSource(2))
        assertEquals(PowerSource.WIRELESS, BatteryParser.parsePowerSource(4))
        assertEquals(PowerSource.DOCK, BatteryParser.parsePowerSource(8))
        assertEquals(PowerSource.UNKNOWN, BatteryParser.parsePowerSource(null))
        assertEquals(PowerSource.UNKNOWN, BatteryParser.parsePowerSource(64))
    }

    @Test
    fun `status codes map to charge status`() {
        assertEquals(ChargeStatus.UNKNOWN, BatteryParser.parseStatus(1))
        assertEquals(ChargeStatus.CHARGING, BatteryParser.parseStatus(2))
        assertEquals(ChargeStatus.DISCHARGING, BatteryParser.parseStatus(3))
        assertEquals(ChargeStatus.NOT_CHARGING, BatteryParser.parseStatus(4))
        assertEquals(ChargeStatus.FULL, BatteryParser.parseStatus(5))
        assertEquals(ChargeStatus.UNKNOWN, BatteryParser.parseStatus(null))
    }

    @Test
    fun `health codes map to battery health`() {
        assertEquals(BatteryHealth.GOOD, BatteryParser.parseHealth(2))
        assertEquals(BatteryHealth.OVERHEAT, BatteryParser.parseHealth(3))
        assertEquals(BatteryHealth.COLD, BatteryParser.parseHealth(7))
        assertEquals(BatteryHealth.UNKNOWN, BatteryParser.parseHealth(99))
    }

    @Test
    fun `unplugged discharging snapshot is not charging`() {
        val s = BatteryParser.parse(typical.copy(status = 3, plugged = 0), 0L)
        assertFalse(s.isCharging)
        assertFalse(s.isPluggedIn)
    }

    @Test
    fun `blank technology is dropped`() {
        assertNull(BatteryParser.parse(typical.copy(technology = "  "), 0L).technology)
    }
}
