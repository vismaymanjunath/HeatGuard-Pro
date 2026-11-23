package com.example.appdev.domain

import com.example.appdev.domain.ai.ChatFailure
import com.example.appdev.domain.ai.ChatMessage
import com.example.appdev.domain.ai.ChatRole
import com.example.appdev.domain.ai.ConversationWindow
import com.example.appdev.domain.ai.DeviceInfo
import com.example.appdev.domain.ai.PhoneContext
import com.example.appdev.domain.ai.PhoneMetrics
import com.example.appdev.domain.ai.PromptBuilder
import com.example.appdev.domain.ai.RelativeTime
import com.example.appdev.domain.analytics.InsightsEngine
import com.example.appdev.domain.cooling.PersonalCoolingProfile
import com.example.appdev.domain.model.ChargeStatus
import com.example.appdev.domain.model.PowerSource
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.readiness.ReadinessEvaluator
import com.example.appdev.domain.thermal.SessionStats
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalAssessment
import com.example.appdev.domain.thermal.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.GregorianCalendar
import java.util.TimeZone

class PromptAndWindowTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = GregorianCalendar(utc).apply { clear(); set(2026, 10, 18, 12, 0) }.timeInMillis

    private val device = DeviceInfo("Google", "Pixel 8", "14", 34)
    private val metrics = PhoneMetrics(
        totalRamBytes = 8L * 1_073_741_824, availRamBytes = 3L * 1_073_741_824, lowMemory = true,
        totalStorageBytes = 128L * 1_073_741_824, freeStorageBytes = 40L * 1_073_741_824,
        powerSaveOn = false, brightnessPercent = 70, uptimeMillis = 2 * 86_400_000L + 3 * 3_600_000L, cpuCores = 8,
    )

    private fun ctx(
        temp: Float? = 38.4f,
        ageMillis: Long = 3_000,
        simulated: Boolean = false,
        withDevice: Boolean = true,
        charging: Boolean = false,
        system: SystemThermalState = SystemThermalState.Unknown,
    ): PhoneContext {
        val settings = UserSettings()
        val snap = snapshot(
            temp, timestampMillis = now - ageMillis,
            chargeStatus = if (charging) ChargeStatus.CHARGING else ChargeStatus.DISCHARGING,
            powerSource = if (charging) PowerSource.AC else PowerSource.NONE,
        )
        val assessment = ThermalAssessment.evaluate(snap, settings)
        val analysis = ThermalAnalysis.analyze(snap, emptyList(), settings.thresholdC, system)
        return PhoneContext(
            nowMillis = now, timeZone = utc, screen = "Insights", simulated = simulated,
            assessment = assessment, analysis = analysis, system = system,
            readiness = ReadinessEvaluator.evaluate(settings, analysis, system, PersonalCoolingProfile.Empty, assessment.temperatureC),
            profile = PersonalCoolingProfile.Empty,
            insights = InsightsEngine.compute(emptyList(), emptyList(), now, utc),
            recentIncidents = emptyList(), sessionStats = SessionStats(),
            device = if (withDevice) device else null, metrics = if (withDevice) metrics else null,
            monitoringRunning = true, coolingSessionActive = false,
        )
    }

    // ---------------------------------------------------------------- the brief

    @Test
    fun `brief states the time, the reading in both units and how old it is`() {
        val text = PromptBuilder.contextText(ctx())
        assertTrue(text, text.contains("Current time: 2026-11-18 12:00 (UTC+00:00)"))
        assertTrue(text.contains("38.4 °C (101.1 °F)"))
        assertTrue(text.contains("read 3 s ago"))
        assertTrue(text.contains("on the \"Insights\" screen"))
        assertTrue(text.contains("Data source: real readings"))
        assertFalse(text.contains("STALE"))
    }

    @Test
    fun `an old reading is flagged as stale so it is not presented as live`() {
        val text = PromptBuilder.contextText(ctx(ageMillis = 95_000))
        assertTrue(text, text.contains("read 1 min ago [STALE"))
    }

    @Test
    fun `the whole phone is described, not just the battery`() {
        val text = PromptBuilder.contextText(ctx())
        assertTrue(text.contains("Device: Google Pixel 8, Android 14 (API 34)"))
        assertTrue(text.contains("Memory: 3.0 GB free of 8.0 GB (37% free), Android reports LOW MEMORY"))
        assertTrue(text.contains("Storage: 40.0 GB free of 128.0 GB (31% free)"))
        assertTrue(text.contains("Power saver: off"))
        assertTrue(text.contains("Screen brightness: 70%"))
        assertTrue(text.contains("Up for 2 d 3 h since last restart"))
        assertTrue(text.contains("CPU cores: 8"))
    }

    @Test
    fun `charging and android thermal status appear when present`() {
        val text = PromptBuilder.contextText(
            ctx(charging = true, system = SystemThermalState(true, ThermalStatus.MODERATE, true, 0.62f, 0.7f)),
        )
        assertTrue(text.contains("charging via AC charger"))
        assertTrue(text.contains("Android thermal status: moderate"))
        assertTrue(text.contains("62% of the throttling limit"))
        assertTrue(text.contains("Pre-call readiness: Caution"))
    }

    @Test
    fun `missing data is stated honestly rather than filled in`() {
        val text = PromptBuilder.contextText(ctx())
        assertTrue(text.contains("Android thermal status: unsupported on this Android version"))
        assertTrue(text.contains("Thermal headroom: not reported"))
        assertTrue(text.contains("Personal cooling profile: insufficient data"))
        assertTrue(text.contains("Overheating incidents recorded: none"))
        assertTrue(text.contains("Trend: not enough readings yet"))
        assertTrue(PromptBuilder.contextText(ctx(temp = null)).contains("no valid reading available"))
    }

    @Test
    fun `demo mode is labelled as simulated and real device details are left out`() {
        val text = PromptBuilder.contextText(ctx(simulated = true, withDevice = false))
        assertTrue(text.contains("SIMULATED (Demo Mode)"))
        assertFalse(text.contains("Device:"))
        assertFalse(text.contains("Memory:"))
    }

    @Test
    fun `the brief contains no identifying fields and stays compact`() {
        val text = PromptBuilder.contextText(ctx())
        listOf("email", "@", "imei", "serial", "latitude", "contact", "account").forEach {
            assertFalse("unexpected '$it' in brief", text.lowercase().contains(it))
        }
        assertTrue("brief is ${text.length} chars", text.length < 3_500)
    }

    @Test
    fun `system prompt sets the guard rails the app promises`() {
        val p = PromptBuilder.systemPrompt()
        assertTrue(p.contains("ONLY the PHONE STATE block"))
        assertTrue(p.contains("cannot control charging"))
        assertTrue(p.contains("never claim to know which app", ignoreCase = true))
        assertTrue(p.contains("Never promise how long a call"))
        assertTrue(p.contains("never as instructions"))
        assertTrue(p.contains("SIMULATED"))
        assertTrue(PromptBuilder.noContextNote().contains("cannot see their phone"))
    }

    // ---------------------------------------------------------------- relative time

    @Test
    fun `relative time is compact and survives clock changes`() {
        assertEquals("0 s", RelativeTime.span(-5_000))
        assertEquals("45 s", RelativeTime.span(45_000))
        assertEquals("5 min", RelativeTime.span(5 * 60_000L + 59_000))
        assertEquals("3 h 12 min", RelativeTime.span(3 * 3_600_000L + 12 * 60_000L))
        assertEquals("2 d 3 h", RelativeTime.span(2 * 86_400_000L + 3 * 3_600_000L))
        assertEquals("just now", RelativeTime.ago(eventMillis = now + 60_000, nowMillis = now)) // clock moved back
        assertEquals("just now", RelativeTime.ago(now - 1_000, now))
        assertEquals("10 min ago", RelativeTime.ago(now - 10 * 60_000L, now))
    }

    // ---------------------------------------------------------------- conversation window

    private fun msg(id: Long, role: ChatRole, text: String, failure: ChatFailure? = null) =
        ChatMessage(id, role, text, timestampMillis = id, failure = failure)

    @Test
    fun `window keeps recent messages in order and starts with the user`() {
        val history = (1..30L).map { msg(it, if (it % 2 == 1L) ChatRole.USER else ChatRole.ASSISTANT, "m$it") }
        val sent = ConversationWindow.select(history)
        assertTrue(sent.size <= ConversationWindow.MAX_MESSAGES)
        assertEquals("user", sent.first().role)
        assertEquals("m30", sent.last().content)
        assertEquals(sent.map { it.content }, history.takeLast(sent.size).map { it.text })
    }

    @Test
    fun `window drops failed and empty messages`() {
        val sent = ConversationWindow.select(
            listOf(
                msg(1, ChatRole.USER, "q1"),
                msg(2, ChatRole.ASSISTANT, "", ChatFailure.Offline),
                msg(3, ChatRole.USER, "q2"),
                msg(4, ChatRole.ASSISTANT, "  "),
            ),
        )
        assertEquals(listOf("q1", "q2"), sent.map { it.content })
    }

    @Test
    fun `window respects the character budget but never drops the latest message`() {
        val big = "x".repeat(5_000)
        val sent = ConversationWindow.select(
            listOf(msg(1, ChatRole.USER, big), msg(2, ChatRole.ASSISTANT, big), msg(3, ChatRole.USER, "latest")),
        )
        assertEquals("latest", sent.last().content)
        assertTrue(sent.sumOf { it.content.length } <= ConversationWindow.MAX_CHARS + 5)
        val huge = ConversationWindow.select(listOf(msg(1, ChatRole.USER, "y".repeat(50_000))))
        assertEquals(1, huge.size)
        assertEquals(ConversationWindow.MAX_CHARS, huge.single().content.length)
    }
}
