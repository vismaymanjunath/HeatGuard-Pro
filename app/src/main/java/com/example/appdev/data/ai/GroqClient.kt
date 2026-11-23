package com.example.appdev.data.ai

import com.example.appdev.domain.ai.ApiMessage
import com.example.appdev.domain.ai.ChatException
import com.example.appdev.domain.ai.ChatFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference

/** Streams an answer for a conversation. Emits text fragments; fails with [ChatException]. */
interface ChatClient {
    fun stream(apiKey: String, messages: List<ApiMessage>): Flow<String>
}

/**
 * Talks to Groq's OpenAI-compatible chat completions endpoint over HTTPS using the platform's own
 * HTTP stack (no extra libraries). Every request has a connect timeout and a read timeout, transient
 * failures (rate limits, timeouts, server errors, dropped connections before any text arrived) are
 * retried with a short back-off, and cancelling the collector closes the connection immediately.
 */
class GroqClient(
    private val endpoint: String = ENDPOINT,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxRetries: Int = 2,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : ChatClient {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun stream(apiKey: String, messages: List<ApiMessage>): Flow<String> = callbackFlow {
        val connection = AtomicReference<HttpURLConnection?>(null)
        val worker = launch(Dispatchers.IO) {
            try {
                run(apiKey, messages, connection::set) { send(it) }
                close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChatException) {
                close(e)
            } catch (e: Exception) {
                close(ChatException(mapIoFailure(e), e))
            }
        }
        // Closing the connection is what interrupts a read that is blocked waiting for the network.
        awaitClose {
            connection.get()?.disconnect()
            worker.cancel()
        }
    }

    private suspend fun run(
        apiKey: String,
        messages: List<ApiMessage>,
        onOpen: (HttpURLConnection) -> Unit,
        onDelta: suspend (String) -> Unit,
    ) {
        var lean = false
        var attempt = 0
        while (true) {
            val conn = open(apiKey, GroqRequest.body(messages, lean))
            onOpen(conn)

            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                conn.disconnect()
                if (attempt < maxRetries && e !is UnknownHostException) {
                    attempt++
                    backoff(attempt, null)
                    continue
                }
                throw ChatException(mapIoFailure(e), e)
            }

            if (code in 200..299) {
                readStream(conn, onDelta)
                return
            }

            val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText().take(MAX_ERROR_BODY) } }.getOrNull()
            val retryAfter = conn.getHeaderField("Retry-After")
            conn.disconnect()

            if (!lean && GroqErrors.isReasoningParameterError(code, body)) {
                lean = true // the optional reasoning parameters were refused; try once without them
                continue
            }
            if (GroqErrors.isRetryable(code) && attempt < maxRetries) {
                attempt++
                backoff(attempt, retryAfter)
                continue
            }
            throw ChatException(GroqErrors.classify(code, body, retryAfter))
        }
    }

    private suspend fun readStream(conn: HttpURLConnection, onDelta: suspend (String) -> Unit) {
        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
            var emitted = false
            while (true) {
                val line = reader.readLine() ?: break
                when (val event = SseParser.parse(line)) {
                    is SseEvent.Delta -> {
                        emitted = true
                        onDelta(event.text)
                    }
                    SseEvent.Done -> {
                        if (!emitted) throw ChatException(ChatFailure.EmptyReply)
                        return
                    }
                    is SseEvent.Error -> throw ChatException(ChatFailure.Rejected(event.message))
                    SseEvent.Ignore -> Unit
                }
            }
            if (!emitted) throw ChatException(ChatFailure.EmptyReply)
        }
    }

    private fun open(apiKey: String, body: String): HttpURLConnection {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "text/event-stream")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return conn
    }

    private suspend fun backoff(attempt: Int, retryAfterHeader: String?) {
        val fromServer = retryAfterHeader?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong() }
        val wait = (fromServer ?: (BASE_BACKOFF_MS shl (attempt - 1))).coerceIn(MIN_BACKOFF_MS, MAX_BACKOFF_MS)
        pause(wait)
    }

    private fun mapIoFailure(e: Exception): ChatFailure = when (e) {
        is SocketTimeoutException -> ChatFailure.Timeout
        is UnknownHostException, is ConnectException, is NoRouteToHostException -> ChatFailure.Offline
        is IOException -> ChatFailure.Offline // a dropped connection mid-answer is a network problem
        else -> ChatFailure.Unknown
    }

    companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MIN_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 8_000L
        private const val MAX_ERROR_BODY = 4_000
    }
}
