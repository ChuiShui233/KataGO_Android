package com.chuishui.katago.ai.config

/**
 * Per-provider configuration, editable from the settings UI.
 */
data class AiProviderConfig(
    val id: String,
    val enabled: Boolean = false,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val maxTokens: Int = 256,
    val temperature: Double = 0.2,
    val priority: Int = 100,
    val enabledForAutoAnalysis: Boolean = true,
    val enabledForUserChat: Boolean = true,
) {
    companion object {
        val DEFAULTS = listOf(
            AiProviderConfig(id = "openai", enabled = false),
            AiProviderConfig(id = "google", enabled = false),
            AiProviderConfig(id = "anthropic", enabled = false),
            AiProviderConfig(id = "deepseek", enabled = false),
            AiProviderConfig(id = "moonshot", enabled = false),
            AiProviderConfig(id = "qwen", enabled = false),
            AiProviderConfig(id = "groq", enabled = false),
            AiProviderConfig(id = "openrouter", enabled = false),
        )
    }
}
