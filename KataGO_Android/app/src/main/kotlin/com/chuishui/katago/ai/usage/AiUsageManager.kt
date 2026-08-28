package com.chuishui.katago.ai.usage

import java.util.Calendar

/** One recorded AI call. */
data class AiUsageEntry(
    val providerId: String,
    val model: String,
    val timestamp: Long = System.currentTimeMillis(),
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCost: Double = 0.0,
    val latencyMs: Long = 0,
    val success: Boolean = true,
) {
    val isToday: Boolean get() = isSameDay(timestamp, System.currentTimeMillis())
    val isThisMonth: Boolean get() = isSameMonth(timestamp, System.currentTimeMillis())

    companion object {
        fun isSameDay(a: Long, b: Long): Boolean {
            val ca = Calendar.getInstance().apply { timeInMillis = a }
            val cb = Calendar.getInstance().apply { timeInMillis = b }
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
        }

        private fun isSameMonth(a: Long, b: Long): Boolean {
            val ca = Calendar.getInstance().apply { timeInMillis = a }
            val cb = Calendar.getInstance().apply { timeInMillis = b }
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH)
        }
    }
}

/**
 * Tracks token usage / estimated cost in memory (bounded ring buffer).
 * Enables today/month totals and per-provider / per-model breakdowns.
 */
class AiUsageManager {

    private val entries = ArrayDeque<AiUsageEntry>()
    private val lock = Any()
    private var dropped = 0

    private val MAX_ENTRIES = 5000

    fun record(entry: AiUsageEntry) {
        synchronized(lock) {
            entries.addLast(entry)
            if (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
                dropped++
            }
        }
    }

    fun recordResponse(
        providerId: String,
        model: String,
        inputTokens: Int?,
        outputTokens: Int?,
        totalTokens: Int?,
        latencyMs: Long,
        success: Boolean,
        cost: Double? = null,
    ) {
        val input = inputTokens ?: 0
        val output = outputTokens ?: 0
        record(
            AiUsageEntry(
                providerId = providerId,
                model = model,
                inputTokens = input,
                outputTokens = output,
                totalTokens = totalTokens ?: (input + output),
                estimatedCost = cost ?: AiCostTable.estimate(model, input, output),
                latencyMs = latencyMs,
                success = success,
            )
        )
    }

    fun all(): List<AiUsageEntry> = synchronized(lock) { entries.toList() }

    fun todayRequests(): Int = synchronized(lock) { entries.count { it.isToday } }

    fun monthRequests(): Int = synchronized(lock) { entries.count { it.isThisMonth } }

    fun todayTokens(): Int = synchronized(lock) { entries.filter { it.isToday }.sumOf { it.totalTokens } }

    fun monthTokens(): Int = synchronized(lock) { entries.filter { it.isThisMonth }.sumOf { it.totalTokens } }

    fun totalEstimatedCostUsd(): Double =
        synchronized(lock) { entries.sumOf { it.estimatedCost } }

    fun providerTokens(providerId: String): Int =
        synchronized(lock) { entries.filter { it.providerId == providerId }.sumOf { it.totalTokens } }

    fun providerRequests(providerId: String): Int =
        synchronized(lock) { entries.count { it.providerId == providerId } }

    fun modelTokens(model: String): Int =
        synchronized(lock) { entries.filter { it.model == model }.sumOf { it.totalTokens } }

    fun clear() = synchronized(lock) { entries.clear(); dropped = 0 }
}