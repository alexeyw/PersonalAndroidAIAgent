package ai.agent.android.data.tools.local

import ai.agent.android.data.engine.KoogClientFactory
import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.repositories.MemoryRepository
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DelegateTaskTool].
 */
class DelegateTaskToolTest {

    private lateinit var koogClientFactory: KoogClientFactory
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var textEmbeddingEngine: TextEmbeddingEngine
    private lateinit var delegateTaskTool: DelegateTaskTool
    private lateinit var mockClient: LLMClient

    @Before
    fun setup() {
        koogClientFactory = mockk()
        memoryRepository = mockk(relaxed = true)
        textEmbeddingEngine = mockk()
        mockClient = mockk(relaxed = true)
        
        delegateTaskTool = DelegateTaskTool(
            koogClientFactory = koogClientFactory,
            memoryRepository = memoryRepository,
            textEmbeddingEngine = textEmbeddingEngine
        )
    }

    @Test
    fun `executeDelegation returns success and saves to memory when client completes successfully`() {
        runTest {
            val targetModel = "anthropic"
            val taskDescription = "Write a hello world app"
            val mockResponseText = "Here is your hello world app"
            val mockEmbedding = floatArrayOf(0.1f, 0.2f, 0.3f)
            
            val mockMessageResponse = mockk<Message.Response>()
            every { mockMessageResponse.content } returns mockResponseText
            
            coEvery { koogClientFactory.createAnthropicExecutor() } returns mockClient
            coEvery { mockClient.models() } returns emptyList()
            every { mockClient.llmProvider() } returns mockk(relaxed = true)
            coEvery { mockClient.execute(any<Prompt>(), any<LLModel>()) } returns listOf(mockMessageResponse)
            
            coEvery { textEmbeddingEngine.generateEmbedding(mockResponseText) } returns mockEmbedding

            val result = delegateTaskTool.executeDelegation(taskDescription, targetModel)
            
            assertTrue(result.startsWith("Success: Task completed"))
            coVerify(exactly = 1) { memoryRepository.saveMemory(mockResponseText, mockEmbedding) }
        }
    }

    @Test
    fun `executeDelegation returns error when target model is unsupported`() = runTest {
        val result = delegateTaskTool.executeDelegation("Task", "unknown_model")
        assertTrue(result.startsWith("Error: Unsupported target model"))
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any()) }
    }

    @Test
    fun `executeDelegation returns error when client cannot be initialized`() = runTest {
        coEvery { koogClientFactory.createAnthropicExecutor() } returns null
        val result = delegateTaskTool.executeDelegation("Task", "anthropic")
        assertTrue(result.startsWith("Error: Client for"))
        coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any()) }
    }

    @Test
    fun `executeDelegation returns error when client throws exception`() {
        runTest {
            coEvery { koogClientFactory.createAnthropicExecutor() } returns mockClient
            coEvery { mockClient.models() } returns emptyList()
            every { mockClient.llmProvider() } returns mockk(relaxed = true)
            coEvery { mockClient.execute(any<Prompt>(), any<LLModel>()) } throws RuntimeException("Network error")

            val result = delegateTaskTool.executeDelegation("Task", "anthropic")
            
            assertTrue(result.startsWith("Error: Task delegation failed"))
            coVerify(exactly = 0) { memoryRepository.saveMemory(any(), any()) }
        }
    }
}
