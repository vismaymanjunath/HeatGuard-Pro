package com.example.appdev.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.appdev.data.ai.AiSettingsRepository
import com.example.appdev.data.ai.AssistantController
import com.example.appdev.data.ai.AssistantPhase
import com.example.appdev.data.ai.ChatClient
import com.example.appdev.data.ai.KeyResult
import com.example.appdev.data.ai.SecretStore
import com.example.appdev.domain.ai.ApiMessage
import com.example.appdev.domain.ai.ChatException
import com.example.appdev.domain.ai.ChatFailure
import com.example.appdev.domain.ai.ChatRole
import com.example.appdev.domain.ai.PhoneContext
import com.example.appdev.domain.analytics.InsightsEngine
import com.example.appdev.domain.cooling.PersonalCoolingProfile
import com.example.appdev.domain.model.UserSettings
import com.example.appdev.domain.readiness.ReadinessEvaluator
import com.example.appdev.domain.snapshot
import com.example.appdev.domain.thermal.SessionStats
import com.example.appdev.domain.thermal.SystemThermalState
import com.example.appdev.domain.thermal.ThermalAnalysis
import com.example.appdev.domain.thermal.ThermalAssessment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.TimeZone

private class FakeSecretStore(initial: String? = null) : SecretStore {
    private var value = initial
    private val _has = MutableStateFlow(initial != null)
    override val hasSecret: StateFlow<Boolean> = _has.asStateFlow()
    override fun get() = value
    override fun put(secret: String) { value = secret; _has.value = true }
    override fun clear() { value = null; _has.value = false }
}

private class FakeChatClient : ChatClient {
    var handler: (String, List<ApiMessage>) -> Flow<String> = { _, _ -> flow { emit("ok") } }
    val calls = mutableListOf<Pair<String, List<ApiMessage>>>()

    override fun stream(apiKey: String, messages: List<ApiMessage>): Flow<String> {
        calls += apiKey to messages
        return handler(apiKey, messages)
    }
}

class AssistantControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var aiSettings: AiSettingsRepository
    private val client = FakeChatClient()
    private val validKey = "gsk_" + "a".repeat(24)
    private var contextCalls = 0
    private var contextShouldFail = false

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "ai.preferences_pb") }
        aiSettings = AiSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun sampleContext(): PhoneContext {
        val settings = UserSettings()
        val snap = snapshot(38.4f, timestampMillis = 1_000)
        val assessment = ThermalAssessment.evaluate(snap, settings)
        val analysis = ThermalAnalysis.analyze(snap, emptyList(), settings.thresholdC, SystemThermalState.Unknown)
        return PhoneContext(
            nowMillis = 4_000, timeZone = TimeZone.getTimeZone("UTC"), screen = "Dashboard", simulated = false,
            assessment = assessment, analysis = analysis, system = SystemThermalState.Unknown,
            readiness = ReadinessEvaluator.evaluate(settings, analysis, SystemThermalState.Unknown, PersonalCoolingProfile.Empty, 38.4f),
            profile = PersonalCoolingProfile.Empty,
            insights = InsightsEngine.compute(emptyList(), emptyList(), 4_000, TimeZone.getTimeZone("UTC")),
            recentIncidents = emptyList(), sessionStats = SessionStats(), device = null, metrics = null,
            monitoringRunning = false, coolingSessionActive = false,
        )
    }

    private fun controller(secrets: SecretStore, timeoutMs: Long = 5_000) = AssistantController(
        client = client, secrets = secrets, aiSettings = aiSettings,
        contextProvider = { contextCalls++; if (contextShouldFail) error("collector failed") else sampleContext() },
        scope = scope, overallTimeoutMillis = timeoutMs,
    )

    private fun configured(timeoutMs: Long = 5_000): AssistantController = runBlocking {
        aiSettings.setConsent(true)
        controller(FakeSecretStore(validKey), timeoutMs)
    }

    private fun AssistantController.awaitIdle() = runBlocking {
        withTimeout(10_000) { while (state.value.isGenerating) delay(10) }
    }

    private fun AssistantController.awaitText(predicate: (String) -> Boolean) = runBlocking {
        withTimeout(10_000) {
            while (state.value.messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.text?.let(predicate) != true) delay(10)
        }
    }

    // ---------------------------------------------------------------- happy path

    @Test
    fun `a question is answered by streaming fragments into one assistant message`() {
        client.handler = { _, _ -> flow { listOf("Your ", "phone ", "is fine.").forEach { emit(it); delay(5) } } }
        val c = configured()
        c.send("  Is my phone ok?  ", screen = "Dashboard")
        c.awaitIdle()

        val messages = c.state.value.messages
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), messages.map { it.role })
        assertEquals("Is my phone ok?", messages[0].text) // trimmed
        assertEquals("Your phone is fine.", messages[1].text)
        assertFalse(messages[1].streaming)
        assertNull(messages[1].failure)
        assertEquals(AssistantPhase.IDLE, c.state.value.phase)
    }

    @Test
    fun `each request carries the system prompt, a fresh phone brief and the question`() {
        val c = configured()
        c.send("Why is it warm?", screen = "Coach")
        c.awaitIdle()

        val (key, sent) = client.calls.single()
        assertEquals(validKey, key)
        assertEquals("system", sent.first().role)
        assertTrue(sent.first().content.contains("HeatGuard Pro"))
        assertTrue(sent.first().content.contains("=== PHONE STATE ==="))
        assertTrue(sent.first().content.contains("38.4 °C"))
        assertEquals(ApiMessage("user", "Why is it warm?"), sent.last())
        assertEquals(1, contextCalls)
    }

    @Test
    fun `follow-up questions include the earlier exchange and a newly gathered brief`() {
        val c = configured()
        c.send("first", null)
        c.awaitIdle()
        c.send("second", null)
        c.awaitIdle()

        val second = client.calls[1].second
        assertEquals(listOf("system", "user", "assistant", "user"), second.map { it.role })
        assertEquals("first", second[1].content)
        assertEquals("ok", second[2].content)
        assertEquals(2, contextCalls) // the brief is refreshed for every question, never reused stale
    }

    // ---------------------------------------------------------------- privacy and setup

    @Test
    fun `without a saved key nothing is sent`() {
        runBlocking { aiSettings.setConsent(true) }
        val c = controller(FakeSecretStore(null))
        c.send("hello", null)
        c.awaitIdle()
        assertEquals(ChatFailure.NotConfigured, c.state.value.messages.last().failure)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun `without consent nothing is sent even if a key exists`() {
        val c = controller(FakeSecretStore(validKey)) // consent never given
        c.send("hello", null)
        c.awaitIdle()
        assertEquals(ChatFailure.NotConfigured, c.state.value.messages.last().failure)
        assertTrue(client.calls.isEmpty())
        assertEquals(0, contextCalls)
    }

    @Test
    fun `turning off phone data sends only the question`() {
        val c = configured()
        runBlocking { c.setIncludePhoneData(false) }
        c.send("general question", null)
        c.awaitIdle()

        assertEquals(0, contextCalls) // the phone is never even read
        val system = client.calls.single().second.first().content
        assertTrue(system.contains("chosen not to share"))
        assertFalse(system.contains("38.4"))
    }

    @Test
    fun `if gathering the brief fails the question still gets answered without it`() {
        contextShouldFail = true
        val c = configured()
        c.send("hello", null)
        c.awaitIdle()
        assertEquals("ok", c.state.value.messages.last().text)
        assertTrue(client.calls.single().second.first().content.contains("chosen not to share"))
    }

    @Test
    fun `ready reflects key and consent`() = runBlocking {
        val secrets = FakeSecretStore(null)
        val c = controller(secrets)
        assertFalse(c.ready.first())
        c.saveKey(validKey)
        assertFalse(c.ready.first()) // key but no consent
        c.setConsent(true)
        assertTrue(c.ready.first())
        c.removeKey()
        assertFalse(c.ready.first())
    }

    @Test
    fun `malformed keys are rejected before being stored`() {
        val secrets = FakeSecretStore(null)
        val c = controller(secrets)
        listOf("", "   ", "sk-abc", "gsk_short", "gsk_ has space inside_xxxxxxxxxxxxxxxx", "gsk_" + "a".repeat(300)).forEach {
            assertEquals(it, KeyResult.INVALID_FORMAT, c.saveKey(it))
        }
        assertNull(secrets.get())
        assertEquals(KeyResult.SAVED, c.saveKey("  $validKey\n"))
        assertEquals(validKey, secrets.get()) // surrounding whitespace removed
    }

    // ---------------------------------------------------------------- failures and retry

    @Test
    fun `a failed request leaves an error message and retry re-asks the same question`() {
        var attempt = 0
        client.handler = { _, _ ->
            flow {
                if (attempt++ == 0) throw ChatException(ChatFailure.Offline)
                emit("recovered")
            }
        }
        val c = configured()
        c.send("question", null)
        c.awaitIdle()
        assertEquals(ChatFailure.Offline, c.state.value.messages.last().failure)
        assertEquals("", c.state.value.messages.last().text)

        c.retry(null)
        c.awaitIdle()
        val messages = c.state.value.messages
        assertEquals(listOf("question", "recovered"), messages.map { it.text })
        assertTrue(messages.all { it.failure == null })
        // The failed attempt is not sent back to the model.
        assertEquals(listOf("system", "user"), client.calls.last().second.map { it.role })
    }

    @Test
    fun `an answer cut off part-way keeps the text that arrived and offers a retry`() {
        client.handler = { _, _ -> flow { emit("Half an ans"); throw ChatException(ChatFailure.Offline) } }
        val c = configured()
        c.send("q", null)
        c.awaitIdle()
        val last = c.state.value.messages.last()
        assertEquals("Half an ans", last.text)
        assertEquals(ChatFailure.Offline, last.failure)
        assertFalse(last.streaming)
    }

    @Test
    fun `unexpected exceptions become an unknown failure instead of crashing`() {
        client.handler = { _, _ -> flow { throw IllegalStateException("bug") } }
        val c = configured()
        c.send("q", null)
        c.awaitIdle()
        assertEquals(ChatFailure.Unknown, c.state.value.messages.last().failure)
    }

    // ---------------------------------------------------------------- timing and cancellation

    @Test
    fun `a request that never finishes is failed by the hard deadline`() {
        client.handler = { _, _ -> flow { delay(60_000); emit("never") } }
        val c = configured(timeoutMs = 300)
        val started = System.nanoTime()
        c.send("q", null)
        c.awaitIdle()
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(ChatFailure.Timeout, c.state.value.messages.last().failure)
        assertTrue("took ${tookMs}ms", tookMs < 3_000)
    }

    @Test
    fun `stop keeps what arrived and ignores anything that arrives afterwards`() {
        val gate = Channel<String>(Channel.UNLIMITED)
        client.handler = { _, _ -> gate.receiveAsFlow() }
        val c = configured()
        c.send("q", null)
        gate.trySend("Hel")
        c.awaitText { it == "Hel" }
        assertEquals(AssistantPhase.STREAMING, c.state.value.phase)

        c.stop()
        gate.trySend("lo, late")
        runBlocking { delay(150) }

        val last = c.state.value.messages.last()
        assertEquals("Hel", last.text)
        assertFalse(last.streaming)
        assertNull(last.failure)
        assertEquals(AssistantPhase.IDLE, c.state.value.phase)
    }

    @Test
    fun `only one request runs at a time`() {
        val gate = Channel<String>(Channel.UNLIMITED)
        client.handler = { _, _ -> gate.receiveAsFlow() }
        val c = configured()
        c.send("first", null)
        c.send("second", null) // ignored: the first is still running
        runBlocking { delay(100) }
        assertEquals(listOf("first"), c.state.value.messages.map { it.text })
        c.stop()
    }

    @Test
    fun `a stopped request cannot overwrite the next one`() {
        val firstGate = Channel<String>(Channel.UNLIMITED)
        var call = 0
        client.handler = { _, _ -> if (call++ == 0) firstGate.receiveAsFlow() else flow { emit("second answer") } }
        val c = configured()
        c.send("one", null)
        runBlocking { delay(50) }
        c.stop()
        c.send("two", null)
        firstGate.trySend("stale text from the cancelled request")
        c.awaitIdle()

        val texts = c.state.value.messages.map { it.text }
        assertEquals(listOf("one", "two", "second answer"), texts)
    }

    @Test
    fun `clear empties the conversation`() {
        val c = configured()
        c.send("q", null)
        c.awaitIdle()
        assertTrue(c.state.value.messages.isNotEmpty())
        c.clear()
        assertTrue(c.state.value.messages.isEmpty())
        assertEquals(AssistantPhase.IDLE, c.state.value.phase)
        assertNotNull(c.state.value)
    }

    @Test
    fun `message ids are unique and increasing`() {
        val c = configured()
        c.send("a", null); c.awaitIdle()
        c.send("b", null); c.awaitIdle()
        val ids = c.state.value.messages.map { it.id }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `preview shows the exact brief or the no-data note`() = runBlocking {
        val c = configured()
        assertTrue(c.previewContext("Insights").contains("=== PHONE STATE ==="))
        c.setIncludePhoneData(false)
        assertTrue(c.previewContext("Insights").contains("chosen not to share"))
    }
}
