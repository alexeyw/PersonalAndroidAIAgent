package app.knotwork.android.domain.engine.executors

import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import app.knotwork.android.domain.engine.CloudLlmClientFactory
import app.knotwork.android.domain.engine.CloudLlmModelResolver
import app.knotwork.android.domain.models.CloudProvider
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.repositories.ApiKeyRepository
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.MetricsRepository
import app.knotwork.android.domain.repositories.NetworkActivityTracker
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudLlmNodeExecutorTest {

    private lateinit var toolRepository: ToolRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var clientFactory: CloudLlmClientFactory
    private lateinit var modelResolver: CloudLlmModelResolver
    private lateinit var networkActivityTracker: NetworkActivityTracker
    private lateinit var executor: CloudLlmNodeExecutor

    @Before
    fun setup() {
        toolRepository = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        settingsRepository = mockk()
        apiKeyRepository = mockk(relaxed = true)
        metricsRepository = mockk(relaxed = true)
        clientFactory = mockk()
        modelResolver = mockk()
        networkActivityTracker = mockk(relaxed = true)

        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        every { apiKeyRepository.getAnthropicKey() } returns flowOf("anthropic-key")
        every { apiKeyRepository.getOpenAIKey() } returns flowOf(null)
        every { apiKeyRepository.getGoogleKey() } returns flowOf(null)
        every { apiKeyRepository.getDeepSeekKey() } returns flowOf(null)
        every { apiKeyRepository.getAnthropicModel() } returns flowOf("claude-sonnet-4-5")

        executor = CloudLlmNodeExecutor(
            toolRepository,
            chatRepository,
            settingsRepository,
            apiKeyRepository,
            metricsRepository,
            clientFactory,
            modelResolver,
            networkActivityTracker,
        )
    }

    @Test
    fun `execute forwards inputText verbatim without refetching context`() = runTest {
        // Defect 1 regression guard for the cloud path: NodeContextBuilder is the single
        // source of truth, so the executor must consume `inputText` as-is.
        val node = NodeModel("1", NodeType.CLOUD, 0f, 0f, cloudProvider = "anthropic", systemPrompt = "Sys")
        val client: LLMClient = mockk(relaxed = true)
        val capturedPrompt = slot<Prompt>()
        coEvery { client.executeStreaming(capture(capturedPrompt), any<LLModel>()) } returns
            flowOf(StreamFrame.TextDelta("ok"))
        coEvery { clientFactory.createClient(CloudProvider.ANTHROPIC) } returns client
        coEvery { modelResolver.resolveModel(CloudProvider.ANTHROPIC) } returns AnthropicModels.Sonnet_4_5

        val assembledContext = "--- Original Task ---\nQ\n\n--- Previous Node Output ---\nU"
        executor.execute(node, assembledContext, "s1", "Q").toList()

        val text = capturedPrompt.captured.messages.joinToString("\n") { it.textContent() }
        assertTrue(text.contains(assembledContext))
        assertFalse(text.contains("RELEVANT LONG-TERM MEMORIES:"))
    }

    @Test
    fun `execute records tokenCount equal to number of stream deltas`() = runTest {
        // Defect 7: each StreamFrame.TextDelta is one cloud-side token; counting by
        // `+= 1` keeps the metric symmetric with the local LiteRT executor and avoids
        // length-based inflation.
        val node = NodeModel("1", NodeType.CLOUD, 0f, 0f, cloudProvider = "anthropic")
        val client: LLMClient = mockk(relaxed = true)
        coEvery { client.executeStreaming(any(), any<LLModel>()) } returns flowOf(
            StreamFrame.TextDelta("a"),
            StreamFrame.TextDelta("bbb"),
            StreamFrame.TextDelta("cc"),
        )
        coEvery { clientFactory.createClient(CloudProvider.ANTHROPIC) } returns client
        coEvery { modelResolver.resolveModel(CloudProvider.ANTHROPIC) } returns AnthropicModels.Sonnet_4_5

        val outputs = executor.execute(node, "input", "s1", "Q").toList()
        val result = outputs.filterIsInstance<NodeOutput.Result>().single().result
        assertEquals(3, result.tokenCount)
    }

    @Test
    fun `execute returns error result when client factory returns null`() = runTest {
        val node = NodeModel("1", NodeType.CLOUD, 0f, 0f, cloudProvider = "anthropic")
        coEvery { clientFactory.createClient(CloudProvider.ANTHROPIC) } returns null

        val outputs = executor.execute(node, "input", "s1", "Q").toList()

        // No exception is thrown — the executor surfaces a Result with the configured
        // outputText so the engine can attribute the failure to this node and continue.
        val result = outputs.filterIsInstance<NodeOutput.Result>().single().result
        assertTrue(result.outputText!!.contains("not configured"))
    }

    @Test
    fun `execute uses domain-level CloudLlmClientFactory not data-layer types`() = runTest {
        // Defect 3 regression guard: this test compiles and runs against the domain
        // interfaces only. If CloudLlmNodeExecutor regressed and started importing
        // KoogClientFactory / KoogModelMapper directly, this test setup would no longer
        // be sufficient to drive it (it would still pass at runtime but the architectural
        // intent would be lost — see the package-level guard test for compile-time enforcement).
        val node = NodeModel("1", NodeType.CLOUD, 0f, 0f, cloudProvider = "anthropic")
        val client: LLMClient = mockk(relaxed = true)
        coEvery { client.executeStreaming(any(), any<LLModel>()) } returns flowOf(StreamFrame.TextDelta("hi"))
        coEvery { clientFactory.createClient(CloudProvider.ANTHROPIC) } returns client
        coEvery { modelResolver.resolveModel(CloudProvider.ANTHROPIC) } returns AnthropicModels.Sonnet_4_5

        val outputs = executor.execute(node, "input", "s1", "Q").toList()
        val result = outputs.filterIsInstance<NodeOutput.Result>().single().result
        assertEquals("hi", result.outputText)
    }
}
