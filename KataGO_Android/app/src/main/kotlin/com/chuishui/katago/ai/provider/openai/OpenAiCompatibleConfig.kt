package com.chuishui.katago.ai.provider.openai

/** Describes an OpenAI-compatible endpoint (DeepSeek, Groq, OpenRouter, ...). */
data class OpenAiCompatibleConfig(
    val providerId: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val defaultModel: String,
    /** Some gateways require an extra "X-Title"/project header, etc. */
    val extraHeaders: Map<String, String> = emptyMap(),
    /**
     * Groq's API reference deprecates `max_tokens` in favor of
     * `max_completion_tokens`; enable for vendors that follow that schema.
     */
    val useMaxCompletionTokens: Boolean = false,
    /**
     * Reasoning models (gpt-5, kimi-k2 and newer) reject any `temperature`
     * other than their fixed default, so the field must be omitted entirely.
     * False for vendors whose default model is a reasoning model.
     */
    val supportsTemperature: Boolean = true,
)
