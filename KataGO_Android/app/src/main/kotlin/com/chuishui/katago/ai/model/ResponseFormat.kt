package com.chuishui.katago.ai.model

/**
 * Response format requested from a provider. Providers that do not support
 * structured output fall back to asking for JSON inside the prompt.
 */
enum class ResponseFormat {
    TEXT,
    JSON,
}
