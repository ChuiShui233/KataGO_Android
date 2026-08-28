package com.chuishui.katago.ai.model

/**
 * Describes what a provider can do. The [com.chuishui.katago.ai.router.AiRouter]
 * uses these flags to pick a provider for a request (JSON output, vision, ...).
 */
data class AiCapabilities(
    val supportsJson: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsStreaming: Boolean = true,
    val supportsSystemPrompt: Boolean = true,
    val supportsToolCalling: Boolean = false,
    val supportsImages: Boolean = false,
    val supportsTokenUsage: Boolean = true,
)
