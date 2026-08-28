package com.chuishui.katago.ai.provider.openai

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.provider.AiHttpClient

/** DeepSeek — OpenAI compatible chat endpoint. */
class DeepSeekProvider internal constructor(
    apiKey: String,
    baseUrl: String,
    defaultModel: String,
    httpClient: AiHttpClient,
) : OpenAiCompatibleProvider(
    config = OpenAiCompatibleConfig(
        providerId = "deepseek",
        name = "DeepSeek",
        baseUrl = baseUrl,
        apiKey = apiKey,
        defaultModel = defaultModel,
    ),
    capabilities = AiCapabilities(),
    httpClient = httpClient,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-v4-flash"

        fun factory(
            apiKey: String,
            baseUrl: String = DEFAULT_BASE_URL,
            defaultModel: String = DEFAULT_MODEL,
            httpClient: AiHttpClient = com.chuishui.katago.ai.provider.HttpUrlConnectionClient(),
        ): DeepSeekProvider = DeepSeekProvider(apiKey, baseUrl, defaultModel, httpClient)
    }
}
