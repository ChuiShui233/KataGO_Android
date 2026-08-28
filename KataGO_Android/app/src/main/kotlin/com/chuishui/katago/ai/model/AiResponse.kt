package com.chuishui.katago.ai.model

/**
 * Uniform response returned by [com.chuishui.katago.ai.provider.AiProvider].
 * Every vendor response (OpenAI JSON, Gemini JSON, Claude JSON, ...) is mapped
 * into this shape inside the provider.
 */
data class AiResponse(
    val providerId: String,
    val model: String,
    val content: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val latencyMs: Long = 0,
    val finishReason: String? = null,
)
