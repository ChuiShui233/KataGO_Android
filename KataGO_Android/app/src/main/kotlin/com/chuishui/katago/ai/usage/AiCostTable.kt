package com.chuishui.katago.ai.usage

/**
 * Approximate USD cost per 1M tokens by model family. Keys are matched by
 * prefix against the model id. Free providers are 0.0. Numbers are rough and
 * config-driven by design; the app never assumes a provider is permanently
 * free.
 */
object AiCostTable {

    private val INPUT_PER_1M = mapOf(
        "gpt-5" to 1.25,
        "gpt-4" to 2.5,
        "gemini-2.5-flash" to 0.10,
        "gemini-1.5" to 0.10,
        "gemini-2.0" to 1.25,
        "claude-sonnet" to 3.0,
        "claude-sonnet-4" to 3.0,
        "claude" to 3.0,
        "deepseek-v4" to 0.14,
        "deepseek" to 0.14,
        "kimi" to 2.0,
        "qwen-plus" to 0.4,
        "qwen" to 0.4,
    )

    private val OUTPUT_PER_1M = mapOf(
        "gpt-5" to 10.0,
        "gpt-4" to 10.0,
        "gemini-2.5-flash" to 0.40,
        "gemini-1.5" to 0.40,
        "gemini-2.0" to 10.0,
        "claude-sonnet" to 15.0,
        "claude-sonnet-4" to 15.0,
        "claude" to 15.0,
        "deepseek-v4" to 0.28,
        "deepseek" to 0.28,
        "kimi" to 8.0,
        "qwen-plus" to 1.2,
        "qwen" to 1.2,
    )

    private const val DEFAULT_INPUT = 1.0
    private const val DEFAULT_OUTPUT = 2.0

    private fun look(table: Map<String, Double>, model: String): Double? =
        table.entries.firstOrNull { (prefix, _) -> model.lowercase().startsWith(prefix) }?.value

    /** Estimated USD cost of one call. Null traits are treated as unknown (0). */
    fun estimate(model: String, inputTokens: Int?, outputTokens: Int?): Double {
        val inRate = look(INPUT_PER_1M, model) ?: DEFAULT_INPUT
        val outRate = look(OUTPUT_PER_1M, model) ?: DEFAULT_OUTPUT
        val inCount = inputTokens ?: 0
        val outCount = outputTokens ?: 0
        return inCount / 1_000_000.0 * inRate + outCount / 1_000_000.0 * outRate
    }
}