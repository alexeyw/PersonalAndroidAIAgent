package app.knotwork.android.data.engine

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * TEMPORARY diagnostic harness for phase 40 task 2/9 (`project_docs/external-scenarios/cloud.md`,
 * rig half (a)). NOT a regression test — deleted before the PR; regression tests for whatever
 * this finds are written separately.
 *
 * Measures the *effective* deadline of a cloud streaming call on the real SSE branch, against a
 * local stub that accepts the request and never answers. Both providers under test resolve their
 * stream through `KtorKoogHttpClient.sse(...)`, and the engine on this classpath is
 * `ktor-client-okhttp` — the same pair that runs on the device.
 */
class CloudDeadlineDiagnosticTest {

    private companion object {
        const val LOG_PATH =
            "/private/tmp/claude-501/-Users-alekseivolodin-ClaudeCodeProjects-android-ai-agent-src/" +
                "1d480c7a-d96f-46ec-9abd-bfa0cb4c2d77/scratchpad/cloud-harness.log"
    }

    /**
     * Appends to a file as well as stdout: Gradle wipes `test-results/` on the next
     * filtered run, and these measurements are too slow to want to repeat.
     */
    private fun record(line: String) {
        println(line)
        java.io.File(LOG_PATH).appendText("$line\n")
    }

    /** Accepts connections, reads the request, and never writes a byte back. */
    private class StallServer : Closeable {
        private val server = ServerSocket(0)
        private val live = mutableListOf<Socket>()
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                runCatching {
                    while (!server.isClosed) {
                        val socket = server.accept()
                        synchronized(live) { live += socket }
                        thread(isDaemon = true) {
                            // Drain the request so the client believes it was delivered, then
                            // hold the connection open without responding.
                            runCatching { socket.getInputStream().read(ByteArray(BUFFER)) }
                        }
                    }
                }
            }
        }

        override fun close() {
            synchronized(live) { live.forEach { runCatching { it.close() } } }
            server.close()
        }

        private companion object {
            const val BUFFER = 8192
        }
    }

    /**
     * Runs one streaming call against the stall server and reports how long it took to fail.
     *
     * @return elapsed millis when the call terminated, or `null` when it outlived [boundMs].
     */
    private fun measure(boundMs: Long, block: suspend () -> Unit): Long? = runBlocking {
        val started = System.currentTimeMillis()
        val finished = withTimeoutOrNull(boundMs) {
            val outcome = runCatching { block() }
            System.currentTimeMillis() - started to outcome.exceptionOrNull()
        }
        finished?.let { (elapsed, error) ->
            record("[cloud-deadline] terminated after ${elapsed}ms: ${error?.let { it::class.simpleName + ": " + it.message } ?: "no error"}")
            elapsed
        } ?: run {
            record("[cloud-deadline] still alive after ${boundMs}ms — no deadline fired inside the bound")
            null
        }
    }

    /**
     * E1 — discriminating experiment. A deliberately small [ConnectionTimeoutConfig] tells us
     * *which* layer owns the deadline on the SSE path:
     *  - terminates near 3 s  → Koog's `HttpTimeout` applies (unlike the MCP SSE path);
     *  - terminates near 10 s → OkHttp's default read timeout won, `HttpTimeout` is ineffective;
     *  - never terminates     → neither applies.
     */
    @Test
    fun `E1 deepseek with a 3s socket timeout`() {
        StallServer().use { stub ->
            val client = DeepSeekLLMClient(
                apiKey = "diagnostic-key",
                settings = DeepSeekClientSettings(
                    baseUrl = "http://127.0.0.1:${stub.port}",
                    timeoutConfig = ConnectionTimeoutConfig(
                        requestTimeoutMillis = 5_000,
                        connectTimeoutMillis = 3_000,
                        socketTimeoutMillis = 3_000,
                    ),
                ),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
            measure(boundMs = 60_000) {
                client.executeStreaming(prompt("d") { user("ping") }, DeepSeekModels.DeepSeekV4Flash)
                    .collect { }
            }
        }
    }

    /** E1 for the second provider — Google builds its SSE call through a different client class. */
    @Test
    fun `E1 google with a 3s socket timeout`() {
        StallServer().use { stub ->
            val client = GoogleLLMClient(
                apiKey = "diagnostic-key",
                settings = GoogleClientSettings(
                    baseUrl = "http://127.0.0.1:${stub.port}",
                    timeoutConfig = ConnectionTimeoutConfig(
                        requestTimeoutMillis = 5_000,
                        connectTimeoutMillis = 3_000,
                        socketTimeoutMillis = 3_000,
                    ),
                ),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
            measure(boundMs = 60_000) {
                client.executeStreaming(prompt("d") { user("ping") }, GoogleModels.Gemini3_Flash_Preview)
                    .collect { }
            }
        }
    }

    /**
     * E2 — the shipped configuration. `KoogClientFactory` never passes a
     * [ConnectionTimeoutConfig], so this is exactly what a user's stalled provider gets.
     * Bounded at 120 s: the question is whether anything short cuts first, not whether
     * 900 s eventually elapses.
     */
    @org.junit.Ignore("Measured: no deadline fires within 120s under the shipped defaults (2026-08-03)")
    @Test
    fun `E2 deepseek with the shipped defaults`() {
        StallServer().use { stub ->
            val client = DeepSeekLLMClient(
                apiKey = "diagnostic-key",
                settings = DeepSeekClientSettings(baseUrl = "http://127.0.0.1:${stub.port}"),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
            measure(boundMs = 120_000) {
                client.executeStreaming(prompt("d") { user("ping") }, DeepSeekModels.DeepSeekV4Flash)
                    .collect { }
            }
        }
    }

    /**
     * E3 — the same shipped configuration, bounded past the documented 900 s so the upper
     * bound is a measurement rather than an inference. Long by construction.
     */
    @org.junit.Ignore("Measured: terminates at 900033ms — Koog's 900s default (2026-08-03)")
    @Test
    fun `E3 deepseek shipped defaults probed past 900s`() {
        StallServer().use { stub ->
            val client = DeepSeekLLMClient(
                apiKey = "diagnostic-key",
                settings = DeepSeekClientSettings(baseUrl = "http://127.0.0.1:${stub.port}"),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
            measure(boundMs = 960_000) {
                client.executeStreaming(prompt("d") { user("ping") }, DeepSeekModels.DeepSeekV4Flash)
                    .collect { }
            }
        }
    }

    /** Serves one canned HTTP response, then closes. Used for the error-shape experiments. */
    private class CannedServer(private val response: String) : Closeable {
        private val server = ServerSocket(0)
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                runCatching {
                    while (!server.isClosed) {
                        val socket = server.accept()
                        thread(isDaemon = true) {
                            runCatching {
                                socket.getInputStream().read(ByteArray(BUFFER))
                                socket.getOutputStream().apply {
                                    write(response.toByteArray())
                                    flush()
                                }
                                socket.close()
                            }
                        }
                    }
                }
            }
        }

        override fun close() = server.close()

        private companion object {
            const val BUFFER = 8192
        }
    }

    /**
     * E4 — what a rejected key actually looks like to our error mapping, and whether the
     * message accidentally matches one of Koog's retryable patterns (it must not: retrying
     * an auth failure burns the budget for nothing).
     */
    @Test
    fun `E4 deepseek 401 error shape`() {
        val body = """{"error":{"message":"Authentication Fails, Your api key is invalid","type":"authentication_error"}}"""
        val canned = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
        CannedServer(canned).use { stub ->
            val client = DeepSeekLLMClient(
                apiKey = "diagnostic-key",
                settings = DeepSeekClientSettings(baseUrl = "http://127.0.0.1:${stub.port}"),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
            runBlocking {
                val error = runCatching {
                    client.executeStreaming(prompt("d") { user("ping") }, DeepSeekModels.DeepSeekV4Flash)
                        .collect { }
                }.exceptionOrNull()
                record("[cloud-401] ${error?.let { it::class.simpleName }}: ${error?.message}")
                record("[cloud-401] retryable by Koog defaults: ${matchesDefaultRetryPatterns(error?.message.orEmpty())}")
                record("[cloud-401] leaks the key: ${error?.message?.contains("diagnostic-key") == true}")
            }
        }
    }

    /**
     * E5 — a stream cut after real frames. Establishes whether the caller sees an error or a
     * silently-short answer; the executor's atomicity promise rests on the former.
     */
    @Test
    fun `E5 deepseek stream cut after two frames`() {
        // `system_fingerprint` is non-nullable and has no default in Koog's DeepSeek stream
        // model; omitting it makes the client fail deserialization, which would measure the
        // wrong thing entirely (the first attempt at this experiment did exactly that).
        val frames = listOf(
            """{"id":"1","object":"chat.completion.chunk","created":1,"model":"deepseek-chat","system_fingerprint":"fp_1","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}""",
            """{"id":"1","object":"chat.completion.chunk","created":1,"model":"deepseek-chat","system_fingerprint":"fp_1","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}""",
        )
        val canned = buildString {
            append("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: keep-alive\r\n\r\n")
            frames.forEach { append("data: $it\n\n") }
            // No [DONE] terminator and no graceful close: the socket just dies.
        }
        CannedServer(canned).use { stub ->
            val client = DeepSeekLLMClient(
                apiKey = "diagnostic-key",
                settings = DeepSeekClientSettings(baseUrl = "http://127.0.0.1:${stub.port}"),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
            runBlocking {
                val received = mutableListOf<String>()
                val error = runCatching {
                    client.executeStreaming(prompt("d") { user("ping") }, DeepSeekModels.DeepSeekV4Flash)
                        .collect { received += it.toString() }
                }.exceptionOrNull()
                record("[cloud-cut] frames delivered: ${received.size} -> $received")
                record("[cloud-cut] terminal error: ${error?.let { it::class.simpleName + ": " + it.message } ?: "NONE — stream ended cleanly"}")
            }
        }
    }

    /**
     * E6 — the control for E5. Same stub, but a *properly terminated* stream: last chunk
     * carries `finish_reason: "stop"` and the body ends with `data: [DONE]`. If the End
     * frame here differs from E5's, that difference is the discriminator a truncation
     * check could use; if it is identical, truncation is undetectable and the finding
     * changes shape entirely.
     */
    @Test
    fun `E6 deepseek complete stream for comparison`() {
        val chunk = { content: String, finish: String? ->
            """{"id":"1","object":"chat.completion.chunk","created":1,"model":"deepseek-chat",""" +
                """"system_fingerprint":"fp_1","choices":[{"index":0,"delta":{"content":"$content"},""" +
                """"finish_reason":${finish?.let { "\"$it\"" } ?: "null"}}]}"""
        }
        val canned = buildString {
            append("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: keep-alive\r\n\r\n")
            append("data: ${chunk("Hello", null)}\n\n")
            append("data: ${chunk(" world", "stop")}\n\n")
            append("data: [DONE]\n\n")
        }
        CannedServer(canned).use { stub ->
            val client = DeepSeekLLMClient(
                apiKey = "diagnostic-key",
                settings = DeepSeekClientSettings(baseUrl = "http://127.0.0.1:${stub.port}"),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
            runBlocking {
                val received = mutableListOf<String>()
                val error = runCatching {
                    client.executeStreaming(prompt("d") { user("ping") }, DeepSeekModels.DeepSeekV4Flash)
                        .collect { received += it.toString() }
                }.exceptionOrNull()
                record("[cloud-complete] frames delivered: ${received.size} -> $received")
                record("[cloud-complete] terminal error: ${error?.let { it::class.simpleName } ?: "NONE"}")
            }
        }
    }

    /**
     * E7/E8 — the same complete-vs-truncated pair on Google, whose client emits an End
     * frame only when the payload carries a `finishReason`. A truncation rule derived
     * from DeepSeek alone could break Google outright, so both providers are measured
     * before anything is built on the difference.
     */
    private fun googleStream(finishReason: String?, terminate: Boolean): String {
        val candidate = """{"content":{"parts":[{"text":"Hello"}],"role":"model"}""" +
            (finishReason?.let { ""","finishReason":"$it"""" } ?: "") + "}"
        return buildString {
            append("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: keep-alive\r\n\r\n")
            append("""data: {"candidates":[$candidate]}""").append("\n\n")
            if (terminate) append("data: [DONE]\n\n")
        }
    }

    private fun runGoogle(label: String, canned: String) {
        CannedServer(canned).use { stub ->
            val client = GoogleLLMClient(
                apiKey = "diagnostic-key",
                settings = GoogleClientSettings(baseUrl = "http://127.0.0.1:${stub.port}"),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
            runBlocking {
                val received = mutableListOf<String>()
                val error = runCatching {
                    client.executeStreaming(prompt("d") { user("ping") }, GoogleModels.Gemini3_Flash_Preview)
                        .collect { received += it.toString() }
                }.exceptionOrNull()
                record("[$label] frames: ${received.size} -> $received")
                record("[$label] terminal error: ${error?.let { it::class.simpleName } ?: "NONE"}")
            }
        }
    }

    @Test
    fun `E7 google complete stream`() =
        runGoogle("google-complete", googleStream(finishReason = "STOP", terminate = true))

    @Test
    fun `E8 google truncated stream`() =
        runGoogle("google-cut", googleStream(finishReason = null, terminate = false))

    /**
     * E9/E10 — Anthropic, which emits its End frame only on a `message_delta` event.
     * Measured rather than inferred: a truncation rule that is wrong for one provider
     * would fail healthy runs on it.
     */
    private fun anthropicStream(complete: Boolean): String = buildString {
        append("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: keep-alive\r\n\r\n")
        append("""data: {"type":"message_start","message":{"id":"m1","model":"claude","role":"assistant","content":[],"usage":{"input_tokens":1,"output_tokens":1}}}""")
        append("\n\n")
        append("""data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
        append("\n\n")
        append("""data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}""")
        append("\n\n")
        if (complete) {
            append("""data: {"type":"content_block_stop","index":0,"delta":{"type":"text_delta"}}""").append("\n\n")
            append("""data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}""")
            append("\n\n")
            append("""data: {"type":"message_stop"}""").append("\n\n")
        }
    }

    @Test
    fun `E9 anthropic complete stream`() {
        runAnthropic("anthropic-complete", anthropicStream(complete = true))
    }

    @Test
    fun `E10 anthropic truncated stream`() {
        runAnthropic("anthropic-cut", anthropicStream(complete = false))
    }

    private fun runAnthropic(label: String, canned: String) {
        CannedServer(canned).use { stub ->
            val client = ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient(
                apiKey = "diagnostic-key",
                settings = ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings(
                    baseUrl = "http://127.0.0.1:${stub.port}",
                ),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
            runBlocking {
                val received = mutableListOf<String>()
                val error = runCatching {
                    client.executeStreaming(
                        prompt("d") { user("ping") },
                        ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_4_5,
                    ).collect { received += it.toString() }
                }.exceptionOrNull()
                record("[$label] frames: ${received.size} -> $received")
                record("[$label] terminal error: ${error?.let { it::class.simpleName } ?: "NONE"}")
            }
        }
    }

    /** Mirrors `RetryConfig.DEFAULT_PATTERNS` matching so the 401 verdict is not guesswork. */
    private fun matchesDefaultRetryPatterns(message: String): Boolean =
        ai.koog.prompt.executor.clients.retry.RetryConfig.DEFAULT_PATTERNS.any { it.matches(message) }
}
