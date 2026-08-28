package com.chuishui.katago.ai.provider.openai

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.provider.AiHttpClient

/** OpenAI / ChatGPT — native OpenAI-compatible chat endpoint. */
class OpenAiProvider internal constructor(
    apiKey: String,
    baseUrl: String,
    defaultModel: String,
    httpClient: AiHttpClient,
) : OpenAiCompatibleProvider(
    config = OpenAiCompatibleConfig(
        providerId = "openai",
        name = "OpenAI",
        baseUrl = baseUrl,
        apiKey = apiKey,
        defaultModel = defaultModel,
        useMaxCompletionTokens = true,
        supportsTemperature = false,
    ),
    capabilities = AiCapabilities(supportsVision = true, supportsToolCalling = true),
    httpClient = httpClient,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-5"

        fun factory(
            apiKey: String,
            baseUrl: String = DEFAULT_BASE_URL,
            defaultModel: String = DEFAULT_MODEL,
            httpClient: AiHttpClient = com.chuishui.katago.ai.provider.HttpUrlConnectionClient(),
        ): OpenAiProvider = OpenAiProvider(apiKey, baseUrl, defaultModel, httpClient)
    }
}
