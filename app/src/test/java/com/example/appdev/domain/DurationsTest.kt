package com.example.appdev.domain

import com.example.appdev.ui.components.Durations
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationsTest {
    @Test
    fun `stopwatch format pads minutes and seconds and adds hours when needed`() {
        assertEquals("00:00", Durations.clock(0))
        assertEquals("00:09", Durations.clock(9_999))
        assertEquals("04:12", Durations.clock(252_000))
        assertEquals("59:59", Durations.clock(3_599_000))
        assertEquals("1:04:12", Durations.clock(3_852_000))
    }

    @Test
    fun `negative durations are treated as zero`() {
        assertEquals("00:00", Durations.clock(-5_000))
        assertEquals("0 s", Durations.format(-5_000))
    }
}
