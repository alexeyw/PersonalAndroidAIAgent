package ai.agent.android.data.engine

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel

/**
 * Maps string identifiers from the UI to Koog LLModel constants.
 * It is prohibited to instantiate LLModel for predefined cloud providers.
 */
object KoogModelMapper {
    fun getOpenAIModel(name: String): LLModel = when (name) {
        "gpt-4o" -> OpenAIModels.Chat.GPT4o
        "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
        "gpt-4.1" -> OpenAIModels.Chat.GPT4_1
        "o1" -> OpenAIModels.Chat.O1
        "o3-mini" -> OpenAIModels.Chat.O3Mini
        "gpt-5" -> OpenAIModels.Chat.GPT5
        else -> OpenAIModels.Chat.GPT4o
    }

    fun getAnthropicModel(name: String): LLModel = when (name) {
        "claude-3-haiku" -> AnthropicModels.Haiku_3
        "claude-haiku-4-5" -> AnthropicModels.Haiku_4_5
        "claude-sonnet-4-5" -> AnthropicModels.Sonnet_4_5
        "claude-opus-4-6" -> AnthropicModels.Opus_4_6
        else -> AnthropicModels.Sonnet_4_5
    }

    fun getGoogleModel(name: String): LLModel = when (name) {
        "gemini-2.0-flash" -> GoogleModels.Gemini2_0Flash
        "gemini-2.5-pro" -> GoogleModels.Gemini2_5Pro
        "gemini-3-flash-preview" -> GoogleModels.Gemini3_Flash_Preview
        "gemini-3-pro-preview" -> GoogleModels.Gemini3_Pro_Preview
        else -> GoogleModels.Gemini3_Flash_Preview
    }

    fun getDeepSeekModel(name: String): LLModel = when (name) {
        "deepseek-chat" -> DeepSeekModels.DeepSeekChat
        "deepseek-reasoner" -> DeepSeekModels.DeepSeekReasoner
        else -> DeepSeekModels.DeepSeekChat
    }
}
