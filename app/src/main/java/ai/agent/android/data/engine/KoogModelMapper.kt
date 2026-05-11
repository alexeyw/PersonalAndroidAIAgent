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

    fun getOpenAIModelIdList(): List<String> = listOf(
        OpenAIModels.Chat.GPT4o.id,
        OpenAIModels.Chat.GPT4oMini.id,
        OpenAIModels.Chat.GPT4_1.id,
        OpenAIModels.Chat.GPT4_1Nano.id,
        OpenAIModels.Chat.GPT4_1Mini.id,
        OpenAIModels.Chat.O1.id,
        OpenAIModels.Chat.O3.id,
        OpenAIModels.Chat.O3Mini.id,
        OpenAIModels.Chat.O4Mini.id,
        OpenAIModels.Chat.GPT5.id,
        OpenAIModels.Chat.GPT5Mini.id,
        OpenAIModels.Chat.GPT5Nano.id,
        OpenAIModels.Chat.GPT5Codex.id,
        OpenAIModels.Chat.GPT5_1.id,
        OpenAIModels.Chat.GPT5_1Codex.id,
        OpenAIModels.Chat.GPT5_1CodexMax.id,
        OpenAIModels.Chat.GPT5_2.id,
        OpenAIModels.Chat.GPT5_2Pro.id,
        OpenAIModels.Chat.GPT5_2Codex.id,
        OpenAIModels.Chat.GPT5_3Codex.id,
        OpenAIModels.Chat.GPT5_4.id,
        OpenAIModels.Chat.GPT5_4Pro.id,
    )

    fun getOpenAIModel(name: String): LLModel = when (name) {
        OpenAIModels.Chat.GPT4o.id -> OpenAIModels.Chat.GPT4o
        OpenAIModels.Chat.GPT4oMini.id -> OpenAIModels.Chat.GPT4oMini
        OpenAIModels.Chat.GPT4_1.id -> OpenAIModels.Chat.GPT4_1
        OpenAIModels.Chat.GPT4_1Nano.id -> OpenAIModels.Chat.GPT4_1Nano
        OpenAIModels.Chat.GPT4_1Mini.id -> OpenAIModels.Chat.GPT4_1Mini
        OpenAIModels.Chat.O1.id -> OpenAIModels.Chat.O1
        OpenAIModels.Chat.O3.id -> OpenAIModels.Chat.O3
        OpenAIModels.Chat.O3Mini.id -> OpenAIModels.Chat.O3Mini
        OpenAIModels.Chat.O4Mini.id -> OpenAIModels.Chat.O4Mini
        OpenAIModels.Chat.GPT5.id -> OpenAIModels.Chat.GPT5
        OpenAIModels.Chat.GPT5Mini.id -> OpenAIModels.Chat.GPT5Mini
        OpenAIModels.Chat.GPT5Nano.id -> OpenAIModels.Chat.GPT5Nano
        OpenAIModels.Chat.GPT5Codex.id -> OpenAIModels.Chat.GPT5Codex
        OpenAIModels.Chat.GPT5_1.id -> OpenAIModels.Chat.GPT5_1
        OpenAIModels.Chat.GPT5_1Codex.id -> OpenAIModels.Chat.GPT5_1Codex
        OpenAIModels.Chat.GPT5_1CodexMax.id -> OpenAIModels.Chat.GPT5_1CodexMax
        OpenAIModels.Chat.GPT5_2.id -> OpenAIModels.Chat.GPT5_2
        OpenAIModels.Chat.GPT5_2Pro.id -> OpenAIModels.Chat.GPT5_2Pro
        OpenAIModels.Chat.GPT5_2Codex.id -> OpenAIModels.Chat.GPT5_2Codex
        OpenAIModels.Chat.GPT5_3Codex.id -> OpenAIModels.Chat.GPT5_3Codex
        OpenAIModels.Chat.GPT5_4.id -> OpenAIModels.Chat.GPT5_4
        OpenAIModels.Chat.GPT5_4Pro.id -> OpenAIModels.Chat.GPT5_4Pro
        else -> OpenAIModels.Chat.GPT5_4
    }

    fun getAnthropicModelIdList(): List<String> = listOf(
        AnthropicModels.Haiku_4_5.id,
        AnthropicModels.Sonnet_4.id,
        AnthropicModels.Sonnet_4_5.id,
        AnthropicModels.Opus_4.id,
        AnthropicModels.Opus_4_1.id,
        AnthropicModels.Opus_4_5.id,
        AnthropicModels.Opus_4_6.id,
    )

    fun getAnthropicModel(name: String): LLModel = when (name) {
        AnthropicModels.Haiku_4_5.id -> AnthropicModels.Haiku_4_5
        AnthropicModels.Sonnet_4.id -> AnthropicModels.Sonnet_4
        AnthropicModels.Sonnet_4_5.id -> AnthropicModels.Sonnet_4_5
        AnthropicModels.Opus_4.id -> AnthropicModels.Opus_4
        AnthropicModels.Opus_4_1.id -> AnthropicModels.Opus_4_1
        AnthropicModels.Opus_4_5.id -> AnthropicModels.Opus_4_5
        AnthropicModels.Opus_4_6.id -> AnthropicModels.Opus_4_6
        else -> AnthropicModels.Sonnet_4_5
    }

    fun getGoogleModelIdList(): List<String> = listOf(
        GoogleModels.Gemini2_0Flash.id,
        GoogleModels.Gemini2_0FlashLite.id,
        GoogleModels.Gemini2_5Pro.id,
        GoogleModels.Gemini2_5Flash.id,
        GoogleModels.Gemini2_5FlashLite.id,
        GoogleModels.Gemini3_Flash_Preview.id,
        GoogleModels.Gemini3_Pro_Preview.id,
    )

    fun getGoogleModel(name: String): LLModel = when (name) {
        GoogleModels.Gemini2_0Flash.id -> GoogleModels.Gemini2_0Flash
        GoogleModels.Gemini2_0FlashLite.id -> GoogleModels.Gemini2_0FlashLite
        GoogleModels.Gemini2_5Pro.id -> GoogleModels.Gemini2_5Pro
        GoogleModels.Gemini2_5Flash.id -> GoogleModels.Gemini2_5Flash
        GoogleModels.Gemini2_5FlashLite.id -> GoogleModels.Gemini2_5FlashLite
        GoogleModels.Gemini3_Flash_Preview.id -> GoogleModels.Gemini3_Flash_Preview
        GoogleModels.Gemini3_Pro_Preview.id -> GoogleModels.Gemini3_Pro_Preview
        else -> GoogleModels.Gemini3_Flash_Preview
    }

    fun getDeepSeekModelIdList(): List<String> = listOf(
        DeepSeekModels.DeepSeekChat.id,
        DeepSeekModels.DeepSeekReasoner.id,
    )

    fun getDeepSeekModel(name: String): LLModel = when (name) {
        DeepSeekModels.DeepSeekChat.id -> DeepSeekModels.DeepSeekChat
        DeepSeekModels.DeepSeekReasoner.id -> DeepSeekModels.DeepSeekReasoner
        else -> DeepSeekModels.DeepSeekChat
    }

    fun getModelForProvider(provider: String): LLModel = when (provider.lowercase()) {
        "openai" -> OpenAIModels.Chat.GPT4oMini
        "anthropic" -> AnthropicModels.Haiku_4_5
        "google" -> GoogleModels.Gemini2_5Flash
        "deepseek" -> DeepSeekModels.DeepSeekChat
        else -> GoogleModels.Gemini2_5Flash
    }
}
