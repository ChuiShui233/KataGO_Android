package com.chuishui.katago.ai.provider.openai

import com.chuishui.katago.ai.model.AiCapabilities
import com.chuishui.katago.ai.provider.AiHttpClient

/** Alibaba Qwen / 通义千问 — OpenAI compatible endpoint. */
class QwenProvider internal constructor(
    apiKey: String,
    baseUrl: String,
    defaultModel: String,
    httpClient: AiHttpClient,
) : OpenAiCompatibleProvider(
    config = OpenAiCompatibleConfig(
        providerId = "qwen",
        name = "Qwen",
        baseUrl = baseUrl,
        apiKey = apiKey,
        defaultModel = defaultModel,
    ),
    capabilities = AiCapabilities(),
    httpClient = httpClient,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        const val DEFAULT_MODEL = "qwen-plus"

        fun factory(
            apiKey: String,
            baseUrl: String = DEFAULT_BASE_URL,
            defaultModel: String = DEFAULT_MODEL,
            httpClient: AiHttpClient = com.chuishui.katago.ai.provider.HttpUrlConnectionClient(),
        ): QwenProvider = QwenProvider(apiKey, baseUrl, defaultModel, httpClient)
    }
}
