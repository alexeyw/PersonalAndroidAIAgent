package ai.agent.android

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import org.junit.Test

class KoogConstantsTest {
    @Test
    fun printConstants() {
        println("OPENAI MODELS:")
        OpenAIModels.Chat::class.java.declaredFields.forEach {
            if (java.lang.reflect.Modifier.isStatic(it.modifiers)) {
                try {
                    val value = it.get(null)
                    if (value is LLModel) {
                        println("  ${it.name} = $value")
                    }
                } catch (e: Exception) {}
            }
        }
        
        println("ANTHROPIC MODELS:")
        AnthropicModels::class.java.declaredFields.forEach {
            if (java.lang.reflect.Modifier.isStatic(it.modifiers)) {
                try {
                    val value = it.get(null)
                    if (value is LLModel) {
                        println("  ${it.name} = $value")
                    }
                } catch (e: Exception) {}
            }
        }
        
        println("GOOGLE MODELS:")
        GoogleModels::class.java.declaredFields.forEach {
            if (java.lang.reflect.Modifier.isStatic(it.modifiers)) {
                try {
                    val value = it.get(null)
                    if (value is LLModel) {
                        println("  ${it.name} = $value")
                    }
                } catch (e: Exception) {}
            }
        }

        println("DEEPSEEK MODELS:")
        DeepSeekModels::class.java.declaredFields.forEach {
            if (java.lang.reflect.Modifier.isStatic(it.modifiers)) {
                try {
                    val value = it.get(null)
                    if (value is LLModel) {
                        println("  ${it.name} = $value")
                    }
                } catch (e: Exception) {}
            }
        }
    }
}
