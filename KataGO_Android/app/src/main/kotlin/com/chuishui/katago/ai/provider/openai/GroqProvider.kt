package com.chuishui.katago.ai.provider.openai

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.provider.AiHttpClient

/**
 * Groq — OpenAI compatible, has a widely used free tier.
 *
 * Follows the Groq API reference: `POST {base}/chat/completions` with a
 * `Bearer` key, `max_completion_tokens` (the legacy `max_tokens` field is
 * deprecated), and `response_format={"type":"json_object"}` for JSON mode.
 */
class GroqProvider internal constructor(
    apiKey: String,
    baseUrl: String,
    defaultModel: String,
    httpClient: AiHttpClient,
) : OpenAiCompatibleProvider(
    config = OpenAiCompatibleConfig(
        providerId = "groq",
        name = "Groq",
        baseUrl = baseUrl,
        apiKey = apiKey,
        defaultModel = defaultModel,
        useMaxCompletionTokens = true,
    ),
    capabilities = AiCapabilities(),
    httpClient = httpClient,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"

        fun factory(
            apiKey: String,
            baseUrl: String = DEFAULT_BASE_URL,
            defaultModel: String = DEFAULT_MODEL,
            httpClient: AiHttpClient = com.chuishui.katago.ai.provider.HttpUrlConnectionClient(),
        ): GroqProvider = GroqProvider(apiKey, baseUrl, defaultModel, httpClient)
    }
}
