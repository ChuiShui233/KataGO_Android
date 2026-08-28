package com.chuishui.katago

import com.chuishui.katago.goai.GoAiPromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardDrawingTest {

    @Test
    fun parsesTips() {
        val text = "推荐走 [tip]D4[/tip] 占据大场，或 [tip]Q16[/tip] 扩张。"
        assertEquals(listOf("D4", "Q16"), GoAiPromptBuilder.parseTips(text))
    }

    @Test
    fun parsesTipsWithWhitespace() {
        assertEquals(listOf("D4"), GoAiPromptBuilder.parseTips("[tip] D4 [/tip]"))
    }

    @Test
    fun returnsEmptyWithoutTips() {
        assertEquals(emptyList<String>(), GoAiPromptBuilder.parseTips("no tips here"))
    }

    @Test
    fun stripsTipsFromText() {
        val clean = GoAiPromptBuilder.stripTips("推荐 [tip]D4[/tip] 与 [tip]Q16[/tip]。")
        assertTrue("D4" !in clean)
        assertTrue("Q16" !in clean)
        assertTrue(clean.contains("推荐"))
    }

    @Test
    fun boardToJsonHasValidShape() {
        val json = GoAiPromptBuilder.boardToJson(
            boardSize = 9,
            grid = MutableList(81) { ' ' }.also { it[40] = 'B' },
            moves = listOf("B E5"),
            currentPlayer = 'W',
            humanColor = 'W',
        )
        assertTrue(json.contains("\"boardSize\": 9"))
        assertTrue(json.contains("\"currentPlayer\": \"W\""))
        assertTrue(json.contains("\"humanColor\": \"W\""))
        assertTrue(json.contains("\"moves\": [\"B E5\"]"))
        assertTrue(json.contains("\"....B....\""))
    }
}