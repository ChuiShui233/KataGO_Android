package com.chuishui.katago.ai.provider.openai

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.provider.AiHttpClient

/** OpenRouter — gateway over many models, single OpenAI-compatible endpoint. */
class OpenRouterProvider internal constructor(
    apiKey: String,
    baseUrl: String,
    defaultModel: String,
    httpClient: AiHttpClient,
) : OpenAiCompatibleProvider(
    config = OpenAiCompatibleConfig(
        providerId = "openrouter",
        name = "OpenRouter",
        baseUrl = baseUrl,
        apiKey = apiKey,
        defaultModel = defaultModel,
        extraHeaders = mapOf("HTTP-Referer" to "https://github.com/chuishui/katago-android"),
    ),
    capabilities = AiCapabilities(supportsToolCalling = true),
    httpClient = httpClient,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct:free"

        fun factory(
            apiKey: String,
            baseUrl: String = DEFAULT_BASE_URL,
            defaultModel: String = DEFAULT_MODEL,
            httpClient: AiHttpClient = com.chuishui.katago.ai.provider.HttpUrlConnectionClient(),
        ): OpenRouterProvider = OpenRouterProvider(apiKey, baseUrl, defaultModel, httpClient)
    }
}
