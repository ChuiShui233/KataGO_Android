package com.chuishui.katago.ai

import com.chuishui.katago.ai.config.AiProviderConfig
import com.chuishui.katago.ai.provider.AiProvider
import com.chuishui.katago.ai.provider.anthropic.ClaudeProvider
import com.chuishui.katago.ai.provider.gemini.GeminiProvider
import com.chuishui.katago.ai.provider.openai.DeepSeekProvider
import com.chuishui.katago.ai.provider.openai.GroqProvider
import com.chuishui.katago.ai.provider.openai.KimiProvider
import com.chuishui.katago.ai.provider.openai.OpenAiProvider
import com.chuishui.katago.ai.provider.openai.OpenRouterProvider
import com.chuishui.katago.ai.provider.openai.QwenProvider

/**
 * Builds a configured [AiProvider] from its [AiProviderConfig]. Blank fields
 * fall back to the vendor default. Adding a future vendor means adding a
 * branch here plus an entry in [Companion.KNOWN_PROVIDER_IDS].
 */
object ProviderFactory {

    /** Canonical provider ids in a stable UI order. */
    val KNOWN_PROVIDER_IDS: List<String> = listOf(
        "groq",
        "google",
        "openai",
        "anthropic",
        "deepseek",
        "moonshot",
        "qwen",
        "openrouter",
    )

    fun create(config: AiProviderConfig): AiProvider? {
        val key = config.apiKey
        return when (config.id) {
            "groq" -> GroqProvider.factory(
                key,
                baseUrl = config.baseUrl.ifBlank { GroqProvider.DEFAULT_BASE_URL },
                defaultModel = config.model.ifBlank { GroqProvider.DEFAULT_MODEL },
            )
            "deepseek" -> DeepSeekProvider.factory(
                key,
                baseUrl = config.baseUrl.ifBlank { DeepSeekProvider.DEFAULT_BASE_URL },
                defaultModel = config.model.ifBlank { DeepSeekProvider.DEFAULT_MODEL },
            )
            "qwen" -> QwenProvider.factory(
                key,
                baseUrl = config.baseUrl.ifBlank { QwenProvider.DEFAULT_BASE_URL },
                defaultModel = config.model.ifBlank { QwenProvider.DEFAULT_MODEL },
            )
            "moonshot" -> KimiProvider.factory(
                key,
                baseUrl = config.baseUrl.ifBlank { KimiProvider.DEFAULT_BASE_URL },
                defaultModel = config.model.ifBlank { KimiProvider.DEFAULT_MODEL },
            )
            "openrouter" -> OpenRouterProvider.factory(
                key,
                baseUrl = config.baseUrl.ifBlank { OpenRouterProvider.DEFAULT_BASE_URL },
                defaultModel = config.model.ifBlank { OpenRouterProvider.DEFAULT_MODEL },
            )
            "openai" -> OpenAiProvider.factory(
                key,
                baseUrl = config.baseUrl.ifBlank { OpenAiProvider.DEFAULT_BASE_URL },
                defaultModel = config.model.ifBlank { OpenAiProvider.DEFAULT_MODEL },
            )
            "google" -> GeminiProvider(
                apiKey = key,
                baseUrl = config.baseUrl.ifBlank { GeminiProvider.DEFAULT_BASE_URL },
                defaultModel = config.model.ifBlank { GeminiProvider.DEFAULT_MODEL },
            )
            "anthropic" -> ClaudeProvider(
                apiKey = key,
                baseUrl = config.baseUrl.ifBlank { ClaudeProvider.DEFAULT_BASE_URL },
                defaultModel = config.model.ifBlank { ClaudeProvider.DEFAULT_MODEL },
            )
            else -> null
        }
    }
}