package com.chuishui.katago.goai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoAiCoachParseTest {

    private val type = GoAiEventType.MISTAKE

    @Test
    fun parsesStraightQuotedJson() {
        val json = """{"summary":"S","reason":"R","suggestion":"G"}"""
        val r = parseJsonCommentInternal(json, type)
        assertEquals("S", r.summary)
        assertEquals("R", r.reason)
        assertEquals("G", r.suggestion)
    }

    @Test
    fun parsesFullWidthQuotedJson() {
        val json =
            """"summary":“未按KataGo建议落子，导致影响力不足”，"reason":“B位置缺乏连接与攻击，导致目差扩大” suggestion":“按KataGo建议落在J3”"""
        val r = parseJsonCommentInternal(json, type)
        assertEquals("未按KataGo建议落子，导致影响力不足", r.summary)
        assertEquals("B位置缺乏连接与攻击，导致目差扩大", r.reason)
        assertEquals("按KataGo建议落在J3", r.suggestion)
    }

    @Test
    fun parsesNewlineSeparatedFields() {
        val json =
            """
            {
              "summary": "A",
              "reason": "B",
              "suggestion": "C"
            }
            """.trimIndent()
        val r = parseJsonCommentInternal(json, type)
        assertEquals("A", r.summary)
        assertEquals("B", r.reason)
        assertEquals("C", r.suggestion)
    }

    @Test
    fun fallsBackToRawContentWhenNoFieldsMatch() {
        val content = "本手选择不佳，建议改进。"
        val r = parseJsonCommentInternal(content, type)
        assertTrue(r.summary.contains("本手选择不佳"))
    }

    @Test
    fun detectsGroqJsonValidationError() {
        assertTrue(
            isJsonValidationError(
                com.chuishui.katago.ai.provider.AiException.ServerError(
                    "GroqProvider: Failed to validate JSON. Please adjust your prompt. See 'failed_generation' for more details.",
                    400,
                )
            )
        )
    }

    @Test
    fun ignoresUnrelatedErrors() {
        assertFalse(
            isJsonValidationError(
                com.chuishui.katago.ai.provider.AiException.RateLimited("429 too many requests")
            )
        )
    }

    @Test
    fun detectsEmptyContentError() {
        assertTrue(
            isEmptyContentError(
                com.chuishui.katago.ai.provider.AiException.ParseError("GroqProvider: empty content (finish_reason=length)")
            )
        )
        assertFalse(
            isEmptyContentError(
                com.chuishui.katago.ai.provider.AiException.RateLimited("429 too many requests")
            )
        )
    }
}