package com.example.appdev.data

import com.example.appdev.data.ai.GroqClient
import com.example.appdev.data.ai.GroqErrors
import com.example.appdev.data.ai.GroqRequest
import com.example.appdev.data.ai.SseEvent
import com.example.appdev.data.ai.SseParser
import com.example.appdev.domain.ai.ApiMessage
import com.example.appdev.domain.ai.ChatException
import com.example.appdev.domain.ai.ChatFailure
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class GroqClientTest {

    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val hits = AtomicInteger()

    /** Decides how the fake Groq endpoint answers the n-th request (0-based). */
    @Volatile private var script: (Int, HttpExchange, String) -> Unit = { _, ex, _ -> reply(ex, 200, sse("Hi")) }

    data class RecordedRequest(val auth: String?, val accept: String?, val body: String)

    private val messages = listOf(ApiMessage("system", "be brief"), ApiMessage("user", "hello"))

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/openai/v1/chat/completions") { ex ->
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            requests += RecordedRequest(ex.requestHeaders.getFirst("Authorization"), ex.requestHeaders.getFirst("Accept"), body)
            script(hits.getAndIncrement(), ex, body)
        }
        server.start()
    }

    @After
    fun stop() {
        server.stop(0)
    }

    private fun client(retries: Int = 2, readTimeoutMs: Int = 2_000, pauses: MutableList<Long>? = null) = GroqClient(
        endpoint = "http://127.0.0.1:${server.address.port}/openai/v1/chat/completions",
        connectTimeoutMs = 2_000,
        readTimeoutMs = readTimeoutMs,
        maxRetries = retries,
        pause = { pauses?.add(it) },
    )

    private fun reply(ex: HttpExchange, code: Int, body: String, headers: Map<String, String> = emptyMap()) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun chunk(text: String) =
        """data: {"choices":[{"index":0,"delta":{"content":${JSONObject.quote(text)}},"finish_reason":null}]}""" + "\n\n"

    private fun sse(vararg parts: String) =
        """data: {"choices":[{"index":0,"delta":{"role":"assistant","content":""}}]}""" + "\n\n" +
            parts.joinToString("") { chunk(it) } +
            """data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""" + "\n\n" +
            "data: [DONE]\n\n"

    private fun errorBody(message: String, code: String = "x") = """{"error":{"message":${JSONObject.quote(message)},"type":"t","code":"$code"}}"""

    private fun collect(c: GroqClient = client()): List<String> = runBlocking {
        withTimeout(15_000) { c.stream("gsk_test_key", messages).toList() }
    }

    private fun failureOf(c: GroqClient = client()): ChatFailure = try {
        collect(c)
        throw AssertionError("expected a ChatException")
    } catch (e: ChatException) {
        e.failure
    }

    // ---------------------------------------------------------------- streaming

    @Test
    fun `streams text fragments in order and sends auth, accept and the model`() {
        script = { _, ex, _ -> reply(ex, 200, sse("Hello", ", ", "world")) }
        assertEquals(listOf("Hello", ", ", "world"), collect())

        val sent = requests.single()
        assertEquals("Bearer gsk_test_key", sent.auth)
        assertEquals("text/event-stream", sent.accept)
        val json = JSONObject(sent.body)
        assertEquals("openai/gpt-oss-120b", json.getString("model"))
        assertTrue(json.getBoolean("stream"))
        assertEquals("low", json.getString("reasoning_effort"))
        assertFalse(json.getBoolean("include_reasoning"))
        assertEquals(2, json.getJSONArray("messages").length())
        assertEquals("hello", json.getJSONArray("messages").getJSONObject(1).getString("content"))
    }

    @Test
    fun `unicode and quotes survive the round trip`() {
        script = { _, ex, _ -> reply(ex, 200, sse("38.4 °C — \"warm\" 🔥")) }
        assertEquals(listOf("38.4 °C — \"warm\" 🔥"), collect())
    }

    @Test
    fun `an answer that ends without a done marker still counts if text arrived`() {
        script = { _, ex, _ -> reply(ex, 200, chunk("partial but complete")) }
        assertEquals(listOf("partial but complete"), collect())
    }

    @Test
    fun `an empty answer is reported as such`() {
        script = { _, ex, _ -> reply(ex, 200, "data: [DONE]\n\n") }
        assertEquals(ChatFailure.EmptyReply, failureOf())
    }

    // ---------------------------------------------------------------- errors

    @Test
    fun `a rejected key is reported without retrying`() {
        script = { _, ex, _ -> reply(ex, 401, errorBody("Invalid API Key", "invalid_api_key")) }
        assertEquals(ChatFailure.InvalidKey, failureOf())
        assertEquals(1, hits.get())
    }

    @Test
    fun `rate limiting is retried using the server's retry-after and then succeeds`() {
        val pauses = mutableListOf<Long>()
        script = { n, ex, _ ->
            if (n == 0) reply(ex, 429, errorBody("slow down"), mapOf("Retry-After" to "3")) else reply(ex, 200, sse("ok"))
        }
        assertEquals(listOf("ok"), collect(client(pauses = pauses)))
        assertEquals(2, hits.get())
        assertEquals(listOf(3_000L), pauses)
    }

    @Test
    fun `persistent rate limiting gives up with the wait time`() {
        script = { _, ex, _ -> reply(ex, 429, errorBody("slow down"), mapOf("Retry-After" to "12")) }
        val failure = failureOf(client(retries = 2))
        assertEquals(ChatFailure.RateLimited(12), failure)
        assertEquals(3, hits.get()) // first try plus two retries
    }

    @Test
    fun `server errors are retried with growing back-off`() {
        val pauses = mutableListOf<Long>()
        script = { n, ex, _ -> if (n < 2) reply(ex, 503, "unavailable") else reply(ex, 200, sse("up")) }
        assertEquals(listOf("up"), collect(client(pauses = pauses)))
        assertEquals(listOf(1_000L, 2_000L), pauses)
    }

    @Test
    fun `exhausted server errors surface as a server failure`() {
        script = { _, ex, _ -> reply(ex, 500, "boom") }
        assertEquals(ChatFailure.Server(500), failureOf(client(retries = 1)))
        assertEquals(2, hits.get())
    }

    @Test
    fun `a bad request is not retried and carries the provider's message`() {
        script = { _, ex, _ -> reply(ex, 400, errorBody("messages too long")) }
        assertEquals(ChatFailure.Rejected("messages too long"), failureOf())
        assertEquals(1, hits.get())
    }

    @Test
    fun `if the reasoning options are refused it retries once without them`() {
        script = { n, ex, body ->
            if (n == 0) reply(ex, 400, errorBody("reasoning_effort is not supported")) else reply(ex, 200, sse("fine"))
        }
        assertEquals(listOf("fine"), collect())
        assertEquals(2, requests.size)
        assertTrue(JSONObject(requests[0].body).has("reasoning_effort"))
        assertFalse(JSONObject(requests[1].body).has("reasoning_effort"))
        assertFalse(JSONObject(requests[1].body).has("include_reasoning"))
    }

    @Test
    fun `an error event inside the stream becomes a failure`() {
        script = { _, ex, _ -> reply(ex, 200, """data: {"error":{"message":"model overloaded"}}""" + "\n\n") }
        assertEquals(ChatFailure.Rejected("model overloaded"), failureOf())
    }

    @Test
    fun `no server at all is reported as offline`() {
        val dead = GroqClient(endpoint = "http://127.0.0.1:1/x", connectTimeoutMs = 1_000, maxRetries = 0)
        assertEquals(ChatFailure.Offline, failureOf(dead))
    }

    @Test
    fun `a server that stalls triggers a timeout instead of hanging`() {
        script = { _, ex, _ -> Thread.sleep(1_500); runCatching { reply(ex, 200, sse("late")) } }
        val failure = failureOf(client(retries = 0, readTimeoutMs = 300))
        assertEquals(ChatFailure.Timeout, failure)
    }

    // ---------------------------------------------------------------- protocol helpers

    @Test
    fun `sse parser handles every kind of line`() {
        assertEquals(SseEvent.Done, SseParser.parse("data: [DONE]"))
        assertEquals(SseEvent.Ignore, SseParser.parse(""))
        assertEquals(SseEvent.Ignore, SseParser.parse(": keep-alive"))
        assertEquals(SseEvent.Ignore, SseParser.parse("event: ping"))
        assertEquals(SseEvent.Ignore, SseParser.parse("data: not json"))
        assertEquals(SseEvent.Delta("Hi"), SseParser.parse("""data: {"choices":[{"delta":{"content":"Hi"}}]}"""))
        assertEquals(SseEvent.Ignore, SseParser.parse("""data: {"choices":[{"delta":{"content":""}}]}"""))
        assertEquals(SseEvent.Ignore, SseParser.parse("""data: {"choices":[{"delta":{"content":null}}]}"""))
        assertEquals(SseEvent.Ignore, SseParser.parse("""data: {"choices":[{"delta":{"reasoning":"thinking"}}]}"""))
        assertEquals(SseEvent.Ignore, SseParser.parse("""data: {"choices":[]}"""))
        assertEquals(SseEvent.Error("nope"), SseParser.parse("""data: {"error":{"message":"nope"}}"""))
    }

    @Test
    fun `request body is lean when asked`() {
        val full = JSONObject(GroqRequest.body(messages))
        val lean = JSONObject(GroqRequest.body(messages, lean = true))
        assertTrue(full.has("reasoning_effort"))
        assertFalse(lean.has("reasoning_effort"))
        assertEquals(full.getInt("max_completion_tokens"), lean.getInt("max_completion_tokens"))
    }

    @Test
    fun `error classification covers the status codes that matter`() {
        assertEquals(ChatFailure.InvalidKey, GroqErrors.classify(401, null, null))
        assertEquals(ChatFailure.InvalidKey, GroqErrors.classify(403, null, null))
        assertEquals(ChatFailure.RateLimited(7), GroqErrors.classify(429, null, "7"))
        assertEquals(ChatFailure.RateLimited(null), GroqErrors.classify(429, null, null))
        assertEquals(ChatFailure.Server(502), GroqErrors.classify(502, null, null))
        assertEquals(ChatFailure.Rejected("HTTP 404"), GroqErrors.classify(404, "", null))
        assertEquals("bad", GroqErrors.extractMessage(errorBody("bad")))
        assertNull(GroqErrors.extractMessage("<html>"))
        assertTrue(GroqErrors.isRetryable(429))
        assertFalse(GroqErrors.isRetryable(401))
        assertFalse(GroqErrors.isRetryable(400))
    }
}
