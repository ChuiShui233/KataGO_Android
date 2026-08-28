package com.chuishui.katago.ai.provider.openai

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.provider.AiHttpClient

/** Moonshot Kimi — OpenAI compatible chat endpoint. */
class KimiProvider internal constructor(
    apiKey: String,
    baseUrl: String,
    defaultModel: String,
    httpClient: AiHttpClient,
) : OpenAiCompatibleProvider(
    config = OpenAiCompatibleConfig(
        providerId = "moonshot",
        name = "Kimi",
        baseUrl = baseUrl,
        apiKey = apiKey,
        defaultModel = defaultModel,
        supportsTemperature = false,
    ),
    capabilities = AiCapabilities(),
    httpClient = httpClient,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.moonshot.cn/v1"
        const val DEFAULT_MODEL = "kimi-k2"

        fun factory(
            apiKey: String,
            baseUrl: String = DEFAULT_BASE_URL,
            defaultModel: String = DEFAULT_MODEL,
            httpClient: AiHttpClient = com.chuishui.katago.ai.provider.HttpUrlConnectionClient(),
        ): KimiProvider = KimiProvider(apiKey, baseUrl, defaultModel, httpClient)
    }
}
