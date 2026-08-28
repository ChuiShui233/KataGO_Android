package com.chuishui.katago

import com.chuishui.katago.ai.usage.AiUsageEntry
import com.chuishui.katago.ai.usage.AiUsageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiUsageManagerTest {

    @Test
    fun recordsAndCountsToday() {
        val manager = AiUsageManager()
        manager.record(AiUsageEntry(providerId = "groq", model = "m", totalTokens = 50))
        manager.record(AiUsageEntry(providerId = "openai", model = "m", totalTokens = 30))
        assertEquals(2, manager.todayRequests())
        assertTrue(manager.monthRequests() >= 2)
        assertEquals(80, manager.todayTokens())
    }

    @Test
    fun recordResponseFillsDefaults() {
        val manager = AiUsageManager()
        manager.recordResponse(
            providerId = "groq",
            model = "m",
            inputTokens = 10,
            outputTokens = 5,
            totalTokens = 15,
            latencyMs = 30,
            success = true,
        )
        val entry = manager.all().single()
        assertEquals(15, entry.totalTokens)
        assertEquals(30, entry.latencyMs)
        assertTrue(entry.success)
    }

    @Test
    fun totalsTokenDefaultsToInputPlusOutput() {
        val manager = AiUsageManager()
        manager.recordResponse(
            providerId = "g", model = "m",
            inputTokens = 3, outputTokens = 4, totalTokens = null,
            latencyMs = 0, success = true,
        )
        assertEquals(7, manager.todayTokens())
    }

    @Test
    fun providerBreakdown() {
        val manager = AiUsageManager()
        manager.record(AiUsageEntry(providerId = "groq", model = "a", totalTokens = 10))
        manager.record(AiUsageEntry(providerId = "groq", model = "b", totalTokens = 20))
        manager.record(AiUsageEntry(providerId = "openai", model = "c", totalTokens = 5))
        assertEquals(2, manager.providerRequests("groq"))
        assertEquals(30, manager.providerTokens("groq"))
        assertEquals(20, manager.modelTokens("b"))
        assertEquals(35, manager.todayTokens())
    }

    @Test
    fun ringBufferBounded() {
        val manager = AiUsageManager()
        // recordResponse path always stores; ring cap is internal (5000), so just verify no crash + count.
        repeat(6000) { manager.record(AiUsageEntry(providerId = "g", model = "m", totalTokens = 1)) }
        assertTrue(manager.all().size <= 5000)
    }

    @Test
    fun clear() {
        val manager = AiUsageManager()
        manager.record(AiUsageEntry(providerId = "g", model = "m"))
        manager.clear()
        assertEquals(0, manager.all().size)
        assertEquals(0, manager.todayRequests())
    }
}