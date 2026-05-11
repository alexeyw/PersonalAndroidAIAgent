package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Tests for [EvaluateIfConditionUseCase].
 */
class EvaluateIfConditionUseCaseTest {

    private lateinit var llmInferenceEngine: LlmInferenceEngine
    private lateinit var evaluateIfConditionUseCase: EvaluateIfConditionUseCase

    @Before
    fun setup() {
        llmInferenceEngine = mockk()
        evaluateIfConditionUseCase = EvaluateIfConditionUseCase(llmInferenceEngine)
    }

    @Test
    fun `invoke returns true when keywords match`() = runTest {
        val node = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.IF_CONDITION,
            x = 0f,
            y = 0f,
            conditionKeywords = "urgent, important",
        )
        val inputText = "This is an URGENT message."

        val result = evaluateIfConditionUseCase(node, inputText)

        assertTrue(result)
    }

    @Test
    fun `invoke returns false when keywords do not match`() = runTest {
        val node = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.IF_CONDITION,
            x = 0f,
            y = 0f,
            conditionKeywords = "urgent, important",
        )
        val inputText = "This is a normal message."

        val result = evaluateIfConditionUseCase(node, inputText)

        assertFalse(result)
    }

    @Test
    fun `invoke returns true when text length exceeds complexity threshold`() = runTest {
        val node = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.IF_CONDITION,
            x = 0f,
            y = 0f,
            conditionComplexity = 10,
        )
        val inputText = "This message is definitely longer than 10 characters."

        val result = evaluateIfConditionUseCase(node, inputText)

        assertTrue(result)
    }

    @Test
    fun `invoke returns false when text length is below complexity threshold`() = runTest {
        val node = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.IF_CONDITION,
            x = 0f,
            y = 0f,
            conditionComplexity = 50,
        )
        val inputText = "Short message."

        val result = evaluateIfConditionUseCase(node, inputText)

        assertFalse(result)
    }

    @Test
    fun `invoke returns true when LLM returns true`() = runTest {
        val node = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.IF_CONDITION,
            x = 0f,
            y = 0f,
            conditionPrompt = "Is this text asking a question?",
        )
        val inputText = "How are you?"

        every { llmInferenceEngine.generateResponseStream(any()) } returns flowOf("t", "r", "u", "e")

        val result = evaluateIfConditionUseCase(node, inputText)

        assertTrue(result)
    }

    @Test
    fun `invoke returns false when LLM returns false`() = runTest {
        val node = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.IF_CONDITION,
            x = 0f,
            y = 0f,
            conditionPrompt = "Is this text asking a question?",
        )
        val inputText = "I am fine."

        every { llmInferenceEngine.generateResponseStream(any()) } returns flowOf("f", "a", "l", "s", "e")

        val result = evaluateIfConditionUseCase(node, inputText)

        assertFalse(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invoke throws exception for wrong node type`() = runTest {
        val node = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.TOOL,
            x = 0f,
            y = 0f,
        )
        val inputText = "Text"

        evaluateIfConditionUseCase(node, inputText)
    }

    @Test
    fun `invoke returns false when input text is blank`() = runTest {
        val node = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.IF_CONDITION,
            x = 0f,
            y = 0f,
            conditionKeywords = "urgent",
        )

        val result = evaluateIfConditionUseCase(node, "   ")

        assertFalse(result)
    }

    @Test
    fun `invoke returns false when no conditions are set`() = runTest {
        val node = NodeModel(
            id = UUID.randomUUID().toString(),
            type = NodeType.IF_CONDITION,
            x = 0f,
            y = 0f,
        )

        val result = evaluateIfConditionUseCase(node, "Some text")

        assertFalse(result)
    }
}
