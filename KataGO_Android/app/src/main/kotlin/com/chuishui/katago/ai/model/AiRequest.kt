package com.chuishui.katago.ai.model

/** One prior turn in a multi-turn conversation. Role is "user" or "assistant". */
data class AiChatMessage(
    val role: String,
    val content: String,
    /** Set only for automatic explanations; ties the bubble to a move so it
     *  can be removed when that move is undone. Null for the user dialogue. */
    val moveNumber: Int? = null,
    /** Board snapshot at the time this message was produced, used to render a
     *  cropped local board under an assistant bubble. Null when not needed. */
    val boardSize: Int? = null,
    val boardGrid: List<Char>? = null,
    /** Suggested move vertices highlighted on the main board and in the crop. */
    val tipVertices: List<Int> = emptyList(),
)

/**
 * Uniform request sent to any [com.chuishui.katago.ai.provider.AiProvider].
 *
 * The provider is the only component that knows how to translate this into a
 * vendor-specific HTTP payload. Upper layers never see HTTP details.
 */
data class AiRequest(
    val systemPrompt: String = "",
    val userPrompt: String,
    val maxTokens: Int = 256,
    val temperature: Double = 0.2,
    val model: String? = null,
    val responseFormat: ResponseFormat = ResponseFormat.TEXT,
    val timeoutMs: Long = 30_000,
    /** Prior turns of a multi-turn conversation, sent between system and user. */
    val history: List<AiChatMessage> = emptyList(),
    /**
     * Optional reasoning budget for reasoning-capable models
     * ("low" / "medium" / "high"). Null leaves the provider default.
     */
    val reasoningEffort: String? = null,
)
